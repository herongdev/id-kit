function toHex(): string[] {
  const h: string[] = []; for (let i=0;i<256;i++) h[i] = (i+0x100).toString(16).substring(1);
  return h;
}
function formatUuid(b: Uint8Array): string {
  b[6] = (b[6] & 0x0f) | 0x40; // version=4
  b[8] = (b[8] & 0x3f) | 0x80; // variant=10
  const H = toHex();
  return (
    H[b[0]]+H[b[1]]+H[b[2]]+H[b[3]]+"-"+H[b[4]]+H[b[5]]+"-"+H[b[6]]+H[b[7]]+"-"+H[b[8]]+H[b[9]]+"-"+H[b[10]]+H[b[11]]+H[b[12]]+H[b[13]]+H[b[14]]+H[b[15]]
  );
}











function getRandom16(): Uint8Array {
  const sr = new java.security.SecureRandom(); // UTS 原生：直接 Java 类
  const arr: number[] = new Array<number>(16);
  for (let i=0;i<16;i++) arr[i] = sr.nextInt(256);
  return new Uint8Array(arr);
}
















export function uuid4(): string {
  return formatUuid(getRandom16());
}
