<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { RouterLink } from 'vue-router'
import { useAiModelsStore } from '@/stores/aiModels'
import AiProviderAvatar from './AiProviderAvatar.vue'

const props = defineProps<{ disabled?: boolean; compact?: boolean }>()
const emit = defineEmits<{ change: [] }>()
const models = useAiModelsStore()
const draftModel = ref('')
const menuOpen = ref(false)
const pickerRef = ref<HTMLElement | null>(null)
const options = computed(
  () => models.catalog.providers.find((p) => p.code === models.selectedProvider)?.models || [],
)
const readyProviders = computed(() =>
  models.saved.map((item) => ({
    ...item,
    name:
      models.catalog.providers.find((p) => p.code === item.providerCode)?.name || item.providerCode,
  })),
)
const hasUsableModel = computed(() =>
  models.selectedProvider === 'SITE_DEFAULT'
    ? models.catalog.siteDefaultAvailable
    : readyProviders.value.some((item) => item.providerCode === models.selectedProvider),
)
const currentLabel = computed(() => (hasUsableModel.value ? models.currentLabel : '选择模型'))

function onOutsidePointer(event: PointerEvent) {
  if (pickerRef.value && !pickerRef.value.contains(event.target as Node)) menuOpen.value = false
}
onMounted(() => {
  document.addEventListener('pointerdown', onOutsidePointer)
  void models.load().catch(() => Message.error('模型列表加载失败，请稍后重试'))
})
onBeforeUnmount(() => document.removeEventListener('pointerdown', onOutsidePointer))
watch(
  () => models.selectedModel,
  (value) => {
    draftModel.value = value
  },
  { immediate: true },
)

function changeProvider(event: Event) {
  const code = (event.target as HTMLSelectElement).value
  const preset = models.saved.find((item) => item.providerCode === code)
  models.select(code, preset?.modelId || '')
  emit('change')
}

function pickProvider(code: string, modelId: string) {
  if (props.disabled) return
  if (models.selectedProvider !== code || models.selectedModel !== modelId) {
    models.select(code, modelId)
    emit('change')
  }
  menuOpen.value = false
}

function changeModel() {
  const value = draftModel.value.trim()
  if (!/^[A-Za-z0-9_./:-]{1,100}$/.test(value)) {
    Message.warning('请输入有效的模型名称，最多 100 字符')
    draftModel.value = models.selectedModel
    return
  }
  if (value === models.selectedModel) return
  models.select(models.selectedProvider, value)
  emit('change')
  menuOpen.value = false
}
</script>

<template>
  <div v-if="compact" ref="pickerRef" class="compact-picker" @keydown.esc="menuOpen = false">
    <button
      type="button"
      class="compact-trigger"
      :disabled="disabled || models.loading"
      :aria-expanded="menuOpen"
      aria-haspopup="menu"
      @click="menuOpen = !menuOpen"
    >
      <AiProviderAvatar :provider-code="models.selectedProvider" :size="22" /><span
        class="compact-current"
        >{{ currentLabel }}</span
      ><span class="compact-chevron" aria-hidden="true">⌄</span>
    </button>
    <div v-if="menuOpen" class="compact-menu" role="menu">
      <div class="compact-menu-title">选择聊天模型 <small>仅显示本账号已配置的厂商</small></div>
      <button
        v-if="models.catalog.siteDefaultAvailable"
        type="button"
        class="compact-option"
        role="menuitemradio"
        :aria-checked="models.selectedProvider === 'SITE_DEFAULT'"
        @click="pickProvider('SITE_DEFAULT', '')"
      >
        <AiProviderAvatar provider-code="SITE_DEFAULT" :size="22" /><span>站点默认</span
        ><small>{{ models.catalog.siteDefaultModel }}</small>
      </button>
      <button
        v-for="item in readyProviders"
        :key="item.providerCode"
        type="button"
        class="compact-option"
        role="menuitemradio"
        :aria-checked="models.selectedProvider === item.providerCode"
        @click="pickProvider(item.providerCode, item.modelId)"
      >
        <AiProviderAvatar :provider-code="item.providerCode" :size="22" /><span>{{
          item.name
        }}</span
        ><small>{{ item.modelId }}</small>
      </button>
      <div
        v-if="models.selectedProvider !== 'SITE_DEFAULT' && hasUsableModel"
        class="compact-custom"
      >
        <label for="compact-model-id">其他模型 ID</label>
        <div>
          <input
            id="compact-model-id"
            v-model="draftModel"
            :list="`ai-models-${models.selectedProvider}`"
            placeholder="输入模型 ID"
            @keydown.enter.prevent="changeModel"
          /><button type="button" @click="changeModel">使用</button>
        </div>
        <datalist :id="`ai-models-${models.selectedProvider}`">
          <option v-for="name in options" :key="name" :value="name" />
        </datalist>
      </div>
      <RouterLink
        class="compact-settings"
        :to="{ name: 'account-settings', query: { tab: 'ai' } }"
        @click="menuOpen = false"
        >{{
          readyProviders.length || models.catalog.siteDefaultAvailable
            ? '管理模型与 API Key'
            : '先添加自己的模型'
        }}
        ↗</RouterLink
      >
    </div>
  </div>
  <div v-else class="ai-model-picker">
    <span class="ai-model-picker__label">当前模型</span>
    <select
      :value="models.selectedProvider"
      :disabled="disabled || models.loading"
      aria-label="选择 AI 平台"
      @change="changeProvider"
    >
      <option v-if="models.catalog.siteDefaultAvailable" value="SITE_DEFAULT">
        站点默认 · {{ models.catalog.siteDefaultModel || 'AI' }}
      </option>
      <option v-for="item in models.saved" :key="item.providerCode" :value="item.providerCode">
        {{
          models.catalog.providers.find((p) => p.code === item.providerCode)?.name ||
          item.providerCode
        }}
      </option>
    </select>
    <template v-if="models.selectedProvider !== 'SITE_DEFAULT'">
      <input
        v-model="draftModel"
        :list="`ai-models-${models.selectedProvider}`"
        :disabled="disabled"
        aria-label="模型名称"
        placeholder="模型名称"
        @change="changeModel"
        @keydown.enter="changeModel"
      />
      <datalist :id="`ai-models-${models.selectedProvider}`">
        <option v-for="name in options" :key="name" :value="name" />
      </datalist>
    </template>
    <RouterLink :to="{ name: 'account-settings', query: { tab: 'ai' } }">管理 API Key</RouterLink>
  </div>
