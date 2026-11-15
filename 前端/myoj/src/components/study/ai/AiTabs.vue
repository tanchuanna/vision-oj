<script setup lang="ts">
import { inject } from 'vue'
import { AI_COPILOT_ACTIVE_TAB_KEY, type AiCopilotTabId } from './keys'

const activeTab = inject(AI_COPILOT_ACTIVE_TAB_KEY)!
if (!activeTab) throw new Error('AiTabs: missing AI_COPILOT_ACTIVE_TAB_KEY')

const tabs: Array<{ id: AiCopilotTabId; label: string }> = [
  { id: 'chat', label: '对话' },
  { id: 'templates', label: '快捷提问' },
]
</script>

<template>
  <nav class="chat-tabs" aria-label="助手视图">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      :class="{ active: activeTab === tab.id }"
      :aria-current="activeTab === tab.id ? 'page' : undefined"
      @click="activeTab = tab.id"
    >
      {{ tab.label }}
    </button>
  </nav>
</template>

<style scoped>
.chat-tabs {
  display: flex;
  gap: 16px;
  border-bottom: 1px solid var(--app-border, rgba(15, 23, 42, 0.1));
}
.chat-tabs button {
  position: relative;
  padding: 8px 2px 10px;
  border: 0;
  background: transparent;
  color: var(--app-text-subtle, #64748b);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  font-weight: 600;
}
.chat-tabs button.active {
  color: var(--app-text, #172b34);
}
.chat-tabs button.active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 2px;
  border-radius: 2px;
  background: #0f766e;
  content: '';
}
.chat-tabs button:focus-visible {
  outline: 2px solid #0d9488;
  outline-offset: 2px;
}
</style>
