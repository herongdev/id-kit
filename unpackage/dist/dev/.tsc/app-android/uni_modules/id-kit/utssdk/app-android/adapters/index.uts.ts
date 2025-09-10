import { IdCodesResult, IdValue } from "../common/types.uts";
import { get, set } from "../common/storage.uts";
import { getAndroidIdRaw } from "./adapters/android_id.uts";

let _consent = false;
let _salt = "";

// Android 原生侧用 Java 的 MessageDigest 做 SHA-256 更稳，这里暂用 Web 版占位：
// 你也可以在此通过 plus.android.importClass 使用 java.security.MessageDigest 实现
async function sha256Hex(input : string) : Promise<string> {
	return input; // TODO: 接入原生 MessageDigest 后返回真 SHA-256
}

function uuid4() : string {
	return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (c) => {
		const r = ((Math.random() * 16) as number) | 0;
		const v = c === "x" ? r : (r & 0x3) | 0x8;
		return v.toString(16);
	});
}

async function build(
	source : string,
	value ?: string,
	exposeRaw ?: boolean,
	limited ?: boolean,
	msg ?: string
) : Promise<IdValue> {
	const available = value != undefined;
	const raw = exposeRaw ? value : undefined;
	const hash = value ? await sha256Hex(value + _salt) : undefined;
	return { value: raw, hash, available, limited, source, message: msg };
}

export async function register(
	_ : UTSJSONObject | null = null
) : Promise<UTSJSONObject> {
	// 这里接入你的合规弹窗/SDK；同意前建议不采集
	_consent = true;
	return { consent: _consent } as UTSJSONObject;
}

export function setSalt(salt : string) : void {
	_salt = salt || "";
}

export async function getAndroidId(
	exposeRaw : boolean = false
) : Promise<IdValue> {
	const v = getAndroidIdRaw();
	return await build("androidId", v || undefined, exposeRaw);
}

// 预留：接入 Google Advertising ID（AAID）
export async function getAAID() : Promise<IdValue> {
	// TODO：集成 com.google.android.gms:play-services-ads-identifier
	return await build("aaid", undefined, false, true, "AAID not integrated");
}

// 预留：接入 MSA OAID（国内主流）
export async function getOAID() : Promise<IdValue> {
	// TODO：接入 MSA/OAID SDK
	return await build("oaid", undefined, false, true, "OAID not integrated");
}

export async function getGuid(exposeRaw : boolean = false) : Promise<IdValue> {
	let guid = get("UNIIDKIT_GUID");
	if (!guid) {
		guid = `app:${uuid4()}`;
		set("UNIIDKIT_GUID", guid);
	}
	return await build("guid", guid as string, exposeRaw);
}

export async function getIdCodes(
	options ?: UTSJSONObject | null
) : Promise<IdCodesResult> {
	const include = (options?.getArray<string>("include") || [
		"oaid",
		"aaid",
		"androidId",
		"guid",
	]) as string[];
	const exposeRaw = (options?.getBoolean("exposeRaw") || false) as boolean;

	const res : IdCodesResult = {
		consent: _consent,
		ts: Date.now(),
	} as IdCodesResult;
	if (!_consent) {
		res.guid = { available: false, source: "guid", message: "consent=false" };
		return res;
	}

	if (include.indexOf("androidId") >= 0)
		res.androidId = await getAndroidId(exposeRaw);
	if (include.indexOf("oaid") >= 0) res.oaid = await getOAID();
	if (include.indexOf("aaid") >= 0) res.aaid = await getAAID();
	if (include.indexOf("guid") >= 0) res.guid = await getGuid(exposeRaw);

	// 国内优先级：oaid > androidId > guid
	const order = ["oaid", "androidId", "guid"];
	res.best = null;
	for (let i = 0; i < order.length; i++) {
		const k = order[i];
		// @ts-ignore
		const v : IdValue | null = (res as UTSJSONObject)[k] as any;
		if (v && v.available) {
			res.best = k;
			break;
		}
	}
	return res;
}

export async function getBestId(
	options ?: UTSJSONObject | null
) : Promise<IdValue> {
	const arr = (options?.getArray<string>("prefer") || [
		"oaid",
		"androidId",
		"guid",
	]) as string[];
	const exposeRaw = (options?.getBoolean("exposeRaw") || false) as boolean;
	const r = await getIdCodes({ include: arr as any, exposeRaw } as any);
	if (r.best) {
		// @ts-ignore
		return (r as UTSJSONObject)[r.best] as IdValue;
	}
	return { available: false, source: "none", message: "no id available" };
}