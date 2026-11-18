<script setup lang="ts">
import { computed, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { publishAnnouncement } from '@/api/inAppNotifications'
import { isResultSuccess } from '@/api/result'
import { getBackendErrorMessage } from '@/api/httpError'

const TITLE_LIMIT = 255
const CONTENT_LIMIT = 20000

const title = ref('')
const content = ref('')
const submitting = ref(false)

const titleCount = computed(() => title.value.length)
const contentCount = computed(() => content.value.length)

function resetForm() {
  if (submitting.value) return
  title.value = ''
  content.value = ''
}

async function onSubmit() {
  const t = title.value.trim()
  const c = content.value.trim()
  if (!t) {
    Message.warning('请填写标题')
    return
  }
  if (!c) {
    Message.warning('请填写正文')
    return
  }
  submitting.value = true
  try {
    const res = await publishAnnouncement(t, c)
    if (!isResultSuccess(res.code)) {
      Message.error(res.message || '发布失败')
      return
    }
    Message.success('公告已发布，全站用户将在通知中心看到未读提示')
    title.value = ''
    content.value = ''
    window.dispatchEvent(new CustomEvent('myoj:notifications-changed'))
  } catch (e) {
    Message.error(getBackendErrorMessage(e, '发布失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="admin-page ann-page">
    <div class="ann-shell">
      <header class="ann-hero">
        <div class="ann-hero__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M4 10.5v3a1.5 1.5 0 0 0 1.5 1.5h1.9l3.5 3.2c.9.8 2.4.2 2.4-1.1v-10c0-1.3-1.5-1.9-2.4-1.1L7.4 9H5.5A1.5 1.5 0 0 0 4 10.5Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
            <path
              d="M17 9c1 .8 1.6 1.8 1.6 3s-.6 2.2-1.6 3"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            />
            <path
              d="M19.6 6.6c1.6 1.4 2.4 3.2 2.4 5.4s-.8 4-2.4 5.4"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
            />
          </svg>
        </div>
        <div class="ann-hero__text">
          <p class="ann-eyebrow">通知中心</p>
          <h1>发布公告</h1>
          <p class="ann-sub">
            发布后立即推送到全站用户的通知中心，并以未读状态提醒；用户可在通知页将公告标为已读。
          </p>
        </div>
      </header>

      <div class="ann-grid">
        <section class="ann-card">
          <div class="ann-field">
            <div class="ann-field__head">
              <label for="ann-title">公告标题</label>
              <span class="ann-count" :class="{ 'is-near': titleCount > TITLE_LIMIT * 0.9 }">
                {{ titleCount }} / {{ TITLE_LIMIT }}
              </span>
            </div>
            <a-input
              id="ann-title"
              v-model="title"
              allow-clear
              placeholder="例如：本周六 20:00 进行系统维护"
              :max-length="TITLE_LIMIT"
            />
          </div>

          <div class="ann-field">
            <div class="ann-field__head">
              <label for="ann-content">公告正文</label>
              <span class="ann-count" :class="{ 'is-near': contentCount > CONTENT_LIMIT * 0.9 }">
                {{ contentCount }} / {{ CONTENT_LIMIT }}
              </span>
            </div>
            <a-textarea
              id="ann-content"
              v-model="content"
              allow-clear
              placeholder="写清楚影响范围、时间安排和需要用户做的事，建议控制在 3 段以内。"
              :auto-size="{ minRows: 10, maxRows: 24 }"
              :max-length="CONTENT_LIMIT"
            />
          </div>

          <div class="ann-actions">
            <button type="button" class="ann-primary" :disabled="submitting" @click="onSubmit">
              <span v-if="submitting" class="ann-spinner" aria-hidden="true" />
              {{ submitting ? '发布中…' : '发布公告' }}
            </button>
            <button
              type="button"
              class="ann-ghost"
              :disabled="submitting || (!title && !content)"
              @click="resetForm"
            >
              清空
            </button>
            <span class="ann-hint">发布后不可撤回，请确认内容无误</span>
          </div>
        </section>

        <aside class="ann-side">
          <section class="ann-preview">
            <p class="ann-preview__title">通知中心预览</p>
            <div class="ann-preview__item">
              <span class="ann-preview__dot" aria-hidden="true" />
              <div class="ann-preview__body">
                <p class="ann-preview__t">{{ title.trim() || '公告标题将显示在这里' }}</p>
                <p class="ann-preview__c">
                  {{ content.trim() || '公告正文会显示在这个位置，建议先说结论，再补充细节。' }}
                </p>
                <p class="ann-preview__meta">刚刚 · 全站公告</p>
              </div>
            </div>
          </section>

          <ul class="ann-tips">
            <li><strong>标题</strong>写动作和时间，例如「今晚 22:00 判题服务升级」。</li>
            <li><strong>正文</strong>说明影响范围与用户需要做的事。</li>
            <li>公告会推送给<strong>全部用户</strong>，避免使用测试内容。</li>
          </ul>
        </aside>
      </div>
    </div>
  </div>
</template>

<style scoped>
.ann-page {
  padding: 8px 0 48px;
}

.ann-shell {
  max-width: 1040px;
  margin: 0 auto;
  padding: 0 4px;
}

/* 顶部标题区 */
.ann-hero {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 22px;
}

.ann-hero__icon {
  flex: 0 0 auto;
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 12%, var(--app-surface-strong));
  border: 1px solid color-mix(in srgb, var(--app-accent) 22%, transparent);
}

.ann-hero__icon svg {
  width: 26px;
  height: 26px;
}

.ann-eyebrow {
  margin: 0 0 4px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.14em;
  color: var(--app-accent);
}

.ann-hero h1 {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: var(--app-text, #2a1e15);
}

.ann-sub {
  margin: 0;
  max-width: 620px;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--app-text-muted);
}

/* 两栏 */
.ann-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}

.ann-card {
  padding: 26px;
  border-radius: 18px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-strong);
  box-shadow: 0 10px 30px rgba(45, 33, 24, 0.05);
}

.ann-field + .ann-field {
  margin-top: 22px;
}

.ann-field__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
}

.ann-field__head label {
  font-size: 13.5px;
  font-weight: 700;
  color: var(--app-text, #2a1e15);
}

.ann-count {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--app-text-muted);
  opacity: 0.7;
}

.ann-count.is-near {
  color: #d97706;
  opacity: 1;
}

/* 输入框外观 */
.ann-card :deep(.arco-input-wrapper),
.ann-card :deep(.arco-textarea-wrapper) {
  border-radius: 12px;
  border: 1px solid var(--app-border);
  background: var(--app-surface-soft);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background 0.18s ease;
}

.ann-card :deep(.arco-input-wrapper) {
  height: 46px;
  padding: 0 14px;
}

.ann-card :deep(.arco-input-wrapper:hover),
.ann-card :deep(.arco-textarea-wrapper:hover) {
  border-color: color-mix(in srgb, var(--app-accent) 32%, var(--app-border));
}

.ann-card :deep(.arco-input-wrapper:focus-within),
.ann-card :deep(.arco-textarea-wrapper:focus-within) {
  border-color: var(--app-accent);
  background: var(--app-surface-strong);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent) 16%, transparent);
}

