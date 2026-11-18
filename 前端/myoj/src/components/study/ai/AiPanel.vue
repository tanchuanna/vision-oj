<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, provide, ref, watch, watchEffect } from 'vue'
import { Message } from '@arco-design/web-vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useStudyAiCopilotStore } from '@/stores/studyAiCopilot'
import { useBanCountdownTick } from '@/composables/useBanCountdownTick'
import { formatBanCountdown, parseBanUntilMs } from '@/utils/formatBanCountdown'
import ModerationBanNotice from '@/components/moderation/ModerationBanNotice.vue'
import { useStudyAiCopilotStream } from '@/composables/useStudyAiCopilotStream'
import type { BailianTurn } from '@/api/bailianAssist'
import AiTabs from './AiTabs.vue'
import AiMessage from './AiMessage.vue'
import AiInput from './AiInput.vue'
import { AI_COPILOT_ACTIVE_TAB_KEY, type AiCopilotTabId } from './keys'
import AiHeroIcon from './AiHeroIcon.vue'
import AiModelPicker from './AiModelPicker.vue'
import { useAiModelsStore } from '@/stores/aiModels'
import AiProviderAvatar from './AiProviderAvatar.vue'
import { type CodeEdit, requestCodeEdit } from '@/api/codeEdit'
import { shouldAutoEditCode } from './codeEditIntent'
import { localize as l } from '@/i18n/locale'

const open = defineModel<boolean>('open', { default: false })

const props = withDefaults(
  defineProps<{
    questionId: string
    questionTitle: string
    questionContent: string
    language: string
    questionType: string
    judgeContext: string
    sampleCases: string
    runResult: string
    getCode: () => string
    applyCodeEdits: (
      base: string,
      edits: CodeEdit[],
      signal: AbortSignal,
      onStep: (step: number, total: number, reason: string) => void,
    ) => Promise<void>
    undoCodeEdit: () => boolean
    runSamples: () => Promise<void>
  }>(),
  {
    questionId: '',
    questionTitle: '',
    questionContent: '',
    language: 'cpp',
    questionType: 'TEXT',
    judgeContext: '',
    sampleCases: '',
    runResult: '',
  },
)

const editBusy = ref(false)
const editStage = ref<'idle' | 'generating' | 'applying' | 'running'>('idle')
const editStatus = ref('')
const canUndoEdit = ref(false)
let editController: AbortController | null = null

async function startCodeEdit(instruction: string, referenceAnswer?: string) {
  if (editBusy.value || sending.value || aiAssistBanned.value) return
  const base = props.getCode()
  if (!base.trim() && !auth.isAdmin && !auth.isTeacher)
    return void Message.warning(
      l(
        '请先在编辑器写入代码，再让助手修复',
        'Write some code in the editor before asking for a fix.',
      ),
    )
  const requestedQuestion = props.questionId
  const requestedLanguage = props.language
  const controller = new AbortController()
  editController = controller
  editBusy.value = true
  editStage.value = 'generating'
  canUndoEdit.value = false
  editStatus.value = l('正在分析当前代码和公开样例…', 'Analyzing your code and public samples…')
  activeTab.value = 'chat'
  try {
    const proposal = await requestCodeEdit(
      {
        questionId: props.questionId,
        instruction,
        code: base,
        language: props.language,
        questionTitle: props.questionTitle,
        questionContent: props.questionContent.slice(0, 12000),
        sampleCases: props.sampleCases.slice(0, 6000),
        runResult: props.runResult.slice(0, 10000),
        referenceAnswer: referenceAnswer?.slice(0, 12000),
        providerCode: aiModels.selectedProvider,
        modelId: aiModels.selectedModel || undefined,
      },
      controller.signal,
    )
    if (controller.signal.aborted)
      throw new DOMException(l('已停止修改', 'Code editing stopped'), 'AbortError')
    if (props.questionId !== requestedQuestion || props.language !== requestedLanguage)
      throw new Error(
        l(
          '题目或语言已切换，请重新生成修改建议',
          'The problem or language changed. Generate a new edit proposal.',
        ),
      )
    editStage.value = 'applying'
    editStatus.value = l(
      `已找到 ${proposal.edits.length} 处修改，正在写入…`,
      `Found ${proposal.edits.length} edits. Writing them now…`,
    )
    await props.applyCodeEdits(base, proposal.edits, controller.signal, (step, total, reason) => {
      editStatus.value = l(
        `修改第 ${step}/${total} 处：${reason || '更新代码'}`,
        `Applying edit ${step}/${total}: ${reason || 'Update code'}`,
      )
    })
    if (controller.signal.aborted) {
      props.undoCodeEdit()
      throw new DOMException(l('已停止修改', 'Code editing stopped'), 'AbortError')
    }
    canUndoEdit.value = true
    editStage.value = 'running'
    editStatus.value = l('修改完成，正在运行公开样例…', 'Edits complete. Running public samples…')
    messages.value.push({ id: `edit-user-${Date.now()}`, role: 'user', content: instruction })
    messages.value.push({
      id: `edit-result-${Date.now()}`,
      role: 'assistant',
      content: l(
        `已修改 ${proposal.edits.length} 处。${proposal.summary}\n\n正在运行公开样例；代码不会自动提交。`,
        `Applied ${proposal.edits.length} edits. ${proposal.summary}\n\nRunning public samples. The code will not be submitted automatically.`,
      ),
      providerCode: aiModels.selectedProvider,
      modelId: aiModels.selectedModel,
    })
    await props.runSamples()
    await nextTick()
    editStatus.value = props.runResult
      ? l(
          '修改已写入，公开样例运行结果见下方；你可以撤销本次修改。',
          'Edits applied. Review the public sample results below; you can undo these edits.',
        )
      : l(
          '代码已写入，但样例运行没有返回结果；请查看页面提示后重试。',
          'Code was updated, but no sample result was returned. Check the page notice and retry.',
        )
  } catch (error) {
    editStatus.value = controller.signal.aborted
      ? l('已停止；写入中的修改已恢复。', 'Stopped. In-progress edits were restored.')
      : error instanceof Error
        ? error.message
        : l('修改失败，编辑器未改变', 'Editing failed. The editor was not changed.')
    if (!controller.signal.aborted) Message.warning(editStatus.value)
  } finally {
    if (editController === controller) editController = null
    editBusy.value = false
    editStage.value = 'idle'
  }
}

