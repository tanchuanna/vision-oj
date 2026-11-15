<script setup lang="ts">
import AiPanel from '@/components/study/ai/AiPanel.vue'
import type { CodeEdit } from '@/api/codeEdit'

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
</script>

<template>
  <AiPanel
    v-model:open="open"
    :question-id="props.questionId"
    :question-title="props.questionTitle"
    :question-content="props.questionContent"
    :language="props.language"
    :question-type="props.questionType"
    :judge-context="props.judgeContext"
    :sample-cases="props.sampleCases"
    :run-result="props.runResult"
    :get-code="props.getCode"
    :apply-code-edits="props.applyCodeEdits"
    :undo-code-edit="props.undoCodeEdit"
    :run-samples="props.runSamples"
  />
</template>
