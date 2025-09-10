// Web 有 crypto.subtle；原生侧建议用平台 API（Android 代码里换成 MessageDigest；iOS 侧可换 CommonCrypto）
export async function sha256Hex(input: string): Promise<string> {








  return input; // 非H5先返回原文；Android/iOS在各自实现中用系统哈希
}
