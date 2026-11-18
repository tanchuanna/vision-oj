<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import type { BlogPostListItemVO } from '@generated'
import { Service } from '@generated'
import { mapApiError } from '@/api/mapApiError'
import { isResultSuccess } from '@/api/result'
import { useAuthStore } from '@/stores/auth'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import BlogCyberShell from '@/components/blog/BlogCyberShell.vue'
import {
  beginBlogSharedTransition,
  blogSharedTransitionActive,
  endBlogSharedTransition,
} from '@/composables/blogSharedTransition'
import { formatSiteDate, localize as l } from '@/i18n/locale'

const router = useRouter()
const auth = useAuthStore()

function openArticle(event: MouseEvent, item: BlogPostListItemVO) {
  if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
  const id = String(item.id || '')
  if (!id) return
  event.preventDefault()
  if (blogSharedTransitionActive) return
  const destination = { name: 'blog-detail', params: { id } }
  const title = (event.currentTarget as HTMLElement | null)?.querySelector<HTMLElement>(
    '.blog-card__title',
  )
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (!title || reduceMotion || !document.startViewTransition) {
    void router.push(destination)
    return
  }
  title.style.viewTransitionName = 'selected-blog-title'
  beginBlogSharedTransition(id, item.title || '')
  try {
    const transition = document.startViewTransition(async () => {
      await router.push(destination)
      await nextTick()
    })
    const cleanup = () => {
      title.style.viewTransitionName = ''
      endBlogSharedTransition()
    }
    void transition.finished.then(cleanup, cleanup)
  } catch {
    title.style.viewTransitionName = ''
    endBlogSharedTransition()
    void router.push(destination)
  }
}

const loading = ref(false)
const rows = ref<BlogPostListItemVO[]>([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(12)
const titleKeyword = ref('')
const activeTag = ref('')
const tagCounts = ref<Record<string, number>>({})
const siteTotal = ref(0)

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

const tagPills = computed(() =>
  Object.entries(tagCounts.value)
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'zh-CN'))
    .slice(0, 14)
    .map(([tag]) => tag),
)

const tagCount = computed(() => Object.keys(tagCounts.value).length)
const hasSearchFilter = computed(() => !!titleKeyword.value.trim() || !!activeTag.value)

const cardsRoot = ref<HTMLElement | null>(null)
const visibleCardIds = ref<Set<string>>(new Set())
const ioReady = ref(false)
let cardObserver: IntersectionObserver | null = null

function formatTime(raw?: string): string {
  if (!raw) return ''
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return formatSiteDate(d, { year: 'numeric', month: 'short', day: 'numeric' })
}

