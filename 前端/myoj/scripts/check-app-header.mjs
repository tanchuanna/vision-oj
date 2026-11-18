import fs from 'node:fs'
import path from 'node:path'

const layoutSource = fs.readFileSync(path.join(process.cwd(), 'src/layouts/AppLayout.vue'), 'utf8')
const appSource = fs.readFileSync(path.join(process.cwd(), 'src/App.vue'), 'utf8')
const source = `${appSource}\n${layoutSource}`

for (const marker of [
  '--app-header-height: 66px',
  'min-height: 42px',
  '.nav-link.active::after',
  'backdrop-filter: blur(18px)',
  '<Teleport to="body">',
  'position: fixed;',
  'overflow-y: auto;',
  'updateAdminDropdownPosition',
]) {
  if (!source.includes(marker)) throw new Error(`公共顶栏现代化样式缺少：${marker}`)
}

console.log('公共顶栏视觉与点击区域检查通过')
