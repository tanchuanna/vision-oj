import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const componentPath = path.join(root, 'src/views/problem/ProblemStatisticsPanel.vue')
const solvePath = path.join(root, 'src/views/problem/ProblemSolveView.vue')
const apiPath = path.join(root, 'src/api/questionStatistics.ts')

for (const file of [componentPath, solvePath, apiPath]) {
  if (!fs.existsSync(file)) throw new Error(`题目统计实现缺少文件：${path.relative(root, file)}`)
}

const component = fs.readFileSync(componentPath, 'utf8')
const solve = fs.readFileSync(solvePath, 'utf8')
const api = fs.readFileSync(apiPath, 'utf8')

for (const marker of ['VChart', 'PieChart', 'fastestRankings', 'memoryRankings', '暂无有效排名']) {
  if (!component.includes(marker)) throw new Error(`题目统计面板缺少关键实现：${marker}`)
}
if (!solve.includes('<ProblemStatisticsPanel')) throw new Error('做题页尚未接入题目统计面板')
if (!api.includes('/question_submit/public/statistics'))
  throw new Error('题目统计 API 未接入公开接口')

console.log('题目统计页面契约检查通过')
