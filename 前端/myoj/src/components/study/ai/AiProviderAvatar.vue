<script setup lang="ts">
import { computed, ref, watch } from 'vue'

const props = withDefaults(defineProps<{ providerCode?: string; size?: number }>(), {
  providerCode: 'SITE_DEFAULT',
  size: 28,
})
const icons: Record<string, string> = {
  DASHSCOPE: '/ai-providers/dashscope.png',
  DEEPSEEK: '/ai-providers/deepseek.png',
  SILICONFLOW: '/ai-providers/siliconflow.png',
  VOLCENGINE: '/ai-providers/volcengine.png',
  BAIDU_QIANFAN: '/ai-providers/baidu.png',
  ZHIPU: '/ai-providers/zhipu.png',
  STEPFUN: '/ai-providers/stepfun.svg',
  MOONSHOT: '/ai-providers/kimi.png',
  TENCENT_TOKENHUB: '/ai-providers/tencent.png',
}
const shortNames: Record<string, string> = {
  SITE_DEFAULT: 'AI',
  DASHSCOPE: '百炼',
  DEEPSEEK: 'DS',
  SILICONFLOW: '硅基',
  VOLCENGINE: '方舟',
  BAIDU_QIANFAN: '千帆',
  ZHIPU: '智谱',
  STEPFUN: '阶跃',
  MOONSHOT: 'Kimi',
  TENCENT_TOKENHUB: '腾讯',
}
const failed = ref(false)
const icon = computed(() => icons[props.providerCode || ''] || '')
const label = computed(() => shortNames[props.providerCode || ''] || 'AI')
watch(
  () => props.providerCode,
  () => {
    failed.value = false
  },
)
</script>

<template>
  <span
    class="provider-avatar"
    :style="{ width: `${size}px`, height: `${size}px` }"
    :title="label"
    aria-hidden="true"
  >
    <img v-if="icon && !failed" :src="icon" alt="" @error="failed = true" />
    <span v-else>{{ label }}</span>
  </span>
</template>

<style scoped>
.provider-avatar {
  display: inline-flex;
  flex: none;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 50%;
  background: #fff;
  color: #0f766e;
  font-size: 10px;
  font-weight: 800;
  line-height: 1;
}
.provider-avatar img {
  width: 76%;
  height: 76%;
  object-fit: contain;
}
</style>
