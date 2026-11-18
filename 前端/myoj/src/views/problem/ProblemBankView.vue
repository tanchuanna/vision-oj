<script setup lang="ts">
import { computed, h, nextTick, onMounted, ref, watch } from 'vue'
import { Message, Modal } from '@arco-design/web-vue'
import type { QuestionQueryRequest } from '@generated'
import { OpenAPI, Service } from '@generated'
import { getBackendErrorMessage } from '@/api/httpError'
import { isResultSuccess } from '@/api/result'
import { mapApiError } from '@/api/mapApiError'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import {
  PROBLEM_DIFFICULTIES,
  PROBLEM_KNOWLEDGE_TAGS,
  PROBLEM_TYPE_OPTIONS,
  problemTypeLabel,
} from '@/config/problemTaxonomy'
import {
  beginProblemSharedTransition,
  endProblemSharedTransition,
} from '@/composables/problemSharedTransition'
import { formatSiteDate, localize as l } from '@/i18n/locale'

type ProblemRow = {
  id: string
  title: string
  questionType?: string
  tags?: string[]
  submitNum?: number
  acceptedNum?: number
  userNickname?: string
  updateTime?: string
}

type StudyRecommendation = {
  questionId: string
  title: string
  questionType: string
  difficulty?: string
  focusTag: string
  reason: string
}
type RecommendationTagMastery = {
  tag: string
  attempted: number
  solved: number
  mastery: number
  level: string
}
type StudyRecommendationPayload = {
  recommendations?: StudyRecommendation[]
  weakTags?: RecommendationTagMastery[]
  todaySolved?: number
  dailyGoal?: number
}

const loading = ref(false)
const router = useRouter()
const auth = useAuthStore()
const rows = ref<ProblemRow[]>([])
const recommendations = ref<StudyRecommendation[]>([])
const weakTags = ref<RecommendationTagMastery[]>([])
const todaySolved = ref(0)
const dailyGoal = ref(3)
const total = ref(0)
const current = ref(1)
const pageSize = ref(10)

const queryTitle = ref('')
const queryTags = ref('')
const selectedType = ref('')
const selectedDifficulty = ref('')
const selectedCategory = ref('')
const viewMode = ref<'table' | 'card'>('table')

const TYPE_OPTIONS = PROBLEM_TYPE_OPTIONS
const DIFFICULTY_OPTIONS = ['', ...PROBLEM_DIFFICULTIES]
const CATEGORY_OPTIONS = ['', ...PROBLEM_KNOWLEDGE_TAGS]

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const trainingProgress = computed(() =>
  Math.min(100, Math.round((todaySolved.value / Math.max(1, dailyGoal.value)) * 100)),
)

const englishTags: Record<string, string> = {
  全部: 'All',
  全部题型: 'All types',
  传统编程题: 'Programming',
  图像目标计数题: 'Object counting',
  图像分类题: 'Main object label',
  目标检测框题: 'Object detection',
  'OCR 文字识别题': 'OCR',
  图像属性分析题: 'Image analysis',
  入门: 'Beginner',
  简单: 'Easy',
  中等: 'Medium',
  困难: 'Hard',
  未分级: 'Unrated',
  基础语法: 'Basics',
  分支: 'Branching',
  循环: 'Loops',
  字符串: 'Strings',
  数组: 'Arrays',
  链表: 'Linked lists',
  栈: 'Stacks',
  队列: 'Queues',
  哈希表: 'Hash tables',
  排序: 'Sorting',
  查找: 'Searching',
  双指针: 'Two pointers',
  枚举: 'Enumeration',
  模拟: 'Simulation',
  贪心: 'Greedy',
  递归: 'Recursion',
  递推: 'Recurrence',
  数学: 'Math',
  二分: 'Binary search',
  树: 'Trees',
  图论: 'Graphs',
  搜索: 'Search',
  动态规划: 'Dynamic programming',
  并查集: 'Disjoint set',
  最短路: 'Shortest path',
  图像识别: 'Image recognition',
}
function displayTag(value: string) {
  return l(value, englishTags[value] || value)
}

function buildQueryPayload(): QuestionQueryRequest {
  const payload: QuestionQueryRequest = {
    current: current.value,
    pageSize: pageSize.value,
  }
  if (queryTitle.value.trim()) payload.title = queryTitle.value.trim()
  if (selectedType.value) payload.questionType = selectedType.value
  const tags = queryTags.value
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean)
  if (selectedDifficulty.value) tags.push(selectedDifficulty.value)
  if (selectedCategory.value) tags.push(selectedCategory.value)
  if (tags.length) payload.tags = [...new Set(tags)]
  return payload
}

async function fetchList() {
  loading.value = true
  try {
    const data = await Service.pageQuestions(buildQueryPayload())
    if (!isResultSuccess(data.code)) {
      Message.error(data.message || l('获取题库失败', 'Could not load problems'))
      return
    }
    const page = data.data
    const recs = page?.records ?? []
    rows.value = recs.map((r) => ({
      id: String(r.id ?? ''),
      title: r.title ?? '',
      questionType: r.questionType,
      tags: r.tags,
      submitNum: r.submitNum,
      acceptedNum: r.acceptedNum,
      userNickname: r.userNickname,
      updateTime: r.updateTime,
    }))
    total.value = page?.total ?? 0
    current.value = page?.current ?? current.value
    pageSize.value = page?.size ?? pageSize.value
  } catch (error) {
    Message.error(
      mapApiError(
        error,
        getBackendErrorMessage(error, l('获取题库失败', 'Could not load problems')),
      ),
    )
  } finally {
    loading.value = false
  }
}

