import type { IdValue } from "../types.uts"
import { DeviceIdNative } from "uts.sdk.modules.idKit"

// 同意隐私：触发 OAID 预取，并把同意状态交给原生维护
export async function register(options ?: UTSJSONObject) : Promise<UTSJSONObject> {
	try { DeviceIdNative.prefetchOaidAfterConsent() } catch { }
	try { DeviceIdNative.setConsent(true) } catch { }
	return { consent: true } as UTSJSONObject
}

// 设置盐：原生统一使用 salt 参与 hash 计算
export function setSalt(salt : string) : void {
	try { DeviceIdNative.setSaltValue(salt) } catch { }
}

export async function getBestId(options ?: UTSJSONObject) : Promise<UTSJSONObject> {
	const prefer = options?.getArray<string>("prefer")
	const exposeRaw = options?.getBoolean("exposeRaw") ?? false
	const json = DeviceIdNative.getBestId(prefer?.toTypedArray(), exposeRaw) as string
	const obj = JSON.parse(json) as UTSJSONObject
	return obj
}