import { computed, ref, watch, type Ref } from 'vue'

export type SolveLayoutMode = 'split' | 'problem' | 'editor'

type StoredLayout = {
  leftPercent?: number
  mode?: SolveLayoutMode
  consoleHeight?: number
  consoleCollapsed?: boolean
}

const clamp = (value: number, min: number, max: number) => Math.min(max, Math.max(min, value))

export function useSolveWorkbenchLayout(userScope: Ref<string>) {
  const leftPercent = ref(50)
  const mode = ref<SolveLayoutMode>('split')
  const consoleHeight = ref(240)
  const consoleCollapsed = ref(false)
  const storageKey = computed(() => `myoj.solve.layout:v3:${userScope.value}`)

  function load() {
    if (typeof window === 'undefined') return
    leftPercent.value = 50
    mode.value = 'split'
    consoleHeight.value = 240
    consoleCollapsed.value = false
    try {
      const raw = window.localStorage.getItem(storageKey.value)
      if (!raw) return
      const saved = JSON.parse(raw) as StoredLayout
      if (Number.isFinite(saved.leftPercent)) leftPercent.value = clamp(Number(saved.leftPercent), 25, 75)
      if (saved.mode === 'split' || saved.mode === 'problem' || saved.mode === 'editor') mode.value = saved.mode
      if (Number.isFinite(saved.consoleHeight)) consoleHeight.value = clamp(Number(saved.consoleHeight), 160, window.innerHeight * 0.55)
      consoleCollapsed.value = saved.consoleCollapsed === true
    } catch {
      // 损坏的本地布局不影响做题，继续使用默认值。
    }
  }

  function save() {
    if (typeof window === 'undefined') return
    window.localStorage.setItem(storageKey.value, JSON.stringify({
      leftPercent: leftPercent.value,
      mode: mode.value,
      consoleHeight: consoleHeight.value,
      consoleCollapsed: consoleCollapsed.value,
    } satisfies StoredLayout))
  }

  function resizeBy(delta: number) {
    leftPercent.value = clamp(leftPercent.value + delta, 25, 75)
  }

  function setLeftPercent(value: number) {
    leftPercent.value = clamp(value, 25, 75)
  }

  function setConsoleHeight(value: number) {
    const max = typeof window === 'undefined' ? 540 : window.innerHeight * 0.55
    consoleHeight.value = clamp(value, 160, max)
  }

  watch([leftPercent, mode, consoleHeight, consoleCollapsed], save)
  watch(storageKey, load, { immediate: true })

  return {
    leftPercent,
    mode,
    consoleHeight,
    consoleCollapsed,
    resizeBy,
    setLeftPercent,
    setConsoleHeight,
    load,
  }
}