async function loadRecommendations() {
  recommendations.value = []
  weakTags.value = []
  todaySolved.value = 0
  dailyGoal.value = 3
  if (!auth.isLoggedIn || !auth.userId) return
  const requestedUserId = auth.userId
  try {
    const token =
      typeof OpenAPI.TOKEN === 'function' ? await OpenAPI.TOKEN({} as never) : OpenAPI.TOKEN
    const response = await fetch(`${OpenAPI.BASE.replace(/\/+$/, '')}/question/recommend/my`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      cache: 'no-store',
    })
    const body = (await response.json()) as { code?: number; data?: StudyRecommendationPayload }
    if (auth.userId === requestedUserId && response.ok && isResultSuccess(body.code)) {
      recommendations.value = Array.isArray(body.data?.recommendations)
        ? body.data.recommendations
        : []
      weakTags.value = Array.isArray(body.data?.weakTags) ? body.data.weakTags : []
      todaySolved.value = typeof body.data?.todaySolved === 'number' ? body.data.todaySolved : 0
      dailyGoal.value =
        typeof body.data?.dailyGoal === 'number' && body.data.dailyGoal > 0
          ? body.data.dailyGoal
          : 3
    }
  } catch {
    // 推荐服务不可用时保留正常题库，避免把个性化功能变成做题入口的故障点。
  }
}

function onSearch() {
  if (loading.value) return
  current.value = 1
  fetchList()
}

function onReset() {
  if (loading.value) return
  queryTitle.value = ''
  queryTags.value = ''
  selectedType.value = ''
  selectedDifficulty.value = ''
  selectedCategory.value = ''
  current.value = 1
  fetchList()
}

function onPrevPage() {
  if (current.value <= 1) return
  current.value -= 1
  fetchList()
}

function onNextPage() {
  if (current.value >= totalPages.value) return
  current.value += 1
  fetchList()
}

function formatDateTime(raw?: string): string {
  if (!raw) return '-'
  const d = new Date(raw)
  if (Number.isNaN(d.getTime())) return raw
  return formatSiteDate(d, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).replace(/\//g, '-')
}

function getPassRate(item: ProblemRow): string {
  const submit = item.submitNum ?? 0
  const accepted = item.acceptedNum ?? 0
  if (submit <= 0) return '0%'
  return `${Math.round((accepted / submit) * 100)}%`
}

function getPassRateValue(item: ProblemRow): number {
  const submit = item.submitNum ?? 0
  const accepted = item.acceptedNum ?? 0
  if (submit <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((accepted / submit) * 100)))
}

function selectQuickFilter(group: 'type' | 'difficulty' | 'category', value: string) {
  if (group === 'type') selectedType.value = value
  if (group === 'difficulty') selectedDifficulty.value = value
  if (group === 'category') selectedCategory.value = value
  current.value = 1
  void fetchList()
}

function difficultyOf(item: ProblemRow): string {
  return displayTag((item.tags || []).find((tag) => DIFFICULTY_OPTIONS.includes(tag)) || '未分级')
}

function goSolve(item: ProblemRow, event?: MouseEvent) {
  if (!auth.isLoggedIn) {
    Modal.confirm({
      title: l('登录后开始做题', 'Sign in to solve problems'),
      modalClass: 'solve-login-modal',
      content: () =>
        h('div', { class: 'solve-login-modal__content' }, [
          h('div', { class: 'solve-login-modal__icon', 'aria-hidden': 'true' }, '✨'),
          h('div', { class: 'solve-login-modal__text' }, [
            h(
              'p',
              { class: 'solve-login-modal__lead' },
              l('登录后可解锁完整作答体验', 'Sign in to save and submit your solutions.'),
            ),
            h(
              'p',
              { class: 'solve-login-modal__desc' },
              l(
                '支持提交记录保存、进度同步与后续成绩追踪。',
                'Your submissions and progress stay with your account.',
              ),
            ),
          ]),
        ]),
      okText: l('去登录', 'Sign in'),
      cancelText: l('稍后再说', 'Later'),
      okButtonProps: { status: 'success' },
      onOk: () => {
        router.push({ path: '/', query: { openAuth: '1' } })
      },
    })
    return
  }
  const destination = { name: 'problem-solve', params: { id: item.id } }
  const source = (event?.currentTarget as HTMLElement | null)
    ?.closest('tr, .problem-card')
    ?.querySelector<HTMLElement>('.problem-list-title')
  if (
    !source ||
    window.matchMedia('(prefers-reduced-motion: reduce)').matches ||
    !document.startViewTransition
  ) {
    void router.push(destination)
    return
  }
  source.style.viewTransitionName = 'selected-problem-title'
  beginProblemSharedTransition(item.id, item.title)
  try {
    const transition = document.startViewTransition(async () => {
      await router.push(destination)
      await nextTick()
    })
    const cleanup = () => {
      source.style.viewTransitionName = ''
      endProblemSharedTransition()
    }
    void transition.finished.then(cleanup, cleanup)
  } catch {
    source.style.viewTransitionName = ''
    endProblemSharedTransition()
    void router.push(destination)
  }
}