async function loadPage() {
  loading.value = true
  try {
    const res = await Service.pagePublic({
      current: current.value,
      pageSize: pageSize.value,
      titleKeyword: titleKeyword.value.trim() || undefined,
      tag: activeTag.value || undefined,
    })
    if (!isResultSuccess(res.code)) {
      Message.error(res.message || l('加载失败', 'Could not load articles'))
      return
    }
    const page = res.data
    rows.value = page?.records ?? []
    total.value = page?.total ?? 0
    current.value = page?.current ?? current.value
    pageSize.value = page?.size ?? pageSize.value
    if (!titleKeyword.value.trim() && !activeTag.value) {
      siteTotal.value = total.value
    }
    const counts = { ...tagCounts.value }
    for (const row of rows.value) {
      for (const raw of row.tags ?? []) {
        const tag = raw.trim()
        if (tag) counts[tag] = (counts[tag] ?? 0) + 1
      }
    }
    tagCounts.value = counts
  } catch (e) {
    Message.error(mapApiError(e, l('加载失败', 'Could not load articles')))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  current.value = 1
  void loadPage()
}

function resetFilters() {
  titleKeyword.value = ''
  activeTag.value = ''
  onSearch()
}

function selectTagPill(tag: string) {
  activeTag.value = activeTag.value === tag ? '' : tag
  onSearch()
}

function clearTagPill() {
  if (!activeTag.value) return
  activeTag.value = ''
  onSearch()
}

function goWrite() {
  if (!auth.isLoggedIn) {
    void router.push({ path: '/', query: { openAuth: '1' } })
    return
  }
  void router.push({ name: 'blog-write' })
}

function goPrev() {
  if (current.value <= 1) return
  current.value -= 1
  void loadPage()
}

function goNext() {
  if (current.value >= totalPages.value) return
  current.value += 1
  void loadPage()
}

function cardId(item: BlogPostListItemVO): string {
  return String(item.id || item.slug || '')
}

function cardIoClass(item: BlogPostListItemVO): Record<string, boolean> {
  const id = cardId(item)
  if (!id || !ioReady.value) return {}
  return {
    'blog-card--inview': visibleCardIds.value.has(id),
    'blog-card--dim': !visibleCardIds.value.has(id),
  }
}

function setupCardObserver() {
  cardObserver?.disconnect()
  cardObserver = null
  visibleCardIds.value = new Set()
  ioReady.value = false
  if (typeof IntersectionObserver === 'undefined' || !cardsRoot.value) return
  const nodes = cardsRoot.value.querySelectorAll('[data-card-id]')
  if (!nodes.length) return
  cardObserver = new IntersectionObserver(
    (entries) => {
      const next = new Set(visibleCardIds.value)
      for (const e of entries) {
        const el = e.target as HTMLElement
        const id = el.dataset.cardId
        if (!id) continue
        if (e.isIntersecting) next.add(id)
        else next.delete(id)
      }
      visibleCardIds.value = next
      ioReady.value = true
    },
    { root: null, rootMargin: '-6% 0px -10% 0px', threshold: [0, 0.15, 0.35] },
  )
  nodes.forEach((el) => cardObserver!.observe(el as HTMLElement))
}

function ripple(ev: MouseEvent, el: HTMLElement) {
  const r = el.getBoundingClientRect()
  el.style.setProperty('--rx', `${ev.clientX - r.left}px`)
  el.style.setProperty('--ry', `${ev.clientY - r.top}px`)
  el.classList.remove('is-rippling')
  void el.offsetWidth
  el.classList.add('is-rippling')
  window.setTimeout(() => el.classList.remove('is-rippling'), 520)
}

function onWriteClick(ev: MouseEvent) {
  const el = ev.currentTarget as HTMLElement
  ripple(ev, el)
  goWrite()
}

watch(pageSize, () => {
  current.value = 1
  void loadPage()
})

watch(
  () => rows.value,
  () => {
    void nextTick(() => setupCardObserver())
  },
  { flush: 'post' },
)

onMounted(() => {
  void loadPage()
})

onBeforeUnmount(() => {
  cardObserver?.disconnect()
  cardObserver = null
})
</script>

<template>
  <BlogCyberShell>
    <div class="blog-hub">
      <section class="blog-hero panel">
        <div class="blog-hero__grid" aria-hidden="true" />
        <div class="blog-hero__content">
          <p class="blog-hero__eyebrow">{{ l('码跃社区', 'Community') }}</p>
          <h1 class="blog-hero__title blog-hero__title--cosmic">
            {{ l('算法笔记与赛题解析', 'Notes and contest insights') }}
          </h1>
          <p class="blog-hero__lead blog-hero__lead--cosmic">
            {{
              l(
                '阅读公开文章，按标题和标签查找解题经验；登录后可整理并发布自己的笔记。',
                'Read articles, search by title or tag, and publish your own notes after signing in.',
              )
            }}
          </p>
          <div class="blog-hero__stats">
            <span class="blog-hero__stat"
              ><strong>{{ siteTotal }}</strong> {{ l('篇公开文章', 'public articles') }}</span
            >
            <template v-if="tagCount">
              <span class="blog-hero__stat-sep">·</span>
              <span class="blog-hero__stat"
                ><strong>{{ tagCount }}</strong> {{ l('个标签', 'tags') }}</span
              >
            </template>
          </div>
          <div class="blog-hero__actions">
            <button
              type="button"
              class="chip-btn chip-btn--primary chip-btn--neon ripple-host blog-magnetic"
              @click="onWriteClick"
            >
              {{ l('写文章', 'Write article') }}
            </button>
            <RouterLink
              v-if="auth.isLoggedIn"
              :to="{ name: 'blog-my' }"
              class="chip-btn chip-btn--ghost blog-magnetic"
            >
              {{ l('我的博客', 'My articles') }}
            </RouterLink>
          </div>
        </div>
      </section>

      <div class="blog-toolbar-host">
        <section class="blog-toolbar panel">
          <div class="blog-toolbar__row">
            <a-input
              v-model="titleKeyword"
              allow-clear
              :placeholder="l('搜索标题关键词…', 'Search article titles…')"
              class="blog-toolbar__search"
              @press-enter="onSearch"
            />
            <button
              type="button"
              class="chip-btn chip-btn--primary blog-toolbar__go"
              :disabled="loading"
              @click="onSearch"
            >
              {{ loading ? l('检索中…', 'Searching…') : l('检索', 'Search') }}
            </button>
          </div>
          <div
            v-if="tagPills.length"
            class="blog-toolbar__pills"
            :aria-label="l('按标签筛选', 'Filter by tag')"
          >
            <button
              type="button"
              class="tag-cap"
              :class="{ 'tag-cap--on': !activeTag }"
              @click="clearTagPill"
            >
              {{ l('全部', 'All') }}
            </button>
            <button
              v-for="t in tagPills"
              :key="t"
              type="button"
              class="tag-cap"
              :class="{ 'tag-cap--on': activeTag === t }"
              :aria-pressed="activeTag === t"
              @click="selectTagPill(t)"
            >
              {{ t }}
            </button>
          </div>
        </section>
      </div>

      <div v-if="!loading" class="blog-results">
        <p class="blog-results__count">
          {{ l('共', 'Total') }} <strong>{{ total }}</strong> {{ l('篇', 'articles') }}
          <template v-if="activeTag"> · {{ l('标签', 'Tag') }}: {{ activeTag }}</template>
          <template v-if="titleKeyword.trim()">
            · {{ l('关键词', 'Keyword') }}: {{ titleKeyword.trim() }}</template
          >
        </p>
        <span v-if="rows.length" class="blog-results__page"
          >{{ l('第', 'Page') }} {{ current }} / {{ totalPages }} {{ l('页', '') }}</span
        >
      </div>

      <div v-if="loading" class="blog-skeleton panel">
        <div v-for="n in 6" :key="n" class="sk-card" />
      </div>

      <div v-else-if="!rows.length" class="blog-empty panel">
        <div class="blog-empty__icon" aria-hidden="true">
          <svg viewBox="0 0 48 48" fill="none">
            <path
              d="M14 8h13l9 9v23a2 2 0 0 1-2 2H14a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z"
              stroke="currentColor"
              stroke-width="2.4"
              stroke-linejoin="round"
            />
            <path d="M27 8v9h9" stroke="currentColor" stroke-width="2.4" stroke-linejoin="round" />
            <path
              d="M18 27h12M18 33h8"
              stroke="currentColor"
              stroke-width="2.4"
              stroke-linecap="round"
            />
          </svg>
        </div>
        <p class="blog-empty__title">
          {{
            hasSearchFilter
              ? l('没有找到相关文章', 'No matching articles')
              : l('还没有公开文章', 'No public articles yet')
          }}
        </p>
        <p class="blog-empty__hint">
          {{
            hasSearchFilter
              ? l(
                  '换个关键词或标签试试，也可以清除筛选查看全部文章。',
                  'Try another keyword or tag, or clear the filters.',
                )
              : l('第一篇笔记发布后会显示在这里。', 'The first published article will appear here.')
          }}
        </p>
        <div class="blog-empty__actions">
          <button
            v-if="hasSearchFilter"
            type="button"
            class="chip-btn chip-btn--primary"
            @click="resetFilters"
          >
            {{ l('清除筛选', 'Clear filters') }}
          </button>
          <button v-else type="button" class="chip-btn chip-btn--primary" @click="onWriteClick">
            {{
              auth.isLoggedIn
                ? l('写第一篇文章', 'Write the first article')
                : l('登录后写文章', 'Sign in to write')
            }}
          </button>
          <RouterLink :to="{ name: 'problems' }" class="chip-btn chip-btn--ghost">{{
            l('去题库看看', 'Browse problems')
          }}</RouterLink>
        </div>
      </div>

      <div v-else ref="cardsRoot" class="blog-cards">
        <RouterLink
          v-for="(item, idx) in rows"
          :key="item.id || item.slug"
          :to="{ name: 'blog-detail', params: { id: item.id || '' } }"
          custom
          v-slot="{ href }"
        >
          <a
            :href="href"
            class="blog-card panel"
            :class="cardIoClass(item)"
            :style="{ '--card-i': String(idx) }"
            :data-card-id="cardId(item)"
            @click="openArticle($event, item)"
          >
            <div class="blog-card__media">
              <img
                v-if="item.coverUrl && resolveMediaUrl(item.coverUrl)"
                :src="resolveMediaUrl(item.coverUrl)"
                alt=""
                class="blog-card__cover"
              />
              <div v-else class="blog-card__cover blog-card__cover--placeholder">
                <span class="blog-card__placeholder-ico" aria-hidden="true">◇</span>
              </div>
              <div class="blog-card__media-overlay" aria-hidden="true" />
              <div class="blog-card__shine" aria-hidden="true" />
            </div>
            <div class="blog-card__body">
              <h2 class="blog-card__title">{{ item.title }}</h2>
              <p class="blog-card__summary">
                {{ item.summary || l('（无摘要）', '(No summary)') }}
              </p>
              <div class="blog-card__foot">
                <span class="blog-card__author">{{ item.authorName || l('作者', 'Author') }}</span>
                <span class="blog-card__dot">·</span>
                <time class="blog-card__time" :datetime="item.publishedAt">{{
                  formatTime(item.publishedAt)
                }}</time>
              </div>
              <div v-if="item.tags?.length" class="blog-card__tags">
                <span v-for="tg in item.tags.slice(0, 4)" :key="tg" class="blog-tag">{{ tg }}</span>
              </div>
              <div class="blog-card__stats">
                <span class="blog-card__stat">
                  <span class="blog-card__stat-ico blog-card__stat-ico--eye" aria-hidden="true" />
                  <span class="blog-card__stat-num">{{ item.viewCount ?? 0 }}</span>
                </span>
                <span class="blog-card__stat">
                  <span class="blog-card__stat-ico blog-card__stat-ico--heart" aria-hidden="true" />
                  <span class="blog-card__stat-num">{{ item.likeCount ?? 0 }}</span>
                </span>
              </div>
            </div>
          </a>
        </RouterLink>
      </div>

      <div v-if="rows.length" class="blog-pager">
        <a-button
          class="pager-btn blog-magnetic"
          :disabled="current <= 1 || loading"
          @click="goPrev"
          >{{ l('上一页', 'Previous') }}</a-button
        >
        <span class="blog-pager__label">{{ current }} / {{ totalPages }}</span>
        <a-button
          class="pager-btn blog-magnetic"
          :disabled="current >= totalPages || loading"
          @click="goNext"
          >{{ l('下一页', 'Next') }}</a-button
        >
      </div>
    </div>
  </BlogCyberShell>
</template>

<style scoped>
.blog-hub {
  position: relative;
  isolation: isolate;
  width: min(1220px, calc(100vw - 28px));
  margin: 0 auto;
  padding: 28px 12px 72px;
  font-family: var(--app-font-body);
}

.panel {
  border: 1px solid var(--app-border);
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--app-surface) 92%, transparent),
    var(--app-surface-strong)
  );
  box-shadow:
    14px 20px 40px rgba(15, 23, 42, 0.09),
    -6px 10px 26px rgba(99, 102, 241, 0.04);
  border-radius: var(--app-radius-xl);
}

