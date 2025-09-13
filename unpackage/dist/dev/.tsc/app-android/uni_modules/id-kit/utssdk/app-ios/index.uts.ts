import { IdCodesResult, IdValue } from "../common/types.uts";
import { get, set } from "../common/storage.uts";
import { uuid4 } from "../common/uuid.uts";
import { setPolicy, getPolicy } from "../common/policy.uts"


// 复杂逻辑：桥接 Swift 原生获取 IDFA/IDFV/Keychain
declare class DeviceIdNative {
  static getIDFAOrNull(requestATT: boolean): string | null
  static getIDFVOrNull(): string | null
  static getKeychainUUID(): string
}

let _consent = false;
let _salt = "";
let _strict = false; // 复杂逻辑：严格合规模式（禁用 IDFA）
let _cache: { ts: number; data: IdCodesResult } | null = null;

function sha256HexSync(input: string): string {
  return input;
} // TODO: 可换 CommonCrypto
function buildSync(
  source: string,
  value?: string | null,
  exposeRaw?: boolean,
  limited?: boolean | null,
  msg?: string | null
): IdValue {
  const available = value != null;
  const out: UTSJSONObject = { available, limited: (limited === true), source } as UTSJSONObject;
  if (msg != null) (out as UTSJSONObject)["message"] = msg;
  if (available) {
    const h = sha256HexSync((value as string) + _salt);
    (out as UTSJSONObject)["hash"] = h;
    if (exposeRaw) (out as UTSJSONObject)["value"] = value as string;
  }
  return out as IdValue;
}

export async function register(
  _: UTSJSONObject | null = null
): Promise<UTSJSONObject> {
  _consent = true;
  return { consent: _consent } as UTSJSONObject;
}
export function setStrictMode(strict: boolean): void { _strict = strict }

export function setSalt(salt: string): void {
  _salt = salt || "";
}

function getIDFVRaw(): string | null {
  try {
    const idfv = UIDevice.currentDevice.identifierForVendor?.UUIDString;
    return idfv ? `idfv:${idfv}` : null;
  } catch {
    return null;
  }
}


export async function getIDFV(exposeRaw: boolean = false): Promise<IdValue> {
  // 复杂逻辑：优先使用原生 IDFV，不可用再降级 Keychain UUID
  const idfv = DeviceIdNative.getIDFVOrNull();
  if (idfv && idfv.length > 0) return buildSync("idfv", idfv, exposeRaw, true, null);
  const uuid = DeviceIdNative.getKeychainUUID();
  return buildSync("idfv", uuid, exposeRaw, false, "fallback:keychain");
}

export async function getIDFA(requestATT: boolean = false): Promise<IdValue> {
  const idfa = DeviceIdNative.getIDFAOrNull(requestATT);
  return buildSync("idfa", idfa ?? null, false, true, idfa ? null : "no-permission-or-limited");
}

export async function getGuid(exposeRaw: boolean = false): Promise<IdValue> {
  let guid = get("UNIIDKIT_GUID");
  if (!guid) {
    guid = `ios:${uuid4()}`;
    set("UNIIDKIT_GUID", guid);
  }
  return buildSync("guid", guid as string, exposeRaw, false, null);
}
export async function getAndroidId(): Promise<IdValue> {
  return buildSync("androidId", null, false, false, "iOS unsupported");
}
export async function getOAID(): Promise<IdValue> {
  return buildSync("oaid", null, false, false, "iOS unsupported");
}
export async function getAAID(): Promise<IdValue> {
  return buildSync("aaid", null, false, false, "iOS unsupported");
}

export async function getIdCodes(
  options?: UTSJSONObject | null
): Promise<IdCodesResult> {
  const include = (options?.getArray<string>("include") || [
    "idfv",
    "guid",
  ]) as string[];
  const exposeRaw = (options?.getBoolean("exposeRaw") || false) as boolean;
  const ttl = (options?.getNumber("ttlMs") || 24 * 3600 * 1000) as number;

  // 复杂逻辑：根据策略计算有效 include 与顺序
  const policy = getPolicy();
  let includeEff = include.slice();
  let orderEff = (options?.getArray<string>("prefer") || includeEff) as string[];
  if (policy === 'cn') {
    includeEff = includeEff.filter(k => ['idfv','guid'].indexOf(k) >= 0);
    orderEff = ['idfv','guid'].filter(k => includeEff.indexOf(k) >= 0);
  } else if (policy === 'global') {
    includeEff = includeEff.filter(k => ['idfa','idfv','guid'].indexOf(k) >= 0);
    orderEff = ['idfa','idfv','guid'].filter(k => includeEff.indexOf(k) >= 0);
  } else if (policy === 'strict') {
    includeEff = includeEff.filter(k => ['idfv','guid'].indexOf(k) >= 0);
    orderEff = ['idfv','guid'].filter(k => includeEff.indexOf(k) >= 0);
    // 复杂逻辑：策略为 strict 时强制进入严格合规模式
    _strict = true;
  }

  if (_cache && Date.now() - _cache.ts < ttl) return _cache.data;

  const res: IdCodesResult = { consent: _consent, ts: Date.now() } as IdCodesResult;
  if (!_consent && _strict) {
    // 复杂逻辑：未同意 + 严格模式 -> 仅非广告类返回
    if (includeEff.indexOf("idfv") >= 0) res.idfv = await getIDFV(exposeRaw);
    if (includeEff.indexOf("guid") >= 0) res.guid = await getGuid(exposeRaw);
  } else {
    if (includeEff.indexOf("idfv") >= 0) res.idfv = await getIDFV(exposeRaw);
    if (includeEff.indexOf("idfa") >= 0) res.idfa = await getIDFA(false);
    if (includeEff.indexOf("guid") >= 0) res.guid = await getGuid(exposeRaw);
  }

  const order = orderEff;
  res.best = null;
  for (let i = 0; i < order.length; i++) {
    const k = order[i];
    /* @ts-ignore */ const v: IdValue = (res as UTSJSONObject)[k] as any;
    if (v && v.available) { res.best = k; break; }
  }
  _cache = { ts: Date.now(), data: res };
  return res;
  }

  if (includeEff.indexOf("idfv") >= 0)
    res.idfv = await getIDFV(exposeRaw);
  if (includeEff.indexOf("guid") >= 0) res.guid = await getGuid(exposeRaw);

  const order = orderEff;
  res.best = null;
  for (let i = 0; i < order.length; i++) {
    const k = order[i];
    /* @ts-ignore */ const v: IdValue = (res as UTSJSONObject)[k] as any;
    if (v && v.available) {
      res.best = k;
      break;
    }
  }
  _cache = { ts: Date.now(), data: res };
  return res;
}

export async function getBestId(
  options?: UTSJSONObject | null
): Promise<IdValue> {
  const prefer = (options?.getArray<string>("prefer") || [
    "idfv",
    "guid",
  ]) as string[];
  const exposeRaw = (options?.getBoolean("exposeRaw") || false) as boolean;
  const r = await getIdCodes({ include: prefer as any, exposeRaw } as any);
  if (r.best) {
    /* @ts-ignore */ return (r as UTSJSONObject)[r.best] as IdValue;
  }
  return { available: false, source: "none", message: "no id available" };
}
