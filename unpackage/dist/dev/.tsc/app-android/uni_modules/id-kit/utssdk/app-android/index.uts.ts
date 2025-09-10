import { IdCodesResult, IdValue } from "../common/types.uts";
import { get, set } from "../common/storage.uts";
import { uuid4 } from "../common/uuid.uts";
import { getAndroidIdRaw } from "./adapters/android_id.uts";
import { getOAIDRaw } from "./adapters/oaid.uts";
import { getAAIDRaw } from "./adapters/aaid.uts";
import { getPseudoIdRaw } from "./adapters/pseudo_id.uts";
import Application from "android.app.Application";
import DeviceIdentifier from "com.github.gzuliyujiang.oaid.DeviceIdentifier";

type CacheType = {
    ts: number
    data: IdCodesResult
}

let _consent = false;
let _salt = "";
let _cache: CacheType | null = null;

const DEFAULT_ORDER = ["oaid", "androidId", "guid", "pseudoId", "aaid"];

function sha256HexSync(input: string): string {
  // 简化：无原生字节数组时使用 JS 级别 hash 近似，避免 getBytes/byte[] 依赖
  let h1 = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h1 ^= (input.charCodeAt(i) as number);
    h1 = (h1 + ((h1 << 1) + (h1 << 4) + (h1 << 7) + (h1 << 8) + (h1 << 24))) | 0;
  }
  const hex = (h1 >>> 0).toString(16);
  return hex.length % 2 === 1 ? ("0" + hex) : hex;
}

function buildSync(
  source: string,
  value?: string,
  exposeRaw?: boolean,
  limited?: boolean,
  msg?: string
): IdValue {
  const available = value!==undefined;
  const raw = exposeRaw ? value : undefined;
  const hash = value ? sha256HexSync(value + _salt) : undefined;
  return { value: raw, hash, available, limited: !!limited, source, message: msg };
}

export async function register(
  _: UTSJSONObject | null = null
): Promise<UTSJSONObject> {
  // 开源库预取（与隐私同意放同一时机）
  try {
    DeviceIdentifier.register(
      UTSAndroid.getUniActivity()!!.getApplication()!! as Application
    );
  } catch {}

  // 可选：提前加载 MSA 安全库（若接入）
  // try { java.lang.System.loadLibrary("msaoaidsec") } catch {}

  _consent = true;
  return { consent: _consent } as UTSJSONObject;
}

export function setSalt(salt: string): void {
  _salt = salt || "";
}

export async function getAndroidId(
  exposeRaw: boolean = false
): Promise<IdValue> {
  return buildSync("androidId", getAndroidIdRaw() || undefined, exposeRaw, false, undefined);
}

export async function getGuid(exposeRaw: boolean = false): Promise<IdValue> {
  let guid = get("UNIIDKIT_GUID");
  if (!guid) {
    guid = `app:${uuid4()}`;
    set("UNIIDKIT_GUID", guid);
  }
  return buildSync("guid", guid as string, exposeRaw, false, undefined);
}

export async function getOAID(): Promise<IdValue> {
  const ctx = UTSAndroid.getUniActivity()!!;
  const r = await getOAIDRaw(ctx);
  return buildSync("oaid", r.value, false, !!r.limited, r.message ?? undefined);
}

export async function getAAID(): Promise<IdValue> {
  const ctx = UTSAndroid.getUniActivity()!!;
  const r = await getAAIDRaw(ctx);
  return buildSync("aaid", r.value, false, !!r.limited, r.message ?? undefined);
}

export async function getIdCodes(
  options?: UTSJSONObject | null
): Promise<IdCodesResult> {
  const include = (options?.getArray<string>("include") || DEFAULT_ORDER) as string[];
  const exposeRaw = (options?.getBoolean("exposeRaw") || false) as boolean;
  const ttl = (options?.getNumber("ttlMs") || 24 * 3600 * 1000) as number;

  if (_cache && Date.now() - _cache.ts < ttl) return _cache.data;

  const res: IdCodesResult = {
    consent: _consent,
    ts: Date.now(),
  } as IdCodesResult;
  if (!_consent) {
    res.guid = { available: false, source: "guid", message: "consent=false" };
    return res;
  }

  if (include.indexOf("oaid") >= 0) res.oaid = await getOAID();
  if (include.indexOf("androidId") >= 0)
    res.androidId = await getAndroidId(exposeRaw);
  if (include.indexOf("aaid") >= 0) res.aaid = await getAAID();
  if (include.indexOf("guid") >= 0) res.guid = await getGuid(exposeRaw);
  if (include.indexOf("pseudoId") >= 0)
    res.pseudoId = buildSync(
      "pseudoId",
      getPseudoIdRaw() || undefined,
      exposeRaw,
      false,
      undefined
    );

  res.best = null;
  for (let i = 0; i < include.length; i++) {
    const k = include[i];
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
  const prefer = (options?.getArray<string>("prefer") || DEFAULT_ORDER) as string[];
  const exposeRaw = (options?.getBoolean("exposeRaw") || false) as boolean;
  const r = await getIdCodes({ include: prefer as any, exposeRaw } as any);
  if (r.best) {
    /* @ts-ignore */
		 return (r as UTSJSONObject)[r.best] as IdValue;
  }
  return { available: false, source: "none", message: "no id available" };
}
