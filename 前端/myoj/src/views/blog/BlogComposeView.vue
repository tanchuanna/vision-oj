<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { Message, Modal } from '@arco-design/web-vue'
import axios from 'axios'
import type { BlogPostDetailVO } from '@generated'
import { OpenAPI, Service } from '@generated'
import { mapApiError } from '@/api/mapApiError'
import { isResultSuccess } from '@/api/result'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import BlogCyberShell from '@/components/blog/BlogCyberShell.vue'

type UploadResult = { code?: number; message?: string; data?: string }

const STATUS_DRAFT = 0
const STATUS_PUBLISHED = 1

const route = useRoute()
const router = useRouter()

const isEdit = computed(() => route.name === 'blog-edit')
const editId = computed(() => String(route.params.id || '').trim())

const loading = ref(false)
const saving = ref(false)

const title = ref('')
const summary = ref('')
const coverUrl = ref('')
const tagsStr = ref('')
const content = ref('')
const status = ref<number>(STATUS_DRAFT)

const coverInput = ref<HTMLInputElement | null>(null)
const uploadingCover = ref(false)
const coverDragging = ref(false)

const coverPreview = computed(() => resolveMediaUrl(coverUrl.value.trim()) || '')

function pickCover() {
  coverInput.value?.click()
}

