/** Keep the teacher preview, saved statement and student view consistent. */
export function normalizeOjStatement(source: string): string {
  const normalizeMath = (text: string) => text
    .replace(/\\leq?\b/g, '≤')
    .replace(/\\geq?\b/g, '≥')
    .replace(/\\times\b/g, '×')
    .replace(/\\cdot\b/g, '·')
    .replace(/\\neq\b/g, '≠')
    .replace(/\\left\b|\\right\b/g, '')
    .replace(/\\([{}])/g, '$1')
    .replace(/\^\{([^{}]+)\}/g, '^$1')
    .replace(/_\{([^{}]+)\}/g, '_$1')

  const normalizePlain = (text: string) => normalizeMath(text
    .replace(/\\\(([^\r\n]+?)\\\)/g, (_, math: string) => normalizeMath(math))
    .replace(/\\\[([^\r\n]+?)\\\]/g, (_, math: string) => normalizeMath(math))
    .replace(/\$\$([^$\r\n]+)\$\$/g, (_, math: string) => normalizeMath(math))
    .replace(/\$([^$\r\n]+)\$/g, (full, math: string) => {
      // Preserve ordinary prices such as "$5 to $10".
      if (!/\\|[<>=^_+*/-]/.test(math) && !/^\s*(?:\d+|[a-zA-Z])\s*$/.test(math)) return full
      return normalizeMath(math)
    }))

  // Markdown code examples must keep their exact bytes, including $ and backslashes.
  return (source || '').split(/(```[\s\S]*?```|`[^`\r\n]*`)/g)
    .map((part, index) => index % 2 === 1 ? part : normalizePlain(part))
    .join('')
}
