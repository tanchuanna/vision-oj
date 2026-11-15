<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SolveRunResult, SolveSyntaxIssue } from './solveWorkbenchTypes'

const props = defineProps<{
  result: SolveRunResult | null
  running: boolean
  customInput: string
  imageQuestion: boolean
  syntaxIssues: SolveSyntaxIssue[]
  collapsed: boolean
}>()

const emit = defineEmits<{
  'update:customInput': [value: string]
  'update:collapsed': [value: boolean]
  'run-samples': []
  'run-custom': []
  'jump-issue': [line: number, column: number]
  'resize-start': [event: PointerEvent]
}>()

type ConsoleTab = 'cases' | 'result' | 'problems'
const activeTab = ref<ConsoleTab>('cases')
const selectedCase = ref(0)

watch(() => props.result, (value) => {
  if (!value) return
  activeTab.value = 'result'
  const failed = value.cases.findIndex((item) => item.passed === false)
  selectedCase.value = failed >= 0 ? failed : 0
})

watch(() => props.syntaxIssues.length, (count) => {
  if (count > 0 && !props.result) activeTab.value = 'problems'
})

const currentCase = computed(() => props.result?.cases?.[selectedCase.value] ?? null)
const resultGuidance = computed(() => {
  const result = props.result
  if (!result) return ''
  if (!result.terminalError && result.allSamplePassed === true) return '公开样例已通过，可以提交隐藏评测。'
  const message = `${result.judgeInfo?.message ?? ''} ${result.sandboxMessage ?? ''}`.toLowerCase()
  if (message.includes('compile') || message.includes('编译')) return '请先点击编译信息中的行号，修复语法或类型错误后重新运行。'
  if (message.includes('time') || message.includes('超时')) return '程序运行超时，请检查死循环，并降低算法时间复杂度。'
  if (message.includes('memory') || message.includes('内存')) return '内存超过限制，请减少大数组、缓存或重复保存的数据。'
  if (message.includes('runtime') || message.includes('运行')) return '程序运行时异常，请检查数组越界、空值、除零和输入读取。'
  if (result.allSamplePassed === false) return '先对比第一组失败用例的预期输出与实际输出，再修正逻辑。'
  return ''
})

</script>

