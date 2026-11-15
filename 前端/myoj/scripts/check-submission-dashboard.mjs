import { readFileSync } from 'node:fs'

const view = readFileSync(
  new URL('../src/views/submission/SubmissionsListView.vue', import.meta.url),
  'utf8',
)
const api = readFileSync(new URL('../src/api/submissionDashboard.ts', import.meta.url), 'utf8')

const requirements = [
  [view.includes('学习趋势'), '缺少学习趋势区'],
  [view.includes('verdictOption') && view.includes('languageOption'), '缺少结果或语言图表'],
  [view.includes('submissionId') && view.includes('detail-drawer'), '缺少可恢复的详情抽屉'],
  [view.includes("scope === 'MANAGED'"), '缺少管理范围界面'],
  [
    view.includes('route.query.scope') && view.includes('route.query.userId'),
    '未读取学生管理页传入的数据范围和学生 ID',
  ],
  [
    view.includes('const initialScope') &&
      view.includes('const scope = ref<SubmissionScope>(initialScope)'),
    '数据范围没有在页面初始化前从路由恢复',
  ],
  [
    view.includes("userId: scope.value === 'MANAGED' && selectedUserId.value"),
    '查看指定学生时未锁定该学生的提交记录',
  ],
  [view.includes('auth.isTeacher'), '老师未被识别为可查看受管学生提交记录的角色'],
  [api.includes('/question_submit/page'), '缺少统一列表接口'],
  [api.includes('/question_submit/overview'), '缺少统计接口'],
  [api.includes('/detail'), '缺少详情接口'],
]

const failed = requirements.filter(([ok]) => !ok)
if (failed.length) {
  for (const [, message] of failed) console.error(`submission dashboard: ${message}`)
  process.exit(1)
}
console.log('submission dashboard contract: ok')