:global(html[data-theme='dark']) .blog-hub .panel {
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow:
    16px 22px 48px rgba(0, 0, 0, 0.38),
    -8px 12px 30px rgba(79, 70, 229, 0.07);
}

:global(html[data-theme='light']) .blog-hub .panel {
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  background: #ffffff;
  border: 1px solid rgba(15, 23, 42, 0.1);
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 1) inset,
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 6px 16px rgba(15, 23, 42, 0.06);
}

.blog-hero {
  position: relative;
  overflow: hidden;
  padding: 36px 40px 42px;
  margin-bottom: 26px;
}

.blog-hero__grid {
  position: absolute;
  inset: 0;
  opacity: 0.18;
  background-image:
    linear-gradient(rgba(99, 102, 241, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99, 102, 241, 0.1) 1px, transparent 1px);
  background-size: 36px 36px;
  mask-image: radial-gradient(ellipse 85% 75% at 50% 0%, #000 32%, transparent 78%);
  animation: bh-grid-shift 22s linear infinite;
}

@keyframes bh-grid-shift {
  from {
    background-position:
      0 0,
      0 0;
  }
  to {
    background-position:
      36px 24px,
      24px 36px;
  }
}

.blog-hero__content {
  position: relative;
  max-width: 760px;
}

.blog-hero__eyebrow {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--app-text-subtle);
  font-weight: 600;
}