function stopCodeEdit() {
  editController?.abort()
  editStatus.value = l('正在停止并恢复代码…', 'Stopping and restoring your code…')
}

watch(
  () => [props.questionId, props.language],
  () => editController?.abort(),
)

function undoCodeEdit() {
  if (props.undoCodeEdit()) {
    canUndoEdit.value = false
    editStatus.value = l('已撤销本次修改。', 'These edits were undone.')
  } else {
    canUndoEdit.value = false
    Message.warning(
      l(
        '代码已被再次编辑，无法安全撤销本次修改',
        'The code changed again, so these edits cannot be undone safely.',
      ),
    )
  }
}

type QuickChip = { icon: string; label: string; text: string; mode?: 'edit' }
const quickActions = computed<QuickChip[]>(() => {
  const common: QuickChip[] = [
    {
      icon: '💡',
      label: l('一级提示', 'First hint'),
      text: l(
        '请只给我一级提示：指出解题方向和需要观察的规律，不要给算法步骤或代码。',
        'Give me a first hint: point out the direction and patterns to notice, without algorithm steps or code.',
      ),
    },
    {
      icon: '🧭',
      label: l('进阶提示', 'Deeper hint'),
      text: l(
        '请给我二级提示：列出关键算法步骤和伪代码，但不要给可直接提交的完整程序。',
        'Give me a deeper hint with key algorithm steps and pseudocode, but no complete solution.',
      ),
    },
    {
      icon: '🔍',
      label: l('分析代码', 'Review code'),
      text: l(
        '请结合当前代码逐点分析最可能的问题，标明原因和最小修改方向，不要重写整题。',
        'Review my current code, explain likely problems and minimal fixes, without rewriting the whole solution.',
      ),
    },
    {
      icon: '⏱',
      label: l('复杂度检查', 'Check complexity'),
      text: l(
        '请根据题目约束和当前代码分析时间、空间复杂度，并判断是否可能 TLE 或 MLE。',
        'Analyze time and space complexity against the constraints and assess TLE or MLE risk.',
      ),
    },
    {
      icon: '🧪',
      label: l('边界用例', 'Edge cases'),
      text: l(
        '请列出容易 WA 的边界情况，并给少量可手算的自测输入与预期现象。',
        'List edge cases likely to cause WA and a few small manual tests.',
      ),
    },
  ]
  if (props.runResult.trim()) {
    common.splice(3, 0, {
      label: l('分析运行结果', 'Review run result'),
      icon: '🩺',
      text: l(
        '请分析最近一次运行结果：判断属于 CE、RE、WA、TLE 还是 MLE，并对照公开样例给出排查顺序和最小修改建议。',
        'Analyze my latest run: classify CE, RE, WA, TLE or MLE and suggest minimal debugging steps using public samples.',
      ),
    })
  }
  if (props.questionType.startsWith('IMAGE_')) {
    common.splice(2, 0, {
      label: l('图像题协议', 'Image I/O'),
      icon: '🖼️',
      text: l(
        '请说明这道图像目标计数题在 OJ 中如何读取图片、加载模型、统计类别并按要求输出 JSON，只给步骤和关键片段。',
        'Explain how to read the image, load the model, count classes and output JSON in this OJ. Give only steps and key snippets.',
      ),
    })
  }
  if (auth.isAdmin) {
    common.unshift({
      icon: '✓',
      label: l('写入完整答案', 'Write full solution'),
      mode: 'edit',
      text: l(
        '请根据当前题面和公开样例，生成当前语言的完整可运行参考程序，并直接写入编辑器供管理员测试。',
        'Generate a complete runnable reference program in the current language and write it into the editor for admin testing.',
      ),
    })
  }
  return common
})

const hotThree = computed(() => quickActions.value.slice(0, 3))

const auth = useAuthStore()
const store = useStudyAiCopilotStore()
const aiModels = useAiModelsStore()
const { messages, draft, sending, streamingAssistantId, streamingPlainText } = storeToRefs(store)
const imageAttachment = ref<{ name: string; dataUrl: string } | null>(null)
const selectedVisionRoute = ref('')
const visionOptions = computed(() => {
  const options: Array<{ value: string; providerCode: string; modelId: string; label: string }> = []
  if (
    aiModels.catalog.siteDefaultAvailable &&
    ['qwen3-vl-plus', 'qwen3-vl-flash'].includes(aiModels.catalog.siteDefaultModel)
  ) {
    options.push({
      value: `SITE_DEFAULT:${aiModels.catalog.siteDefaultModel}`,
      providerCode: 'SITE_DEFAULT',
      modelId: aiModels.catalog.siteDefaultModel,
      label: `站点默认 · ${aiModels.catalog.siteDefaultModel}`,
    })
  }
  for (const saved of aiModels.saved) {
    const provider = aiModels.catalog.providers.find((item) => item.code === saved.providerCode)
    for (const id of provider?.visionModels || []) {
      options.push({
        value: `${saved.providerCode}:${id}`,
        providerCode: saved.providerCode,
        modelId: id,
        label: `${provider?.name || saved.providerCode} · ${id}`,
      })
    }
  }
  return options
})

watch(
  [visionOptions, () => aiModels.selectedProvider, () => aiModels.selectedModel],
  () => {
    if (visionOptions.value.some((item) => item.value === selectedVisionRoute.value)) return
    const current = visionOptions.value.find(
      (item) =>
        item.providerCode === aiModels.selectedProvider && item.modelId === aiModels.selectedModel,
    )
    selectedVisionRoute.value = current?.value || visionOptions.value[0]?.value || ''
  },
  { immediate: true },
)

