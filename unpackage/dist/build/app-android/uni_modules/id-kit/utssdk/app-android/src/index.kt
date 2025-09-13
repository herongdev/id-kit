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
fun register(options: UTSJSONObject?): UTSPromise<UTSJSONObject> {
    return wrapUTSPromise(suspend w@{
            try {
                DeviceIdNative.prefetchOaidAfterConsent()
            }
             catch (e: Throwable) {}
            try {
                DeviceIdNative.setConsent(true)
            }
             catch (e: Throwable) {}
            return@w object : UTSJSONObject() {
                var consent = true
            }
    })
}
fun setSalt(salt: String): Unit {
    try {
        DeviceIdNative.setSaltValue(salt)
    }
     catch (e: Throwable) {}
}
fun getBestId(options: UTSJSONObject?): UTSPromise<UTSJSONObject> {
    return wrapUTSPromise(suspend w@{
            val prefer = options?.getArray<String>("prefer")
            val exposeRaw = options?.getBoolean("exposeRaw") ?: false
            val json = DeviceIdNative.getBestId(prefer?.toTypedArray(), exposeRaw) as String
            val obj = JSON.parse(json) as UTSJSONObject
            return@w obj
    })
}
