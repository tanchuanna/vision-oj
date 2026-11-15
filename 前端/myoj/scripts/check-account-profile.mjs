import { readFileSync } from 'node:fs'

const source = readFileSync(
  new URL('../src/views/account/AccountSettingsView.vue', import.meta.url),
  'utf8',
)
const checks = [
  [source.includes('资料完成度'), '缺少资料完成度'],
  [source.includes('邮箱与登录'), '基本信息页缺少邮箱入口'],
  [source.includes('hasUnsavedChanges'), '缺少未保存修改状态'],
  [source.includes('profile-char-count'), '缺少个人介绍字数反馈'],
  [source.includes('保存资料'), '缺少明确的保存操作'],
]

const failed = checks.filter(([ok]) => !ok)
if (failed.length) {
  for (const [, message] of failed) console.error(`account profile: ${message}`)
  process.exit(1)
}
console.log('account profile contract: ok')
