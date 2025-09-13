// 策略层封装：一行切换组合策略
export type Policy = 'cn' | 'global' | 'strict'

let _policy: Policy = 'global'

export function setPolicy(policy: Policy): void {
  _policy = policy
}

export function getPolicy(): Policy {
  return _policy
}
