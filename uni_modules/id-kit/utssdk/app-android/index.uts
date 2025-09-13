import { IdCodesResult, IdValue } from "../types.uts";
import { get, set } from "../common/storage.uts";
import { uuid4 } from "../common/uuid.uts";
import { DeviceIdNative } from "uts.sdk.modules.idKit";

type CacheType = {
  ts: number
  data: IdCodesResult
}

function buildId(
  type: string,
  rawVal: string | null,
  exposeRaw: boolean,
  limited: boolean | null,
  message: string | null
): IdValue {
  const available = !!(rawVal && rawVal.length > 0)
  return {
    type,
    value: rawVal,
    available,
    limited: limited as (boolean | null),
    raw: exposeRaw ? rawVal : null,
    message
  } as IdValue
}

// Kotlin 获取各类ID，并做可用性判断（均 try-catch 兜底返回 null）
function _getOaidNative(): string | null {
  try { return DeviceIdNative.getOAIDOrNull() } catch (e) { return null }
}
function _getAppSetIdNative(): string | null {
  try { return DeviceIdNative.getAppSetIdOrNull() } catch (e) { return null }
}
function _getAaidNative(): string | null {
  try { return DeviceIdNative.getAdvertisingIdOrNull() } catch (e) { return null }
}
function _getAndroidIdNative(): string | null {
  try { return DeviceIdNative.getAndroidIdOrNull() } catch (e) { return null }
}
function _getPseudoIdNative(): string | null {
  try { return DeviceIdNative.getPseudoId() } catch (e) { return null }
}

let _consent = false;
let _salt = "";
let _cache: CacheType | null = null;

// 将 appSetId 纳入默认优先级，放在 oaid 之后以保证“跨重装稳定ID”优先
const DEFAULT_ORDER = ["oaid", "appSetId", "androidId", "guid", "pseudoId", "aaid"];

export async function register(
  options?: UTSJSONObject | null
): Promise<UTSJSONObject> {
  // 隐私同意后，仅通过原生预取，避免 UTS 直接依赖第三方库
  try {
    DeviceIdNative.prefetchOaidAfterConsent();
  } catch { /* ignore */ }

  _consent = true;
  return { consent: _consent } as UTSJSONObject;
}

export function setSalt(salt: string): void {
  _salt = salt;
}

export async function getAndroidId(exposeRaw: boolean = false): Promise<IdValue> {
  // 完全由 Kotlin 决定可用性；UTS 不再做适配器回退
  const v = _getAndroidIdNative();
  if (v != null && v.length > 0) return buildId("androidId", v, exposeRaw, false, null);
  return buildId("androidId", null, exposeRaw, false, "unavailable");
}

export async function getGuid(exposeRaw: boolean = false): Promise<IdValue> {
  let guid = get("UNIIDKIT_GUID") as string | null;
  if (guid == null || guid.length === 0) {
    guid = `app:${uuid4()}`;
    set("UNIIDKIT_GUID", guid);
  }
  return buildId("guid", guid as string, exposeRaw, false, null);
}

export async function getOAID(): Promise<IdValue> {
  // 仅走原生逻辑（支持性短路/异常兜底已在 Kotlin 内处理）
  const v = _getOaidNative();
  if (v != null && v.length > 0) return buildId("oaid", v, false, true, null);
  return buildId("oaid", null, false, null, "unavailable");
}

export async function getAAID(): Promise<IdValue> {
  // 完全走原生 GAID（限制个性化广告或清零时返回 null）
  const v = _getAaidNative();
  if (v != null && v.length > 0) return buildId("aaid", v, false, true, null);
  return buildId("aaid", null, false, null, "unavailable");
}

// 单独暴露 App Set ID，并参与 best 选择（已纳入 DEFAULT_ORDER）
export async function getAppSetId(exposeRaw: boolean = false): Promise<IdValue> {
  // 原生侧已做超时与异常兜底
  const v = _getAppSetIdNative();
  return buildId("appSetId", v ?? null, exposeRaw, /* limited= */ null, v ? null : "unavailable");
}

export async function getPseudoId(exposeRaw: boolean = false): Promise<IdValue> {
  const v = _getPseudoIdNative();
  if (v != null && v.length > 0) return buildId("pseudoId", v, exposeRaw, false, null);
  return buildId("pseudoId", null, false, null, "unavailable");
}

export async function getIdCodes(
  options?: UTSJSONObject | null
): Promise<IdCodesResult> {
  const include = (options?.getArray<string>("include") ?? DEFAULT_ORDER) as string[];
  const exposeRaw = (options?.getBoolean("exposeRaw") === true);
  const ttl = (options?.getNumber("ttlMs") ?? (24 * 3600 * 1000)) as number;

  const c = _cache;
  if (c != null && Date.now() - c.ts < ttl) return c.data;

  const res: IdCodesResult = {
    consent: _consent,
    ts: Date.now(),
  } as IdCodesResult;

  if (!_consent) {
    res.guid = buildId("guid", null, false, null, "consent=false");
    _cache = { ts: Date.now(), data: res };
    return res;
  }

  if (include.indexOf("oaid") >= 0) res.oaid = await getOAID();

  // 仅在 include 含 appSetId 时才计算，避免无谓调用
  if (include.indexOf("appSetId") >= 0)
    res.appSetId = await getAppSetId(exposeRaw);

  if (include.indexOf("androidId") >= 0)
    res.androidId = await getAndroidId(exposeRaw);

  if (include.indexOf("aaid") >= 0) res.aaid = await getAAID();

  if (include.indexOf("guid") >= 0) res.guid = await getGuid(exposeRaw);

  if (include.indexOf("pseudoId") >= 0)
    res.pseudoId = await getPseudoId(exposeRaw);

  // 把 appSetId 纳入 pick/best 选择
  function pick(res: IdCodesResult, key: string): IdValue | null {
    if (key === "oaid") return (res.oaid ?? null);
    if (key === "appSetId") return (res.appSetId ?? null);
    if (key === "androidId") return (res.androidId ?? null);
    if (key === "guid") return (res.guid ?? null);
    if (key === "pseudoId") return (res.pseudoId ?? null);
    if (key === "aaid") return (res.aaid ?? null);
    return null;
  }

  res.best = null;
  for (let i = 0; i < include.length; i++) {
    const k = include[i];
    const v = pick(res, k);
    if (v != null && v.available) { res.best = k; break; }
  }
  _cache = { ts: Date.now(), data: res };
  return res;
}

export async function getBestId(
  options?: UTSJSONObject | null
): Promise<IdValue> {
  const prefer = options?.getArray<string>("prefer") ?? DEFAULT_ORDER;
  const exposeRaw = (options?.getBoolean("exposeRaw") === true);
  const args: UTSJSONObject = { "include": prefer, "exposeRaw": exposeRaw };
  const result = await getIdCodes(args);
  if (result.best != null) {
    const bestIdTypeName = result.best;
    if (bestIdTypeName === "oaid" && result.oaid != null) return result.oaid;
    // 当 best 命中 appSetId 时返回
    if (bestIdTypeName === "appSetId" && result.appSetId != null) return result.appSetId;
    if (bestIdTypeName === "androidId" && result.androidId != null) return result.androidId;
    if (bestIdTypeName === "guid" && result.guid != null) return result.guid;
    if (bestIdTypeName === "pseudoId" && result.pseudoId != null) return result.pseudoId;
    if (bestIdTypeName === "aaid" && result.aaid != null) return result.aaid;
  }
  return buildId("none", null, false, null, "no id available");
}
