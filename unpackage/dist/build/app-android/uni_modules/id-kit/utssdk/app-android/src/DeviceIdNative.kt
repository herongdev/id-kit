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
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException


/**
 * 原生设备ID聚合（Android）
 * 注意：所有获取方法可能返回 null；由本类统一封装并向 UTS 返回页面期望结构
 */
object DeviceIdNative {

    // ===== 全局状态（由 UTS 调用 setConsent/setSaltValue 设置） =====
    @Volatile
    private var consent: Boolean = false

    @Volatile
    private var salt: String = ""

    // 简单缓存（24h）
    @Volatile
    private var cacheTs: Long = 0L

    @Volatile
    private var cacheData: JSONObject? = null

    // ===== Context 工具 =====
    private fun ctx(): Context? {
        val app = UTSAndroid.getAppContext()
        if (app != null) return app
        val act = UTSAndroid.getUniActivity()
        return act?.applicationContext ?: act
    }

    // ===== 对外状态设置 =====
    @JvmStatic
    fun setConsent(agree: Boolean) {
        consent = agree
    }

    @JvmStatic
    fun setSaltValue(s: String) {
        salt = s
    }

    // ===== OAID 预取（建议在同意后调用一次） =====
    @JvmStatic
    fun prefetchOaidAfterConsent() {
        try {
            val app: Application = (UTSAndroid.getAppContext() as? Application)
                ?: UTSAndroid.getUniActivity()?.application
                ?: return
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                try {
                    DeviceIdentifier.register(app)
                } catch (e: Throwable) {
                    console.log("OAID prefetch error(inner): ${e.message}")
                } finally {
                    executor.shutdownNow()
                }
            }
        } catch (e: Throwable) {
            console.log("OAID prefetch error: ${e.message}")
        }
    }

    // ===== 基础取值 =====

    /** OAID（国内 ROM 优先） */
    @JvmStatic
    fun getOAIDOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            if (!DeviceID.supportedOAID(c)) return null
            val id = DeviceIdentifier.getOAID(c)
            if (id.isNullOrBlank()) null else id
        } catch (e: Throwable) {
            console.log("OAID error: ${e.message}")
            null
        }
    }

    /** App Set ID（后台线程 + 超时） */
    @JvmStatic
    fun getAppSetIdOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<String?> {
                    try {
                        val client = AppSet.getClient(c)
                        val info: AppSetIdInfo = try {
                            Tasks.await(client.appSetIdInfo, 1500, TimeUnit.MILLISECONDS)
                        } catch (toe: TimeoutException) {
                            console.log("AppSetID timeout")
                            return@submit null
                        }
                        val id = info.id
                        if (id.isNullOrBlank()) null else id
                    } catch (t: Throwable) {
                        console.log("AppSetID error(inner): ${t.message}")
                        null
                    }
                }
                future.get(1600, TimeUnit.MILLISECONDS)
            } finally {
                executor.shutdownNow()
            }
        } catch (e: Throwable) {
            console.log("AppSetID error: ${e.message}")
            null
        }
    }

    /** GAID/AAID（后台线程 + 超时；限制个性化时返回 null） */
    @JvmStatic
    fun getAdvertisingIdOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            val executor = Executors.newSingleThreadExecutor()
            try {
                val future = executor.submit<String?> {
                    try {
                        val info = AdvertisingIdClient.getAdvertisingIdInfo(c) ?: return@submit null
                        if (info.isLimitAdTrackingEnabled) return@submit null
                        val id = info.id
                        if (id.isNullOrBlank()) null else id
                    } catch (t: Throwable) {
                        console.log("GAID error(inner): ${t.message}")
                        null
                    }
                }
                future.get(1500, TimeUnit.MILLISECONDS)
            } finally {
                executor.shutdownNow()
            }
        } catch (e: Throwable) {
            console.log("GAID error: ${e.message}")
            null
        }
    }

    /** ANDROID_ID（过滤空/unknown/老缺陷值） */
    @SuppressLint("HardwareIds")
    @JvmStatic
    fun getAndroidIdOrNull(): String? {
        return try {
            val c = ctx() ?: return null
            val id = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID)
            val bad = "9774d56d682e549c"
            val v = id?.trim()?.lowercase()
            if (v.isNullOrEmpty() || v == "unknown" || v == bad) null else id
        } catch (e: Throwable) {
            console.log("ANDROID_ID error: ${e.message}")
            null
        }
    }

    /** 伪 ID（仅作为弱可用项） */
    @JvmStatic
    fun getPseudoId(): String {
        return try {
            val text = android.os.Build.BOARD + android.os.Build.BRAND +
                    android.os.Build.DEVICE + android.os.Build.DISPLAY +
                    android.os.Build.FINGERPRINT + "uni-id-kit"
            val hash = text.hashCode()
            "pseudo:" + Integer.toHexString(hash)
        } catch (e: Throwable) {
            "pseudo:null"
        }
    }

    /** 原生持久化 GUID（首次生成后缓存到 SharedPreferences） */
    private fun getOrCreateGuid(context: Context): String {
        val sp = context.getSharedPreferences("uni_id_kit", Context.MODE_PRIVATE)
        val v = sp.getString("guid", null)
        if (!v.isNullOrEmpty()) return v
        val g = "app:" + UUID.randomUUID().toString()
        sp.edit().putString("guid", g).apply()
        return g
    }

    // ===== Hash 工具 =====
    @JvmStatic
    fun hashSha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    private fun makeHash(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        return hashSha256("$raw|$salt")
    }

    // ===== 页面期望结构构造 =====
    private fun buildId(
        source: String,
        raw: String?,
        exposeRaw: Boolean,
        limited: Boolean?,
        message: String?
    ): JSONObject {
        val o = JSONObject()
        val available = !raw.isNullOrEmpty()
        o.put("source", source)
        o.put("available", available)
        o.put("limited", limited ?: JSONObject.NULL)
        o.put("value", if (exposeRaw) raw else JSONObject.NULL)
        o.put("hash", makeHash(raw))
        o.put("message", message ?: JSONObject.NULL)
        return o
    }

    private fun pick(res: JSONObject, key: String): JSONObject? {
        return when (key) {
            "oaid" -> res.optJSONObject("oaid")
            "appSetId" -> res.optJSONObject("appSetId")
            "androidId" -> res.optJSONObject("androidId")
            "guid" -> res.optJSONObject("guid")
            "pseudoId" -> res.optJSONObject("pseudoId")
            "aaid" -> res.optJSONObject("aaid")
            else -> null
        }
    }

    /**
     * 对外总控：根据 prefer/exposeRaw 返回“最佳 ID”的页面结构
     * prefer 允许为 null/空数组；内部会做默认优先级与越界防御
     */
    @JvmStatic
    fun getBestId(prefer: Array<String>?, exposeRaw: Boolean): String {
        val order = (prefer?.takeIf { it.isNotEmpty() }
            ?: arrayOf("oaid", "appSetId", "androidId", "guid", "pseudoId", "aaid"))

        val now = System.currentTimeMillis()
        if (consent && cacheData != null && now - cacheTs < 24 * 3600 * 1000) {
            val bestKey = cacheData!!.optString("best", "")
						 if (bestKey.isNotEmpty()) {
						     val hit = pick(cacheData!!, bestKey)
						     if (hit != null && hit.optBoolean("available", false)) return hit.toString()
						 }
        }

        val res = JSONObject()
        res.put("consent", consent)
        res.put("ts", now)

        if (!consent) {
					 val none = buildId("none", null, false, null, "consent=false")
					 cacheData = res
					 cacheTs = now
					 return none.toString()
        }

        val c = ctx()
        val guid = c?.let { getOrCreateGuid(it) }

        val oaid = getOAIDOrNull()
        val appset = getAppSetIdOrNull()
        val aaid = getAdvertisingIdOrNull()
        val androidId = getAndroidIdOrNull()
        val pseudo = getPseudoId()

        res.put(
            "oaid",
            buildId("oaid", oaid, false, true, if (oaid == null) "unavailable" else null)
        )
        res.put(
            "appSetId",
            buildId(
                "appSetId",
                appset,
                exposeRaw,
                null,
                if (appset == null) "unavailable" else null
            )
        )
        res.put(
            "androidId",
            buildId(
                "androidId",
                androidId,
                exposeRaw,
                false,
                if (androidId == null) "unavailable" else null
            )
        )
        res.put(
            "aaid",
            buildId("aaid", aaid, false, true, if (aaid == null) "unavailable" else null)
        )
        res.put("pseudoId", buildId("pseudoId", pseudo, exposeRaw, false, null))
        res.put("guid", buildId("guid", guid, exposeRaw, false, null))

        var best: String? = null
        for (k in order) {
            val v = pick(res, k)
            if (v != null && v.optBoolean("available", false)) {
                best = k; break
            }
        }
        res.put("best", best ?: JSONObject.NULL)

        cacheData = res
        cacheTs = now

        if (best != null) return res.getJSONObject(best).toString()
        return buildId("none", null, false, null, "no id available").toString()
    }
}