<template>
  <section class="solve-console" :class="{ 'solve-console--collapsed': collapsed }" aria-label="运行与提交控制台">
    <button v-if="!collapsed" class="solve-console__resize" type="button" aria-label="拖动调整控制台高度" @pointerdown="emit('resize-start', $event)" />
    <header class="solve-console__header">
      <div class="solve-console__tabs" role="tablist">
        <button v-for="tab in ([['cases', '测试用例'], ['result', '运行结果'], ['problems', `问题${syntaxIssues.length ? ` ${syntaxIssues.length}` : ''}`]] as const)" :key="tab[0]" type="button" role="tab" :aria-selected="activeTab === tab[0]" :class="{ active: activeTab === tab[0] }" @click="activeTab = tab[0]">{{ tab[1] }}</button>
      </div>
      <button class="solve-console__collapse" type="button" @click="emit('update:collapsed', !collapsed)">{{ collapsed ? '展开控制台' : '收起' }}</button>
    </header>

    <div v-if="!collapsed" class="solve-console__body">
      <div v-if="activeTab === 'cases'" class="console-cases">
        <div class="console-cases__actions">
          <button type="button" class="console-primary" :disabled="running" @click="emit('run-samples')">{{ running ? '运行中…' : '运行全部公开样例' }}</button>
          <span>Ctrl / ⌘ + Enter</span>
        </div>
        <label class="custom-input-label" for="custom-stdin">自定义输入</label>
        <textarea id="custom-stdin" :value="customInput" :disabled="imageQuestion || running" maxlength="50000" :placeholder="imageQuestion ? '图像题由评测环境提供只读图片路径' : '可留空；空格与换行会原样传给程序'" @input="emit('update:customInput', ($event.target as HTMLTextAreaElement).value)" />
        <div class="custom-input-foot">
          <span v-if="imageQuestion">图像题暂不支持自定义 stdin</span>
          <span v-else>{{ customInput.length.toLocaleString() }} / 50,000</span>
          <button type="button" :disabled="imageQuestion || running" @click="emit('run-custom')">运行自定义输入</button>
        </div>
      </div>

      <div v-else-if="activeTab === 'result'" class="console-result">
        <div v-if="!result" class="console-empty">运行代码后，这里会展示每组输入、输出和资源消耗。</div>
        <template v-else>
          <div class="console-result__summary" :class="{ ok: result.allSamplePassed === true, fail: result.terminalError || result.allSamplePassed === false }">
            <strong>{{ result.terminalError ? (result.judgeInfo?.message || '执行异常') : result.mode === 'CUSTOM' ? '自定义运行完成' : result.allSamplePassed ? '公开样例全部通过' : '存在未通过的样例' }}</strong>
            <span v-if="result.judgeInfo?.time != null">{{ result.judgeInfo.time }} ms</span>
            <span v-if="result.judgeInfo?.memory != null">{{ result.judgeInfo.memory }} KB</span>
          </div>
          <pre v-if="result.terminalError && result.judgeInfo?.detail" class="console-error">{{ result.judgeInfo.detail }}</pre>
          <p v-if="result.warning" class="console-warning">{{ result.warning }}</p>
          <p v-if="resultGuidance" class="console-guidance">{{ resultGuidance }}</p>
          <div v-if="result.cases.length" class="console-result__layout">
            <nav v-if="result.cases.length > 1" class="case-tabs" aria-label="运行用例">
              <button v-for="(item, index) in result.cases" :key="item.index" type="button" :class="{ active: selectedCase === index, fail: item.passed === false }" @click="selectedCase = index">用例 {{ item.index }}</button>
            </nav>
            <div v-if="currentCase" class="case-output-grid">
              <section><span>输入</span><pre>{{ currentCase.input || '(空 stdin)' }}</pre></section>
              <section v-if="currentCase.expectedOutput != null"><span>预期输出</span><pre>{{ currentCase.expectedOutput }}</pre></section>
              <section><span>实际输出</span><pre>{{ currentCase.actualOutput || '(无输出)' }}</pre></section>
            </div>
          </div>
          <ul v-if="result.hints?.length" class="console-hints"><li v-for="hint in result.hints" :key="hint">{{ hint }}</li></ul>
        </template>
      </div>

      <div v-else class="console-problems">
        <div v-if="!syntaxIssues.length" class="console-empty">当前没有发现语法问题。</div>
        <button v-for="(issue, index) in syntaxIssues" :key="`${issue.line}-${issue.column}-${index}`" type="button" @click="emit('jump-issue', issue.line, issue.column)">
          <span>{{ issue.severity === 'ERROR' ? '错误' : '警告' }} · L{{ issue.line }}:{{ issue.column }}</span>
          <strong>{{ issue.message }}</strong>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.solve-console { position: relative; min-height: 0; border-top: 1px solid var(--app-border); background: color-mix(in srgb, var(--app-surface) 97%, var(--app-accent) 3%); }