function validateImage(file: File): boolean {
  if (!file.type.startsWith('image/')) {
    Message.warning('请选择图片文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    Message.warning('图片不能超过 5MB')
    return false
  }
  return true
}

/** 统一上传入口：返回图片地址，失败返回 null */
async function postImage(file: File): Promise<string | null> {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  if (!token) {
    Message.warning('登录已过期，请重新登录')
    return null
  }
  const payload = new FormData()
  payload.append('image', file)
  const { data } = await axios.post<UploadResult>(
    `${OpenAPI.BASE.replace(/\/+$/, '')}/file/blog-cover`,
    payload,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  if (!isResultSuccess(data.code) || !data.data) {
    Message.error(data.message?.trim() || '图片上传失败')
    return null
  }
  return data.data
}

/** 选择/拖放共用：校验后上传到后端，回填封面地址 */
async function saveCoverFile(file: File | undefined | null) {
  if (!file || !validateImage(file)) return
  uploadingCover.value = true
  try {
    const url = await postImage(file)
    if (url) {
      coverUrl.value = url
      Message.success('封面已上传')
    }
  } catch (error) {
    Message.error(mapApiError(error, '封面上传失败'))
  } finally {
    uploadingCover.value = false
  }
}

function onCoverPick(event: Event) {
  const input = event.target as HTMLInputElement
  void saveCoverFile(input.files?.[0])
  input.value = ''
}

function onCoverDrop(event: DragEvent) {
  coverDragging.value = false
  void saveCoverFile(event.dataTransfer?.files?.[0])
}

/** 从剪贴板取第一张图片，Ctrl+V 粘贴封面 */
function onCoverPaste(event: ClipboardEvent) {
  const items = event.clipboardData?.items
  if (!items) return
  for (const item of Array.from(items)) {
    if (!item.type.startsWith('image/')) continue
    const file = item.getAsFile()
    if (!file) continue
    event.preventDefault()
    void saveCoverFile(file)
    return
  }
}

/** 页面任意位置粘贴图片也能设为封面，但输入框内的文本粘贴不拦截 */
function onWindowPaste(event: ClipboardEvent) {
  const target = event.target as HTMLElement | null
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) return
  onCoverPaste(event)
}

onMounted(() => window.addEventListener('paste', onWindowPaste))
onBeforeUnmount(() => window.removeEventListener('paste', onWindowPaste))

function clearCover() {
  coverUrl.value = ''
}

const contentAreaRef = ref<{ $el?: HTMLElement } | null>(null)
const contentInput = ref<HTMLInputElement | null>(null)
const uploadingContentImage = ref(false)
const contentDragging = ref(false)
const imageLinkOpen = ref(false)
const imageLinkInput = ref('')

function getContentTextarea(): HTMLTextAreaElement | null {
  const root = contentAreaRef.value?.$el
  return root instanceof HTMLElement ? root.querySelector('textarea') : null
}

/** 在光标处插入 Markdown 片段 */
function insertIntoContent(snippet: string) {
  const textarea = getContentTextarea()
  if (!textarea) {
    content.value = `${content.value}${snippet}`
    return
  }
  const start = textarea.selectionStart ?? content.value.length
  const end = textarea.selectionEnd ?? start
  content.value = `${content.value.slice(0, start)}${snippet}${content.value.slice(end)}`
  void nextTick(() => {
    const caret = start + snippet.length
    textarea.focus()
    textarea.setSelectionRange(caret, caret)
  })
}

function imageMarkdown(url: string): string {
  return `\n![图片](${url})\n`
}

/** 正文插图：本地上传 / 拖拽 / 粘贴截图共用 */
async function insertContentImage(file: File | undefined | null) {
  if (!file || !validateImage(file)) return
  uploadingContentImage.value = true
  try {
    const url = await postImage(file)
    if (!url) return
    insertIntoContent(imageMarkdown(url))
    Message.success('图片已插入正文')
  } catch (error) {
    Message.error(mapApiError(error, '图片上传失败'))
  } finally {
    uploadingContentImage.value = false
  }
}

function pickContentImage() {
  contentInput.value?.click()
}

function onContentImagePick(event: Event) {
  const input = event.target as HTMLInputElement
  void insertContentImage(input.files?.[0])
  input.value = ''
}

function onContentDrop(event: DragEvent) {
  contentDragging.value = false
  void insertContentImage(event.dataTransfer?.files?.[0])
}

/** 正文里粘贴截图直接上传并插入；粘贴文字保持默认行为 */
function onContentPaste(event: ClipboardEvent) {
  const items = event.clipboardData?.items
  if (!items) return
  for (const item of Array.from(items)) {
    if (!item.type.startsWith('image/')) continue
    const file = item.getAsFile()
    if (!file) continue
    event.preventDefault()
    void insertContentImage(file)
    return
  }
}

function insertImageLink() {
  const url = imageLinkInput.value.trim()
  if (!url) {
    Message.warning('请先粘贴图片地址')
    return
  }
  insertIntoContent(imageMarkdown(url))
  imageLinkInput.value = ''
  imageLinkOpen.value = false
}

function parseTags(raw: string): string[] {
  return raw
    .split(/[,，]/)
    .map((t) => t.trim())
    .filter(Boolean)
}

async function loadMine() {
  const id = editId.value
  if (!id) return
  loading.value = true
  try {
    const res = await Service.getMine(id)
    if (!isResultSuccess(res.code) || !res.data) {
      Message.error(res.message || '无法加载文章')
      return
    }
    applyDetail(res.data)
  } catch (e) {
    Message.error(mapApiError(e, '无法加载文章'))
  } finally {
    loading.value = false
  }
}

function applyDetail(d: BlogPostDetailVO) {
  title.value = d.title ?? ''
  summary.value = d.summary ?? ''
  coverUrl.value = d.coverUrl ?? ''
  tagsStr.value = (d.tags ?? []).join(', ')
  content.value = d.content ?? ''
  status.value = typeof d.status === 'number' ? d.status : STATUS_DRAFT
}

async function submit() {
  const t = title.value.trim()
  const c = content.value.trim()
  if (!t) {
    Message.warning('请填写标题')
    return
  }
  if (c.length < 10) {
    Message.warning('正文至少 10 个字符')
    return
  }
  saving.value = true
  try {
    if (!isEdit.value) {
      const res = await Service.add1({
        title: t,
        summary: summary.value.trim() || undefined,
        content: c,
        coverUrl: coverUrl.value.trim() || undefined,
        tags: parseTags(tagsStr.value),
        status: status.value,
      })
      if (!isResultSuccess(res.code)) {
        Message.error(res.message || '保存失败')
        return
      }
      const newId = res.data?.trim()
      Message.success(
        status.value === STATUS_PUBLISHED ? '已发布' : '草稿已保存，可在「我的博客」继续编辑',
      )
      if (!newId) {
        void router.push({ name: 'blog-my' })
      } else if (status.value === STATUS_PUBLISHED) {
        void router.replace({ name: 'blog-detail', params: { id: newId } })
      } else {
        void router.replace({ name: 'blog-my' })
      }
      return
    }

    const id = editId.value
    const res = await Service.update1({
      id,
      title: t,
      summary: summary.value.trim() || undefined,
      content: c,
      coverUrl: coverUrl.value.trim() || undefined,
      tags: parseTags(tagsStr.value),
      status: status.value,
    })
    if (!isResultSuccess(res.code)) {
      Message.error(res.message || '保存失败')
      return
    }
    if (status.value === STATUS_PUBLISHED) {
      Message.success('已更新并发布')
      void router.push({ name: 'blog-detail', params: { id } })
    } else {
      Message.success('草稿已更新')
      void router.push({ name: 'blog-my' })
    }
  } catch (e) {
    Message.error(mapApiError(e, '保存失败'))
  } finally {
    saving.value = false
  }
}

function confirmDelete() {
  if (!isEdit.value) return
  const id = editId.value
  Modal.warning({
    title: '删除文章',
    content: '删除后不可恢复，确定继续吗？',
    hideCancel: false,
    onOk: async () => {
      try {
        const res = await Service.delete({ id })
        if (!isResultSuccess(res.code)) {
          Message.error(res.message || '删除失败')
          return
        }
        Message.success('已删除')
        void router.replace({ name: 'blog-my' })
      } catch (e) {
        Message.error(mapApiError(e, '删除失败'))
      }
    },
  })
}

watch(
  () => route.params.id,
  () => {
    if (isEdit.value) void loadMine()
  },
)

onMounted(() => {
  if (isEdit.value) void loadMine()
})
</script>

<template>
  <BlogCyberShell>
    <div class="blog-compose">
      <div class="blog-compose__inner">
        <header class="blog-compose__head panel">
          <div>
            <p class="blog-compose__eyebrow">Editor</p>
            <h1 class="blog-compose__title">{{ isEdit ? '编辑文章' : '写文章' }}</h1>
            <p class="blog-compose__hint">
              正文不少于 10 字；草稿仅自己可见，发布后出现在博客首页。
            </p>
          </div>
          <div class="blog-compose__head-actions">
            <RouterLink :to="{ name: 'blog' }" class="ghost-link blog-magnetic"
              >返回首页</RouterLink
            >
            <RouterLink
              v-if="isEdit && editId && status === STATUS_PUBLISHED"
              :to="{ name: 'blog-detail', params: { id: editId } }"
              class="ghost-link blog-magnetic"
            >
              打开公开页
            </RouterLink>
          </div>
        </header>

        <div v-if="loading && isEdit" class="panel blog-compose__loading">加载编辑器…</div>

        <div v-else class="blog-compose__form panel">
          <label class="field">
            <span class="label">标题</span>
            <a-input
              v-model="title"
              allow-clear
              placeholder="给你的想法起一个清晰的标题"
              size="large"
            />
          </label>

          <label class="field">
            <span class="label">摘要（可选）</span>
            <a-textarea
              v-model="summary"
              class="compose-sans"
              placeholder="列表卡片与详情顶部摘要；留空则从正文截取"
              :auto-size="{ minRows: 2, maxRows: 5 }"
            />
          </label>

          <div class="field">
            <span class="label">封面图（可选）</span>
            <div
              class="cover-drop"
              :class="{ 'cover-drop--drag': coverDragging, 'cover-drop--filled': !!coverPreview }"
              role="button"
              tabindex="0"
              aria-label="上传封面图"
              @click="pickCover"
              @keydown.enter.prevent="pickCover"
              @keydown.space.prevent="pickCover"
              @dragover.prevent="coverDragging = true"
              @dragleave.prevent="coverDragging = false"
              @drop.prevent="onCoverDrop"
              @paste="onCoverPaste"
            >
              <img v-if="coverPreview" :src="coverPreview" alt="封面预览" class="cover-drop__img" />
              <div v-else class="cover-drop__empty">
                <span class="cover-drop__ico" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none">
                    <rect
                      x="3"
                      y="4.5"
                      width="18"
                      height="15"
                      rx="3"
                      stroke="currentColor"
                      stroke-width="1.8"
                    />
                    <circle cx="8.5" cy="9.5" r="1.6" fill="currentColor" />
                    <path
                      d="m4.5 17 4.6-4.4a2 2 0 0 1 2.7 0l3 2.9m0 0 1.7-1.6a2 2 0 0 1 2.7 0l1.3 1.3"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                </span>
                <p class="cover-drop__title">点击上传封面，或把图片拖进来</p>
                <p class="cover-drop__hint">
                  也支持 Ctrl / ⌘ + V 直接粘贴截图 · JPG / PNG / WebP / GIF · 不超过 5MB
                </p>
              </div>
              <div v-if="uploadingCover" class="cover-drop__mask">上传中…</div>
              <div v-else-if="coverPreview" class="cover-drop__bar">
                <button type="button" @click.stop="pickCover">更换</button>
                <button type="button" class="is-danger" @click.stop="clearCover">移除</button>
              </div>
            </div>
            <input
              ref="coverInput"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              class="cover-input"
              @change="onCoverPick"
            />
            <a-input v-model="coverUrl" allow-clear placeholder="也可直接粘贴图片地址：https://…" />
          </div>

          <label class="field">
            <span class="label">标签</span>
            <a-input v-model="tagsStr" allow-clear placeholder="逗号分隔，最多 12 个" />
          </label>

          <fieldset class="status-field">
            <legend class="label">发布状态</legend>
            <label class="radio-line">
              <input v-model.number="status" type="radio" name="st" :value="STATUS_DRAFT" />
              <span>草稿（仅「我的博客」可见）</span>
            </label>
            <label class="radio-line">
              <input v-model.number="status" type="radio" name="st" :value="STATUS_PUBLISHED" />
              <span>立即发布（博客首页可见）</span>
            </label>
          </fieldset>

          <div class="field">
            <div class="field-head">
              <span class="label">正文</span>
              <div class="content-tools">
                <button
                  type="button"
                  class="content-tool"
                  :disabled="uploadingContentImage"
                  @click="pickContentImage"
                >
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <rect
                      x="3"
                      y="4.5"
                      width="18"
                      height="15"
                      rx="3"
                      stroke="currentColor"
                      stroke-width="1.8"
                    />
                    <circle cx="8.5" cy="9.5" r="1.6" fill="currentColor" />
                    <path
                      d="m4.5 17 4.6-4.4a2 2 0 0 1 2.7 0l3 2.9m0 0 1.7-1.6a2 2 0 0 1 2.7 0l1.3 1.3"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                      stroke-linejoin="round"
                    />
                  </svg>
                  {{ uploadingContentImage ? '上传中…' : '插入图片' }}
                </button>
                <button
                  type="button"
                  class="content-tool"
                  :class="{ 'is-on': imageLinkOpen }"
                  @click="imageLinkOpen = !imageLinkOpen"
                >
                  <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path
                      d="M10.5 13.5a4 4 0 0 0 5.7 0l2.3-2.3a4 4 0 1 0-5.7-5.7l-1 1M13.5 10.5a4 4 0 0 0-5.7 0l-2.3 2.3a4 4 0 1 0 5.7 5.7l1-1"
                      stroke="currentColor"
                      stroke-width="1.8"
                      stroke-linecap="round"
                    />
                  </svg>
                  图片链接
                </button>
              </div>
            </div>
            <div v-if="imageLinkOpen" class="content-link-bar">
              <a-input
                v-model="imageLinkInput"
                allow-clear
                placeholder="粘贴图片地址，例如 https://example.com/a.png"
                @press-enter="insertImageLink"
              />
              <button type="button" class="content-link-bar__ok" @click="insertImageLink">
                插入
              </button>
            </div>
            <div
              class="content-drop-wrap"
              :class="{ 'is-drag-over': contentDragging }"
              @dragover.prevent="contentDragging = true"
              @dragleave.prevent="contentDragging = false"
              @drop.prevent="onContentDrop"
            >
              <a-textarea
                ref="contentAreaRef"
                v-model="content"
                class="content-area"
                placeholder="支持 Markdown：可直接粘贴图片地址（多条可紧挨着）；Bing 等无后缀链接也会显示为图；或用 ![](url)、# 标题、**加粗** 等"
                :auto-size="{ minRows: 16, maxRows: 36 }"
                @paste="onContentPaste"
              />
              <div v-if="contentDragging" class="content-drop-tip">松开即可上传并插入图片</div>
            </div>
            <input
              ref="contentInput"
              type="file"
              accept="image/png,image/jpeg,image/webp,image/gif"
              class="cover-input"
              @change="onContentImagePick"
            />
            <p class="content-hint">
              支持点击上传、拖拽图片到输入框，或在输入框内 Ctrl / ⌘ + V 粘贴截图
            </p>
          </div>

          <div class="action-row">
            <a-button
              type="primary"
              size="large"
              class="save-btn blog-magnetic"
              :loading="saving"
              @click="submit"
            >
              {{ isEdit ? '保存修改' : '提交' }}
            </a-button>
            <a-button
              v-if="isEdit"
              size="large"
              status="danger"
              class="del-btn blog-magnetic"
              :disabled="saving"
              @click="confirmDelete"
            >
              删除
            </a-button>
          </div>
        </div>
      </div>
    </div>
  </BlogCyberShell>
</template>

<style scoped>
.blog-compose {
  position: relative;
  isolation: isolate;
  padding: 28px 14px 72px;
  font-family:
    'Plus Jakarta Sans',
    'Noto Sans SC',
    'DM Sans',
    var(--app-font-body, system-ui),
    -apple-system,
    sans-serif;
  font-optical-sizing: auto;
  -webkit-font-smoothing: antialiased;
  text-rendering: optimizeLegibility;
}
.blog-compose__inner {
  width: min(880px, 100%);
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.panel {
  border: 1px solid var(--app-border);
  background: linear-gradient(
    155deg,
    color-mix(in srgb, var(--app-surface) 93%, transparent),
    var(--app-surface-strong)
  );
  box-shadow: var(--app-card-shadow);
  border-radius: var(--app-radius-xl);
}

.blog-compose__head {
  padding: 26px 28px;
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
}
.blog-compose__eyebrow {
  margin: 0 0 8px;
  font-size: 11px;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--app-text-subtle);
  font-weight: 700;
}
.blog-compose__title {
  margin: 0 0 8px;
  font-size: clamp(22px, 3vw, 28px);
  font-weight: 900;
  letter-spacing: -0.02em;
  color: var(--app-text);
}
.blog-compose__hint {
  margin: 0;
  font-size: 14px;
  color: var(--app-text-muted);
  line-height: 1.65;
  letter-spacing: 0.01em;
  max-width: 560px;
}
.blog-compose__head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}
.ghost-link {
  font-size: 14px;
  font-weight: 700;
  color: var(--app-accent);
  text-decoration: none;
}
.ghost-link:hover {
  text-decoration: underline;
}

