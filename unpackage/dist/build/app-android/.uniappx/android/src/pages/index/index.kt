@file:Suppress("UNCHECKED_CAST", "USELESS_CAST", "INAPPLICABLE_JVM_NAME", "UNUSED_ANONYMOUS_PARAMETER", "NAME_SHADOWING", "UNNECESSARY_NOT_NULL_ASSERTION")
package uni.UNI64E08CC
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
import uts.sdk.modules.idKit.register
import uts.sdk.modules.idKit.setSalt
import uts.sdk.modules.idKit.getBestId
import io.dcloud.uniapp.extapi.showToast as uni_showToast
open class GenPagesIndexIndex : BasePage {
    constructor(__ins: ComponentInternalInstance, __renderer: String?) : super(__ins, __renderer) {
        onLoad(fun(_: OnLoadOptions) {}, __ins)
    }
    @Suppress("UNUSED_PARAMETER", "UNUSED_VARIABLE")
    override fun `$render`(): Any? {
        val _ctx = this
        val _cache = this.`$`.renderCache
        return _cE("view", _uM("class" to "wrapper"), _uA(
            _cE("view", _uM("class" to "actions"), _uA(
                _cE("button", _uM("type" to "primary", "onClick" to _ctx.onConsent, "disabled" to (_ctx.consent || _ctx.loading)), _tD(if (_ctx.consent) {
                    "已同意"
                } else {
                    "同意并初始化"
                }
                ), 9, _uA(
                    "onClick",
                    "disabled"
                )),
                _cE("button", _uM("onClick" to _ctx.onGetBest, "disabled" to (!_ctx.consent || _ctx.loading)), "获取最佳ID", 8, _uA(
                    "onClick",
                    "disabled"
                ))
            )),
            if (isTrue(_ctx.hasResult)) {
                _cE("view", _uM("key" to 0, "class" to "result"), _uA(
                    _cE("text", null, "来源：" + _tD(_ctx.result["source"]), 1),
                    _cE("text", null, "可用：" + _tD(_ctx.result["available"]), 1),
                    if (isTrue(_ctx.result["hash"])) {
                        _cE("text", _uM("key" to 0), "哈希：" + _tD(_ctx.result["hash"]), 1)
                    } else {
                        _cC("v-if", true)
                    },
                    if (isTrue(_ctx.result["value"])) {
                        _cE("text", _uM("key" to 1), "原值：" + _tD(_ctx.result["value"]), 1)
                    } else {
                        _cC("v-if", true)
                    },
                    if (isTrue(_ctx.result["message"])) {
                        _cE("text", _uM("key" to 2), "说明：" + _tD(_ctx.result["message"]), 1)
                    } else {
                        _cC("v-if", true)
                    }
                ))
            } else {
                _cC("v-if", true)
            }
        ))
    }
    open var consent: Boolean by `$data`
    open var loading: Boolean by `$data`
    open var hasResult: Boolean by `$data`
    open var result: UTSJSONObject by `$data`
    @Suppress("USELESS_CAST")
    override fun data(): Map<String, Any?> {
        return _uM("consent" to false, "loading" to false, "hasResult" to false, "result" to UTSJSONObject())
    }
    open var onConsent = ::gen_onConsent_fn
    open fun gen_onConsent_fn(): UTSPromise<Unit> {
        return wrapUTSPromise(suspend {
                this.loading = true
                try {
                    await(register())
                    setSalt("demo-salt")
                    this.consent = true
                    uni_showToast(ShowToastOptions(title = "已同意"))
                }
                 catch (e: Throwable) {
                    uni_showToast(ShowToastOptions(title = "初始化失败", icon = "none"))
                }
                 finally{
                    this.loading = false
                }
        })
    }
    open var onGetBest = ::gen_onGetBest_fn
    open fun gen_onGetBest_fn(): UTSPromise<Unit> {
        return wrapUTSPromise(suspend {
                this.loading = true
                try {
                    val r = await(getBestId(object : UTSJSONObject() {
                        var exposeRaw = false
                    }))
                    this.result = r as UTSJSONObject
                    this.hasResult = true
                }
                 catch (e: Throwable) {
                    this.result = object : UTSJSONObject() {
                        var available = false
                        var source = "unknown"
                        var message = String(e)
                        var hash = ""
                        var value = ""
                    }
                    this.hasResult = true
                }
                 finally{
                    this.loading = false
                }
        })
    }
    companion object {
        val styles: Map<String, Map<String, Map<String, Any>>> by lazy {
            _nCS(_uA(
                styles0
            ), _uA(
                GenApp.styles
            ))
        }
        val styles0: Map<String, Map<String, Map<String, Any>>>
            get() {
                return _uM("wrapper" to _pS(_uM("paddingTop" to "24rpx", "paddingRight" to "24rpx", "paddingBottom" to "24rpx", "paddingLeft" to "24rpx", "display" to "flex", "justifyContent" to "center")), "logo" to _pS(_uM("height" to 100, "width" to 100, "marginTop" to 100, "marginRight" to "auto", "marginBottom" to 25, "marginLeft" to "auto")), "title" to _pS(_uM("fontSize" to 18, "color" to "#8f8f94", "textAlign" to "center")))
            }
        var inheritAttrs = true
        var inject: Map<String, Map<String, Any?>> = _uM()
        var emits: Map<String, Any?> = _uM()
        var props = _nP(_uM())
        var propsNeedCastKeys: UTSArray<String> = _uA()
        var components: Map<String, CreateVueComponent> = _uM()
    }
}
