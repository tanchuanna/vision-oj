import fs from 'node:fs'
import path from 'node:path'

const source = fs.readFileSync(
  path.join(process.cwd(), 'src/components/blog/BlogCyberShell.vue'),
  'utf8',
)
const layoutSource = fs.readFileSync(path.join(process.cwd(), 'src/layouts/AppLayout.vue'), 'utf8')

for (const marker of [
  '--cb-light-page: #f8f7f4',
  'background: var(--cb-light-page)',
  'background: #ffffff !important',
  'background: #176b6d !important',
  'box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08) !important',
  "html[data-theme='light'] .blog-cyber__spotlight",
]) {
  if (!source.includes(marker)) {
    throw new Error(`博客白天模式清晰度样式缺失：${marker}`)
  }
}

for (const marker of [
  "html[data-theme='light'] .header",
  'background: #fffdf9',
  'backdrop-filter: none',
]) {
  if (!layoutSource.includes(marker)) {
    throw new Error(`公共顶栏白天模式清晰度样式缺失：${marker}`)
  }
}

const lightShell = source.match(/html\[data-theme='light'\] \.blog-cyber \{([^}]*)\}/)?.[1] ?? ''
if (lightShell.includes('filter: blur') || lightShell.includes('backdrop-filter')) {
  throw new Error('博客白天模式仍含有模糊滤镜，会产生蒙雾感')
}

const homeSource = fs.readFileSync(path.join(process.cwd(), 'src/views/blog/BlogHomeView.vue'), 'utf8')
const lightDimCard = homeSource.match(/:global\(html\[data-theme='light'\]\) \.blog-card\.blog-card--dim:not\(:hover\) \{([^}]*)\}/)?.[1] ?? ''
if (!lightDimCard.includes('opacity: 1') || !lightDimCard.includes('filter: none')) {
  throw new Error('博客白天模式仍把未进入观察区的卡片淡化')
}
if (!layoutSource.includes("(to === 'blog' || to === 'blog-detail') && document.documentElement.dataset.theme !== 'dark'")) {
  throw new Error('博客白天模式仍在导航时整页半透明淡入')
}

console.log('博客白天模式清晰度检查通过')
