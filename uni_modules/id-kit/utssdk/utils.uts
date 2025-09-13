import { IdValue } from "./types.uts";

export function buildSync(
  source: string,
  value?: string | null,
  exposeRaw?: boolean,
  limited?: boolean | null,
  msg?: string | null,
  salt?: string
): IdValue {
  const available = value != null;
  const out: IdValue = { available, limited: limited === true, source };
  if (msg != null) out.message = msg;
  if (available) {
    out.hash = sha256HexSync((value as string) + (salt === null ? "" : salt));
    if (exposeRaw === true) out.value = value;
  }
  return out;
}

export function sha256HexSync(input: string): string {
  // 简化：无原生字节数组时使用 JS 级别 hash 近似，避免 getBytes/byte[] 依赖
  let h1 = 0x811c9dc5;
  for (let i = 0; i < input.length; i++) {
    h1 ^= input.charCodeAt(i) as number;
    h1 =
      (h1 + ((h1 << 1) + (h1 << 4) + (h1 << 7) + (h1 << 8) + (h1 << 24))) | 0;
  }
  const hex = (h1 >>> 0).toString(16);
  let outHex = hex;
  if (hex.length % 2 == 1) {
    outHex = "0" + hex;
  }
  return outHex;
}