.blog-compose__loading {
  padding: 48px;
  text-align: center;
  color: var(--app-text-muted);
}

.blog-compose__form {
  padding: 28px 28px 32px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.cover-drop {
  position: relative;
  aspect-ratio: 16 / 7;
  max-height: 260px;
  border-radius: 16px;
  border: 1.5px dashed color-mix(in srgb, var(--app-accent) 34%, var(--app-border, #cbd5e1));
  background: color-mix(in srgb, var(--app-accent) 5%, transparent);
  display: grid;
  place-items: center;
  overflow: hidden;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background 0.2s ease,
    box-shadow 0.2s ease;
}
.cover-drop:hover,
.cover-drop:focus-visible {
  border-color: color-mix(in srgb, var(--app-accent) 60%, transparent);
  background: color-mix(in srgb, var(--app-accent) 8%, transparent);
  outline: none;
}
.cover-drop--drag {
  border-color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 12%, transparent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--app-accent) 12%, transparent);
}
.cover-drop__img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-drop__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 20px;
  text-align: center;
}
.cover-drop__ico {
  width: 40px;
  height: 40px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 12%, transparent);
}
.cover-drop__ico svg {
  width: 22px;
  height: 22px;
}
.cover-drop__title {
  margin: 6px 0 0;
  font-size: 14px;
  font-weight: 700;
  color: var(--app-text);
}
.cover-drop__hint {
  margin: 0;
  font-size: 12.5px;
  color: var(--app-text-muted);
}
.cover-drop__mask {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  background: rgba(2, 6, 23, 0.52);
}
.cover-drop__bar {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 10px 12px;
  background: linear-gradient(transparent, rgba(2, 6, 23, 0.66));
}
.cover-drop__bar button {
  border: none;
  border-radius: 8px;
  padding: 6px 12px;
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
  color: #0f172a;
  background: rgba(255, 255, 255, 0.94);
}
.cover-drop__bar button.is-danger {
  color: #b91c1c;
  background: rgba(254, 226, 226, 0.96);
}
.cover-input {
  display: none;
}
.field-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.content-tools {
  display: flex;
  gap: 8px;
}
.content-tool {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 30px;
  padding: 0 12px;
  border-radius: 9px;
  border: 1px solid color-mix(in srgb, var(--app-accent) 26%, transparent);
  background: color-mix(in srgb, var(--app-accent) 7%, transparent);
  color: var(--app-accent);
  font-size: 12.5px;
  font-weight: 700;
  cursor: pointer;
  transition:
    background 0.18s ease,
    border-color 0.18s ease,
    opacity 0.18s ease;
}
.content-tool:hover:not(:disabled) {
  background: color-mix(in srgb, var(--app-accent) 14%, transparent);
}
.content-tool:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
.content-tool.is-on {
  background: color-mix(in srgb, var(--app-accent) 18%, transparent);
  border-color: color-mix(in srgb, var(--app-accent) 45%, transparent);
}
.content-tool svg {
  width: 15px;
  height: 15px;
}
.content-link-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  border-radius: 12px;
  border: 1px dashed color-mix(in srgb, var(--app-accent) 30%, transparent);
  background: color-mix(in srgb, var(--app-accent) 6%, transparent);
}
.content-link-bar__ok {
  flex: 0 0 auto;
  height: 32px;
  padding: 0 16px;
  border: none;
  border-radius: 9px;
  background: var(--app-accent);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
}
.content-drop-wrap {
  position: relative;
  border-radius: 14px;
  transition: box-shadow 0.18s ease;
}
.content-drop-wrap.is-drag-over {
  box-shadow: 0 0 0 2px var(--app-accent);
}
.content-drop-tip {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: 14px;
  pointer-events: none;
  color: var(--app-accent);
  font-size: 14px;
  font-weight: 800;
  background: color-mix(in srgb, var(--app-accent) 14%, transparent);
}
.content-hint {
  margin: 0;
  font-size: 12.5px;
  color: var(--app-text-muted);
}
.label {
  font-size: 13px;
  font-weight: 800;
  letter-spacing: 0.02em;
  color: color-mix(in srgb, var(--app-text) 84%, var(--app-text-muted) 16%);
}

