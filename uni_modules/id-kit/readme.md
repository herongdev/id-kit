# id-kit

一个简单统一的设备标识聚合 UTS 插件（UniApp X 专用）：

- 聚合多种平台标识（Android: OAID/AAID/Android ID/伪 ID/本地 GUID；iOS: IDFV/本地 GUID；Web: 本地 GUID）。
- 支持隐私同意开关（register），未同意前不采集且返回 available=false。
- 支持“只回传哈希”模式：对原值按 `SHA-256(value + salt)` 计算十六进制哈希。
- 支持本地 GUID 兜底与结果缓存（TTL）。

> 说明：本插件以“合规、稳定”为前提，Android 的 OAID/AAID 需按需接入第三方 SDK；iOS 的 IDFA 未集成（默认不采集广告标识）。

## 平台支持

- UniApp X（HBuilderX ≥ 3.6.8）
- 平台功能对照：

| 平台        | 可用标识                              | 说明                                                                               |
| ----------- | ------------------------------------- | ---------------------------------------------------------------------------------- |
| Web         | guid                                  | 使用本地存储生成并持久化，hash 由 Web Crypto 计算                                  |
| App-Android | oaid, aaid, androidId, guid, pseudoId | oaid/aaid 需接入对应 SDK；androidId/pseudoId 直接可用；hash 使用原生 MessageDigest |
| App-iOS     | idfv, guid                            | hash 可替换为 CommonCrypto；未集成 IDFA                                            |

## 快速上手

1. 导入（按平台自动解析 `utssdk` 下的入口）

```ts
import { register, setSalt, getBestId, getIdCodes } from "@/uni_modules/id-kit";
```

2. 在用户隐私弹窗“同意”后调用 `register()` 开启采集，并可设置哈希盐值：

```ts
await register(); // 标记 consent=true
setSalt("your-app-salt"); // 用于 SHA-256(value + salt)，提升去标识化安全
```

3. 获取一个“最优”标识（自动按平台优先级挑选）：

```ts
const best = await getBestId({
  // prefer 可调整优先级，未指定时 Android: oaid>androidId>guid>pseudoId>aaid；iOS: idfv>guid；Web: guid
  prefer: ["oaid", "androidId", "guid"] as any,
  exposeRaw: false, // 默认 false；false=仅返回 hash，true=同时返回 value 原文（注意合规）
});

// best.shape: { value?: string; hash?: string; available: boolean; limited?: boolean; source: string; message?: string }
```

4. 或一次性获取多种标识：

```ts
const all = await getIdCodes({
  include: ["oaid", "androidId", "guid", "aaid", "pseudoId"] as any,
  exposeRaw: false,
  ttlMs: 24 * 3600 * 1000, // 结果缓存 24h，期间重复调用直接走缓存
});

// all.shape: {
//   oaid?: IdValue; aaid?: IdValue; androidId?: IdValue; idfv?: IdValue; pseudoId?: IdValue; guid?: IdValue;
//   best?: string | null; consent: boolean; ts: number
// }
```

## API 说明

所有 API 导入自 `@/uni_modules/id-kit`，按平台自动选择实现。

### register(options?: object): Promise<{ consent: boolean }>

- 标记“已获得用户隐私同意”，方可开始采集真实设备标识。
- 未调用或未同意时，`get*` 系列将返回 `available=false`，仅提供必要兜底（如 guid）。

### setSalt(salt: string): void

- 设置哈希盐值。所有返回的 `hash` 均为 `SHA-256(value + salt)` 的十六进制字符串。
- 建议每个应用配置独立盐值，避免可逆或跨应用关联。

### getBestId(options?: { prefer?: string[]; exposeRaw?: boolean }): Promise<IdValue>

- 返回单一“最优”项（按平台内置优先级或传入 `prefer` 调整）。
- `IdValue` 结构：
  - `value?: string` 原始值（仅在 `exposeRaw=true` 且可用时返回）
  - `hash?: string` `SHA-256(value + salt)` 十六进制
  - `available: boolean` 是否获取成功
  - `limited?: boolean` 是否受限（如系统关闭广告跟踪）
  - `source: string` 来源标识（oaid/androidId/idfv/guid/...）
  - `message?: string` 说明或错误信息