const { nowMs: aiBanNowMs } = useBanCountdownTick()
const aiAssistBanned = computed(() => auth.isAiAssistBanned)
const aiBanCountdown = computed(() =>
  aiAssistBanned.value
    ? formatBanCountdown(parseBanUntilMs(auth.aiBanUntil ?? auth.banUntil), aiBanNowMs.value) ||
      '计算中…'
    : '',
)
const inputDisabled = computed(() => sending.value || editBusy.value || aiAssistBanned.value)

const activeTab = ref<AiCopilotTabId>('chat')
provide(AI_COPILOT_ACTIVE_TAB_KEY, activeTab)

const listRef = ref<HTMLElement | null>(null)

const panelWidth = ref(500)
const panelHeight = ref(720)
const panelLeft = ref(0)
const panelTop = ref(0)
const maximized = ref(false)
let restoreBounds: { width: number; height: number; left: number; top: number } | null = null
const panelRef = ref<HTMLElement | null>(null)
const PANEL_POSITION_KEY = 'myoj.study-ai.panel-position.v1'
const PANEL_SIZE_KEY = 'myoj.study-ai.panel-size.v1'
let pointerCleanup: (() => void) | null = null

function clampWidth(w: number) {
  const max = typeof window !== 'undefined' ? window.innerWidth - 16 : 560
  return Math.min(Math.max(320, w), max)
}

function clampHeight(h: number) {
  const max = typeof window !== 'undefined' ? window.innerHeight - 16 : 720
  return Math.min(Math.max(480, h), max)
}

function initPanelSize() {
  if (typeof window === 'undefined') return
  let saved: { width?: number; height?: number } | null = null
  try {
    saved = JSON.parse(localStorage.getItem(PANEL_SIZE_KEY) || 'null')
  } catch {
    /* 使用默认大小 */
  }
  panelWidth.value = clampWidth(Number.isFinite(saved?.width) ? saved!.width! : 500)
  panelHeight.value = clampHeight(Number.isFinite(saved?.height) ? saved!.height! : 720)
}

function savePanelBounds() {
  try {
    localStorage.setItem(
      PANEL_POSITION_KEY,
      JSON.stringify({ x: panelLeft.value, y: panelTop.value }),
    )
    localStorage.setItem(
      PANEL_SIZE_KEY,
      JSON.stringify({ width: panelWidth.value, height: panelHeight.value }),
    )
  } catch {
    /* 浏览器禁用本地存储时仍可正常缩放 */
  }
}

function clampPanelPosition(x: number, y: number) {
  return {
    x: Math.max(8, Math.min(x, window.innerWidth - panelWidth.value - 8)),
    y: Math.max(8, Math.min(y, window.innerHeight - panelHeight.value - 8)),
  }
}

function initPanelPosition() {
  if (typeof window === 'undefined') return
  let saved: { x?: number; y?: number } | null = null
  try {
    saved = JSON.parse(localStorage.getItem(PANEL_POSITION_KEY) || 'null')
  } catch {
    /* 使用居中位置 */
  }
  const x = Number.isFinite(saved?.x) ? saved!.x! : (window.innerWidth - panelWidth.value) / 2
  const y = Number.isFinite(saved?.y) ? saved!.y! : 24
  const position = clampPanelPosition(x, y)
  panelLeft.value = position.x
  panelTop.value = position.y
}

function onHeaderPointerDown(e: PointerEvent) {
  if (
    e.button !== 0 ||
    maximized.value ||
    window.innerWidth < 640 ||
    (e.target as HTMLElement).closest('button, a, input, select')
  )
    return
  e.preventDefault()
  pointerCleanup?.()
  const startX = e.clientX
  const startY = e.clientY
  const startLeft = panelLeft.value
  const startTop = panelTop.value
  const move = (ev: PointerEvent) => {
    const next = clampPanelPosition(startLeft + ev.clientX - startX, startTop + ev.clientY - startY)
    panelLeft.value = next.x
    panelTop.value = next.y
  }
  const stop = () => {
    pointerCleanup?.()
    savePanelBounds()
  }
  window.addEventListener('pointermove', move)
  window.addEventListener('pointerup', stop, { once: true })
  window.addEventListener('pointercancel', stop, { once: true })
  pointerCleanup = () => {
    window.removeEventListener('pointermove', move)
    window.removeEventListener('pointerup', stop)
    window.removeEventListener('pointercancel', stop)
    pointerCleanup = null
  }
}

function onResizeHandlePointerDown(e: PointerEvent, direction: 'horizontal' | 'both') {
  if (window.innerWidth < 640 || maximized.value || e.button !== 0) return
  e.preventDefault()
  e.stopPropagation()
  pointerCleanup?.()
  const startX = e.clientX
  const startY = e.clientY
  const startW = panelWidth.value
  const startH = panelHeight.value

  const onMove = (ev: PointerEvent) => {
    panelWidth.value = clampWidth(startW + ev.clientX - startX)
    if (direction === 'both') panelHeight.value = clampHeight(startH + ev.clientY - startY)
    const next = clampPanelPosition(panelLeft.value, panelTop.value)
    panelLeft.value = next.x
    panelTop.value = next.y
  }
  const onUp = () => {
    pointerCleanup?.()
    savePanelBounds()
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp, { once: true })
  window.addEventListener('pointercancel', onUp, { once: true })
  pointerCleanup = () => {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    window.removeEventListener('pointercancel', onUp)
    pointerCleanup = null
  }
}

function toggleMaximize() {
  pointerCleanup?.()
  if (maximized.value) {
    maximized.value = false
    if (restoreBounds) {
      panelWidth.value = clampWidth(restoreBounds.width)
      panelHeight.value = clampHeight(restoreBounds.height)
      const next = clampPanelPosition(restoreBounds.left, restoreBounds.top)
      panelLeft.value = next.x
      panelTop.value = next.y
    }
    savePanelBounds()
    return
  }
  restoreBounds = {
    width: panelWidth.value,
    height: panelHeight.value,
    left: panelLeft.value,
    top: panelTop.value,
  }
  maximized.value = true
  panelWidth.value = window.innerWidth - 16
  panelHeight.value = window.innerHeight - 16
  panelLeft.value = 8
  panelTop.value = 8
}

