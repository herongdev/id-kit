import Foundation
import UIKit
import AdSupport
import AppTrackingTransparency
import Security
import CommonCrypto

@objc public class DeviceIdNative: NSObject {
    private static var consent: Bool = false
    private static var strict: Bool = false
    private static var salt: String = ""

    private struct CacheEntry {
        let ts: Int64
        let key: String
        let json: String
    }
    private static var cache: CacheEntry? = nil

    @objc public static func setConsent(_ v: Bool) {
        consent = v
    }

    @objc public static func setStrict(_ v: Bool) {
        strict = v
    }

    @objc public static func setSalt(_ s: String) {
        salt = s
    }

    // MARK: - ID Sources

    //仅在 requestATT=true 且状态未决定时，同步等待一次 ATT 请求
    @objc public static func getIDFAOrNull(_ requestATT: Bool) -> String? {
        if #available(iOS 14, *), requestATT,
            ATTrackingManager.trackingAuthorizationStatus == .notDetermined {
            let sem = DispatchSemaphore(value: 0)
            ATTrackingManager.requestTrackingAuthorization { _ in sem.signal() }
            _ = sem.wait(timeout: .now() + 1.0)
        }
        let manager = ASIdentifierManager.shared()
        guard manager.isAdvertisingTrackingEnabled else { return nil }
        let id = manager.advertisingIdentifier.uuidString
        //全 0 视为不可用
        let stripped = id.replacingOccurrences(of: "-", with: "").replacingOccurrences(of: "0", with: "")
        return stripped.isEmpty ? nil : id
    }

    //IDFV 在同 vendor App 全卸载后重装会变化
    @objc public static func getIDFVOrNull() -> String? {
        return UIDevice.current.identifierForVendor?.uuidString
    }

    //首次生成 UUID 写入 Keychain；跨重装尽量保留
    @objc public static func getKeychainUUID() -> String {
        let service = "uts.sdk.modules.idKit"
        let account = "device.guid"
        if let data = read(service: service, account: account),
           let s = String(data: data, encoding: .utf8), !s.isEmpty {
            return s
        }
        let newVal = UUID().uuidString
        _ = save(service: service, account: account, data: Data(newVal.utf8))
        return newVal
    }

    //原生实现 SHA-256，供统一脱敏
    @objc public static func sha256Hex(_ input: String) -> String {
        let data = input.data(using: .utf8) ?? Data()
        var hash = [UInt8](repeating: 0, count: Int(CC_SHA256_DIGEST_LENGTH))
        data.withUnsafeBytes { buf in
            _ = CC_SHA256(buf.baseAddress, CC_LONG(data.count), &hash)
        }
        return hash.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - High-level JSON APIs

    //主入口——返回各 ID 与 best（JSON）
    // optionsJson:
    // {
    //   include?: ["idfa","idfv","guid"],
    //   prefer?: ["idfa","idfv","guid"],
    //   exposeRaw?: boolean,
    //   ttlMs?: number,
    //   policy?: "global" | "cn" | "strict",
    //   requestATT?: boolean
    // }
    @objc public static func getIdCodesJSON(_ optionsJson: String) -> String {
        let now = nowMs()
        //解析 options
        let opts = (try? parseOptions(optionsJson)) ?? Options()

        //缓存命中（相同 key+状态，且未过期）
        let ckey = cacheKey(optionsJson: optionsJson, exposeRaw: opts.exposeRaw)
        if let c = cache, (now - c.ts) < Int64(opts.ttlMs), c.key == ckey {
            return c.json
        }

        //计算策略后的 include/order
        let policy = opts.policy ?? "global"
        var includeEff = opts.include ?? defaultInclude(policy: policy)
        var orderEff = opts.prefer ?? includeEff

        var effectiveStrict = strict
        if policy == "strict" { effectiveStrict = true }

        if policy == "cn" {
            includeEff = includeEff.filter { ["idfv", "guid"].contains($0) }
            orderEff = ["idfv", "guid"].filter { includeEff.contains($0) }
        } else if policy == "global" {
            includeEff = includeEff.filter { ["idfa", "idfv", "guid"].contains($0) }
            orderEff = ["idfa", "idfv", "guid"].filter { includeEff.contains($0) }
        } else if policy == "strict" {
            includeEff = includeEff.filter { ["idfv", "guid"].contains($0) }
            orderEff = ["idfv", "guid"].filter { includeEff.contains($0) }
        }

        //未同意 + 严格模式 -> 仅非广告类
        if effectiveStrict && !consent {
            includeEff = includeEff.filter { $0 != "idfa" }
            orderEff = orderEff.filter { $0 != "idfa" }
        }

        //开始取值并构建结果
        var result: [String: Any] = [
            "consent": consent,
            "ts": now
        ]

        // idfv
        if includeEff.contains("idfv") {
            let idfv = getIDFVOrNull()
            if let v = idfv, !v.isEmpty {
                result["idfv"] = buildValue(source: "idfv", value: v, exposeRaw: opts.exposeRaw, limited: false, msg: nil)
            } else {
                let uuid = getKeychainUUID()
                result["idfv"] = buildValue(source: "idfv", value: uuid, exposeRaw: opts.exposeRaw, limited: false, msg: "fallback:keychain")
            }
        }

        // idfa
        if includeEff.contains("idfa") {
            let idfa = effectiveStrict ? nil : getIDFAOrNull(opts.requestATT)
            let limited = (idfa == nil)
            let msg = limited ? "no-permission-or-limited" : nil
            result["idfa"] = buildValue(source: "idfa", value: idfa, exposeRaw: false, limited: limited, msg: msg)
        }

        // guid
        if includeEff.contains("guid") {
            let guid = getKeychainUUID()
            result["guid"] = buildValue(source: "guid", value: guid, exposeRaw: opts.exposeRaw, limited: false, msg: nil)
        }

        //best 选择
        var best: String? = nil
        for k in orderEff {
            if let iv = result[k] as? [String: Any],
               let available = iv["available"] as? Bool, available {
                best = k; break
            }
        }
        result["best"] = best as Any

        let json = toJSONString(result)
        cache = CacheEntry(ts: now, key: ckey, json: json)
        return json
    }

    //返回 best 的 IdValue（JSON）
    @objc public static func getBestIdJSON(_ optionsJson: String) -> String {
        let full = getIdCodesJSON(optionsJson)
        guard let obj = toObject(full) as? [String: Any] else {
            return toJSONString(["available": false, "source": "none", "message": "no id available"])
        }
        let bestKey = obj["best"] as? String
        if let k = bestKey, let val = obj[k] {
            return toJSONString(val)
        }
        return toJSONString(["available": false, "source": "none", "message": "no id available"])
    }

    // MARK: - Helpers

    private struct Options {
        var include: [String]?
        var prefer: [String]?
        var exposeRaw: Bool = false
        var ttlMs: Double = 24 * 3600 * 1000
        var policy: String? = "global"
        var requestATT: Bool = false
    }

    //解析 JSON 选项
    private static func parseOptions(_ json: String) throws -> Options {
        guard let data = json.data(using: .utf8),
              let dict = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return Options()
        }
        var o = Options()
        if let inc = dict["include"] as? [String] { o.include = inc }
        if let pref = dict["prefer"] as? [String] { o.prefer = pref }
        if let ex = dict["exposeRaw"] as? Bool { o.exposeRaw = ex }
        if let ttl = dict["ttlMs"] as? Double { o.ttlMs = ttl }
        if let pol = dict["policy"] as? String { o.policy = pol }
        if let req = dict["requestATT"] as? Bool { o.requestATT = req }
        return o
    }

