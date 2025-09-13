
import { register, setSalt, getBestId } from '@/uni_modules/id-kit'

const __sfc__ = defineComponent({
  data() {
    return {
      consent: false,
      loading: false,
      hasResult: false,
      // 结果使用 UTSJSONObject，避免将原生 JSONObject 强转为自定义类型导致 ClassCastException
      result: {} as UTSJSONObject
    }
  },
  methods: {
    async onConsent() {
      this.loading = true
      try {
        await register({})
        setSalt('demo-salt')
        this.consent = true
        uni.showToast({ title: '已同意' })
      } catch (e) {
        uni.showToast({ title: '初始化失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async onGetBest() {
      this.loading = true
      try {
        // 原生返回 UTSJSONObject；按需拷贝到响应式 result
        const best = (await getBestId({ exposeRaw: false })) as UTSJSONObject

        // 为稳妥起见做一次空值兜底
        this.result['source'] = (best['source'] ?? 'none') as string
        this.result['available'] = (best['available'] === true)
        this.result['hash'] = (best['hash'] ?? null) as (string | null)
        this.result['value'] = (best['value'] ?? null) as (string | null)
        this.result['limited'] = (best['limited'] ?? null) as (boolean | null)
        this.result['message'] = (best['message'] ?? null) as (string | null)

        this.hasResult = true
      } catch (e) {
        console.log('onGetBest error', e)
        this.result = {} as UTSJSONObject
        this.hasResult = true
      } finally {
        this.loading = false
      }
    }
  }
})

export default __sfc__
function GenPagesIndexIndexRender(this: InstanceType<typeof __sfc__>): any | null {
const _ctx = this
const _cache = this.$.renderCache
  return _cE("view", _uM({ class: "wrapper" }), [
    _cE("view", _uM({ class: "actions" }), [
      _cE("button", _uM({
        type: "primary",
        onClick: _ctx.onConsent,
        disabled: _ctx.consent || _ctx.loading
      }), _tD(_ctx.consent ? '已同意' : '同意并初始化'), 9 /* TEXT, PROPS */, ["onClick", "disabled"]),
      _cE("button", _uM({
        onClick: _ctx.onGetBest,
        disabled: !_ctx.consent || _ctx.loading
      }), "获取最佳ID", 8 /* PROPS */, ["onClick", "disabled"])
    ]),
    isTrue(_ctx.hasResult)
      ? _cE("view", _uM({
          key: 0,
          class: "result"
        }), [
          _cE("text", null, "来源：" + _tD(_ctx.result.source), 1 /* TEXT */),
          _cE("text", null, "可用：" + _tD(_ctx.result.available), 1 /* TEXT */),
          isTrue(_ctx.result.hash)
            ? _cE("text", _uM({ key: 0 }), "哈希：" + _tD(_ctx.result.hash), 1 /* TEXT */)
            : _cC("v-if", true),
          isTrue(_ctx.result.value)
            ? _cE("text", _uM({ key: 1 }), "原值：" + _tD(_ctx.result.value), 1 /* TEXT */)
            : _cC("v-if", true),
          isTrue(_ctx.result.message)
            ? _cE("text", _uM({ key: 2 }), "说明：" + _tD(_ctx.result.message), 1 /* TEXT */)
            : _cC("v-if", true)
        ])
      : _cC("v-if", true)
  ])
}
const GenPagesIndexIndexStyles = [_uM([["wrapper", _pS(_uM([["paddingTop", "24rpx"], ["paddingRight", "24rpx"], ["paddingBottom", "24rpx"], ["paddingLeft", "24rpx"], ["display", "flex"], ["flexDirection", "column"], ["justifyContent", "center"]]))], ["actions", _pS(_uM([["display", "flex"]]))], ["result", _pS(_uM([["display", "flex"], ["flexDirection", "column"]]))], ["logo", _pS(_uM([["height", 100], ["width", 100], ["marginTop", 100], ["marginRight", "auto"], ["marginBottom", 25], ["marginLeft", "auto"]]))], ["title", _pS(_uM([["fontSize", 18], ["color", "#8f8f94"], ["textAlign", "center"]]))]])]