onMounted(() => {
  void fetchList()
  void loadRecommendations()
})
watch(
  () => auth.userId,
  () => {
    void loadRecommendations()
  },
)
</script>

<template>
  <div class="problem-page">
    <section class="hero panel">
      <div>
        <h1>{{ l('题库', 'Problems') }}</h1>
        <p class="lead">
          {{ l('按题型、难度和知识点查找题目。', 'Find problems by type, difficulty and topic.') }}
        </p>
      </div>
      <div class="hero-meta">
        <span class="meta-chip">{{ l('总题数', 'Total') }} {{ total }}</span>
        <span class="meta-chip"
          >{{ l('当前第', 'Page') }} {{ current }} / {{ totalPages }} {{ l('页', '') }}</span
        >
      </div>
    </section>

    <section
      v-if="recommendations.length"
      class="recommend-panel panel"
      :aria-label="l('个性化练习推荐', 'Recommended practice')"
    >
      <div class="recommend-panel__heading">
        <div>
          <span class="eyebrow">{{ l('为你推荐', 'Recommended') }}</span>
          <h2>{{ l('今日训练', "Today's practice") }}</h2>
        </div>
        <div class="recommend-progress">
          <span class="recommend-progress__text">
            {{ l('今日已通过', 'Solved today') }} <strong>{{ todaySolved }}</strong> /
            {{ dailyGoal }} {{ l('题', '') }}
          </span>
          <span class="recommend-progress__bar" aria-hidden="true">
            <i :style="{ width: `${trainingProgress}%` }" />
          </span>
        </div>
      </div>
      <div v-if="weakTags.length" class="recommend-tags">
        <span class="recommend-tags__label">{{ l('需要加强', 'Review next') }}</span>
        <span
          v-for="tag in weakTags"
          :key="tag.tag"
          class="recommend-chip"
          :class="{ 'is-weak': tag.mastery < 40 }"
          :title="
            l(
              `已尝试 ${tag.attempted} 题，通过 ${tag.solved} 题`,
              `${tag.attempted} attempted, ${tag.solved} solved`,
            )
          "
        >
          {{ displayTag(tag.tag) }} · {{ tag.mastery }}%
        </span>
      </div>
      <div class="recommend-panel__grid">
        <button
          v-for="item in recommendations"
          :key="item.questionId"
          type="button"
          class="recommend-card"
          @click="
            goSolve({ id: item.questionId, title: item.title, questionType: item.questionType })
          "
        >
          <span class="recommend-card__meta">
            <span class="recommend-card__tag">{{
              displayTag(item.focusTag || problemTypeLabel(item.questionType))
            }}</span>
            <span v-if="item.difficulty" class="recommend-card__difficulty">{{
              displayTag(item.difficulty)
            }}</span>
          </span>
          <strong>{{ item.title }}</strong>
          <small>{{ item.reason }}</small>
          <span class="recommend-card__action">{{ l('去练习', 'Practice') }} →</span>
        </button>
      </div>
    </section>

    <section class="panel table-shell">
      <div class="filter-intro">
        <div>
          <h2>{{ l('查找题目', 'Find problems') }}</h2>
          <p>
            {{
              l(
                '可以输入名称，或按题型、难度和知识点缩小范围。',
                'Search by title or narrow results by type, difficulty and topic.',
              )
            }}
          </p>
        </div>
        <span>{{ total }} {{ l('道题', 'problems') }}</span>
      </div>
      <div class="view-switch">
        <a-button
          size="small"
          class="switch-btn"
          :type="viewMode === 'table' ? 'primary' : 'secondary'"
          @click="viewMode = 'table'"
        >
          {{ l('表格视图', 'Table') }}
        </a-button>
        <a-button
          size="small"
          class="switch-btn"
          :type="viewMode === 'card' ? 'primary' : 'secondary'"
          @click="viewMode = 'card'"
        >
          {{ l('卡片视图', 'Cards') }}
        </a-button>
      </div>

      <div class="filter-row">
        <a-input v-model="queryTitle" allow-clear :placeholder="l('按标题搜索', 'Search titles')" />
        <a-input
          v-model="queryTags"
          allow-clear
          :placeholder="
            l('标签，逗号分隔，如：数组,图论', 'Tags, comma separated (e.g. 数组,图论)')
          "
        />
        <a-button
          type="primary"
          class="query-btn query-btn--primary"
          :loading="loading"
          :disabled="loading"
          @click="onSearch"
          >{{ l('查询', 'Search') }}</a-button
        >
        <a-button class="query-btn query-btn--ghost" :disabled="loading" @click="onReset">{{
          l('重置', 'Reset')
        }}</a-button>
      </div>

      <div class="category-filter">
        <div class="category-filter__row">
          <span class="category-filter__label">{{ l('题型', 'Type') }}</span>
          <button
            v-for="option in TYPE_OPTIONS"
            :key="option.value || 'all-type'"
            type="button"
            class="category-chip"
            :class="{ 'category-chip--active': selectedType === option.value }"
            @click="selectQuickFilter('type', option.value)"
          >
            {{ displayTag(option.label) }}
          </button>
        </div>
        <div class="category-filter__row">
          <span class="category-filter__label">{{ l('难度', 'Difficulty') }}</span>
          <button
            v-for="option in DIFFICULTY_OPTIONS"
            :key="option || 'all-difficulty'"
            type="button"
            class="category-chip"
            :class="{ 'category-chip--active': selectedDifficulty === option }"
            @click="selectQuickFilter('difficulty', option)"
          >
            {{ displayTag(option || '全部') }}
          </button>
        </div>
        <div class="category-filter__row">
          <span class="category-filter__label">{{ l('知识点', 'Topic') }}</span>
          <button
            v-for="option in CATEGORY_OPTIONS"
            :key="option || 'all-category'"
            type="button"
            class="category-chip"
            :class="{ 'category-chip--active': selectedCategory === option }"
            @click="selectQuickFilter('category', option)"
          >
            {{ displayTag(option || '全部') }}
          </button>
        </div>
      </div>

      <div v-if="viewMode === 'table'" class="table-wrap">
        <table class="problem-table">
          <thead>
            <tr>
              <th>{{ l('题目', 'Problem') }}</th>
              <th>{{ l('标签', 'Tags') }}</th>
              <th>{{ l('出题人', 'Author') }}</th>
              <th>{{ l('通过 / 提交', 'Solved / Submissions') }}</th>
              <th>{{ l('通过率', 'Acceptance') }}</th>
              <th>{{ l('最近更新', 'Updated') }}</th>
              <th>{{ l('操作', 'Action') }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading && rows.length === 0">
              <td colspan="7" class="empty-cell">{{ l('加载中...', 'Loading...') }}</td>
            </tr>
            <tr v-else-if="rows.length === 0">
              <td colspan="7" class="empty-cell">
                <div class="problem-empty">
                  <strong>{{ l('没有找到符合条件的题目', 'No matching problems') }}</strong
                  ><span>{{
                    l('换个关键词或清除筛选后再试。', 'Try another keyword or clear filters.')
                  }}</span
                  ><button type="button" @click="onReset">
                    {{ l('清除筛选', 'Clear filters') }}
                  </button>
                </div>
              </td>
            </tr>
            <tr v-for="item in rows" :key="item.id">
              <td class="title-cell">
                <span
                  class="problem-type-badge"
                  :class="{ 'problem-type-badge--vision': item.questionType?.startsWith('IMAGE_') }"
                >
                  {{ displayTag(problemTypeLabel(item.questionType)) }}
                </span>
                <span class="problem-list-title">{{ item.title }}</span>
                <span class="difficulty-badge">{{ difficultyOf(item) }}</span>
              </td>
              <td class="tag-cell">
                <template v-if="(item.tags || []).length">
                  <span v-for="tag in item.tags" :key="`${item.id}-${tag}`" class="tag-pill">{{
                    displayTag(tag)
                  }}</span>
                </template>
                <span v-else class="muted">-</span>
              </td>
              <td>{{ item.userNickname || '-' }}</td>
              <td>{{ item.acceptedNum ?? 0 }} / {{ item.submitNum ?? 0 }}</td>
              <td>
                <div class="rate-cell">
                  <span class="rate-text">{{ getPassRate(item) }}</span>
                  <div class="rate-track">
                    <span class="rate-fill" :style="{ width: `${getPassRateValue(item)}%` }"></span>
                  </div>
                </div>
              </td>
              <td class="muted">{{ formatDateTime(item.updateTime) }}</td>
              <td>
                <a-button
                  type="primary"
                  size="mini"
                  class="solve-btn"
                  @click="goSolve(item, $event)"
                  >{{ l('去做题', 'Solve') }}</a-button
                >
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else class="card-wrap">
        <div v-if="loading" class="empty-cell">{{ l('加载中...', 'Loading...') }}</div>
        <div v-else-if="rows.length === 0" class="empty-cell">
          <div class="problem-empty">
            <strong>{{ l('没有找到符合条件的题目', 'No matching problems') }}</strong
            ><span>{{
              l('换个关键词或清除筛选后再试。', 'Try another keyword or clear filters.')
            }}</span
            ><button type="button" @click="onReset">{{ l('清除筛选', 'Clear filters') }}</button>
          </div>
        </div>
        <article v-for="item in rows" :key="`card-${item.id}`" class="problem-card">
          <h3>
            <span
              class="problem-type-badge"
              :class="{ 'problem-type-badge--vision': item.questionType?.startsWith('IMAGE_') }"
              >{{ displayTag(problemTypeLabel(item.questionType)) }}</span
            >
            <span class="problem-list-title">{{ item.title }}</span>
          </h3>
          <p class="card-meta">{{ l('出题人：', 'Author: ') }}{{ item.userNickname || '-' }}</p>
          <div class="tag-cell">
            <template v-if="(item.tags || []).length">
              <span v-for="tag in item.tags" :key="`card-${item.id}-${tag}`" class="tag-pill">{{
                displayTag(tag)
              }}</span>
            </template>
            <span v-else class="muted">{{ l('暂无标签', 'No tags') }}</span>
          </div>
          <div class="card-stats">
            <span>{{ item.acceptedNum ?? 0 }} / {{ item.submitNum ?? 0 }}</span>
            <span>{{ getPassRate(item) }}</span>
          </div>
          <div class="rate-track">
            <span class="rate-fill" :style="{ width: `${getPassRateValue(item)}%` }"></span>
          </div>
          <p class="muted card-time">
            {{ l('更新于', 'Updated') }} {{ formatDateTime(item.updateTime) }}
          </p>
          <a-button
            type="primary"
            class="solve-btn card-solve-btn"
            @click="goSolve(item, $event)"
            >{{ l('去做题', 'Solve') }}</a-button
          >
        </article>
      </div>

      <div class="pager-row">
        <a-button :disabled="current <= 1 || loading" @click="onPrevPage">{{
          l('上一页', 'Previous')
        }}</a-button>
        <span>{{ l('第', 'Page') }} {{ current }} / {{ totalPages }} {{ l('页', '') }}</span>
        <a-button :disabled="current >= totalPages || loading" @click="onNextPage">{{
          l('下一页', 'Next')
        }}</a-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.problem-page {
  position: relative;
  isolation: isolate;
  width: min(1620px, calc(100vw - 30px));
  margin: 0 auto;
  padding: 28px 12px 52px;
}

.panel {
  border: 1px solid var(--app-border);
  background: linear-gradient(
    145deg,
    color-mix(in srgb, var(--app-surface) 94%, #ffffff 6%),
    var(--app-surface)
  );
  box-shadow:
    0 14px 36px color-mix(in srgb, var(--app-accent) 8%, transparent),
    var(--app-card-shadow);
  border-radius: 24px;
}

.hero {
  position: relative;
  overflow: hidden;
  padding: 8px 4px 14px;
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: end;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  animation: bank-enter 0.38s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}

.hero > * {
  position: relative;
}
.recommend-panel {
  animation: bank-enter 0.38s 0.08s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
.table-shell {
  animation: bank-enter 0.38s 0.13s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
@keyframes bank-enter {
  from {
    opacity: 0.45;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.eyebrow {
  margin: 0 0 10px;
  color: var(--app-accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(30px, 3vw, 40px);
  line-height: 1.06;
  font-family: var(--app-font-display);
}

.lead {
  margin: 8px 0 0;
  color: var(--app-text-muted);
  max-width: 760px;
  line-height: 1.8;
}

.hero-meta {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.meta-chip {
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 6px 12px;
  color: var(--app-text-subtle);
  font-size: 12px;
  background: color-mix(in srgb, var(--app-surface) 84%, #ffffff 16%);
}

.table-shell {
  margin-top: 18px;
  padding: 20px;
}
.recommend-panel {
  margin-top: 18px;
  padding: 22px 26px;
}
.recommend-panel__heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.recommend-panel__heading h2 {
  margin: 0;
  font-size: 20px;
  color: var(--app-text);
}
.recommend-panel__heading .eyebrow {
  display: block;
  margin-bottom: 5px;
}
.recommend-progress {
  display: flex;
  flex-direction: column;
  gap: 7px;
  min-width: 200px;
}
.recommend-progress__text {
  font-size: 12px;
  color: var(--app-text-muted);
}
.recommend-progress__text strong {
  color: var(--app-accent);
  font-size: 14px;
}
.recommend-progress__bar {
  height: 6px;
  border-radius: 999px;
  overflow: hidden;
  background: color-mix(in srgb, var(--app-text) 9%, transparent);
}
.recommend-progress__bar i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    var(--app-accent),
    color-mix(in srgb, var(--app-accent) 55%, #f59e0b)
  );
  transition: width 0.4s ease;
}
.recommend-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: 2px 0 14px;
}
.recommend-tags__label {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.06em;
  color: var(--app-text-muted);
  text-transform: uppercase;
}
.recommend-chip {
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 12%, transparent);
  border: 1px solid color-mix(in srgb, var(--app-accent) 26%, transparent);
}
.recommend-chip.is-weak {
  color: #c2410c;
  background: rgba(249, 115, 22, 0.1);
  border-color: rgba(249, 115, 22, 0.26);
}
.recommend-panel__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.recommend-card {
  display: flex;
  flex-direction: column;
  align-items: start;
  gap: 8px;
  min-height: 140px;
  text-align: left;
  cursor: pointer;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  padding: 15px;
  background: color-mix(in srgb, var(--app-surface) 90%, var(--app-accent) 10%);
  color: var(--app-text);
}
.recommend-card:hover {
  border-color: var(--app-accent);
  transform: translateY(-2px);
}
.recommend-card strong {
  font-size: 15px;
}
.recommend-card small {
  color: var(--app-text-muted);
  line-height: 1.5;
}
.recommend-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}
.recommend-card__tag {
  color: var(--app-accent);
  font-size: 11px;
  font-weight: 800;
}
.recommend-card__difficulty {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: var(--app-text-muted);
  background: color-mix(in srgb, var(--app-text) 8%, transparent);
}
.recommend-card__action {
  margin-top: auto;
  color: var(--app-accent);
  font-size: 12px;
  font-weight: 700;
}
@media (max-width: 800px) {
  .recommend-panel__grid {
    grid-template-columns: 1fr;
  }
  .recommend-panel__heading {
    align-items: start;
    flex-direction: column;
  }
}

.view-switch {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-bottom: 10px;
}

.switch-btn {
  min-height: 32px;
  border-radius: 999px;
}

.filter-row {
  display: grid;
  grid-template-columns: minmax(260px, 1.3fr) minmax(280px, 1.5fr) auto auto;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  padding: 12px;
  background: color-mix(in srgb, var(--app-surface) 88%, #ffffff 12%);
}

.category-filter {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  background: color-mix(in srgb, var(--app-surface) 92%, #ecfeff 8%);
}

.category-filter__row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.category-filter__label {
  width: 58px;
  flex: 0 0 58px;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 800;
}

.category-chip {
  padding: 5px 12px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  background: var(--app-surface);
  color: var(--app-text-subtle);
  cursor: pointer;
  transition: 0.18s ease;
}

.category-chip:hover,
.category-chip--active {
  border-color: #14b8a6;
  background: color-mix(in srgb, #14b8a6 13%, var(--app-surface));
  color: #0f766e;
}

.table-wrap {
  overflow-x: auto;
  border: 1px solid var(--app-border);
  border-radius: 20px;
}

.card-wrap {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.problem-card {
  border: 1px solid var(--app-border);
  border-radius: 16px;
  padding: 14px;
  background: color-mix(in srgb, var(--app-surface) 88%, #ffffff 12%);
  transition:
    transform 0.2s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease;
}

.problem-card:hover {
  transform: translateY(-2px);
  border-color: color-mix(in srgb, var(--app-accent) 28%, var(--app-border));
  box-shadow: 0 12px 26px color-mix(in srgb, var(--app-accent) 12%, transparent);
}

.problem-card h3 {
  margin: 0;
  font-size: 16px;
}

.card-meta {
  margin: 8px 0 0;
  color: var(--app-text-subtle);
  font-size: 12px;
}

.card-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  font-weight: 700;
}

.card-time {
  margin: 10px 0 0;
  font-size: 12px;
}

.problem-table {
  width: 100%;
  min-width: 980px;
  border-collapse: collapse;
}

.problem-table th,
.problem-table td {
  padding: 14px 16px;
  border-bottom: 1px solid var(--app-border);
  text-align: left;
}

.problem-table th {
  position: sticky;
  top: 0;
  z-index: 2;
  background: color-mix(in srgb, var(--app-surface) 76%, #ffffff 24%);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  color: var(--app-text-subtle);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.title-cell {
  font-weight: 700;
  color: var(--app-text-main);
}

.problem-type-badge,
.difficulty-badge {
  display: inline-flex;
  align-items: center;
  margin-right: 6px;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eef2ff;
  color: #4338ca;
  font-size: 11px;
  font-weight: 800;
  vertical-align: middle;
}

.problem-type-badge--vision {
  background: #ccfbf1;
  color: #0f766e;
}

.difficulty-badge {
  margin-right: 0;
  margin-left: 5px;
  background: #fef3c7;
  color: #92400e;
}

.muted {
  color: var(--app-text-muted);
}

.tag-cell {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}

.tag-pill {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid color-mix(in srgb, var(--app-border) 82%, #bfdbfe);
  background: color-mix(in srgb, var(--app-surface) 84%, #dbeafe 16%);
  color: var(--app-text-subtle);
  font-size: 12px;
  white-space: nowrap;
}

.rate-cell {
  min-width: 110px;
}

.rate-text {
  display: inline-block;
  min-width: 40px;
  font-weight: 700;
  color: var(--app-text-main);
}

.rate-track {
  margin-top: 6px;
  width: 100%;
  height: 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--app-border) 74%, #cbd5e1);
  overflow: hidden;
}

.rate-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #22c55e, #14b8a6);
}

.empty-cell {
  text-align: center !important;
  color: var(--app-text-subtle);
  padding: 24px !important;
}

.pager-row {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  color: var(--app-text-subtle);
}

.query-btn {
  min-height: 40px;
  border-radius: 12px;
}

.query-btn--primary {
  min-width: 88px;
  box-shadow: 0 8px 18px color-mix(in srgb, var(--app-accent) 18%, transparent);
}

.query-btn--ghost {
  min-width: 80px;
  border: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface) 86%, #ffffff 14%);
  color: var(--app-text-main);
}

.solve-btn {
  min-width: 76px;
  border-radius: 10px;
}

.card-solve-btn {
  margin-top: 12px;
}

:deep(.view-switch .arco-btn) {
  border-radius: 999px;
  min-height: 34px;
  padding-inline: 14px;
  border: 1px solid var(--app-border);
}

:deep(.view-switch .arco-btn-secondary) {
  background: color-mix(in srgb, var(--app-surface) 86%, #ffffff 14%);
  color: var(--app-text-main);
}

:deep(.view-switch .arco-btn-primary) {
  background: linear-gradient(135deg, color-mix(in srgb, var(--app-accent) 76%, #2563eb), #1d4ed8);
  border-color: color-mix(in srgb, var(--app-accent) 60%, #60a5fa);
  box-shadow: 0 8px 18px color-mix(in srgb, var(--app-accent) 18%, transparent);
}

:deep(.pager-row .arco-btn) {
  min-height: 38px;
  border-radius: 10px;
  border: 1px solid var(--app-border);
  background: color-mix(in srgb, var(--app-surface) 86%, #ffffff 14%);
  color: var(--app-text-main);
}

:deep(.pager-row .arco-btn:disabled) {
  opacity: 0.46;
}

@media (prefers-color-scheme: dark) {
  :deep(.view-switch .arco-btn-secondary) {
    border-color: rgba(148, 163, 184, 0.24);
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.72), rgba(15, 23, 42, 0.74));
    color: #cbd5e1;
  }

  :deep(.view-switch .arco-btn-primary) {
    border-color: rgba(96, 165, 250, 0.48);
    background: linear-gradient(135deg, rgba(37, 99, 235, 0.96), rgba(29, 78, 216, 0.96));
    color: #eff6ff;
    box-shadow:
      0 10px 20px rgba(37, 99, 235, 0.24),
      inset 0 1px 0 rgba(255, 255, 255, 0.22);
  }

  .query-btn--ghost {
    border-color: rgba(148, 163, 184, 0.26);
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.74), rgba(15, 23, 42, 0.74));
    color: #cbd5e1;
  }

  :deep(.pager-row .arco-btn) {
    border-color: rgba(148, 163, 184, 0.22);
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.74), rgba(15, 23, 42, 0.74));
    color: #e2e8f0;
  }

  :deep(.pager-row .arco-btn:not(:disabled):hover),
  :deep(.view-switch .arco-btn:not(:disabled):hover),
  .query-btn--ghost:not(:disabled):hover {
    border-color: rgba(96, 165, 250, 0.42);
    transform: translateY(-1px);
  }

  :deep(.arco-input-wrapper) {
    border-color: rgba(148, 163, 184, 0.26);
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.74), rgba(15, 23, 42, 0.76));
  }

  :deep(.arco-input) {
    color: #e5e7eb;
  }

  :deep(.arco-input::placeholder) {
    color: #94a3b8;
  }

  :deep(.arco-input-wrapper:hover) {
    border-color: rgba(96, 165, 250, 0.4);
  }

  :deep(.arco-input-wrapper:focus-within) {
    border-color: rgba(96, 165, 250, 0.62);
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.22);
  }

  .problem-table th {
    background: linear-gradient(180deg, rgba(51, 65, 85, 0.8), rgba(30, 41, 59, 0.82));
    color: #cbd5e1;
  }

  .rate-track {
    background: rgba(100, 116, 139, 0.35);
  }
}

:deep(.arco-input-wrapper) {
  border-radius: 12px;
  min-height: 40px;
  background: color-mix(in srgb, var(--app-surface) 86%, #ffffff 14%);
  border-color: color-mix(in srgb, var(--app-border) 78%, #94a3b8);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    background 0.2s ease;
}

:deep(.arco-input) {
  color: var(--app-text-main);
  font-weight: 500;
}

:deep(.arco-input::placeholder) {
  color: color-mix(in srgb, var(--app-text-muted) 78%, #94a3b8);
}

:deep(.arco-input-wrapper:hover) {
  border-color: color-mix(in srgb, var(--app-accent) 38%, var(--app-border));
}

:deep(.arco-input-wrapper:focus-within) {
  border-color: color-mix(in srgb, var(--app-accent) 70%, #60a5fa);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent) 18%, transparent);
}

:deep(.arco-input-clear-btn) {
  color: color-mix(in srgb, var(--app-text-muted) 70%, #94a3b8);
}

:deep(.arco-input-clear-btn:hover) {
  color: var(--app-text-main);
}

.problem-table tbody tr {
  transition:
    background 0.2s ease,
    transform 0.2s ease;
}

.problem-table tbody tr:hover {
  background: var(--app-hover);
  transform: translateY(-1px);
}

@media (max-width: 900px) {
  .hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-row {
    grid-template-columns: 1fr 1fr;
  }

  .card-wrap {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 640px) {
  .problem-page {
    width: 100%;
    padding: 20px 12px 42px;
  }

  .filter-row {
    grid-template-columns: 1fr;
  }

  .pager-row {
    justify-content: center;
  }

  .card-wrap {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero,
  .recommend-panel,
  .table-shell {
    animation: none !important;
  }
  .problem-table tbody tr {
    animation: none !important;
    transition: none !important;
    transform: none !important;
  }
}

/* 放在样式末尾，确保暗色输入框可读性覆盖生效 */
@media (prefers-color-scheme: dark) {
  :deep(.filter-row .arco-input-wrapper) {
    border-color: rgba(148, 163, 184, 0.32) !important;
    background: linear-gradient(135deg, rgba(30, 41, 59, 0.9), rgba(15, 23, 42, 0.9)) !important;
  }

  :deep(.filter-row .arco-input) {
    color: #f1f5f9 !important;
    font-weight: 600;
  }

  :deep(.filter-row .arco-input::placeholder) {
    color: #cbd5e1 !important;
    opacity: 0.92;
  }

  :deep(.filter-row .arco-input-wrapper:hover) {
    border-color: rgba(96, 165, 250, 0.52) !important;
  }

  :deep(.filter-row .arco-input-wrapper:focus-within) {
    border-color: rgba(96, 165, 250, 0.72) !important;
    box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.24) !important;
  }
}

:global(.solve-login-modal__content) {
  display: flex;
  gap: 14px;
  align-items: flex-start;
}

:global(.solve-login-modal) {
  border-radius: 18px !important;
  overflow: hidden;
  animation: solveModalIn 0.32s cubic-bezier(0.2, 0.9, 0.2, 1);
  border: 1px solid rgba(226, 232, 240, 0.95);
  background: #ffffff;
  box-shadow:
    0 24px 56px rgba(15, 23, 42, 0.18),
    0 10px 20px rgba(15, 23, 42, 0.08);
}

:global(.solve-login-modal .arco-modal-header) {
  border-bottom: 1px solid #eef2f7;
  background: #ffffff;
  padding: 16px 20px 14px;
}

:global(.solve-login-modal .arco-modal-title) {
  color: #0f172a;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.01em;
}

:global(.solve-login-modal .arco-modal-body) {
  padding: 16px 20px 14px;
}

:global(.solve-login-modal .arco-modal-footer) {
  border-top: 1px solid #eef2f7;
  background: #ffffff;
  padding: 14px 20px 18px;
}

:global(.solve-login-modal__icon) {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #dbeafe, #ccfbf1);
  font-size: 18px;
  flex-shrink: 0;
  animation: iconFloat 1.8s ease-in-out infinite;
}

:global(.solve-login-modal__lead) {
  margin: 0;
  color: #0f172a;
  font-size: 15px;
  font-weight: 700;
}

:global(.solve-login-modal__desc) {
  margin: 6px 0 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

:global(.solve-login-modal .arco-btn) {
  min-width: 98px;
  min-height: 38px;
  border-radius: 10px;
  font-weight: 600;
  box-shadow: none !important;
}

:global(.solve-login-modal .arco-btn-secondary) {
  background: #f8fafc !important;
  border: 1px solid #dbe3ee !important;
  color: #334155 !important;
}

:global(.solve-login-modal .arco-btn-secondary:hover) {
  background: #f1f5f9 !important;
  border-color: #cbd5e1 !important;
  color: #1e293b !important;
}

:global(.solve-login-modal .arco-btn-status-success) {
  border: 1px solid #16a34a !important;
  background: linear-gradient(135deg, #22c55e, #16a34a) !important;
  color: #ffffff !important;
  box-shadow: 0 8px 18px rgba(22, 163, 74, 0.25) !important;
}

:global(.solve-login-modal .arco-btn-status-success:hover) {
  filter: brightness(1.03);
  transform: translateY(-1px);
}

:global(.arco-modal-mask) {
  backdrop-filter: blur(8px) saturate(114%);
  -webkit-backdrop-filter: blur(8px) saturate(114%);
  background: rgba(15, 23, 42, 0.36) !important;
  animation: maskFadeIn 0.34s ease-out;
}

:global(.arco-modal-mask::before) {
  content: '';
  position: absolute;
  inset: -18%;
  pointer-events: none;
  background:
    radial-gradient(circle at 18% 22%, rgba(59, 130, 246, 0.2), transparent 46%),
    radial-gradient(circle at 78% 76%, rgba(16, 185, 129, 0.16), transparent 44%),
    radial-gradient(circle at 52% 46%, rgba(147, 197, 253, 0.14), transparent 40%);
  animation: maskGlowDrift 9s ease-in-out infinite alternate;
}

@keyframes solveModalIn {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes iconFloat {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

@keyframes maskFadeIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes maskGlowDrift {
  0% {
    transform: translate3d(-1.5%, -1.2%, 0) scale(1);
  }
  50% {
    transform: translate3d(1.8%, 1.6%, 0) scale(1.04);
  }
  100% {
    transform: translate3d(1.2%, -0.8%, 0) scale(1.02);
  }
}
.filter-intro {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 16px;
  margin: 0 0 18px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--app-border);
}
.filter-intro h2 {
  margin: 0 0 4px;
  color: var(--app-text);
  font-size: 20px;
}
.filter-intro p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}
.filter-intro > span {
  flex: none;
  color: var(--app-accent);
  font-size: 13px;
  font-weight: 700;
}
.problem-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 9px;
  padding: 36px 10px;
  color: var(--app-text);
  text-align: center;
}
.problem-empty strong {
  font-size: 17px;
}
.problem-empty span {
  color: var(--app-text-muted);
  font-size: 13px;
}
.problem-empty button {
  min-height: 40px;
  margin-top: 6px;
  padding: 0 18px;
  border: 1px solid var(--app-accent);
  border-radius: 10px;
  background: var(--app-surface);
  color: var(--app-accent);
  font-weight: 700;
  cursor: pointer;
}
.problem-empty button:hover {
  background: color-mix(in srgb, var(--app-accent) 10%, var(--app-surface));
}
@media (max-width: 640px) {
  .filter-intro {
    align-items: start;
    flex-direction: column;
  }
}
</style>
