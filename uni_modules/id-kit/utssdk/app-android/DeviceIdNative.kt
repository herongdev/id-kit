package uts.sdk.modules.idKit

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.provider.Settings
import com.github.gzuliyujiang.oaid.DeviceID
import com.github.gzuliyujiang.oaid.DeviceIdentifier
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.appset.AppSet
import com.google.android.gms.appset.AppSetIdInfo
import com.google.android.gms.tasks.Tasks
import io.dcloud.uts.UTSAndroid
import io.dcloud.uts.console
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * 原生设备ID聚合（Android）
 * 注意：以下方法均可能返回 null；由 UTS 层做兜底与优先级控制
 */
object DeviceIdNative {

    // 统一拿到 Context（优先 Application Context，避免潜在泄露；兜底 Activity）
    private fun ctx(): Context? {
        val app = UTSAndroid.getAppContext()
        if (app != null) return app
        val act = UTSAndroid.getUniActivity()
        return act?.applicationContext ?: act
    }

    // 官方建议在【用户已同意隐私】后调用一次，预取并缓存 OAID（减少同步读取波动）
		 @JvmStatic
		 fun prefetchOaidAfterConsent() {
				 try {
						 val app: Application = (UTSAndroid.getAppContext() as? Application)
								 ?: UTSAndroid.getUniActivity()?.application
								 ?: return
						 DeviceIdentifier.register(app)
				 } catch (e: Throwable) {
						 console.log("OAID prefetch error: ${e.message}")
				 }
		 }


    /** OAID（国内 ROM 优先） */
    @JvmStatic
    fun getOAIDOrNull(): String? {
        return try {
            val c = ctx() ?: return null

            // 可选短路判断，设备/系统不支持时直接返回 null，减少无谓调用与日志噪音
            if (!DeviceID.supportedOAID(c)) return null

            val id = DeviceIdentifier.getOAID(c)
            if (id.isNullOrBlank()) null else id
        } catch (e: Throwable) {
            console.log("OAID error: ${e.message}")
            null
        }
    }

    /** App Set ID（开发者作用域优先；若仅提供 App 作用域也返回，以保证可用性） */
    @JvmStatic
    fun getAppSetIdOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            val client = AppSet.getClient(c)

            // 避免极端情况下 await 卡住，设置 1.5s 超时
            val info: AppSetIdInfo = try {
                Tasks.await(client.appSetIdInfo, 1500, TimeUnit.MILLISECONDS)
            } catch (toe: TimeoutException) {
                console.log("AppSetID timeout")
                return null
            }

            val scope = info.scope // 1: developer, 2: app
            val id = info.id

            // 优先 Developer 作用域；若仅返回 App 作用域，也按可用值返回
            if (id.isNullOrBlank()) null else id
        } catch (e: Throwable) {
            console.log("AppSetID error: ${e.message}")
            null
        }
    }

    /** GAID/AAID（用户限制个性化广告时应视为不可用） */
    @JvmStatic
    fun getAdvertisingIdOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            val info = AdvertisingIdClient.getAdvertisingIdInfo(c) ?: return null

            // 严格合规——用户限制个性化广告时不返回 GAID
            if (info.isLimitAdTrackingEnabled) return null

            val id = info.id
            if (id.isNullOrBlank()) null else id
        } catch (e: Throwable) {
            console.log("GAID error: ${e.message}")
            null
        }
    }

    /** ANDROID_ID（自 Android 8 起与签名/用户/设备相关；少数老机型存在固定缺陷值） */
    @SuppressLint("HardwareIds")
    @JvmStatic
    fun getAndroidIdOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            val id = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID)

            // 过滤空、unknown、以及历史缺陷值（早期设备常见）
            val bad = "9774d56d682e549c"
            val v = id?.trim()?.lowercase()
            if (v.isNullOrEmpty() || v == "unknown" || v == bad) null else id
        } catch (e: Throwable) {
            console.log("ANDROID_ID error: ${e.message}")
            null
        }
    }

    @JvmStatic
    fun getPseudoId(): String {
        return try {
            val text = android.os.Build.BOARD + android.os.Build.BRAND +
                       android.os.Build.DEVICE + android.os.Build.DISPLAY +
                       android.os.Build.FINGERPRINT + "uni-id-kit"
            val hash = text.hashCode() // Java 自带的稳定 hash 实现
            "pseudo:" + Integer.toHexString(hash)
        } catch (e: Throwable) {
            "pseudo:null"
        }
    }

}
