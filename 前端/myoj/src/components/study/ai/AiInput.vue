<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useStudyAiCopilotStore } from '@/stores/studyAiCopilot'
import { localize as l } from '@/i18n/locale'

const props = defineProps<{
  disabled: boolean
  attachment: { name: string; dataUrl: string } | null
}>()

const emit = defineEmits<{
  send: []
  stop: []
  attach: [attachment: { name: string; dataUrl: string }]
  removeAttachment: []
}>()

const store = useStudyAiCopilotStore()
const { draft, sending } = storeToRefs(store)
const fileInput = ref<HTMLInputElement | null>(null)
const dragActive = ref(false)

function chooseImage() {
  fileInput.value?.click()
}

async function readImage(file: File) {
  if (props.disabled) return
  if (!['image/png', 'image/jpeg'].includes(file.type))
    return void Message.warning(l('请选择 PNG 或 JPEG 图片', 'Choose a PNG or JPEG image'))
  if (file.size > 4 * 1024 * 1024)
    return void Message.warning(l('图片不能超过 4 MB', 'Image must be under 4 MB'))
  try {
    const dataUrl = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result || ''))
      reader.onerror = () => reject(new Error('读取图片失败'))
      reader.readAsDataURL(file)
    })
    emit('attach', { name: file.name, dataUrl })
  } catch {
    Message.error(l('读取图片失败，请重新选择', 'Could not read image. Choose it again.'))
  }
}

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (file) void readImage(file)
}

function onPaste(event: ClipboardEvent) {
  const image = Array.from(event.clipboardData?.files || []).find((file) =>
    file.type.startsWith('image/'),
  )
  if (!image) return
  event.preventDefault()
  void readImage(image)
}

function onDrop(event: DragEvent) {
  dragActive.value = false
  const file = event.dataTransfer?.files?.[0]
  if (file) void readImage(file)
}

function onSend() {
  emit('send')
}
</script>

<template>
  <div
    class="composer"
    :class="{ 'is-dragging': dragActive }"
    @dragenter.prevent="dragActive = true"
    @dragover.prevent="dragActive = true"
    @dragleave.prevent="dragActive = false"
    @drop.prevent="onDrop"
  >
    <input
      ref="fileInput"
      type="file"
      accept="image/png,image/jpeg"
      class="file-input"
      :aria-label="l('选择要分析的图片', 'Choose image to analyze')"
      @change="onFileChange"
    />
    <div v-if="props.attachment" class="attachment-preview">
      <img :src="props.attachment.dataUrl" :alt="l('待分析图片预览', 'Image preview')" />
      <span class="attachment-name">{{ props.attachment.name }}</span>
      <button
        type="button"
        :aria-label="l('移除图片', 'Remove image')"
        :title="l('移除图片', 'Remove image')"
        :disabled="disabled"
        @click="emit('removeAttachment')"
      >
        ×
      </button>
    </div>
    <textarea
      v-model="draft"
      rows="2"
      class="composer-textarea"
      :placeholder="l('向助手提问，也可以粘贴图片…', 'Ask the assistant or paste an image…')"
      :disabled="disabled"
      @paste="onPaste"
      @keydown.enter.exact.prevent="onSend"
    />
    <div class="composer-footer">
      <button
        type="button"
        class="attach-button"
        :aria-label="l('添加图片', 'Add image')"
        :title="l('添加图片', 'Add image')"
        :disabled="disabled"
        @click="chooseImage"
      >
        <svg
          width="18"
          height="18"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M12 5v14M5 12h14" />
        </svg>
      </button>
      <span class="composer-hint">{{
        props.attachment
          ? l('已附加图片 · 发送时会用于分析', 'Image attached for analysis')
          : l('可上传、粘贴或拖入 PNG/JPEG 图片', 'Upload, paste or drop a PNG/JPEG image')
      }}</span>
      <button v-if="sending" type="button" class="stop-button" @click="emit('stop')">
        {{ l('停止', 'Stop') }}
      </button>
      <button
        type="button"
        class="send-button"
        :aria-label="l('发送消息', 'Send message')"
        :title="l('发送消息', 'Send message')"
        :disabled="disabled || (!draft.trim() && !props.attachment)"
        @click="onSend"
      >
        <svg
          width="17"
          height="17"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          stroke-linecap="round"
          stroke-linejoin="round"
          aria-hidden="true"
        >
          <path d="M12 19V5m-6 6 6-6 6 6" />
        </svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.file-input {
  display: none;
}
.composer {
  padding: 10px 11px 9px;
  border: 1px solid var(--app-border, rgba(15, 23, 42, 0.13));
  border-radius: 19px;
  background: var(--app-surface-strong, #fff);
  box-shadow: 0 3px 16px rgba(17, 24, 39, 0.06);
  transition:
    border-color 0.18s,
    box-shadow 0.18s;
}
.composer:focus-within,
.composer.is-dragging {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.1);
}
.composer-textarea {
  display: block;
  box-sizing: border-box;
  width: 100%;
  min-height: 58px;
  max-height: 160px;
  resize: vertical;
  padding: 4px 4px 8px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--app-text, #172b34);
  font: inherit;
  font-size: 13px;
  line-height: 1.55;
}
.composer-textarea::placeholder {
  color: var(--app-text-subtle, #94a3b8);
}
.composer-textarea:disabled {
  opacity: 0.6;
}
.attachment-preview {
  display: flex;
  align-items: center;
  gap: 9px;
  width: max-content;
  max-width: 100%;
  margin-bottom: 9px;
  padding: 5px 6px;
  border: 1px solid var(--app-border, rgba(15, 23, 42, 0.12));
  border-radius: 11px;
  background: rgba(148, 163, 184, 0.08);
  color: var(--app-text, #172b34);
  font-size: 11px;
}
.attachment-preview img {
  width: 48px;
  height: 48px;
  border-radius: 7px;
  object-fit: cover;
}
.attachment-name {
  min-width: 0;
  max-width: 170px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.attachment-preview button {
  align-self: flex-start;
  width: 22px;
  height: 22px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--app-text-subtle, #64748b);
  cursor: pointer;
  font-size: 17px;
  line-height: 1;
}
.attachment-preview button:hover {
  background: rgba(15, 23, 42, 0.09);
}
.composer-footer {
  display: flex;
  align-items: center;
  gap: 7px;
}
.composer-hint {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  color: var(--app-text-subtle, #64748b);
  font-size: 10px;
}
.attach-button,
.send-button {
  display: grid;
  flex: none;
  width: 31px;
  height: 31px;
  place-items: center;
  border-radius: 9px;
  cursor: pointer;
}
.attach-button {
  border: 1px solid var(--app-border, rgba(15, 23, 42, 0.15));
  background: transparent;
  color: var(--app-text, #172b34);
}
.attach-button:hover {
  background: rgba(20, 184, 166, 0.08);
}
.send-button {
  border: 0;
  background: #0f766e;
  color: white;
}
.send-button:hover {
  background: #115e59;
}
.send-button:disabled {
  background: rgba(100, 116, 139, 0.3);
  cursor: not-allowed;
}
.attach-button:disabled,
.attachment-preview button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.stop-button {
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
  cursor: pointer;
  font-size: 11px;
  font-weight: 700;
}
</style>