.blog-hero__title {
  margin: 0 0 14px;
  font-size: clamp(26px, 4vw, 40px);
  font-weight: 800;
  letter-spacing: -0.03em;
  line-height: 1.12;
  color: var(--app-text);
}

.blog-hero__lead {
  margin: 0 0 18px;
  font-size: 15px;
  font-weight: 300;
  line-height: 1.7;
  color: var(--app-text-muted);
  max-width: 560px;
}

.blog-hero__stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: -6px 0 18px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.76);
}

.blog-hero__stat strong {
  font-size: 15px;
  font-weight: 800;
  letter-spacing: -0.01em;
  color: #fff;
}

.blog-hero__stat-sep {
  opacity: 0.5;
}

.blog-hero__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.chip-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  text-decoration: none;
  border: 1px solid transparent;
  transition:
    transform 0.15s ease,
    box-shadow 0.2s ease,
    filter 0.15s ease;
}

.chip-btn--primary {
  color: #fff;
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 45%, #ec4899 130%);
  box-shadow: 0 10px 32px rgba(79, 70, 229, 0.35);
  border: none;
}

.chip-btn--primary:hover {
  transform: translateY(-1px);
  filter: brightness(1.06);
}

.chip-btn--ghost {
  color: var(--app-accent);
  border-color: color-mix(in srgb, var(--app-accent) 38%, var(--app-border));
  background: color-mix(in srgb, var(--app-accent) 8%, transparent);
}

