
import Foundation
import AdSupport
import AppTrackingTransparency
import DCloudUTSFoundation

@objc public class DeviceIdNative: NSObject {

    // 复杂逻辑：请求并读取 IDFA（如果未授权或限制，则返回 nil）
    @objc public static func getIDFAOrNull(_ requestATT: Bool) -> String? {
        if #available(iOS 14, *), requestATT {
            let sem = DispatchSemaphore(value: 0)
            ATTrackingManager.requestTrackingAuthorization { _ in sem.signal() }
            _ = sem.wait(timeout: .now() + 1.0)
        }
        guard ASIdentifierManager.shared().isAdvertisingTrackingEnabled else { return nil }
        let idfa = ASIdentifierManager.shared().advertisingIdentifier.uuidString
        return (idfa == "00000000-0000-0000-0000-000000000000") ? nil : idfa
    }

    /** IDFV（同 Vendor 稳定） */
    @objc public static func getIDFVOrNull() -> String? {
        return UIDevice.current.identifierForVendor?.uuidString
    }

    /** Keychain 持久化 UUID（单 App 近永久） */
    @objc public static func getKeychainUUID() -> String {
        let service = "uts.idkit.uuid"
        let account = "device"
        if let data = Keychain.read(service: service, account: account),
           let s = String(data: data, encoding: .utf8), !s.isEmpty {
            return s
        }
        let uuid = UUID().uuidString
        Keychain.save(service: service, account: account, data: Data(uuid.utf8))
        return uuid
    }
}

// 极简 Keychain 封装
fileprivate enum Keychain {
    static func save(service: String, account: String, data: Data) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        ]
        SecItemDelete(query as CFDictionary)
        SecItemAdd(query as CFDictionary, nil)
    }

    static func read(service: String, account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: kCFBooleanTrue as Any,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecSuccess { return result as? Data }
        return nil
    }
}
