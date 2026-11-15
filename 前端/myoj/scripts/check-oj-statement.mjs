import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import ts from 'typescript'

const sourcePath = fileURLToPath(new URL('../src/utils/normalizeOjStatement.ts', import.meta.url))
const source = readFileSync(sourcePath, 'utf8')
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText
const exports = {}
new Function('exports', compiled)(exports)
const normalize = exports.normalizeOjStatement

assert.equal(normalize('范围：$1 \\le a \\le 10^9$'), '范围：1 ≤ a ≤ 10^9')
assert.equal(normalize('解释：$3$ 次'), '解释：3 次')
assert.equal(normalize('上限：\\(2^{31}-1\\)'), '上限：2^31-1')
assert.equal(normalize('$$a \\ge 2$$'), 'a ≥ 2')
assert.equal(normalize('上界 \\leq 10'), '上界 ≤ 10')
assert.equal(normalize('价格 $5 to $10'), '价格 $5 to $10')
const code = '```python\nprint("$3$")\n```'
assert.equal(normalize(code), code)
assert.equal(normalize('`$a \\le b$`'), '`$a \\le b$`')

console.log('题面格式回归检查通过（8 个场景）')