function onHeaderDoubleClick(e: MouseEvent) {
  if ((e.target as HTMLElement).closest('button, a, input, select') || window.innerWidth < 640)
    return
  toggleMaximize()
}

const stream = useStudyAiCopilotStream(
  (userText: string, history: BailianTurn[], imageDataUrl, imageRoute) => ({
    userMessage: userText,
    questionId: props.questionId || undefined,
    questionTitle: props.questionTitle || undefined,
    questionContent: (props.questionContent || '').slice(0, 12000),
    language: props.language || undefined,
    questionType: props.questionType || undefined,
    judgeContext: (props.judgeContext || '').slice(0, 3000) || undefined,
    sampleCases: (props.sampleCases || '').slice(0, 6000) || undefined,
    runResult: (props.runResult || '').slice(0, 10000) || undefined,
    code: props.getCode?.() ?? '',
    providerCode: imageRoute?.providerCode || aiModels.selectedProvider,
    modelId: imageRoute?.modelId || aiModels.selectedModel || undefined,
    imageDataUrl,
    history,
  }),
)

const runDiagnosis = computed(() => {
  if (!props.runResult.trim()) return null
  try {
    const result = JSON.parse(props.runResult) as {
      terminalError?: boolean
      allSamplePassed?: boolean
      suspectedShortcut?: boolean
      sandboxMessage?: string
      judgeInfo?: { message?: string }
      cases?: Array<{
        index?: number
        expectedOutput?: string
        actualOutput?: string
        passed?: boolean
      }>
    }
    if (result.terminalError) {
      const verdict =
        result.judgeInfo?.message || result.sandboxMessage || l('运行失败', 'Run failed')
      return {
        title: verdict,
        evidence: l('依据：本次沙箱运行状态。', 'Based on the execution status of this run.'),
        next: l(
          '先查看编译或运行错误，再让助手结合当前代码定位原因。',
          'Check the compiler or runtime error, then ask the assistant to inspect your code.',
        ),
      }
    }
    const failed = result.cases?.find((item) => item.passed === false)
    if (failed) {
      return {
        title: l(
          `公开样例 ${failed.index || 1} 未通过`,
          `Public sample ${failed.index || 1} failed`,
        ),
        evidence: l(
          `依据：样例预期 ${String(failed.expectedOutput ?? '').slice(0, 100)}；当前输出 ${String(failed.actualOutput ?? '').slice(0, 100)}。`,
          `Expected: ${String(failed.expectedOutput ?? '').slice(0, 100)}; actual: ${String(failed.actualOutput ?? '').slice(0, 100)}.`,
        ),
        next: l(
          '先核对输入读取和输出格式，再检查计算逻辑。仅凭输出差异不能确定具体代码错误。',
          'Check input parsing and output format, then the algorithm. Output differences alone do not identify the exact bug.',
        ),
      }
    }
    if (result.allSamplePassed) {
      return {
        title: l('公开样例已通过', 'Public samples passed'),
        evidence: l(
          '依据：本次公开样例的运行与输出比对。',
          'Based on this run and output comparison.',
        ),
        next: result.suspectedShortcut
          ? l(
              '代码可能只输出固定值；请确认它会读取输入，并提交以验证隐藏用例。',
              'Your code may print a fixed value. Confirm that it reads input, then submit for hidden tests.',
            )
          : l(
              '继续提交代码验证隐藏用例；样例通过不代表整题通过。',
              'Submit to check hidden tests. Passing samples does not guarantee full acceptance.',
            ),
      }
    }
  } catch {
    /* 旧版非 JSON 运行摘要继续交给聊天上下文处理。 */
  }
  return null
})

function askRunDiagnosis() {
  if (inputDisabled.value) return
  activeTab.value = 'chat'
  void stream.sendUserMessage(
    l(
      '请根据刚才的运行诊断、题面、公开样例和当前代码，说明最可能的问题、每项判断的依据，以及我下一步该检查什么。信息不足时明确说不确定，不要给完整可提交答案。',
      'Based on the run diagnosis, problem, public samples and my code, explain likely issues, evidence and next checks. Say when evidence is insufficient; do not provide a complete submission.',
    ),
    listRef.value,
  )
}

function onModelChange() {
  stream.stopGeneration()
  store.clearChat()
  Message.info(l('已切换模型，开始新的对话', 'Model switched. Starting a new conversation.'))
}

function onSend() {
  if (inputDisabled.value) return
  const attachment = imageAttachment.value
  const route = attachment
    ? visionOptions.value.find((item) => item.value === selectedVisionRoute.value)
    : undefined
  if (attachment && !route)
    return void Message.warning(
      l(
        '请先在账号设置中配置支持看图的文字模型',
        'Configure a vision-capable model in account settings first.',
      ),
    )
  const text =
    draft.value.trim() ||
    (attachment
      ? l(
          '请结合当前题目和代码分析这张图片，指出我下一步该怎么做。',
          'Analyze this image together with the current problem and code. Tell me what to do next.',
        )
      : '')
  if (!text) return
  if (!attachment && shouldAutoEditCode(text, auth.isAdmin)) {
    draft.value = ''
    void startCodeEdit(text)
    return
  }
  void stream.sendUserMessage(
    text,
    listRef.value,
    attachment?.dataUrl,
    route ? { providerCode: route.providerCode, modelId: route.modelId } : undefined,
  )
  imageAttachment.value = null
}

function onQuickAction(action: QuickChip) {
  if (inputDisabled.value) return
  activeTab.value = 'chat'
  if (action.mode === 'edit' || shouldAutoEditCode(action.text, auth.isAdmin)) {
    void startCodeEdit(action.text)
  } else {
    void stream.sendUserMessage(action.text, listRef.value)
  }
}