.chip-btn--ghost:hover {
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(79, 70, 229, 0.15);
}

.ripple-host {
  position: relative;
  overflow: hidden;
}

.ripple-host::before {
  content: '';
  position: absolute;
  left: var(--rx, 50%);
  top: var(--ry, 50%);
  width: 12px;
  height: 12px;
  transform: translate(-50%, -50%) scale(0);
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.45), transparent 62%);
  pointer-events: none;
  opacity: 0;
}

.ripple-host.is-rippling::before {
  animation: bh-ripple 0.52s ease-out forwards;
}

@keyframes bh-ripple {
  to {
    transform: translate(-50%, -50%) scale(18);
    opacity: 0;
  }
}

.blog-toolbar-host {
  position: sticky;
  top: 64px;
  z-index: 8;
  margin-bottom: 26px;
}

.blog-toolbar {
  padding: 20px 22px 18px;
  box-shadow:
    0 18px 44px rgba(15, 23, 42, 0.12),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset !important;
}

:global(html[data-theme='dark']) .blog-toolbar {
  box-shadow:
    0 22px 56px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(129, 140, 248, 0.08) inset !important;
}

:global(html[data-theme='light']) .blog-toolbar {
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.04),
    0 6px 18px rgba(15, 23, 42, 0.05),
    0 1px 0 rgba(255, 255, 255, 1) inset !important;
}

:global(html[data-theme='light']) .blog-hub .chip-btn--primary {
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.06),
    0 4px 14px rgba(79, 70, 229, 0.18) !important;
}

:global(html[data-theme='light']) .blog-hub .chip-btn--primary:hover {
  filter: none !important;
}

:global(html[data-theme='light']) .blog-hub .tag-cap {
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  color: #475569;
}