    //不同策略的默认 include
    private static func defaultInclude(policy: String) -> [String] {
        switch policy {
        case "cn":     return ["idfv", "guid"]
        case "strict": return ["idfv", "guid"]
        default:       return ["idfa", "idfv", "guid"]
        }
    }

    //统一构造 IdValue（含原生哈希）
    private static func buildValue(source: String, value: String?, exposeRaw: Bool, limited: Bool, msg: String?) -> [String: Any] {
        var out: [String: Any] = [
            "available": value != nil,
            "limited": limited,
            "source": source
        ]
        if let m = msg { out["message"] = m }
        if let v = value {
            let salted = salt.isEmpty ? v : "\(salt):\(v)"
            out["hash"] = sha256Hex(salted)
            if exposeRaw { out["value"] = v }
        }
        return out
    }

    private static func nowMs() -> Int64 {
        return Int64(Date().timeIntervalSince1970 * 1000.0)
    }

    //缓存 key 要包含状态（salt/consent/strict）及是否暴露明文
    private static func cacheKey(optionsJson: String, exposeRaw: Bool) -> String {
        return "o:\(optionsJson)#c:\(consent ? 1 : 0)#s:\(strict ? 1 : 0)#x:\(exposeRaw ? 1 : 0)#salt:\(salt)"
    }

    // MARK: - Keychain helpers

    private static func read(service: String, account: String) -> Data? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]
        var out: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &out)
        if status == errSecSuccess, let data = out as? Data { return data }
        return nil
    }

    @discardableResult
    private static func save(service: String, account: String, data: Data) -> Bool {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        let status = SecItemAdd(query as CFDictionary, nil)
        if status == errSecDuplicateItem {
            let find: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword,
                kSecAttrService as String: service,
                kSecAttrAccount as String: account
            ]
            let attrs: [String: Any] = [kSecValueData as String: data]
            return SecItemUpdate(find as CFDictionary, attrs as CFDictionary) == errSecSuccess
        }
        return status == errSecSuccess
    }

    // MARK: - JSON helpers

    private static func toJSONString(_ any: Any) -> String {
        guard JSONSerialization.isValidJSONObject(any),
              let data = try? JSONSerialization.data(withJSONObject: any, options: []) else {
            return "{}"
        }
        return String(data: data, encoding: .utf8) ?? "{}"
    }

    private static func toObject(_ json: String) -> Any? {
        guard let data = json.data(using: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: data) else { return nil }
        return obj
    }
		
		//与安卓同名，iOS 用来在“同意后”尝试预取 IDFA（可能触发 ATT）
		@objc public static func prefetchOaidAfterConsent() {
		    _ = getIDFAOrNull(true)
		}
		
		//与安卓同名别名；内部沿用已有 salt 变量
		@objc public static func setSaltValue(_ s: String) {
		    setSalt(s)
		}
		
		//与安卓签名一致的最简入口；原生侧完成策略、兜底、哈希与裁决
		@objc public static func getBestId(_ prefer: [String]?, _ exposeRaw: Bool) -> String {
		    //组装最小选项，走现有 JSON 流程，保持与安卓一致的行为
		    let opts: [String: Any] = [
		        "prefer": prefer ?? ["idfa", "idfv", "guid"],
		        "exposeRaw": exposeRaw,
		        //默认 24h 缓存；策略与 include 由原生内部决定（global/strict/cn）
		        "ttlMs": 24 * 3600 * 1000
		    ]
		    let data = try? JSONSerialization.data(withJSONObject: opts, options: [])
		    let json = String(data: data ?? Data("{}".utf8), encoding: .utf8) ?? "{}"
		    return getBestIdJSON(json)
		}

}
