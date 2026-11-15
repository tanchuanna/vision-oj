import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const root = process.cwd()
const authModalPath = path.join(root, 'src/components/auth/AuthModal.vue')
const source = fs.readFileSync(authModalPath, 'utf8')

const requirements = [
  ['使用中性信息提示，避免把请求受理误写成邮件已发送', 'Message.info('],
  ['明确说明只有已绑定邮箱会收到验证码', '只有已绑定账号的邮箱才会收到验证码'],
  ['请求结果说明对辅助技术可感知', 'aria-live="polite"'],
  ['保留请求后的页面内说明', 'recoveryRequestNotice'],
]

const missing = requirements.filter(([, token]) => !source.includes(token))

if (missing.length > 0) {
  console.error('找回密码提示契约检查失败：')
  for (const [message] of missing) console.error(`- ${message}`)
  process.exit(1)
}

if (source.includes("Message.success(data.message || '如果邮箱已绑定账号")) {
  console.error('找回密码提示契约检查失败：仍将中性结果显示为成功发送')
  process.exit(1)
}

console.log('找回密码提示契约检查通过')
