import fs from 'node:fs'
import path from 'node:path'

const root = process.cwd()
const editorPath = path.join(root, 'src/views/admin/ProblemEditorView.vue')
const judgeConfigPath = path.join(root, 'generated/models/JudgeConfig.ts')

for (const file of [editorPath, judgeConfigPath]) {
  if (!fs.existsSync(file)) throw new Error(`图像题实现缺少文件：${path.relative(root, file)}`)
}

const editor = fs.readFileSync(editorPath, 'utf8')
const judgeConfig = fs.readFileSync(judgeConfigPath, 'utf8')

for (const marker of [
  '深度学习目标识别',
  '文字识别',
  '传统计算机视觉',
  '主目标标签分类题',
  '候选标准答案已经人工复核',
  'imageJudgeConfig.countTolerance',
  'imageJudgeConfig.iouThreshold',
  'imageJudgeConfig.ocrEditDistance',
  'imageJudgeConfig.meanGrayTolerance',
  'imageJudgeConfig.edgeStrengthTolerance',
]) {
  if (!editor.includes(marker)) throw new Error(`教师命题页缺少图像题关键实现：${marker}`)
}

if (editor.includes("'结果允许误差'")) {
  throw new Error('教师命题页仍在使用含义模糊的“结果允许误差”')
}

for (const field of [
  'countTolerance?: number',
  'iouThreshold?: number',
  'ocrEditDistance?: number',
  'meanGrayTolerance?: number',
  'edgeStrengthTolerance?: number',
]) {
  if (!judgeConfig.includes(field)) throw new Error(`前端 JudgeConfig 缺少字段：${field}`)
}

console.log('五类图像题命题页面契约检查通过')