.ann-card :deep(.arco-input),
.ann-card :deep(.arco-textarea) {
  font-size: 14px;
  color: var(--app-text, #2a1e15);
}

.ann-card :deep(.arco-textarea) {
  padding: 14px;
  line-height: 1.75;
  background: transparent;
}

/* 操作区 */
.ann-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px dashed var(--app-border);
}

.ann-primary {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 26px;
  border: none;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 800;
  color: #fff;
  cursor: pointer;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--app-accent) 82%, #ffffff),
    var(--app-accent)
  );
  box-shadow: 0 10px 22px color-mix(in srgb, var(--app-accent) 26%, transparent);
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease,
    opacity 0.18s ease;
}

.ann-primary:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 14px 26px color-mix(in srgb, var(--app-accent) 32%, transparent);
}

.ann-primary:disabled {
  opacity: 0.65;
  cursor: not-allowed;
  box-shadow: none;
}

.ann-spinner {
  width: 15px;
  height: 15px;
  border-radius: 50%;
  border: 2px solid rgba(255, 255, 255, 0.45);
  border-top-color: #fff;
  animation: ann-spin 0.7s linear infinite;
}

@keyframes ann-spin {
  to {
    transform: rotate(360deg);
  }
}

.ann-ghost {
  height: 44px;
  padding: 0 20px;
  border-radius: 12px;
  border: 1px solid var(--app-border-strong);
  background: transparent;
  color: var(--app-text-muted);
  font-size: 13.5px;
  font-weight: 700;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    color 0.18s ease,
    background 0.18s ease;
}

.ann-ghost:hover:not(:disabled) {
  border-color: color-mix(in srgb, var(--app-accent) 40%, transparent);
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 6%, transparent);
}

.ann-ghost:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ann-hint {
  font-size: 12px;
  color: var(--app-text-muted);
  opacity: 0.75;
}

/* 右侧预览与提示 */
.ann-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 16px;
}

.ann-preview {
  padding: 18px;
  border-radius: 16px;
  border: 1px solid color-mix(in srgb, var(--app-accent) 20%, var(--app-border));
  background: color-mix(in srgb, var(--app-accent) 7%, var(--app-surface-strong));
}

.ann-preview__title {
  margin: 0 0 12px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  color: var(--app-accent);
}

.ann-preview__item {
  display: flex;
  gap: 10px;
  padding: 14px;
  border-radius: 12px;
  background: var(--app-surface-strong);
  border: 1px solid var(--app-border);
}

.ann-preview__dot {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: var(--app-accent);
  box-shadow: 0 0 0 4px color-mix(in srgb, var(--app-accent) 16%, transparent);
}

.ann-preview__body {
  min-width: 0;
}

.ann-preview__t {
  margin: 0 0 6px;
  font-size: 13.5px;
  font-weight: 800;
  line-height: 1.5;
  color: var(--app-text, #2a1e15);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  word-break: break-word;
}

.ann-preview__c {
  margin: 0 0 8px;
  font-size: 12.5px;
  line-height: 1.7;
  color: var(--app-text-muted);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 4;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
}

.ann-preview__meta {
  margin: 0;
  font-size: 11.5px;
  color: var(--app-text-muted);
  opacity: 0.65;
}

.ann-tips {
  margin: 0;
  padding: 16px 18px 16px 34px;
  border-radius: 14px;
  border: 1px dashed var(--app-border-strong);
  background: var(--app-surface-soft);
  color: var(--app-text-muted);
  font-size: 12.5px;
  line-height: 1.75;
}

.ann-tips li + li {
  margin-top: 6px;
}

.ann-tips strong {
  color: var(--app-text, #2a1e15);
  font-weight: 700;
}

/* 响应式 */
@media (max-width: 900px) {
  .ann-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .ann-side {
    position: static;
  }

  .ann-card {
    padding: 20px;
  }

  .ann-hero h1 {
    font-size: 23px;
  }
}

@media (max-width: 560px) {
  .ann-hero {
    gap: 12px;
  }

  .ann-hero__icon {
    width: 44px;
    height: 44px;
    border-radius: 13px;
  }

  .ann-primary,
  .ann-ghost {
    flex: 1 1 auto;
    justify-content: center;
  }

  .ann-hint {
    flex-basis: 100%;
  }
}
</style>