### getIdCodes(options?: { include?: string[]; exposeRaw?: boolean; ttlMs?: number }): Promise<IdCodesResult>

- 批量返回指定集合；默认集合：
  - Android: `['oaid','androidId','guid','pseudoId','aaid']`
  - iOS: `['idfv','guid']`
  - Web: `['guid']`
- `IdCodesResult` 结构：
  - 各标识的 `IdValue`
  - `best?: string | null` 内部挑选的“最佳 key”
  - `consent: boolean` 是否已同意
  - `ts: number` 生成时间戳
- `ttlMs`：结果缓存时长，默认 24h。缓存命中直接返回上次结果以减少系统调用。

### 其他便捷方法（按平台兼容返回）

- `getGuid(exposeRaw?: boolean)`：跨平台本地 GUID 兜底（会持久化在本地存储键 `UNIIDKIT_GUID`）。
- `getAndroidId(exposeRaw?: boolean)`：Android 设备 ID（部分厂商可能变化）。
- `getOAID()`：需接入 MSA/国内 OAID SDK 后生效。
- `getAAID()`：需接入 Google Advertising ID 依赖后生效。

## 合规与注意事项

- 强烈建议在“用户同意隐私协议”后再调用 `register()` 再进行采集。
- 默认仅回传哈希（`exposeRaw=false`），减少可识别性；如确需原文请确保展示充分告知与用途说明。
- Android 广告标识（AAID）与 OAID 在系统层面可能被用户关闭或受限（`limited=true`）。
- 请遵循各平台隐私规范与本地法规（GDPR/CCPA/个保法等）。

## Android 集成提示（可选）

本插件内已预留对 OAID/AAID 的接口实现：

- OAID：建议集成 `Android_CN_OAID`（JitPack）。如需启用：

  1. 添加 JitPack 仓库（按你的 Gradle 版本选择 settings.gradle 或 build.gradle）：

     ```gradle
     dependencyResolutionManagement {
       repositories { maven { url 'https://jitpack.io' } }
     }
     // 或
     allprojects { repositories { maven { url 'https://jitpack.io' } } }
     ```

  2. 添加依赖（择一）：

     ```gradle
     dependencies {
       implementation 'com.github.gzu-liyujiang:Android_CN_OAID:最新版本号'
       // 或使用码云构建的包：
       // implementation 'com.gitee.li_yu_jiang:Android_CN_OAID:最新版本号'
     }
     ```

  3. 若与 MSA 共存，参考其文档排除/添加 HMS/Honor 依赖。
  4. 按库文档完成混淆与权限配置（consumer-rules 已内置）。
  5. 在隐私同意后调用 `register()` 以便 `DeviceIdentifier.register(...)` 预热（本插件已对接）。
  6. 确认 `app-android/adapters/oaid.uts` 已启用 `DeviceIdentifier.getOAID(...)` 分支。

- AAID（Google 广告标识）：引入 `com.google.android.gms:play-services-ads-identifier` 后完善 `getAAID()` 的实现。

如不集成上述 SDK，对应 API 会返回 `available=false` 或 `limited=true` 并给出 message 说明。

## iOS 提示

- 已提供 `IDFV` 获取；如需 IDFA，需要按规范集成广告相关依赖并完善实现（默认不采集）。
- `sha256HexSync` 可替换为 CommonCrypto 以在原生侧计算真实哈希。

## Web 提示

- 仅提供 `guid` 兜底；哈希使用 Web Crypto API（不可用时自动降级为回传原文）。

## 返回示例

```json
{
  "hash": "5e884898da28047151...",
  "available": true,
  "limited": false,
  "source": "androidId"
}
```

## 常见问题（FAQ）

1. 为什么默认只返回 hash？

   - 降低可识别性，便于合规。你可以通过 `setSalt` 配置应用唯一的盐值，服务端按相同盐值验证即可。

2. 未调用 `register()` 会怎样？

   - `consent=false`；除必要兜底（如 guid）外，其他标识返回 `available=false`。

3. `ttlMs` 缓存不起作用？

   - 请确认多次调用间隔小于 `ttlMs`，否则会触发重新采集。

4. 如何清除/重置 GUID？
   - 本地存储键为 `UNIIDKIT_GUID`；可自行清除（注意影响登录/风控逻辑）。
