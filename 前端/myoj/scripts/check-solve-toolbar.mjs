import fs from 'node:fs'
import path from 'node:path'

const file = path.resolve('src/views/problem/ProblemSolveView.vue')
const source = fs.readFileSync(file, 'utf8')
const normalizedSource = source.replace(/>\s+/g, '>').replace(/\s+<\/button>/g, '</button>')
const appSource = fs.readFileSync(path.resolve('src/App.vue'), 'utf8')
const layoutSource = fs.readFileSync(path.resolve('src/layouts/AppLayout.vue'), 'utf8')

const checks = [
  ['设置按钮', 'class="editor-settings-trigger"'],
  ['展开状态', ':aria-expanded="editorSettingsOpen"'],
  ['独立菜单', 'class="editor-settings-popover"'],
  ['点击外部关闭', 'onEditorSettingsOutsidePointer'],
  ['全宽工作台', 'solve-page--edge-to-edge'],
  ['题目描述导航', "l('题目描述', 'Description')"],
  ['题解导航', "l('题解', 'Solutions')"],
  ['题目提交记录导航', "l('提交记录', 'Submissions')"],
  ['题目统计导航', "l('统计', 'Statistics')"],
]

for (const [name, marker] of checks) {
  if (!normalizedSource.includes(marker)) throw new Error(`做题页编辑器设置缺少：${name}`)
}

if (source.includes('<details class="editor-settings-menu"')) {
  throw new Error('编辑器设置仍在使用会挤压工具栏的原生 details 结构')
}

if (!appSource.includes('--app-header-height: 66px')) {
  throw new Error('全站缺少统一的桌面导航高度变量')
}

if (!layoutSource.includes('height: var(--app-header-height)')) {
  throw new Error('顶部导航没有复用统一高度变量')
}

if (!source.includes('height: calc(100dvh - var(--app-header-height))')) {
  throw new Error('做题工作台没有按真实导航高度填满视口')
}

if (!source.includes('max-height: none;') || !/\.problem-panel,\s*\.answer-panel/.test(source)) {
  throw new Error('做题工作台仍可能被旧版面板最大高度截短并在底部留空')
}

console.log('做题页工具栏交互检查通过')