:global(html[data-theme='light']) .blog-hub .tag-cap--on {
  color: #1e1b4b;
  border-color: rgba(79, 70, 229, 0.35);
  background: linear-gradient(135deg, #eef2ff 0%, #faf5ff 100%);
}

:global(html[data-theme='light']) .blog-pager {
  color: #64748b;
}

.blog-toolbar__row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.blog-toolbar__search {
  flex: 1 1 220px;
  max-width: 420px;
  height: 38px;
  border-radius: 12px !important;
}

.blog-toolbar__go {
  height: 38px;
  padding: 0 22px;
  border-radius: 12px;
  flex: 0 0 auto;
}

.blog-toolbar__go:disabled {
  opacity: 0.7;
  cursor: not-allowed;
  transform: none;
}

.blog-toolbar__pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.tag-cap {
  appearance: none;
  border: 1px solid color-mix(in srgb, var(--app-accent) 28%, var(--app-border));
  background: color-mix(in srgb, var(--app-surface) 88%, transparent);
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
  padding: 6px 14px;
  border-radius: 999px;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.22s ease,
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease;
}

.tag-cap:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--app-accent) 55%, transparent);
  box-shadow: 0 6px 18px rgba(79, 70, 229, 0.12);
}

.tag-cap--on {
  color: var(--app-text);
  border-color: color-mix(in srgb, #ec4899 45%, #6366f1 35%);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, #6366f1 22%, transparent),
    color-mix(in srgb, #ec4899 16%, transparent)
  );
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.06) inset,
    0 0 20px rgba(236, 72, 153, 0.18);
}

:global(html[data-theme='dark']) .tag-cap--on {
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.05) inset,
    0 0 28px rgba(129, 140, 248, 0.25);
}

.blog-results {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin: 0 4px 16px;
  font-size: 13px;
  color: var(--app-text-subtle);
}

.blog-results__count {
  margin: 0;
}

.blog-results__count strong {
  font-weight: 700;
  color: var(--app-text);
}

.blog-results__page {
  flex: 0 0 auto;
  font-variant-numeric: tabular-nums;
}

.blog-skeleton {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
  padding: 22px;
  margin-bottom: 26px;
}

.sk-card {
  height: 320px;
  border-radius: 22px;
  background: linear-gradient(
    110deg,
    var(--color-fill-2) 0%,
    var(--color-fill-3) 14%,
    var(--color-fill-2) 28%,
    var(--color-fill-3) 42%,
    var(--color-fill-2) 56%
  );
  background-size: 240% 100%;
  animation: bh-shimmer 1.35s ease-in-out infinite;
}

@keyframes bh-shimmer {
  0% {
    background-position: 240% 0;
  }
  100% {
    background-position: -240% 0;
  }
}

.blog-empty {
  text-align: center;
  padding: 48px 24px;
  margin-bottom: 26px;
}

.blog-empty__icon {
  width: 64px;
  height: 64px;
  margin: 0 auto 16px;
  display: grid;
  place-items: center;
  border-radius: 20px;
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--app-accent) 22%, transparent);
}

.blog-empty__icon svg {
  width: 30px;
  height: 30px;
}

.blog-empty__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.blog-empty__title {
  margin: 0 0 8px;
  font-size: 20px;
  font-weight: 800;
  color: var(--app-text);
}

.blog-empty__hint {
  margin: 0 0 22px;
  color: var(--app-text-muted);
}

.blog-cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 32px;
}

.blog-card {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  color: inherit;
  overflow: hidden;
  border-radius: 24px;
  transition:
    opacity 0.45s ease,
    filter 0.45s ease,
    transform 0.35s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.35s ease,
    border-color 0.28s ease;
  animation: bh-card-enter 0.7s cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: calc(var(--card-i, 0) * 0.1s);
}

@keyframes bh-card-enter {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.blog-card--inview {
  border-color: color-mix(in srgb, #818cf8 42%, transparent) !important;
  box-shadow:
    0 0 0 1px rgba(255, 255, 255, 0.05) inset,
    0 20px 48px rgba(0, 0, 0, 0.28),
    0 0 40px rgba(99, 102, 241, 0.12) !important;
}

:global(html[data-theme='light']) .blog-card.blog-card--inview {
  border-color: rgba(99, 102, 241, 0.32) !important;
  box-shadow:
    0 1px 0 rgba(255, 255, 255, 1) inset,
    0 8px 22px rgba(15, 23, 42, 0.06),
    0 0 0 1px rgba(99, 102, 241, 0.14) !important;
}

.blog-card:hover {
  transform: translateY(-6px);
}

.blog-card__media {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
}

.blog-card__cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transform: scale(1);
  transition: transform 0.55s cubic-bezier(0.22, 1, 0.36, 1);
}

.blog-card:hover .blog-card__cover {
  transform: scale(1.05);
}

.blog-card__media-overlay {
  position: absolute;
  inset: 0;
  opacity: 0;
  background: linear-gradient(
    160deg,
    rgba(15, 23, 42, 0.05) 0%,
    rgba(79, 70, 229, 0.22) 45%,
    rgba(236, 72, 153, 0.28) 100%
  );
  transition: opacity 0.4s ease;
  pointer-events: none;
}

.blog-card:hover .blog-card__media-overlay {
  opacity: 1;
}

.blog-card__cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(145deg, rgba(79, 70, 229, 0.2), rgba(236, 72, 153, 0.14));
}

