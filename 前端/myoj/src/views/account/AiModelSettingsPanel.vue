<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import TextModelSettingsPanel from './TextModelSettingsPanel.vue'
import ImageModelSettingsPanel from './ImageModelSettingsPanel.vue'

const props = defineProps<{ isLight: boolean }>()
const route = useRoute()
const auth = useAuthStore()
const mode = ref<'text' | 'image'>(
  route.query.modelType === 'image' && auth.isAdmin ? 'image' : 'text',
)
</script>

<template>
  <div class="ai-settings">
    <div class="ai-settings__tabs" role="tablist" aria-label="模型类型">
      <button
        type="button"
        role="tab"
        :aria-selected="mode === 'text'"
        :class="{ active: mode === 'text' }"
        @click="mode = 'text'"
      >
        文字模型
      </button>
      <button
        v-if="auth.isAdmin"
        type="button"
        role="tab"
        :aria-selected="mode === 'image'"
        :class="{ active: mode === 'image' }"
        @click="mode = 'image'"
      >
        图片模型
      </button>
    </div>
    <ImageModelSettingsPanel v-if="mode === 'image' && auth.isAdmin" :is-light="props.isLight" />
    <TextModelSettingsPanel v-else :is-light="props.isLight" />
  </div>
</template>

<style scoped>
.ai-settings__tabs {
  display: inline-flex;
  gap: 4px;
  padding: 5px;
  margin-bottom: 19px;
  border: 1px solid rgba(100, 116, 139, 0.18);
  border-radius: 13px;
  background: rgba(100, 116, 139, 0.08);
}
.ai-settings__tabs button {
  border: 0;
  border-radius: 9px;
  padding: 9px 19px;
  background: transparent;
  color: var(--app-text-muted, #64748b);
  cursor: pointer;
  font-weight: 750;
}
.ai-settings__tabs button.active {
  background: var(--app-surface-strong, #fff);
  color: #0f9d8b;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.1);
}
.ai-settings__tabs button:focus-visible {
  outline: 2px solid #0f9d8b;
  outline-offset: 2px;
}
</style>