.solve-console--collapsed { flex: 0 0 45px; }
.solve-console__resize { position: absolute; z-index: 2; top: -5px; left: 0; width: 100%; height: 10px; border: 0; cursor: row-resize; background: transparent; }
.solve-console__resize::after { content: ''; position: absolute; left: 50%; top: 4px; width: 46px; height: 3px; border-radius: 99px; background: var(--app-border-strong, var(--app-border)); transform: translateX(-50%); }
.solve-console__header { height: 45px; display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 0 12px; }
.solve-console__tabs { display: flex; min-width: 0; height: 100%; overflow-x: auto; }
.solve-console__tabs button, .solve-console__collapse { min-height: 44px; border: 0; background: transparent; color: var(--app-text-muted); cursor: pointer; font-weight: 700; white-space: nowrap; }
.solve-console__tabs button { position: relative; padding: 0 14px; }
.solve-console__tabs button.active { color: var(--app-accent); }
.solve-console__tabs button.active::after { content: ''; position: absolute; left: 12px; right: 12px; bottom: 0; height: 2px; background: var(--app-accent); border-radius: 99px; }
.solve-console__body { height: calc(100% - 45px); overflow: auto; padding: 12px 14px 16px; }
.console-cases__actions, .custom-input-foot, .console-result__summary { display: flex; align-items: center; gap: 10px; }
.console-cases__actions { justify-content: space-between; margin-bottom: 10px; color: var(--app-text-muted); font-size: 12px; }
.console-primary, .custom-input-foot button { min-height: 38px; border: 1px solid color-mix(in srgb, var(--app-accent) 55%, var(--app-border)); border-radius: 10px; padding: 0 14px; background: color-mix(in srgb, var(--app-accent) 12%, var(--app-surface)); color: var(--app-accent); font-weight: 800; cursor: pointer; }
.custom-input-label { display: block; margin-bottom: 6px; color: var(--app-text); font-weight: 800; }
.console-cases textarea { box-sizing: border-box; width: 100%; min-height: 86px; resize: vertical; border: 1px solid var(--app-border); border-radius: 12px; padding: 10px 12px; background: var(--app-surface); color: var(--app-text); font: 13px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace; }
.custom-input-foot { justify-content: space-between; margin-top: 8px; color: var(--app-text-muted); font-size: 12px; }
.console-result__summary { flex-wrap: wrap; padding: 10px 12px; border-radius: 12px; background: color-mix(in srgb, var(--app-warning, #d97706) 10%, var(--app-surface)); color: var(--app-text); }
.console-result__summary.ok { background: color-mix(in srgb, #10a37f 13%, var(--app-surface)); color: #08745c; }
.console-result__summary.fail { background: color-mix(in srgb, #ef4444 11%, var(--app-surface)); color: #b42318; }
.console-result__summary strong { margin-right: auto; }
.console-error, .case-output-grid pre { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.55 ui-monospace, SFMono-Regular, Consolas, monospace; }
.console-error { margin-top: 10px; padding: 10px; border-radius: 10px; background: #2b1015; color: #fecaca; }
.console-warning { color: #b45309; }
.console-guidance { margin: 9px 0 0; border-left: 3px solid var(--app-accent); padding: 7px 10px; background: color-mix(in srgb, var(--app-accent) 7%, transparent); color: var(--app-text-muted); font-size: 12px; }
.console-result__layout { margin-top: 10px; }
.case-tabs { display: flex; gap: 6px; overflow-x: auto; margin-bottom: 8px; }
.case-tabs button { min-height: 34px; border: 1px solid var(--app-border); border-radius: 9px; background: var(--app-surface); color: var(--app-text-muted); cursor: pointer; }
.case-tabs button.active { border-color: var(--app-accent); color: var(--app-accent); }
.case-tabs button.fail { color: #b42318; }
.case-output-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 8px; }
.case-output-grid section { min-width: 0; border: 1px solid var(--app-border); border-radius: 10px; padding: 9px; background: var(--app-surface); }
.case-output-grid span { display: block; margin-bottom: 5px; color: var(--app-text-muted); font-size: 11px; font-weight: 800; }
.console-hints { margin: 10px 0 0; padding-left: 18px; color: var(--app-text-muted); font-size: 12px; line-height: 1.55; }
.console-problems { display: grid; gap: 7px; }.console-problems button { display: grid; grid-template-columns: 110px 1fr; gap: 8px; min-height: 42px; text-align: left; border: 1px solid var(--app-border); border-radius: 10px; padding: 8px 10px; background: var(--app-surface); color: var(--app-text); cursor: pointer; }.console-problems span { color: #b42318; font-size: 12px; }
.console-empty { padding: 18px; text-align: center; color: var(--app-text-muted); }
@media (max-width: 700px) { .case-output-grid { grid-template-columns: 1fr; }.solve-console__tabs button { padding: 0 9px; } }
</style>
