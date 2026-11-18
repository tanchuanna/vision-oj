<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { SolveRunResult, SolveSyntaxIssue } from './solveWorkbenchTypes'
import { localize as l } from '@/i18n/locale'

const props = defineProps<{
  result: SolveRunResult | null
  running: boolean
  hasPublicSamples: boolean
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

watch(
  () => props.result,
  (value) => {
    if (!value) return
    activeTab.value = 'result'
    const failed = value.cases.findIndex((item) => item.passed === false)
    selectedCase.value = failed >= 0 ? failed : 0
  },
)

watch(
  () => props.running,
  (running) => {
    if (running) activeTab.value = 'result'
  },
)

watch(
  () => props.syntaxIssues.length,
  (count) => {
    if (count > 0 && !props.result) activeTab.value = 'problems'
  },
)

const currentCase = computed(() => props.result?.cases?.[selectedCase.value] ?? null)
const resultGuidance = computed(() => {
  const result = props.result
  if (!result) return ''
  if (!result.terminalError && result.allSamplePassed === true)
    return l(
      '公开样例已通过，可以提交隐藏评测。',
      'Public samples passed. You can submit for full judging.',
    )
  const message = `${result.judgeInfo?.message ?? ''} ${result.sandboxMessage ?? ''}`.toLowerCase()
  if (message.includes('compile') || message.includes('编译'))
    return l(
      '请先点击编译信息中的行号，修复语法或类型错误后重新运行。',
      'Open the compiler error line, fix the syntax or type error, then run again.',
    )
  if (message.includes('time') || message.includes('超时'))
    return l(
      '程序运行超时，请检查死循环，并降低算法时间复杂度。',
      'Time limit exceeded. Check for infinite loops and reduce complexity.',
    )
  if (message.includes('memory') || message.includes('内存'))
    return l(
      '内存超过限制，请减少大数组、缓存或重复保存的数据。',
      'Memory limit exceeded. Reduce large arrays, caches or duplicate data.',
    )
  if (message.includes('runtime') || message.includes('运行'))
    return l(
      '程序运行时异常，请检查数组越界、空值、除零和输入读取。',
      'Runtime error. Check array bounds, null values, division by zero and input parsing.',
    )
  if (result.allSamplePassed === false)
    return l(
      '先对比第一组失败用例的预期输出与实际输出，再修正逻辑。',
      'Compare expected and actual output for the first failed case.',
    )
  return ''
})
</script>

<template>
  <section
    class="solve-console"
    :class="{ 'solve-console--collapsed': collapsed }"
    :aria-label="l('运行与提交控制台', 'Run console')"
  >
    <button
      v-if="!collapsed"
      class="solve-console__resize"
      type="button"
      :aria-label="l('拖动调整控制台高度', 'Drag to resize console')"
      @pointerdown="emit('resize-start', $event)"
    />
    <header class="solve-console__header">
      <div class="solve-console__tabs" role="tablist">
        <button
          v-for="tab in [
            ['cases', l('测试用例', 'Test cases')],
            ['result', l('运行结果', 'Results')],
            [
              'problems',
              `${l('问题', 'Issues')}${syntaxIssues.length ? ` ${syntaxIssues.length}` : ''}`,
            ],
          ] as const"
          :key="tab[0]"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab[0]"
          :class="{ active: activeTab === tab[0] }"
          @click="activeTab = tab[0]"
        >
          {{ tab[1] }}
        </button>
      </div>
      <button
        class="solve-console__collapse"
        type="button"
        @click="emit('update:collapsed', !collapsed)"
      >
        {{ collapsed ? l('展开控制台', 'Expand') : l('收起', 'Collapse') }}
      </button>
    </header>

    <div v-if="!collapsed" class="solve-console__body">
      <div v-if="activeTab === 'cases'" class="console-cases">
        <div class="console-cases__actions">
          <button
            type="button"
            class="console-primary"
            :disabled="running || !hasPublicSamples"
            @click="emit('run-samples')"
          >
            {{
              running ? l('运行中…', 'Running…') : l('运行全部公开样例', 'Run all public samples')
            }}
          </button>
          <span>Ctrl / ⌘ + Enter</span>
        </div>
        <p v-if="!hasPublicSamples" class="console-guidance">
          {{
            imageQuestion
              ? l(
                  '这道图像题没有公开样例，请联系出题人补充。',
                  'This image problem has no public sample. Contact the author.',
                )
              : l(
                  '这道题没有公开样例。可在下方填写自定义输入运行，或直接提交完整评测。',
                  'No public samples. Enter custom input below or submit for full judging.',
                )
          }}
        </p>
        <label class="custom-input-label" for="custom-stdin">{{
          l('自定义输入', 'Custom input')
        }}</label>
        <textarea
          id="custom-stdin"
          :value="customInput"
          :disabled="imageQuestion || running"
          maxlength="50000"
          :placeholder="
            imageQuestion
              ? l('图像题由评测环境提供只读图片路径', 'Image paths are provided by the judge')
              : l(
                  '可留空；空格与换行会原样传给程序',
                  'Can be empty; spaces and line breaks are preserved',
                )
          "
          @input="emit('update:customInput', ($event.target as HTMLTextAreaElement).value)"
        />
        <div class="custom-input-foot">
          <span v-if="imageQuestion">{{
            l('图像题暂不支持自定义 stdin', 'Custom stdin is unavailable for image problems')
          }}</span>
          <span v-else>{{ customInput.length.toLocaleString() }} / 50,000</span>
          <button type="button" :disabled="imageQuestion || running" @click="emit('run-custom')">
            {{ l('运行自定义输入', 'Run custom input') }}
          </button>
        </div>
      </div>

      <div v-else-if="activeTab === 'result'" class="console-result">
        <div v-if="running" class="console-running" role="status">
          <span class="console-running__spinner" aria-hidden="true" />
          <div>
            <strong>{{ l('正在运行代码', 'Running code') }}</strong>
            <p>
              {{
                l(
                  '正在执行公开样例或自定义输入，结果会显示在这里。',
                  'Results will appear here when execution finishes.',
                )
              }}
            </p>
          </div>
        </div>
        <div v-else-if="!result" class="console-empty">
          {{
            l(
              '运行代码后，这里会展示每组输入、输出和资源消耗。',
              'Run your code to see inputs, outputs and resource usage.',
            )
          }}
        </div>
        <template v-else>
          <div
            class="console-result__summary"
            role="status"
            aria-live="polite"
            :class="{
              ok: result.allSamplePassed === true,
              fail: result.terminalError || result.allSamplePassed === false,
            }"
          >
            <strong>{{
              result.terminalError
                ? result.judgeInfo?.message || l('执行异常', 'Execution error')
                : result.mode === 'CUSTOM'
                  ? l('自定义运行完成', 'Custom run finished')
                  : result.allSamplePassed
                    ? l('公开样例全部通过', 'All public samples passed')
                    : l('存在未通过的样例', 'Some samples failed')
            }}</strong>
            <span v-if="result.judgeInfo?.time != null">{{ result.judgeInfo.time }} ms</span>
            <span v-if="result.judgeInfo?.memory != null">{{ result.judgeInfo.memory }} KB</span>
          </div>
          <pre v-if="result.terminalError && result.judgeInfo?.detail" class="console-error">{{
            result.judgeInfo.detail
          }}</pre>
          <p v-if="result.warning" class="console-warning">{{ result.warning }}</p>
          <p v-if="resultGuidance" class="console-guidance">{{ resultGuidance }}</p>
          <div v-if="result.cases.length" class="console-result__layout">
            <nav
              v-if="result.cases.length > 1"
              class="case-tabs"
              :aria-label="l('运行用例', 'Run cases')"
            >
              <button
                v-for="(item, index) in result.cases"
                :key="item.index"
                type="button"
                :class="{ active: selectedCase === index, fail: item.passed === false }"
                @click="selectedCase = index"
              >
                {{ l('用例', 'Case') }} {{ item.index }}
              </button>
            </nav>
            <div v-if="currentCase" class="case-output-grid">
              <section>
                <span>{{ l('输入', 'Input') }}</span>
                <pre>{{ currentCase.input || l('(空 stdin)', '(empty stdin)') }}</pre>
              </section>
              <section v-if="currentCase.expectedOutput != null">
                <span>{{ l('预期输出', 'Expected output') }}</span>
                <pre>{{ currentCase.expectedOutput }}</pre>
              </section>
              <section>
                <span>{{ l('实际输出', 'Actual output') }}</span>
                <pre>{{ currentCase.actualOutput || l('(无输出)', '(no output)') }}</pre>
              </section>
            </div>
          </div>
          <ul v-if="result.hints?.length" class="console-hints">
            <li v-for="hint in result.hints" :key="hint">{{ hint }}</li>
          </ul>
        </template>
      </div>

      <div v-else class="console-problems">
        <div v-if="!syntaxIssues.length" class="console-empty">
          {{ l('当前没有发现语法问题。', 'No syntax issues found.') }}
        </div>
        <button
          v-for="(issue, index) in syntaxIssues"
          :key="`${issue.line}-${issue.column}-${index}`"
          type="button"
          @click="emit('jump-issue', issue.line, issue.column)"
        >
          <span
            >{{ issue.severity === 'ERROR' ? l('错误', 'Error') : l('警告', 'Warning') }} · L{{
              issue.line
            }}:{{ issue.column }}</span
          >
          <strong>{{ issue.message }}</strong>
        </button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.solve-console {
  position: relative;
  min-height: 0;
  border-top: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface) 97%, var(--app-accent) 3%);
}
.solve-console--collapsed {
  flex: 0 0 45px;
}
.solve-console__resize {
  position: absolute;
  z-index: 2;
  top: -5px;
  left: 0;
  width: 100%;
  height: 10px;
  border: 0;
  cursor: row-resize;
  background: transparent;
}
.solve-console__resize::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 4px;
  width: 46px;
  height: 3px;
  border-radius: 99px;
  background: var(--app-border-strong, var(--app-border));
  transform: translateX(-50%);
}
.solve-console__header {
  height: 45px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 12px;
}
.solve-console__tabs {
  display: flex;
  min-width: 0;
  height: 100%;
  overflow-x: auto;
}
.solve-console__tabs button,
.solve-console__collapse {
  min-height: 44px;
  border: 0;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
}
.solve-console__tabs button {
  position: relative;
  padding: 0 14px;
}
.solve-console__tabs button.active {
  color: var(--app-accent);
}
.solve-console__tabs button.active::after {
  content: '';
  position: absolute;
  left: 12px;
  right: 12px;
  bottom: 0;
  height: 2px;
  background: var(--app-accent);
  border-radius: 99px;
}
.solve-console__body {
  height: calc(100% - 45px);
  overflow: auto;
  padding: 12px 14px 16px;
}
.console-cases__actions,
.custom-input-foot,
.console-result__summary {
  display: flex;
  align-items: center;
  gap: 10px;
}
.console-cases__actions {
  justify-content: space-between;
  margin-bottom: 10px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.console-primary,
.custom-input-foot button {
  min-height: 38px;
  border: 1px solid color-mix(in srgb, var(--app-accent) 55%, var(--app-border));
  border-radius: 10px;
  padding: 0 14px;
  background: color-mix(in srgb, var(--app-accent) 12%, var(--app-surface));
  color: var(--app-accent);
  font-weight: 800;
  cursor: pointer;
}
.console-primary:disabled,
.custom-input-foot button:disabled {
  opacity: 0.48;
  cursor: not-allowed;
}
.custom-input-label {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text);
  font-weight: 800;
}
.console-cases textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 86px;
  resize: vertical;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 10px 12px;
  background: var(--app-surface);
  color: var(--app-text);
  font:
    13px/1.55 ui-monospace,
    SFMono-Regular,
    Consolas,
    monospace;
}
.custom-input-foot {
  justify-content: space-between;
  margin-top: 8px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.console-result__summary {
  flex-wrap: wrap;
  padding: 10px 12px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--app-warning, #d97706) 10%, var(--app-surface));
  color: var(--app-text);
}
.console-result__summary.ok {
  background: color-mix(in srgb, #10a37f 13%, var(--app-surface));
  color: #08745c;
}
.console-result__summary.fail {
  background: color-mix(in srgb, #ef4444 11%, var(--app-surface));
  color: #b42318;
}
.console-result__summary strong {
  margin-right: auto;
}
.console-error,
.case-output-grid pre {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  font:
    13px/1.55 ui-monospace,
    SFMono-Regular,
    Consolas,
    monospace;
}
.console-error {
  margin-top: 10px;
  padding: 10px;
  border-radius: 10px;
  background: #2b1015;
  color: #fecaca;
}
.console-warning {
  color: #b45309;
}
.console-guidance {
  margin: 9px 0 0;
  border-left: 3px solid var(--app-accent);
  padding: 7px 10px;
  background: color-mix(in srgb, var(--app-accent) 7%, transparent);
  color: var(--app-text-muted);
  font-size: 12px;
}
.console-result__layout {
  margin-top: 10px;
}
.case-tabs {
  display: flex;
  gap: 6px;
  overflow-x: auto;
  margin-bottom: 8px;
}
.case-tabs button {
  min-height: 34px;
  border: 1px solid var(--app-border);
  border-radius: 9px;
  background: var(--app-surface);
  color: var(--app-text-muted);
  cursor: pointer;
}
.case-tabs button.active {
  border-color: var(--app-accent);
  color: var(--app-accent);
}
.case-tabs button.fail {
  color: #b42318;
}
.case-output-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.case-output-grid section {
  min-width: 0;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 9px;
  background: var(--app-surface);
}
.case-output-grid span {
  display: block;
  margin-bottom: 5px;
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 800;
}
.console-hints {
  margin: 10px 0 0;
  padding-left: 18px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.console-problems {
  display: grid;
  gap: 7px;
}
.console-problems button {
  display: grid;
  grid-template-columns: 110px 1fr;
  gap: 8px;
  min-height: 42px;
  text-align: left;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 8px 10px;
  background: var(--app-surface);
  color: var(--app-text);
  cursor: pointer;
}
.console-problems span {
  color: #b42318;
  font-size: 12px;
}
.console-empty {
  padding: 18px;
  text-align: center;
  color: var(--app-text-muted);
}
.console-running {
  display: flex;
  align-items: center;
  gap: 13px;
  min-height: 76px;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface);
}
.console-running strong {
  color: var(--app-text);
  font-size: 14px;
}
.console-running p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}
.console-running__spinner {
  flex: none;
  width: 22px;
  height: 22px;
  border: 2px solid color-mix(in srgb, var(--app-accent) 22%, transparent);
  border-top-color: var(--app-accent);
  border-radius: 50%;
  animation: console-spin 0.8s linear infinite;
}
.console-result__summary {
  animation: console-result-enter 0.24s ease-out both;
}
.console-primary:not(:disabled):active,
.custom-input-foot button:not(:disabled):active {
  transform: scale(0.98);
}
@keyframes console-spin {
  to {
    transform: rotate(360deg);
  }
}
@keyframes console-result-enter {
  from {
    opacity: 0.65;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .console-running__spinner {
    animation: none;
    border-color: var(--app-accent);
  }
  .console-result__summary {
    animation: none;
  }
  .console-primary:not(:disabled):active,
  .custom-input-foot button:not(:disabled):active {
    transform: none;
  }
}
@media (max-width: 700px) {
  .case-output-grid {
    grid-template-columns: 1fr;
  }
  .solve-console__tabs button {
    padding: 0 9px;
  }
}
</style>