const hasMessages = computed(() => messages.value.length > 0)

const welcomeLine = ref('')
const welcomeFull = computed(() =>
  auth.isAdmin
    ? l(
        '可以直接索取完整题解与参考代码，用来核对题目。',
        'Ask for full solutions and reference code to test the problem.',
      )
    : l(
        '我会按提示层级引导你，也能分析最近一次运行结果。',
        'I can offer step-by-step hints and review your latest run.',
      ),
)
let welcomeTimer: ReturnType<typeof setInterval> | null = null

function clearWelcomeTimer() {
  if (welcomeTimer != null) {
    clearInterval(welcomeTimer)
    welcomeTimer = null
  }
}

function startWelcomeTypewriter() {
  clearWelcomeTimer()
  welcomeLine.value = ''
  let i = 0
  welcomeTimer = setInterval(() => {
    i += 1
    welcomeLine.value = welcomeFull.value.slice(0, i)
    if (i >= welcomeFull.value.length) clearWelcomeTimer()
  }, 36)
}

watch(
  () => [props.questionId, auth.userId, aiModels.selectedProvider, aiModels.selectedModel] as const,
  ([id, userId, providerCode, modelId]) => {
    if (id) store.bindSession(`${userId || 'guest'}:${id}:${providerCode}:${modelId}`)
  },
  { immediate: true },
)

watch(open, (v) => {
  if (v) {
    maximized.value = false
    initPanelSize()
    void nextTick(initPanelPosition)
    void stream.scrollToEnd(listRef.value)
    if (!hasMessages.value) startWelcomeTypewriter()
  } else {
    clearWelcomeTimer()
    pointerCleanup?.()
  }
})

watch(hasMessages, (h) => {
  if (h) clearWelcomeTimer()
})

watch(welcomeFull, () => {
  if (open.value && !hasMessages.value) startWelcomeTypewriter()
})

watchEffect((onCleanup) => {
  if (!open.value) return
  const fn = (e: KeyboardEvent) => {
    if (e.key !== 'Escape') return
    if (sending.value) {
      e.preventDefault()
      stream.stopGeneration()
    } else {
      open.value = false
    }
  }
  window.addEventListener('keydown', fn)
  onCleanup(() => window.removeEventListener('keydown', fn))
})

function clearChat() {
  store.clearChat()
  imageAttachment.value = null
  if (open.value && !hasMessages.value) startWelcomeTypewriter()
}

function close() {
  open.value = false
}

function onAutoEditFromAnswer(answer: string) {
  if (inputDisabled.value) return
  const instruction = auth.isAdmin
    ? '请依据这条助手回复和当前题目，生成当前语言的完整可运行程序，并自动写入编辑器。'
    : '请依据这条助手回复和当前代码，只修复局部错误并自动写入编辑器。'
  void startCodeEdit(instruction, answer)
}

onBeforeUnmount(() => {
  editController?.abort()
  clearWelcomeTimer()
  pointerCleanup?.()
})

watchEffect((onCleanup) => {
  if (!open.value) return
  const onResize = () => {
    if (maximized.value) {
      panelWidth.value = window.innerWidth - 16
      panelHeight.value = window.innerHeight - 16
      return
    }
    panelWidth.value = clampWidth(panelWidth.value)
    panelHeight.value = clampHeight(panelHeight.value)
    const next = clampPanelPosition(panelLeft.value, panelTop.value)
    panelLeft.value = next.x
    panelTop.value = next.y
  }
  window.addEventListener('resize', onResize)
  onCleanup(() => window.removeEventListener('resize', onResize))
})
</script>