.status-field {
  margin: 0;
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px dashed var(--color-border-2);
  background: color-mix(in srgb, var(--color-fill-2) 80%, transparent);
}
.radio-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.55;
  letter-spacing: 0.01em;
  color: color-mix(in srgb, var(--app-text-muted) 88%, var(--app-text) 12%);
  cursor: pointer;
}
.radio-line:first-of-type {
  margin-top: 4px;
}

.blog-compose__form :deep(.arco-input) {
  font-size: 15px;
  line-height: 1.55;
  letter-spacing: 0.01em;
}

.compose-sans :deep(.arco-textarea) {
  font-family:
    'Plus Jakarta Sans', 'Noto Sans SC', 'DM Sans', var(--app-font-body, system-ui), sans-serif;
  font-size: 15px;
  line-height: 1.72;
  letter-spacing: 0.012em;
}

.content-area :deep(.arco-textarea) {
  font-family: 'JetBrains Mono', 'Noto Sans SC', 'Cascadia Code', ui-monospace, monospace;
  font-size: clamp(14px, 0.35vw + 13.2px, 16px);
  line-height: 1.82;
  letter-spacing: 0.02em;
  font-feature-settings:
    'kern' 1,
    'liga' 1,
    'calt' 1;
}

.action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}
.save-btn {
  border-radius: 14px;
  min-width: 140px;
}
.del-btn {
  border-radius: 14px;
}
</style>
