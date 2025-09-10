@file:Suppress("UNCHECKED_CAST", "USELESS_CAST", "INAPPLICABLE_JVM_NAME", "UNUSED_ANONYMOUS_PARAMETER", "NAME_SHADOWING", "UNNECESSARY_NOT_NULL_ASSERTION")
package uts.sdk.modules.idKit
import android.app.Application
import android.provider.Settings
import com.github.gzuliyujiang.oaid.DeviceID
import com.github.gzuliyujiang.oaid.DeviceIdentifier
import com.github.gzuliyujiang.oaid.IGetter
import io.dcloud.uniapp.*
import io.dcloud.uniapp.extapi.*
import io.dcloud.uniapp.framework.*
import io.dcloud.uniapp.runtime.*
import io.dcloud.uniapp.vue.*
import io.dcloud.uniapp.vue.shared.*
import io.dcloud.uts.*
import io.dcloud.uts.Map
import io.dcloud.uts.Set
import io.dcloud.uts.UTSAndroid
import java.lang.Exception
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import io.dcloud.uniapp.extapi.getStorageSync as uni_getStorageSync
import io.dcloud.uniapp.extapi.setStorageSync as uni_setStorageSync
open class IdValue (
    open var value: String? = null,
    open var hash: String? = null,
    @JsonNotNull
    open var available: Boolean = false,
    open var limited: Boolean? = null,
    @JsonNotNull
    open var source: String,
    open var message: String? = null,
) : UTSObject()
open class IdCodesResult (
    open var oaid: IdValue? = null,
    open var aaid: IdValue? = null,
    open var androidId: IdValue? = null,
    open var idfv: IdValue? = null,
    open var widevineId: IdValue? = null,
    open var pseudoId: IdValue? = null,
    open var imei: IdValue? = null,
    open var guid: IdValue? = null,
    open var best: String? = null,
    @JsonNotNull
    open var consent: Boolean = false,
    @JsonNotNull
    open var ts: Number,
) : UTSObject()
fun get(key: String): String? {
    try {
        val value = uni_getStorageSync(key)
        return if (value != null && value != "") {
            value as String
        } else {
            null
        }
    }
     catch (e: Throwable) {
        return null
    }
}
fun set(key: String, kVal: String) {
    try {
        uni_setStorageSync(key, kVal)
    }
     catch (e: Throwable) {}
}
fun toHex(): UTSArray<String> {
    val h: UTSArray<String> = _uA()
    run {
        var i: Number = 0
        while(i < 256){
            h[i] = (i + 0x100).toString(16).substring(1)
            i++
        }
    }
    return h
}
fun formatUuid(b: Uint8Array): String {
    b[6] = (b[6] and 0x0f) or 0x40
    b[8] = (b[8] and 0x3f) or 0x80
    val H = toHex()
    return (H[b[0]] + H[b[1]] + H[b[2]] + H[b[3]] + "-" + H[b[4]] + H[b[5]] + "-" + H[b[6]] + H[b[7]] + "-" + H[b[8]] + H[b[9]] + "-" + H[b[10]] + H[b[11]] + H[b[12]] + H[b[13]] + H[b[14]] + H[b[15]])
}
fun getRandom16(): Uint8Array {
    val sr = java.security.SecureRandom()
    val arr: UTSArray<Number> = UTSArray<Number>(16)
    run {
        var i: Number = 0
        while(i < 16){
            arr[i] = sr.nextInt(256)
            i++
        }
    }
    return Uint8Array(arr)
}
fun uuid4(): String {
    return formatUuid(getRandom16())
}
fun getAndroidIdRaw(): String? {
    try {
        val context = UTSAndroid.getAppContext()!!
        val contentResolver = context.getContentResolver()
        val id = Settings.Secure.getString(contentResolver, "android_id") as String
        return if (id !== "") {
            "android:" + id
        } else {
            null
        }
    }
     catch (e: Throwable) {
        return null
    }
}
open class OAIDResult (
    open var value: String? = null,
    open var limited: Boolean? = null,
    @JsonNotNull
    open var source: String,
    open var message: String? = null,
) : UTSObject()
fun getOAIDRaw(ctx: android.content.Context): UTSPromise<OAIDResult> {
    return wrapUTSPromise(suspend w@{
            try {
                val id = DeviceIdentifier.getOAID(ctx) as String
                if (id != "") {
                    return@w OAIDResult(value = id, source = "GZU")
                }
            }
             catch (e: Throwable) {}
            try {
                return@w await(UTSPromise(fun(resolve, _reject){
                    DeviceID.getOAID(ctx, object : IGetter {
                        override fun onOAIDGetComplete(result: String) {
                            resolve(OAIDResult(value = result, source = "GZU"))
                        }
                        override fun onOAIDGetError(error: Exception) {
                            resolve(OAIDResult(source = "NONE", message = ("" + error)))
                        }
                    })
                }
                ))
            }
             catch (e: Throwable) {
                return@w OAIDResult(source = "NONE", message = ("" + e))
            }
    })
}
open class AAIDResult (
    open var value: String? = null,
    open var limited: Boolean? = null,
    open var message: String? = null,
) : UTSObject()
fun getAAIDRaw(ctx: android.content.Context): UTSPromise<AAIDResult> {
    return wrapUTSPromise(suspend w@{
            try {
                return@w AAIDResult(message = "AAID not integrated")
            }
             catch (e: Throwable) {
                return@w AAIDResult(message = "" + e)
            }
    })
}
fun getPseudoIdRaw(): String? {
    try {
        val text = android.os.Build.BOARD + android.os.Build.BRAND + android.os.Build.DEVICE + android.os.Build.DISPLAY + android.os.Build.FINGERPRINT + "uni-id-kit"
        var hash: Number = 0
        run {
            var i: Number = 0
            while(i < text.length){
                val chr = (text.charCodeAt(i) as Number)
                hash = ((hash shl 5) - hash + chr) or 0
                i++
            }
        }
        return "pseudo:" + (hash ushr 0).toString(16)
    }
     catch (e: Throwable) {
        return null
    }
}
open class CacheType (
    @JsonNotNull
    open var ts: Number,
    @JsonNotNull
    open var data: IdCodesResult,
) : UTSObject()
var _consent = false
var _salt = ""
var _cache: CacheType? = null
val DEFAULT_ORDER = _uA(
    "oaid",
    "androidId",
    "guid",
    "pseudoId",
    "aaid"
)
fun sha256HexSync(input: String): String {
    var h1: Number = 0x811c9dc5
    run {
        var i: Number = 0
        while(i < input.length){
            h1 = h1 xor (input.charCodeAt(i) as Number)
            h1 = (h1 + ((h1 shl 1) + (h1 shl 4) + (h1 shl 7) + (h1 shl 8) + (h1 shl 24))) or 0
            i++
        }
    }
    val hex = (h1 ushr 0).toString(16)
    var outHex = hex
    if ((hex.length % 2) == 1) {
        outHex = ("0" + hex)
    }
    return outHex
}
fun buildSync(source: String, value: String?, exposeRaw: Boolean?, limited: Boolean?, msg: String?): IdValue {
    val available = value != null
    val out: UTSJSONObject = _uO("available" to available, "limited" to (limited === true), "source" to source)
    if (msg != null) {
        (out as UTSJSONObject)["message"] = msg
    }
    if (available) {
        val h = sha256HexSync((value as String) + _salt)
        (out as UTSJSONObject)["hash"] = h
        val showRaw = (exposeRaw === true)
        if (showRaw) {
            (out as UTSJSONObject)["value"] = value as String
        }
    }
    return out as IdValue
}
fun register(options: UTSJSONObject?): UTSPromise<UTSJSONObject> {
    return wrapUTSPromise(suspend w@{
            try {
                DeviceIdentifier.register(UTSAndroid.getUniActivity()!!!!.getApplication()!!!! as Application)
            }
             catch (e: Throwable) {}
            _consent = true
            return@w object : UTSJSONObject() {
                var consent = _consent
            }
    })
}
fun setSalt(salt: String): Unit {
    _salt = salt
}
fun getAndroidId(exposeRaw: Boolean = false): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val v = getAndroidIdRaw()
            return@w buildSync("androidId", v, exposeRaw, false, null)
    })
}
fun getGuid(exposeRaw: Boolean = false): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            var guid = get("UNIIDKIT_GUID") as String?
            if (guid == null || guid.length === 0) {
                guid = "app:" + uuid4()
                set("UNIIDKIT_GUID", guid)
            }
            return@w buildSync("guid", guid as String, exposeRaw, false, null)
    })
}
fun getOAID(): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val ctx = UTSAndroid.getUniActivity()!!!!
            val r = await(getOAIDRaw(ctx))
            return@w buildSync("oaid", r.value ?: null, false, r.limited ?: null, r.message ?: null)
    })
}
fun getAAID(): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val ctx = UTSAndroid.getUniActivity()!!!!
            val r = await(getAAIDRaw(ctx))
            return@w buildSync("aaid", r.value ?: null, false, r.limited ?: null, r.message ?: null)
    })
}
fun getIdCodes(options: UTSJSONObject?): UTSPromise<IdCodesResult> {
    return wrapUTSPromise(suspend w@{
            val include = (options?.getArray<String>("include") ?: DEFAULT_ORDER) as UTSArray<String>
            val exposeRaw = (options?.getBoolean("exposeRaw") === true)
            val ttl = (options?.getNumber("ttlMs") ?: 86400000) as Number
            val c = _cache
            if (c != null && Date.now() - c.ts < ttl) {
                return@w c.data
            }
            val res: IdCodesResult = IdCodesResult(consent = _consent, ts = Date.now())
            if (!_consent) {
                res.guid = IdValue(available = false, source = "guid", message = "consent=false")
                return@w res
            }
            if (include.indexOf("oaid") >= 0) {
                res.oaid = await(getOAID())
            }
            if (include.indexOf("androidId") >= 0) {
                res.androidId = await(getAndroidId(exposeRaw))
            }
            if (include.indexOf("aaid") >= 0) {
                res.aaid = await(getAAID())
            }
            if (include.indexOf("guid") >= 0) {
                res.guid = await(getGuid(exposeRaw))
            }
            if (include.indexOf("pseudoId") >= 0) {
                res.pseudoId = buildSync("pseudoId", getPseudoIdRaw(), exposeRaw, false, null)
            }
            fun pick(res: IdCodesResult, key: String): IdValue? {
                if (key === "oaid") {
                    return (res.oaid ?: null)
                }
                if (key === "androidId") {
                    return (res.androidId ?: null)
                }
                if (key === "guid") {
                    return (res.guid ?: null)
                }
                if (key === "pseudoId") {
                    return (res.pseudoId ?: null)
                }
                if (key === "aaid") {
                    return (res.aaid ?: null)
                }
                return null
            }
            res.best = null
            run {
                var i: Number = 0
                while(i < include.length){
                    val k = include[i]
                    val v = pick(res, k)
                    if (v != null && v.available) {
                        res.best = k
                        break
                    }
                    i++
                }
            }
            _cache = CacheType(ts = Date.now(), data = res)
            return@w res
    })
}
fun getBestId(options: UTSJSONObject?): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val prefer = (options?.getArray<String>("prefer") ?: DEFAULT_ORDER) as UTSArray<String>
            val exposeRaw = (options?.getBoolean("exposeRaw") === true)
            val args: UTSJSONObject = object : UTSJSONObject() {
                var include = prefer as Any
                var exposeRaw = exposeRaw
            }
            val r = await(getIdCodes(args))
            if (r.best != null) {
                val b = r.best as String
                if (b === "oaid" && r.oaid != null) {
                    return@w r.oaid as IdValue
                }
                if (b === "androidId" && r.androidId != null) {
                    return@w r.androidId as IdValue
                }
                if (b === "guid" && r.guid != null) {
                    return@w r.guid as IdValue
                }
                if (b === "pseudoId" && r.pseudoId != null) {
                    return@w r.pseudoId as IdValue
                }
                if (b === "aaid" && r.aaid != null) {
                    return@w r.aaid as IdValue
                }
            }
            return@w IdValue(available = false, source = "none", message = "no id available")
    })
}