<template>
  <Teleport to="body">
    <Transition name="study-copilot-ctx">
      <div v-if="open" class="study-copilot-overlay" role="presentation">
        <aside
          ref="panelRef"
          class="study-copilot tw-relative tw-flex tw-shrink-0 tw-flex-col tw-overflow-hidden tw-rounded-2xl tw-border tw-border-black/[0.08] tw-bg-[var(--app-surface-strong,rgba(255,248,238,0.96))] dark:tw-border-white/10"
          role="dialog"
          aria-modal="false"
          aria-labelledby="study-copilot-title"
          :style="{
            width: `${panelWidth}px`,
            height: `${panelHeight}px`,
            left: `${panelLeft}px`,
            top: `${panelTop}px`,
          }"
          @click.stop
        >
          <button
            type="button"
            class="resize-handle resize-handle--right"
            :aria-label="l('拖动调整宽度', 'Drag to resize width')"
            :disabled="maximized"
            @pointerdown="onResizeHandlePointerDown($event, 'horizontal')"
          />
          <button
            type="button"
            class="resize-handle resize-handle--corner"
            :aria-label="l('拖动调整窗口宽度和高度', 'Drag to resize window')"
            :disabled="maximized"
            @pointerdown="onResizeHandlePointerDown($event, 'both')"
          >
            ↘
          </button>

          <header
            class="assistant-header tw-relative tw-z-10 tw-flex tw-items-start tw-justify-between tw-gap-3 tw-border-b tw-border-black/[0.06] tw-px-4 tw-pb-3 tw-pt-3 dark:tw-border-white/10"
            :title="
              maximized
                ? l('双击还原窗口', 'Double-click to restore')
                : l('按住拖动，双击放大窗口', 'Drag to move; double-click to maximize')
            "
            @pointerdown="onHeaderPointerDown"
            @dblclick="onHeaderDoubleClick"
          >
            <div class="tw-flex tw-min-w-0 tw-items-center tw-gap-3">
              <AiProviderAvatar :provider-code="aiModels.selectedProvider" :size="44" />
              <div class="tw-min-w-0">
                <h2
                  id="study-copilot-title"
                  class="tw-m-0 tw-truncate tw-text-base tw-font-extrabold tw-tracking-tight tw-text-zinc-900 dark:tw-text-zinc-50"
                >
                  {{ l('智能做题助手', 'Coding assistant') }}
                </h2>
                <small class="tw-text-[11px] tw-text-zinc-500 dark:tw-text-zinc-400"
                  >{{
                    auth.isAdmin
                      ? l('管理员测试 · 可索取完整答案', 'Admin test · full answers available')
                      : l('学习模式 · 分层提示', 'Learning mode · guided hints')
                  }}<span class="drag-tip">
                    ·
                    {{
                      maximized
                        ? l('双击标题栏还原', 'Double-click to restore')
                        : l('拖动标题栏移动', 'Drag header to move')
                    }}</span
                  ></small
                >
              </div>
            </div>
            <div class="tw-flex tw-shrink-0 tw-items-center tw-gap-2">
              <button
                type="button"
                class="window-control"
                :aria-label="
                  maximized ? l('还原窗口', 'Restore window') : l('放大窗口', 'Maximize window')
                "
                :title="
                  maximized ? l('还原窗口', 'Restore window') : l('放大窗口', 'Maximize window')
                "
                @click="toggleMaximize"
              >
                {{ maximized ? '❐' : '⛶' }}
              </button>
              <button
                type="button"
                class="tw-rounded-lg tw-border tw-border-black/[0.08] tw-bg-white/60 tw-px-2.5 tw-py-1.5 tw-text-xs tw-font-semibold tw-text-zinc-600 tw-backdrop-blur disabled:tw-opacity-40 dark:tw-border-white/12 dark:tw-bg-zinc-900/50 dark:tw-text-zinc-300"
                :disabled="!hasMessages"
                @click="clearChat"
              >
                {{ l('清空', 'Clear') }}
              </button>
              <button
                type="button"
                class="tw-grid tw-h-9 tw-w-9 tw-place-items-center tw-rounded-lg tw-border tw-border-black/[0.08] tw-bg-white/70 tw-text-zinc-500 tw-backdrop-blur hover:tw-border-rose-300 hover:tw-text-rose-600 dark:tw-border-white/12 dark:tw-bg-zinc-900/50 dark:tw-text-zinc-300"
                :aria-label="l('关闭', 'Close')"
                @click="close"
              >
                ✕
              </button>
            </div>
          </header>

          <div class="model-row tw-relative tw-z-20 tw-mx-4 tw-mt-2">
            <AiModelPicker compact :disabled="inputDisabled" @change="onModelChange" />
          </div>

          <div class="context-strip tw-relative tw-z-10 tw-mx-4 tw-my-2">
            <span class="context-strip__dot" />
            <div>
              <strong>{{ l('OJ 上下文已同步', 'OJ context synced') }}</strong
              ><small
                >{{ l('题面 · 公开样例 · 当前代码', 'Problem · Public samples · Current code')
                }}<span v-if="props.runResult"> · {{ l('运行结果', 'Run result') }}</span></small
              >
            </div>
            <span class="context-strip__type">{{
              props.questionType.startsWith('IMAGE_') ? l('图像题', 'Image') : l('编程题', 'Code')
            }}</span>
          </div>
          <div
            v-if="runDiagnosis"
            class="tw-relative tw-z-10 tw-mx-4 tw-mb-3 tw-rounded-xl tw-border tw-border-teal-500/25 tw-bg-teal-500/[0.07] tw-p-3 tw-text-xs tw-leading-relaxed tw-text-zinc-700 dark:tw-text-zinc-200"
          >
            <div class="tw-flex tw-items-center tw-justify-between tw-gap-2">
              <strong>{{ l('本次运行诊断', 'Run diagnosis') }} · {{ runDiagnosis.title }}</strong
              ><button
                type="button"
                class="tw-shrink-0 tw-font-semibold tw-text-teal-700 disabled:tw-opacity-40 dark:tw-text-teal-300"
                :disabled="inputDisabled"
                @click="askRunDiagnosis"
              >
                {{ l('让 AI 结合代码分析', 'Analyze with AI') }} →
              </button>
            </div>
            <p class="tw-m-0 tw-mt-2">{{ runDiagnosis.evidence }}</p>
            <p class="tw-m-0 tw-mt-1 tw-text-zinc-500 dark:tw-text-zinc-400">
              {{ l('下一步：', 'Next: ') }}{{ runDiagnosis.next }}
            </p>
          </div>
          <div class="tw-relative tw-z-10 tw-mx-4 tw-mb-2">
            <ModerationBanNotice
              v-if="aiAssistBanned"
              variant="ai"
              compact
              :title="l('学习助手已锁定', 'Assistant locked')"
              :description="
                l(
                  '检测到不当提问内容，本面板输入已暂停。',
                  'Input is temporarily disabled due to a moderation restriction.',
                )
              "
              :countdown="aiBanCountdown"
              :reason="(auth.aiBanReason ?? auth.banReason ?? '').trim()"
              :footer="
                l(
                  '到期后自动恢复；如需提前解除请联系管理员。',
                  'Access restores when the restriction expires. Contact an admin for help.',
                )
              "
            />
          </div>

          <div class="tw-relative tw-z-10 tw-px-4 tw-pb-1">
            <AiTabs />
          </div>

          <div class="code-edit-bar tw-relative tw-z-10 tw-mx-4 tw-mb-2">
            <button
              type="button"
              :disabled="inputDisabled"
              @click="startCodeEdit('请结合当前题目、代码和公开运行结果，修复当前代码中的错误。')"
            >
              {{ l('修复当前代码', 'Fix current code') }}
            </button>
            <button
              v-if="editBusy && editStage !== 'running'"
              type="button"
              class="code-edit-bar__stop"
              @click="stopCodeEdit"
            >
              {{ l('停止', 'Stop') }}
            </button>
            <button v-if="canUndoEdit && !editBusy" type="button" @click="undoCodeEdit">
              {{ l('撤销本次修改', 'Undo changes') }}
            </button>
            <small v-if="editStatus" role="status">{{ editStatus }}</small>
          </div>

          <div class="tw-relative tw-z-10 tw-min-h-0 tw-flex-1 tw-px-4">
            <div
              v-show="activeTab === 'chat'"
              class="tw-flex tw-h-full tw-min-h-0 tw-flex-col tw-gap-2"
            >
              <div
                ref="listRef"
                class="tw-min-h-[160px] tw-flex-1 tw-overflow-y-auto tw-px-1 tw-py-2"
                style="contain: layout"
              >
                <div
                  v-if="!hasMessages"
                  class="tw-flex tw-h-full tw-min-h-[240px] tw-flex-col tw-items-center tw-justify-center tw-gap-3 tw-px-2 tw-text-center"
                >
                  <div class="empty-orb">
                    <AiHeroIcon
                      name="chat-bubble-left-right"
                      class="tw-text-teal-600 dark:tw-text-teal-300"
                    />
                  </div>
                  <div>
                    <p
                      class="tw-m-0 tw-text-base tw-font-bold tw-text-zinc-800 dark:tw-text-zinc-100"
                    >
                      {{ l('从哪里开始？', 'Where to start?') }}
                    </p>
                    <p class="tw-m-0 tw-mt-1 tw-text-xs tw-text-zinc-500 dark:tw-text-zinc-400">
                      {{
                        auth.isAdmin
                          ? l(
                              '直接获取参考答案、检查代码，或附图分析',
                              'Request a reference answer, review code or analyze an image',
                            )
                          : l(
                              '问解题思路、检查代码，或附图分析',
                              'Ask for hints, review code or analyze an image',
                            )
                      }}
                    </p>
                  </div>
                  <p
                    class="tw-m-0 tw-max-w-[280px] tw-text-xs tw-leading-relaxed tw-text-zinc-500 dark:tw-text-zinc-400"
                  >
                    {{ welcomeLine }}
                  </p>
                  <div class="tw-flex tw-flex-wrap tw-justify-center tw-gap-2">
                    <button
                      v-for="(h, i) in hotThree"
                      :key="i"
                      type="button"
                      class="tw-rounded-full tw-border tw-border-black/10 tw-bg-transparent tw-px-3 tw-py-1.5 tw-text-[11px] tw-font-medium tw-text-zinc-700 tw-transition hover:tw-border-teal-500 hover:tw-text-teal-700 disabled:tw-opacity-40 dark:tw-border-white/15 dark:tw-text-zinc-200"
                      :disabled="inputDisabled"
                      @click="onQuickAction(h)"
                    >
                      <span class="tw-mr-1">{{ h.icon }}</span
                      >{{ h.label }}
                    </button>
                  </div>
                </div>
                <div v-else class="tw-flex tw-flex-col tw-gap-2.5">
                  <AiMessage
                    v-for="m in messages"
                    :key="m.id"
                    :message="m"
                    :is-streaming="sending && streamingAssistantId === m.id"
                    :streaming-plain-text="streamingPlainText"
                    @auto-edit="onAutoEditFromAnswer"
                  />
                </div>
              </div>

              <div class="tw-pb-3 tw-pt-1">
                <div v-if="imageAttachment" class="vision-route tw-mb-2">
                  <label for="vision-model">{{ l('图片分析使用', 'Image analysis model') }}</label>
                  <select
                    v-if="visionOptions.length"
                    id="vision-model"
                    v-model="selectedVisionRoute"
                    :disabled="sending"
                  >
                    <option v-for="item in visionOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </option>
                  </select>
                  <span v-else>{{
                    l(
                      '还没有可看图的模型，请先到账号设置配置。',
                      'No vision model configured. Set one up in account settings.',
                    )
                  }}</span>
                  <small>{{
                    l(
                      '图片由这里选中的模型分析，可能与顶部聊天模型不同。调用可能收费，附件不会保存。',
                      'The selected model analyzes the image and may differ from the chat model. Calls may incur a charge. Attachments are not saved.',
                    )
                  }}</small>
                </div>
                <AiInput
                  :disabled="inputDisabled"
                  :attachment="imageAttachment"
                  @attach="imageAttachment = $event"
                  @remove-attachment="imageAttachment = null"
                  @send="onSend"
                  @stop="stream.stopGeneration()"
                />
              </div>
            </div>

            <div
              v-show="activeTab === 'templates'"
              class="tw-flex tw-h-full tw-min-h-[240px] tw-flex-col tw-gap-2 tw-overflow-y-auto tw-p-2"
            >
              <p class="tw-m-0 tw-text-xs tw-font-semibold tw-text-zinc-600 dark:tw-text-zinc-300">
                {{ l('快捷模板（点击填入并发送）', 'Quick prompts (click to send)') }}
              </p>
              <div class="tw-flex tw-flex-col tw-gap-2">
                <button
                  v-for="(c, i) in quickActions"
                  :key="i"
                  type="button"
                  class="tw-rounded-xl tw-border tw-border-white/20 tw-bg-white/55 tw-px-3 tw-py-2 tw-text-left tw-text-xs tw-font-medium tw-text-zinc-800 tw-backdrop-blur-md tw-transition hover:tw-border-teal-400/45 hover:tw-bg-white/80 disabled:tw-opacity-40 dark:tw-border-white/10 dark:tw-bg-zinc-900/40 dark:tw-text-zinc-100"
                  :disabled="inputDisabled"
                  @click="onQuickAction(c)"
                >
                  <span class="tw-font-bold tw-text-teal-700 dark:tw-text-teal-300">{{
                    c.label
                  }}</span>
                  <span
                    class="tw-mt-1 tw-line-clamp-2 tw-block tw-text-[11px] tw-text-zinc-500 dark:tw-text-zinc-400"
                    >{{ c.text }}</span
                  >
                </button>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.code-edit-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  padding: 8px 10px;
  border: 1px solid rgba(13, 148, 136, 0.2);
  border-radius: 12px;
  background: rgba(13, 148, 136, 0.055);
}
.code-edit-bar button {
  padding: 5px 9px;
  border: 1px solid rgba(13, 148, 136, 0.3);
  border-radius: 8px;
  background: var(--app-surface-strong, #fff);
  color: #0f766e;
  font-size: 11px;
  font-weight: 700;
  cursor: pointer;
}
.code-edit-bar button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
.code-edit-bar .code-edit-bar__stop {
  border-color: rgba(220, 38, 38, 0.25);
  color: #b91c1c;
}
.code-edit-bar small {
  flex-basis: 100%;
  color: var(--app-text-subtle, #64748b);
  font-size: 11px;
  line-height: 1.5;
}
.study-copilot-overlay {
  position: fixed;
  inset: 0;
  z-index: 10050;
  background: transparent;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
}
:global(html[data-theme='dark']) .study-copilot-overlay {
  background: transparent;
}
.study-copilot {
  position: absolute;
  pointer-events: auto;
  max-width: calc(100vw - 16px);
  max-height: calc(100vh - 16px);
  min-width: min(320px, calc(100vw - 16px));
  min-height: min(480px, calc(100vh - 16px));
  font-family: var(--app-font-body, 'Segoe UI Variable', 'Microsoft YaHei UI', sans-serif);
  box-shadow:
    0 20px 70px rgba(0, 0, 0, 0.18),
    0 0 0 1px color-mix(in srgb, var(--app-border, rgba(92, 68, 46, 0.12)) 65%, transparent),
    var(--app-card-shadow, 0 12px 32px rgba(70, 41, 14, 0.08));
}
.assistant-header {
  background: var(--app-surface-strong, rgba(255, 248, 238, 0.96));
  cursor: grab;
  touch-action: none;
  user-select: none;
}
.assistant-header:active {
  cursor: grabbing;
}
.assistant-header button {
  cursor: pointer;
}
.window-control {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 9px;
  background: var(--app-surface-strong, #fff);
  color: var(--app-text-main, #172b34);
  font-size: 19px;
  cursor: pointer;
}
.window-control:hover {
  border-color: #0d9488;
  color: #0f766e;
}
.window-control:focus-visible,
.resize-handle:focus-visible {
  outline: 2px solid #0d9488;
  outline-offset: 2px;
}
.resize-handle {
  position: absolute;
  z-index: 30;
  border: 0;
  background: transparent;
  color: var(--app-text-subtle, #64748b);
  touch-action: none;
}
.resize-handle--right {
  top: 48px;
  right: 0;
  bottom: 22px;
  width: 8px;
  cursor: ew-resize;
}
.resize-handle--right:hover {
  background: rgba(20, 184, 166, 0.12);
}
.resize-handle--corner {
  right: 0;
  bottom: 0;
  display: grid;
  place-items: center;
  width: 24px;
  height: 24px;
  border-radius: 8px 0 12px 0;
  cursor: nwse-resize;
  font-size: 16px;
}
.resize-handle--corner:hover {
  background: rgba(20, 184, 166, 0.16);
  color: #0f766e;
}
.resize-handle:disabled {
  display: none;
}
.assistant-logo {
  position: relative;
}
.assistant-logo::after {
  content: '';
  position: absolute;
  inset: -4px;
  border: 1px solid rgba(20, 184, 166, 0.22);
  border-radius: 20px;
}
.model-row {
  border: 1px solid var(--app-border, rgba(15, 23, 42, 0.1));
  border-radius: 12px;
  background: var(--app-surface-strong, #fff);
}
.context-strip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 11px;
  border: 1px solid rgba(20, 184, 166, 0.16);
  border-radius: 13px;
  background: rgba(20, 184, 166, 0.055);
}
.context-strip__dot {
  width: 8px;
  height: 8px;
  flex: 0 0 auto;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.12);
}
.context-strip div {
  min-width: 0;
  display: grid;
  gap: 1px;
}
.context-strip strong {
  font-size: 11px;
  color: var(--app-text-main);
}
.context-strip small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10px;
  color: var(--app-text-subtle);
}
.context-strip__type {
  margin-left: auto;
  flex: 0 0 auto;
  padding: 4px 7px;
  border-radius: 999px;
  background: rgba(20, 184, 166, 0.12);
  color: #0f766e;
  font-size: 10px;
  font-weight: 800;
}
.vision-route {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  align-items: center;
  gap: 7px 10px;
  padding: 10px 11px;
  border: 1px solid rgba(20, 184, 166, 0.23);
  border-radius: 12px;
  background: rgba(20, 184, 166, 0.055);
  color: var(--app-text, #172b34);
  font-size: 12px;
}
.vision-route label {
  font-weight: 750;
}
.vision-route select {
  min-width: 0;
  padding: 6px 8px;
  border: 1px solid rgba(20, 184, 166, 0.25);
  border-radius: 8px;
  background: var(--app-surface-strong, #fff);
  color: var(--app-text, #172b34);
}
.vision-route span {
  line-height: 1.5;
}
.vision-route small {
  grid-column: 1 / -1;
  color: var(--app-text-subtle, #64748b);
}
.empty-orb {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 15px;
  background: rgba(20, 184, 166, 0.07);
  border: 1px solid rgba(20, 184, 166, 0.14);
}
.study-copilot-ctx-enter-active,
.study-copilot-ctx-leave-active {
  transition: opacity 0.22s ease;
}
.study-copilot-ctx-enter-from,
.study-copilot-ctx-leave-to {
  opacity: 0;
}
.study-copilot-ctx-enter-active .study-copilot,
.study-copilot-ctx-leave-active .study-copilot {
  transition: transform 0.26s cubic-bezier(0.22, 1, 0.36, 1);
}
.study-copilot-ctx-enter-from .study-copilot,
.study-copilot-ctx-leave-to .study-copilot {
  transform: translateY(12px);
}
@media (max-width: 639px) {
  .study-copilot {
    inset: 8px !important;
    width: calc(100vw - 16px) !important;
    height: calc(100dvh - 16px) !important;
    max-height: calc(100dvh - 16px);
    min-height: 0;
  }
  .assistant-header {
    cursor: default;
  }
  .drag-tip {
    display: none;
  }
  .resize-handle,
  .window-control {
    display: none;
  }
}
</style>