</template>

<style scoped>
.ai-model-picker {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 9px 11px;
  border: 1px solid rgba(20, 184, 166, 0.2);
  border-radius: 12px;
  background: rgba(20, 184, 166, 0.07);
  color: var(--app-text, #172b34);
  font-size: 12px;
}
.ai-model-picker__label {
  font-weight: 700;
  color: var(--app-text-muted, #567);
}
.ai-model-picker select,
.ai-model-picker input {
  min-height: 29px;
  max-width: 165px;
  padding: 3px 8px;
  border: 1px solid rgba(20, 184, 166, 0.25);
  border-radius: 8px;
  background: var(--app-surface-strong, #fff);
  color: var(--app-text, #172b34);
  font: inherit;
}
.ai-model-picker a {
  margin-left: auto;
  color: #0f8c7a;
  font-weight: 700;
  text-decoration: none;
}
.ai-model-picker a:hover {
  text-decoration: underline;
}
:global(html[data-theme='dark']) .ai-model-picker a {
  color: #5eead4;
}
.compact-picker {
  min-width: 0;
}
.compact-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 7px;
  width: 100%;
  min-height: 38px;
  padding: 7px 12px;
  border: 0;
  border-radius: 11px;
  background: transparent;
  color: var(--app-text, #172b34);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 700;
}
.compact-trigger:hover,
.compact-trigger[aria-expanded='true'] {
  border-color: rgba(20, 184, 166, 0.24);
  background: rgba(20, 184, 166, 0.07);
}
.compact-trigger:disabled {
  cursor: wait;
  opacity: 0.5;
}
.compact-trigger:focus-visible,
.compact-option:focus-visible,
.compact-settings:focus-visible {
  outline: 2px solid #0d9488;
  outline-offset: 2px;
}
.compact-current {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  text-align: left;
}
.compact-chevron {
  flex: none;
  color: var(--app-text-subtle, #64748b);
  font-size: 16px;
  line-height: 1;
}
.compact-menu {
  width: 100%;
  max-height: min(45vh, 360px);
  overflow: auto;
  box-sizing: border-box;
  padding: 6px 8px 8px;
  border-top: 1px solid var(--app-border, rgba(15, 23, 42, 0.1));
  background: var(--app-surface-strong, #fff);
  border-radius: 0 0 12px 12px;
}
.compact-menu-title {
  padding: 7px 9px 8px;
  color: var(--app-text-subtle, #64748b);
  font-size: 11px;
  font-weight: 700;
}
.compact-menu-title small {
  display: block;
  margin-top: 2px;
  font-weight: 400;
}
.compact-option {
  display: grid;
  width: 100%;
  grid-template-columns: 22px minmax(0, 1fr);
  align-items: center;
  gap: 2px 8px;
  padding: 9px 10px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: var(--app-text, #172b34);
  cursor: pointer;
  text-align: left;
  font: inherit;
  font-size: 12px;
}
.compact-option:hover,
.compact-option[aria-checked='true'] {
  background: rgba(20, 184, 166, 0.09);
}
.compact-option > span:nth-child(2) {
  min-width: 0;
  font-weight: 750;
}
.compact-option small {
  grid-column: 2;
  overflow: hidden;
  width: 100%;
  color: var(--app-text-subtle, #64748b);
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
}
.compact-custom {
  display: grid;
  gap: 6px;
  margin: 6px 2px;
  padding: 10px 8px;
  border-top: 1px solid var(--app-border, rgba(15, 23, 42, 0.1));
  color: var(--app-text-subtle, #64748b);
  font-size: 11px;
}
.compact-custom div {
  display: flex;
  gap: 6px;
}
.compact-custom input {
  min-width: 0;
  flex: 1;
  padding: 7px 9px;
  border: 1px solid var(--app-border, rgba(15, 23, 42, 0.15));
  border-radius: 8px;
  background: var(--app-surface-strong, #fff);
  color: var(--app-text, #172b34);
  font: inherit;
}
.compact-custom button {
  padding: 6px 10px;
  border: 0;
  border-radius: 8px;
  background: #0f766e;
  color: #fff;
  cursor: pointer;
  font-weight: 700;
}
.compact-settings {
  display: block;
  margin-top: 4px;
  padding: 9px 10px;
  border-radius: 9px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}
.compact-settings:hover {
  background: rgba(20, 184, 166, 0.09);
}
:global(html[data-theme='dark']) .compact-settings {
  color: #5eead4;
}
</style>
