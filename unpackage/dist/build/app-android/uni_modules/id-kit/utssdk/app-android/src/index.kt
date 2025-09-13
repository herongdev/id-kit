@file:Suppress("UNCHECKED_CAST", "USELESS_CAST", "INAPPLICABLE_JVM_NAME", "UNUSED_ANONYMOUS_PARAMETER", "NAME_SHADOWING", "UNNECESSARY_NOT_NULL_ASSERTION")
package uts.sdk.modules.idKit
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import uts.sdk.modules.idKit.DeviceIdNative
import io.dcloud.uniapp.extapi.getStorageSync as uni_getStorageSync
import io.dcloud.uniapp.extapi.setStorageSync as uni_setStorageSync
open class IdValue (
    @JsonNotNull
    open var type: String,
    open var value: String? = null,
    @JsonNotNull
    open var available: Boolean = false,
    open var limited: Boolean? = null,
    open var raw: String? = null,
    open var message: String? = null,
) : UTSObject()
open class IdCodesResult (
    @JsonNotNull
    open var consent: Boolean = false,
    @JsonNotNull
    open var ts: Number,
    open var oaid: IdValue? = null,
    open var appSetId: IdValue? = null,
    open var androidId: IdValue? = null,
    open var guid: IdValue? = null,
    open var pseudoId: IdValue? = null,
    open var aaid: IdValue? = null,
    open var best: String? = null,
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
open class CacheType (
    @JsonNotNull
    open var ts: Number,
    @JsonNotNull
    open var data: IdCodesResult,
) : UTSObject()
fun buildId(type: String, rawVal: String?, exposeRaw: Boolean, limited: Boolean?, message: String?): IdValue {
    val available = !!(rawVal && rawVal.length > 0)
    return IdValue(type = type, value = rawVal, available = available, limited = limited as Boolean?, raw = if (exposeRaw) {
        rawVal
    } else {
        null
    }
    , message = message)
}
fun _getOaidNative(): String? {
    try {
        return DeviceIdNative.getOAIDOrNull()
    }
     catch (e: Throwable) {
        return null
    }
}
fun _getAppSetIdNative(): String? {
    try {
        return DeviceIdNative.getAppSetIdOrNull()
    }
     catch (e: Throwable) {
        return null
    }
}
fun _getAaidNative(): String? {
    try {
        return DeviceIdNative.getAdvertisingIdOrNull()
    }
     catch (e: Throwable) {
        return null
    }
}
fun _getAndroidIdNative(): String? {
    try {
        return DeviceIdNative.getAndroidIdOrNull()
    }
     catch (e: Throwable) {
        return null
    }
}
fun _getPseudoIdNative(): String? {
    try {
        return DeviceIdNative.getPseudoId()
    }
     catch (e: Throwable) {
        return null
    }
}
var _consent = false
var _cache: CacheType? = null
val DEFAULT_ORDER = _uA(
    "oaid",
    "appSetId",
    "androidId",
    "guid",
    "pseudoId",
    "aaid"
)
fun register(options: UTSJSONObject?): UTSPromise<UTSJSONObject> {
    return wrapUTSPromise(suspend w@{
            try {
                DeviceIdNative.prefetchOaidAfterConsent()
            }
             catch (e: Throwable) {}
            _consent = true
            return@w object : UTSJSONObject() {
                var consent = _consent
            }
    })
}
fun setSalt(salt: String): Unit {
    salt
}
fun getAndroidId(exposeRaw: Boolean = false): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val v = _getAndroidIdNative()
            if (v != null && v.length > 0) {
                return@w buildId("androidId", v, exposeRaw, false, null)
            }
            return@w buildId("androidId", null, exposeRaw, false, "unavailable")
    })
}
fun getGuid(exposeRaw: Boolean = false): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            var guid = get("UNIIDKIT_GUID") as String?
            if (guid == null || guid.length === 0) {
                guid = "app:" + uuid4()
                set("UNIIDKIT_GUID", guid)
            }
            return@w buildId("guid", guid as String, exposeRaw, false, null)
    })
}
fun getOAID(): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val v = _getOaidNative()
            if (v != null && v.length > 0) {
                return@w buildId("oaid", v, false, true, null)
            }
            return@w buildId("oaid", null, false, null, "unavailable")
    })
}
fun getAAID(): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val v = _getAaidNative()
            if (v != null && v.length > 0) {
                return@w buildId("aaid", v, false, true, null)
            }
            return@w buildId("aaid", null, false, null, "unavailable")
    })
}
fun getAppSetId(exposeRaw: Boolean = false): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val v = _getAppSetIdNative()
            return@w buildId("appSetId", v ?: null, exposeRaw, null, if (v) {
                null
            } else {
                "unavailable"
            }
            )
    })
}
fun getPseudoId(exposeRaw: Boolean = false): UTSPromise<IdValue> {
    return wrapUTSPromise(suspend w@{
            val v = _getPseudoIdNative()
            if (v != null && v.length > 0) {
                return@w buildId("pseudoId", v, exposeRaw, false, null)
            }
            return@w buildId("pseudoId", null, false, null, "unavailable")
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
                res.guid = buildId("guid", null, false, null, "consent=false")
                _cache = CacheType(ts = Date.now(), data = res)
                return@w res
            }
            if (include.indexOf("oaid") >= 0) {
                res.oaid = await(getOAID())
            }
            if (include.indexOf("appSetId") >= 0) {
                res.appSetId = await(getAppSetId(exposeRaw))
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
                res.pseudoId = await(getPseudoId(exposeRaw))
            }
            fun pick(res: IdCodesResult, key: String): IdValue? {
                if (key === "oaid") {
                    return (res.oaid ?: null)
                }
                if (key === "appSetId") {
                    return (res.appSetId ?: null)
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
            val prefer = options?.getArray<String>("prefer") ?: DEFAULT_ORDER
            val exposeRaw = (options?.getBoolean("exposeRaw") === true)
            val args: UTSJSONObject = object : UTSJSONObject() {
                var include = prefer
                var exposeRaw = exposeRaw
            }
            val result = await(getIdCodes(args))
            if (result.best != null) {
                val bestIdTypeName = result.best!!
                if (bestIdTypeName === "oaid" && result.oaid != null) {
                    return@w result.oaid!!
                }
                if (bestIdTypeName === "appSetId" && result.appSetId != null) {
                    return@w result.appSetId!!
                }
                if (bestIdTypeName === "androidId" && result.androidId != null) {
                    return@w result.androidId!!
                }
                if (bestIdTypeName === "guid" && result.guid != null) {
                    return@w result.guid!!
                }
                if (bestIdTypeName === "pseudoId" && result.pseudoId != null) {
                    return@w result.pseudoId!!
                }
                if (bestIdTypeName === "aaid" && result.aaid != null) {
                    return@w result.aaid!!
                }
            }
            return@w buildId("none", null, false, null, "no id available")
    })
}