.blog-card__placeholder-ico {
  font-size: 42px;
  opacity: 0.55;
  color: var(--app-accent);
}

.blog-card__shine {
  position: absolute;
  inset: -40% -30%;
  background: linear-gradient(
    115deg,
    transparent 40%,
    rgba(255, 255, 255, 0.16) 48%,
    transparent 56%
  );
  transform: rotate(12deg);
  opacity: 0;
  transition: opacity 0.35s ease;
}

.blog-card:hover .blog-card__shine {
  opacity: 1;
}

.blog-card__body {
  padding: 18px 18px 20px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.blog-card__title {
  margin: 0;
  font-family: 'Syne', 'Plus Jakarta Sans', 'Noto Sans SC', var(--app-font-body), sans-serif;
  font-size: 17px;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.35;
  color: var(--app-text);
  transition: color 0.25s ease;
}

.blog-card__summary {
  margin: 0;
  font-family:
    system-ui,
    -apple-system,
    'Segoe UI',
    'Noto Sans SC',
    sans-serif;
  font-size: 13px;
  line-height: 1.58;
  color: var(--app-text-muted);
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.blog-card__foot {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.02em;
  color: var(--app-text-subtle);
  opacity: 0.72;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.blog-card__dot {
  opacity: 0.4;
}

.blog-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.blog-tag {
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.04em;
  padding: 4px 11px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--app-accent) 14%, transparent);
  color: var(--app-accent);
  border: 1px solid color-mix(in srgb, var(--app-accent) 28%, transparent);
  box-shadow: 0 0 14px color-mix(in srgb, var(--app-accent) 12%, transparent);
  transition:
    transform 0.2s ease,
    box-shadow 0.22s ease;
}

.blog-tag:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--app-accent) 22%, transparent);
}

.blog-card__stats {
  margin-top: auto;
  display: flex;
  gap: 18px;
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--app-text-subtle);
}

.blog-card__stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.blog-card__stat-ico {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  flex-shrink: 0;
  transition: transform 0.25s ease;
}

.blog-card:hover .blog-card__stat-ico {
  animation: bh-stat-pop 0.55s ease;
}

@keyframes bh-stat-pop {
  0% {
    transform: scale(1);
  }
  35% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

.blog-card__stat-ico--eye {
  background: radial-gradient(circle at 30% 30%, #bae6fd, #38bdf8 55%, #0ea5e9);
  box-shadow: 0 0 10px rgba(14, 165, 233, 0.35);
}

.blog-card__stat-ico--heart {
  background: radial-gradient(circle at 32% 28%, #fce7f3, #f472b6 55%, #db2777);
  box-shadow: 0 0 10px rgba(244, 114, 182, 0.35);
}

.blog-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18px;
  margin-top: 36px;
  color: var(--app-text-muted);
}

.pager-btn {
  border-radius: 12px;
}

.blog-pager__label {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

:global(html[data-theme='dark']) .blog-card:hover {
  box-shadow:
    0 28px 64px rgba(0, 0, 0, 0.48),
    0 0 0 1px rgba(129, 140, 248, 0.22);
}

:global(html[data-theme='light']) .blog-card.blog-card--dim:not(:hover) {
  opacity: 1;
  filter: none;
}

:global(html[data-theme='light']) .blog-card {
  animation: none;
}

@media (prefers-reduced-motion: reduce) {
  .blog-hero__grid,
  .sk-card,
  .blog-card,
  .blog-card__cover {
    animation: none !important;
  }

  .blog-card {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
  }
}
</style>
