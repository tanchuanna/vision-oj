import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const api = fs.readFileSync(path.join(root, 'src/api/questionSolution.ts'), 'utf8')
const panel = fs.readFileSync(path.join(root, 'src/views/problem/ProblemSolutionPanel.vue'), 'utf8')
const solve = fs.readFileSync(path.join(root, 'src/views/problem/ProblemSolveView.vue'), 'utf8')

const requiredApi = [
  '/question-solution/public/eligibility',
  '/question-solution/public/page',
  '/question-solution/public/get',
  '/question-solution/save',
  '/question-solution/submit-review',
]
for (const endpoint of requiredApi) {
  if (!api.includes(endpoint)) throw new Error(`题解 API 缺少 ${endpoint}`)
}
if (!api.includes('id: string') || !api.includes('questionId: string'))
  throw new Error('题解长 ID 必须使用 string')
for (const text of ['通过本题后可解锁完整题解', '写题解', '提交审核', '官方题解', '安全提示']) {
  if (!panel.includes(text)) throw new Error(`题解界面缺少：${text}`)
}
if (!panel.includes("selected.accessLevel === 'FULL'"))
  throw new Error('题解详情缺少 FULL 权限渲染保护')
if (!panel.includes('class="solution-reader"')) throw new Error('题解详情缺少独立阅读容器')
if (!panel.includes('class="solution-detail__hero"'))
  throw new Error('题解详情缺少清晰的标题与作者信息区')
if (!panel.includes('.solution-detail{') || !panel.includes('background:var(--app-bg)')) {
  throw new Error('题解详情必须使用不透明背景，避免列表内容透出叠字')
}
if (panel.includes('题解标题<input') || panel.includes('列表摘要<input')) {
  throw new Error('写题解时不应要求用户重复填写题目标题和列表摘要')
}
if (!panel.includes('<Teleport to="body">'))
  throw new Error('题解编辑器必须挂载到全页面，不能被左侧分栏限制')
if (panel.includes('window.prompt(')) throw new Error('题解驳回不能使用浏览器原生 prompt')
for (const text of [
  'class="review-reject-modal"',
  '常用原因',
  '确认驳回',
  'reviewRejectReason.length',
]) {
  if (!panel.includes(text)) throw new Error(`题解驳回弹窗缺少：${text}`)
}
if (!api.includes('adminPage: (questionId: string'))
  throw new Error('管理员审核队列必须按当前题目过滤')
if (!solve.includes('<ProblemSolutionPanel')) throw new Error('做题页尚未接入独立题解组件')
if (!panel.includes('.solution-detail{position:relative'))
  throw new Error('题解详情必须使用正常文档流，不能再以绝对定位覆盖列表')
if (!panel.includes('v-else-if="!selected" class="solution-list"'))
  throw new Error('打开详情时应隐藏题解列表，避免两层内容重叠')
if (panel.includes('.solution-detail__nav{position:sticky'))
  throw new Error('题解返回导航不应长期吸顶遮挡正文')
if (
  !panel.includes(
    '<article class="solution-reader">\n        <header class="solution-detail__nav">',
  )
) {
  throw new Error('题解返回导航应与正文同宽并随文章正常滚动')
}
console.log('题解权限与界面契约检查通过')
