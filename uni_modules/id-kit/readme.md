# id-kit

一个 **统一设备标识聚合** 的 UTS 插件：

* Android：OAID、AAID、Android ID、App Set ID、伪 ID、本地 GUID
* iOS：IDFV、IDFA、本地 GUID
* Web：本地 GUID

特性：

* ✔️ 聚合多种标识，自动挑选“最佳”
* ✔️ 支持隐私同意开关（register）
* ✔️ 支持 **只返回 SHA-256 哈希**（可设置盐值）
* ✔️ 内置本地 GUID 兜底，支持缓存 TTL
* ✔️ 适配国内合规策略

---

## 快速上手

```ts
import { register, setSalt, getBestId, getIdCodes } from "@/uni_modules/id-kit";

// 用户同意隐私后调用
await register();
setSalt("your-app-salt");

// 获取一个最优 ID
const best = await getBestId({
  prefer: ["oaid", "appSetId", "androidId", "guid"], // 优先级
  exposeRaw: false, // 默认 false，仅返回 hash
});
console.log(best);

// 或一次性获取多个
const all = await getIdCodes({
  include: ["oaid", "appSetId", "androidId", "guid", "aaid", "pseudoId"],
  exposeRaw: false,
  ttlMs: 24 * 3600 * 1000,
});
console.log(all);
```

---

## API 简表

| 方法                    | 说明                                                                                                                     |
| --------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| `register()`          | 标记已同意隐私，允许采集                                                                                                           |
| `setSalt(salt)`       | 设置哈希盐，所有 hash = SHA256(value + salt)                                                                                   |
| `getBestId(options)`  | 获取最优标识，默认优先级：<br>Android: oaid > appSetId > androidId > guid > pseudoId > aaid<br>iOS: idfv > idfa > guid<br>Web: guid |
| `getIdCodes(options)` | 批量返回指定集合及缓存结果                                                                                                          |
| 便捷方法                  | `getGuid`、`getAndroidId`、`getAppSetId`、`getOAID`、`getAAID`、`getIDFA`、`getIDFV`                                         |

---

## 返回示例

```json
{
  "hash": "5e884898da28047151...",
  "available": true,
  "limited": false,
  "source": "androidId"
}
```

---

## 合规说明

* 默认只返回 hash（exposeRaw=false）
* 建议在用户同意隐私后调用 `register()`
* Android 广告标识（OAID/AAID）与 iOS IDFA 可能被用户关闭或受限
* iOS 14+ 获取 IDFA 需要先申请 ATT 权限

---

## 平台支持

| 平台      | 可用标识                                            |
| ------- | ----------------------------------------------- |
| Web     | guid                                            |
| Android | oaid, aaid, androidId, appSetId, guid, pseudoId |
| iOS     | idfv, idfa, guid                                |

