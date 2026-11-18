<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useClipboard } from '@vueuse/core'
import { extractMarkdownCodeBlocks } from '@/utils/extractMarkdownCodeBlocks'
import { useAuthStore } from '@/stores/auth'
import AiProviderAvatar from './AiProviderAvatar.vue'
import { localize as l } from '@/i18n/locale'

const props = defineProps<{
  message: {
    id: string
    role: 'user' | 'assistant'
    content: string
    html?: string
    imagePreview?: string
    providerCode?: string
    modelId?: string
  }
  /** 当前流式中的气泡 */
  isStreaming: boolean
  /** 流式纯文本（仅 isStreaming 时） */
  streamingPlainText?: string
}>()

const emit = defineEmits<{
  'auto-edit': [answer: string]
}>()

const mdRoot = ref<HTMLElement | null>(null)
const auth = useAuthStore()
const avatarFailed = ref(false)
watch(
  () => auth.avatarUrl,
  () => {
    avatarFailed.value = false
  },
)
const { copy, isSupported } = useClipboard({ legacy: true })
const copiedIdx = ref<number | null>(null)

const variant = computed(() => {
  if (props.message.role === 'user') return 'user' as const
  const c = props.message.content
  if (/错误|失败|Error|HTTP\s\d|异常|请求失败/.test(c)) return 'error' as const
  if (/```/.test(c)) return 'code' as const
  if (/思路|提示|建议|注意/.test(c)) return 'hint' as const
  return 'analysis' as const
})

const fences = computed(() =>
  props.message.role === 'assistant' ? extractMarkdownCodeBlocks(props.message.content) : [],
)

let highlightIdle = 0
let highlightCancelled = false

async function highlightCodeBlocksWithPrism() {
  if (props.message.role !== 'assistant' || props.isStreaming || !props.message.html) return
  const root = mdRoot.value
  if (!root) return

  const run = async () => {
    if (highlightCancelled) return
    const PrismMod = await import('prismjs')
    await import('prismjs/themes/prism.css')
    const Prism = PrismMod.default
    await import('prismjs/components/prism-clike')
    await import('prismjs/components/prism-c')
    await import('prismjs/components/prism-cpp')
    await import('prismjs/components/prism-java')
    await import('prismjs/components/prism-python')
    await import('prismjs/components/prism-javascript')

    const pres = root.querySelectorAll<HTMLElement>('pre > code')
    for (const codeEl of pres) {
      if (highlightCancelled) return
      const pre = codeEl.parentElement
      if (!pre || pre.dataset.prismDone === '1') continue
      const code = codeEl.textContent ?? ''
      if (!code.trim()) continue
      try {
        Prism.highlightElement(codeEl)
        pre.dataset.prismDone = '1'
      } catch {
        pre.dataset.prismDone = '1'
      }
    }
  }

  if (typeof requestIdleCallback !== 'undefined') {
    highlightIdle = requestIdleCallback(() => void run(), { timeout: 1200 })
  } else {
    await nextTick()
    setTimeout(() => void run(), 0)
  }
}

watch(
  () => [props.message.html, props.message.id, props.isStreaming] as const,
  async () => {
    highlightCancelled = true
    if (typeof cancelIdleCallback !== 'undefined' && highlightIdle)
      cancelIdleCallback(highlightIdle)
    highlightCancelled = false
    await nextTick()
    void highlightCodeBlocksWithPrism()
  },
  { flush: 'post' },
)

onBeforeUnmount(() => {
  highlightCancelled = true
  if (typeof cancelIdleCallback !== 'undefined' && highlightIdle) cancelIdleCallback(highlightIdle)
})

async function onCopyBlock(code: string, idx: number) {
  if (!isSupported.value) return
  await copy(code)
  copiedIdx.value = idx
  window.setTimeout(() => {
    if (copiedIdx.value === idx) copiedIdx.value = null
  }, 1600)
}
</script>

<template>
  <article
    class="tw-group tw-relative tw-flex tw-gap-2.5 tw-rounded-2xl tw-px-2 tw-py-2.5"
    :class="
      message.role === 'user'
        ? 'tw-ml-auto tw-max-w-[88%] tw-bg-zinc-100/90 dark:tw-bg-zinc-800/85'
        : 'tw-w-full'
    "
  >
    <div class="tw-min-w-0 tw-flex-1">
      <div class="tw-mb-1 tw-flex tw-items-center tw-gap-2">
        <span
          v-if="message.role === 'user'"
          class="tw-inline-flex tw-h-7 tw-w-7 tw-shrink-0 tw-items-center tw-justify-center tw-overflow-hidden tw-rounded-full tw-bg-zinc-600 tw-text-[11px] tw-font-bold tw-text-white"
        >
          <img
            v-if="auth.avatarUrl && !avatarFailed"
            :src="auth.avatarUrl"
            alt=""
            class="tw-h-full tw-w-full tw-object-cover"
            @error="avatarFailed = true"
          />
          <template v-else>{{ l('我', 'Me') }}</template>
        </span>
        <AiProviderAvatar
          v-else
          :provider-code="message.providerCode"
          :size="28"
          :class="{ 'tw-animate-pulse': isStreaming }"
        />
        <span class="tw-text-[11px] tw-font-medium tw-text-zinc-500 dark:tw-text-zinc-400">
          {{
            message.role === 'user'
              ? l('你', 'You')
              : variant === 'error'
                ? l('提示', 'Notice')
                : message.modelId || l('助手', 'Assistant')
          }}
        </span>
      </div>

      <template v-if="message.role === 'assistant'">
        <div
          v-if="isStreaming && !message.content.trim()"
          class="tw-space-y-2 tw-py-1"
          aria-live="polite"
        >
          <div
            class="tw-h-2.5 tw-w-[92%] tw-max-w-md tw-rounded-md tw-bg-gradient-to-r tw-from-zinc-200 tw-via-zinc-100 tw-to-zinc-200 tw-bg-[length:200%_100%] tw-animate-ai-shimmer dark:tw-from-zinc-700 dark:tw-via-zinc-600 dark:tw-to-zinc-700"
          />
          <div
            class="tw-h-2.5 tw-w-[78%] tw-max-w-sm tw-rounded-md tw-bg-gradient-to-r tw-from-zinc-200 tw-via-zinc-100 tw-to-zinc-200 tw-bg-[length:200%_100%] tw-animate-ai-shimmer dark:tw-from-zinc-700 dark:tw-via-zinc-600 dark:tw-to-zinc-700"
          />
          <div
            class="tw-h-2.5 tw-w-[64%] tw-max-w-xs tw-rounded-md tw-bg-gradient-to-r tw-from-zinc-200 tw-via-zinc-100 tw-to-zinc-200 tw-bg-[length:200%_100%] tw-animate-ai-shimmer dark:tw-from-zinc-700 dark:tw-via-zinc-600 dark:tw-to-zinc-700"
          />
        </div>
        <div
          v-else-if="isStreaming"
          class="tw-whitespace-pre-wrap tw-break-words tw-text-sm tw-leading-relaxed tw-text-zinc-800 dark:tw-text-zinc-100"
        >
          {{ streamingPlainText
          }}<span class="tw-inline-block tw-w-2 tw-animate-ai-type-caret tw-align-text-bottom"
            >▍</span
          >
        </div>
        <div v-else>
          <div
            ref="mdRoot"
            class="ai-msg-md oj-statement tw-text-sm tw-leading-relaxed tw-text-zinc-800 dark:tw-text-zinc-100"
            v-html="message.html"
          />
          <div v-if="fences.length" class="tw-mt-2 tw-flex tw-flex-col tw-gap-2">
            <button
              type="button"
              class="tw-self-start tw-rounded-lg tw-border tw-border-teal-500/35 tw-bg-teal-500/10 tw-px-2.5 tw-py-1 tw-text-[11px] tw-font-semibold tw-text-teal-800 hover:tw-bg-teal-500/20 dark:tw-text-teal-200"
              @click="emit('auto-edit', message.content)"
            >
              {{
                auth.isAdmin
                  ? l('自动写入编辑器并运行样例', 'Write to editor and run samples')
                  : l('自动修复局部代码并运行样例', 'Fix local code and run samples')
              }}
            </button>
            <div
              v-for="(f, i) in fences"
              :key="i"
              class="tw-flex tw-flex-wrap tw-items-center tw-gap-2"
            >
              <button
                type="button"
                class="tw-rounded-lg tw-border tw-border-zinc-300 tw-bg-white/60 tw-px-2.5 tw-py-1 tw-text-[11px] tw-font-semibold tw-text-zinc-700 tw-backdrop-blur hover:tw-bg-white dark:tw-border-zinc-600 dark:tw-bg-zinc-800/60 dark:tw-text-zinc-200"
                @click="onCopyBlock(f.code, i)"
              >
                {{
                  copiedIdx === i
                    ? l('已复制', 'Copied')
                    : l(`复制代码块 ${i + 1}`, `Copy code block ${i + 1}`)
                }}
              </button>
            </div>
          </div>
        </div>
      </template>
      <div v-else>
        <img
          v-if="message.imagePreview"
          :src="message.imagePreview"
          :alt="l('本轮提问附带的图片', 'Image attached to this message')"
          class="tw-mb-2 tw-max-h-48 tw-max-w-full tw-rounded-lg tw-border tw-border-zinc-200 tw-object-contain dark:tw-border-zinc-700"
        />
        <p
          class="tw-m-0 tw-whitespace-pre-wrap tw-break-words tw-text-sm tw-leading-relaxed tw-text-zinc-800 dark:tw-text-zinc-100"
        >
          {{ message.content }}
        </p>
      </div>
    </div>
  </article>
</template>

<style scoped>
.ai-msg-md :deep(pre) {
  max-width: 100%;
  overflow-x: auto;
  border-radius: 10px;
  font-size: 12px;
}
.ai-msg-md :deep(code[class*='language-']) {
  font-family: ui-monospace, 'JetBrains Mono', Consolas, monospace;
}
</style>
