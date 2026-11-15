<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useRoute, useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { useAuthStore } from '@/stores/auth'
import {
  getSubmissionDetail,
  getSubmissionOverview,
  getSubmissionPage,
  type SubmissionDetail,
  type SubmissionFilters,
  type SubmissionListItem,
  type SubmissionOverview,
  type SubmissionScope,
} from '@/api/submissionDashboard'

use([
  CanvasRenderer,
  LineChart,
  PieChart,
  BarChart,
  GridComponent,
  LegendComponent,
  TooltipComponent,
])

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const overviewLoading = ref(false)
const detailLoading = ref(false)
const rows = ref<SubmissionListItem[]>([])
const overview = ref<SubmissionOverview | null>(null)
const detail = ref<SubmissionDetail | null>(null)
const total = ref(0)
const current = ref(1)
const pageSize = ref(15)
const rangeDays = ref<7 | 30 | 90>(30)
const scope = ref<SubmissionScope>('SELF')
const selectedUserId = ref<string>('')
const questionKeyword = ref('')
const userKeyword = ref('')
const verdict = ref('')
const language = ref('')
const startDate = ref('')
const endDate = ref('')
const sort = ref<'ASC' | 'DESC'>('DESC')
const filterOpen = ref(false)
const detailOpen = ref(false)
const detailFullscreen = ref(false)
const highlightedIds = ref(new Set<string>())
const liveNow = ref(Date.now())
const lastSyncAt = ref(Date.now())
const previousStatus = new Map<string, number | undefined>()
let refreshTimer: number | undefined
let clockTimer: number | undefined
let refreshTicks = 0

const verdictMeta: Record<string, { label: string; abbr: string; tone: string; icon: string }> = {
  Accepted: { label: '通过', abbr: 'AC', tone: 'success', icon: '✓' },
  'Wrong Answer': { label: '答案错误', abbr: 'WA', tone: 'danger', icon: '×' },
  'Compile Error': { label: '编译错误', abbr: 'CE', tone: 'danger', icon: '!' },
  'Runtime Error': { label: '运行错误', abbr: 'RE', tone: 'danger', icon: '!' },
  'Time Limit Exceeded': { label: '运行超时', abbr: 'TLE', tone: 'warning', icon: '⌛' },
  'Memory Limit Exceeded': { label: '内存超限', abbr: 'MLE', tone: 'warning', icon: '◇' },
  'Output Limit Exceeded': { label: '输出超限', abbr: 'OLE', tone: 'warning', icon: '!' },
  'Presentation Error': { label: '格式错误', abbr: 'PE', tone: 'warning', icon: '!' },
  'System Error': { label: '系统异常', abbr: 'SE', tone: 'neutral', icon: '!' },
  Pending: { label: '等待评测', abbr: 'WJ', tone: 'pending', icon: '…' },
  Judging: { label: '评测中', abbr: 'J', tone: 'pending', icon: '↻' },
  Failed: { label: '未通过', abbr: 'FAIL', tone: 'danger', icon: '×' },
}

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const canManage = computed(() => auth.isAdmin === true || auth.isTeacher === true)
const managedScopeLabel = computed(() => (auth.isAdmin ? '全站管理' : '学生提交'))
const hasPending = computed(() => rows.value.some((row) => row.status === 0 || row.status === 1))
const metricCards = computed(() => {
  const data = overview.value
  if (scope.value === 'MANAGED') {
    return [
      { label: '总提交', value: data?.totalSubmissions ?? 0, icon: '▤', tone: 'teal' },
      { label: '通过次数', value: data?.acceptedSubmissions ?? 0, icon: '✓', tone: 'green' },
      { label: '活跃用户', value: data?.activeUsers ?? 0, icon: '◎', tone: 'blue' },
      { label: '等待 / 评测中', value: data?.pendingSubmissions ?? 0, icon: '↻', tone: 'amber' },
    ]
  }
  return [
    { label: '总提交', value: data?.totalSubmissions ?? 0, icon: '▤', tone: 'teal' },
    { label: '通过次数', value: data?.acceptedSubmissions ?? 0, icon: '✓', tone: 'green' },
    { label: '已解决题目', value: data?.solvedProblems ?? 0, icon: '◆', tone: 'blue' },
    {
      label: '通过率',
      value: `${(data?.acceptedRate ?? 0).toFixed(1)}%`,
      icon: '↗',
      tone: 'amber',
    },
  ]
})

const trendOption = computed(() => ({
  color: ['#168f80', '#72b6ff'],
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#172525',
    borderWidth: 0,
    textStyle: { color: '#fff' },
  },
  legend: {
    top: 2,
    right: 8,
    icon: 'roundRect',
    itemWidth: 14,
    itemHeight: 8,
    data: ['提交', '通过'],
  },
  grid: { left: 42, right: 18, top: 42, bottom: 28 },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: overview.value?.trend.map((p) => p.date.slice(5)) ?? [],
    axisLine: { lineStyle: { color: '#d9e2df' } },
    axisLabel: { color: '#73807d' },
  },
  yAxis: {
    type: 'value',
    minInterval: 1,
    splitLine: { lineStyle: { color: '#edf1ef' } },
    axisLabel: { color: '#73807d' },
  },
  series: [
    {
      name: '提交',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      areaStyle: { opacity: 0.08 },
      data: overview.value?.trend.map((p) => p.total) ?? [],
    },
    {
      name: '通过',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      data: overview.value?.trend.map((p) => p.accepted) ?? [],
    },
  ],
}))

const verdictOption = computed(() => ({
  color: ['#25a46f', '#ef6a61', '#f2a94a', '#6f89a8', '#b7c3bf'],
  tooltip: {
    trigger: 'item',
    formatter: '{b}<br/>{c} 次（{d}%）',
    backgroundColor: '#172525',
    borderWidth: 0,
    textStyle: { color: '#fff' },
  },
  legend: {
    bottom: 0,
    left: 'center',
    icon: 'circle',
    itemWidth: 8,
    itemHeight: 8,
    textStyle: { fontSize: 11 },
  },
  series: [
    {
      type: 'pie',
      radius: ['48%', '70%'],
      center: ['50%', '44%'],
      padAngle: 2,
      itemStyle: { borderColor: '#fff', borderWidth: 3, borderRadius: 5 },
      label: { show: false },
      data: (overview.value?.verdictDistribution ?? []).map((item) => ({
        value: item.value,
        name: verdictInfo(item.name).label,
      })),
    },
  ],
}))

const languageOption = computed(() => {
  const data = [...(overview.value?.languageDistribution ?? [])].slice(0, 6).reverse()
  return {
    color: ['#2aa696'],
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      backgroundColor: '#172525',
      borderWidth: 0,
      textStyle: { color: '#fff' },
    },
    grid: { left: 66, right: 24, top: 12, bottom: 24 },
    xAxis: {
      type: 'value',
      minInterval: 1,
      splitLine: { lineStyle: { color: '#edf1ef' } },
      axisLabel: { color: '#73807d' },
    },
    yAxis: {
      type: 'category',
      data: data.map((item) => languageLabel(item.name)),
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: '#485653' },
    },
    series: [
      {
        type: 'bar',
        barWidth: 12,
        itemStyle: { borderRadius: [0, 6, 6, 0] },
        data: data.map((item) => item.value),
      },
    ],
  }
})

function filters(): SubmissionFilters {
  return {
    scope: scope.value,
    userId: scope.value === 'MANAGED' && selectedUserId.value ? selectedUserId.value : undefined,
    questionKeyword: questionKeyword.value.trim() || undefined,
    userKeyword: scope.value === 'MANAGED' ? userKeyword.value.trim() || undefined : undefined,
    verdict: verdict.value || undefined,
    language: language.value || undefined,
    startTime: startDate.value ? `${startDate.value}T00:00:00` : undefined,
    endTime: endDate.value ? `${nextDate(endDate.value)}T00:00:00` : undefined,
    sort: sort.value,
    current: current.value,
    pageSize: pageSize.value,
  }
}

function nextDate(value: string) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + 1)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

async function loadPage(silent = false) {
  if (!silent) loading.value = true
  try {
    const page = await getSubmissionPage(filters())
    const nextRows = page.records ?? []
    nextRows.forEach((row) => {
      const old = previousStatus.get(row.submissionId)
      if (old != null && old !== row.status && row.status !== 0 && row.status !== 1) {
        highlightedIds.value = new Set([...highlightedIds.value, row.submissionId])
        window.setTimeout(() => {
          const next = new Set(highlightedIds.value)
          next.delete(row.submissionId)
          highlightedIds.value = next
        }, 2200)
      }
      previousStatus.set(row.submissionId, row.status)
    })
    rows.value = nextRows
    lastSyncAt.value = Date.now()
    total.value = page.total ?? 0
    current.value = page.current ?? current.value
    pageSize.value = page.size ?? pageSize.value
  } catch (error) {
    if (!silent) Message.error(error instanceof Error ? error.message : '提交记录加载失败')
  } finally {
    if (!silent) loading.value = false
  }
}

async function loadOverview(silent = false) {
  if (!silent) overviewLoading.value = true
  try {
    overview.value = await getSubmissionOverview(filters(), rangeDays.value)
  } catch (error) {
    if (!silent) Message.error(error instanceof Error ? error.message : '统计数据加载失败')
  } finally {
    if (!silent) overviewLoading.value = false
  }
}

async function refreshAll(silent = false) {
  await Promise.all([loadPage(silent), loadOverview(silent)])
}

function applyFilters() {
  current.value = 1
  filterOpen.value = false
  void refreshAll()
}

function resetFilters() {
  questionKeyword.value = ''
  userKeyword.value = ''
  verdict.value = ''
  language.value = ''
  startDate.value = ''
  endDate.value = ''
  sort.value = 'DESC'
  applyFilters()
}

async function openDetail(row: SubmissionListItem) {
  detailOpen.value = true
  detail.value = null
  detailLoading.value = true
  await router.replace({ query: { ...route.query, submissionId: row.submissionId } })
  try {
    detail.value = await getSubmissionDetail(row.submissionId)
    await nextTick()
    await highlightCode()
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '提交详情加载失败')
    closeDetail()
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  detailOpen.value = false
  detailFullscreen.value = false
  detail.value = null
  const query = { ...route.query }
  delete query.submissionId
  void router.replace({ query })
}

async function highlightCode() {
  const element = document.querySelector<HTMLElement>('#submission-code')
  if (!element) return
  const PrismModule = await import('prismjs')
  const Prism = PrismModule.default
  await import('prismjs/components/prism-clike')
  await import('prismjs/components/prism-cpp')
  await import('prismjs/components/prism-java')
  await import('prismjs/components/prism-python')
  element.className = `language-${prismLanguage(detail.value?.language)}`
  Prism.highlightElement(element)
}

function prismLanguage(value?: string) {
  const lang = value?.toLowerCase()
  if (lang === 'c++' || lang === 'cpp') return 'cpp'
  if (lang === 'python') return 'python'
  if (lang === 'java') return 'java'
  return 'clike'
}

async function copyCode() {
  if (!detail.value?.code) return
  await navigator.clipboard.writeText(detail.value.code)
  Message.success('代码已复制')
}

function continueProblem() {
  if (!detail.value?.questionId) return
  void router.push({ name: 'problem-solve', params: { id: detail.value.questionId } })
}

function verdictInfo(value?: string) {
  if (!value) return verdictMeta.Pending!
  return verdictMeta[value] ?? { label: value, abbr: 'OJ', tone: 'neutral', icon: '•' }
}

function rowVerdict(row: SubmissionListItem) {
  if (row.status === 0) return verdictInfo('Pending')
  if (row.status === 1) return verdictInfo('Judging')
  return verdictInfo(row.verdict || (row.status === 2 ? 'Accepted' : 'Failed'))
}

function languageLabel(value?: string) {
  const lang = (value || 'unknown').toLowerCase()
  if (lang === 'cpp' || lang === 'c++') return 'C++'
  if (lang === 'python') return 'Python'
  if (lang === 'java') return 'Java'
  return lang === 'unknown' ? '未知' : value || '未知'
}

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('zh-CN', { hour12: false })
}

function formatClock(value: number) {
  return new Date(value).toLocaleTimeString('zh-CN', { hour12: false })
}

function formatRelative(value?: string) {
  if (!value) return ''
  const timestamp = new Date(value).getTime()
  if (Number.isNaN(timestamp)) return ''
  const seconds = Math.max(0, Math.floor((liveNow.value - timestamp) / 1000))
  if (seconds < 5) return '刚刚'
  if (seconds < 60) return `${seconds} 秒前`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  return `${Math.floor(hours / 24)} 天前`
}

function formatMemory(value?: number) {
  if (value == null) return '—'
  return value >= 1024 ? `${(value / 1024).toFixed(value >= 10240 ? 0 : 1)} MB` : `${value} KB`
}

function scheduleRefresh() {
  window.clearInterval(refreshTimer)
  refreshTimer = window.setInterval(() => {
    if (document.visibilityState !== 'visible') return
    refreshTicks += 1
    if (hasPending.value || refreshTicks % 5 === 0) void loadPage(true)
    if (refreshTicks % 15 === 0) void loadOverview(true)
  }, 1000)
  window.clearInterval(clockTimer)
  clockTimer = window.setInterval(() => {
    liveNow.value = Date.now()
  }, 1000)
}

watch(scope, () => {
  current.value = 1
  // 切换范围时清空指定用户过滤
  selectedUserId.value = ''
  void refreshAll()
})
watch(rangeDays, () => void loadOverview())
watch(pageSize, () => {
  current.value = 1
  void loadPage()
})

onMounted(async () => {
  if (!auth.token) {
    Message.warning('请先登录后查看提交记录')
    return
  }
  // 从路由读取数据范围（老师端跳转带 scope=MANAGED）
  const routeScope = String(route.query.scope || '').toUpperCase()
  if (routeScope === 'MANAGED' || routeScope === 'SELF') {
    scope.value = routeScope as SubmissionScope
  }
  // 老师从学生管理页跳转时带 userId，仅在 MANAGED 范围下生效
  if (scope.value === 'MANAGED') {
    selectedUserId.value = String(route.query.userId || '')
  }
  await refreshAll()
  scheduleRefresh()
  const submissionId = String(route.query.submissionId || '')
  if (/^\d+$/.test(submissionId)) {
    await openDetail({ submissionId } as SubmissionListItem)
  }
})

onBeforeUnmount(() => {
  window.clearInterval(refreshTimer)
  window.clearInterval(clockTimer)
})
</script>

<template>
  <main class="submission-workspace">
    <header class="page-heading">
      <div>
        <h1>提交记录</h1>
        <p>
          {{
            scope === 'SELF'
              ? '回顾每次尝试，找到下一步改进方向。'
              : auth.isAdmin
                ? '查看全站评测状态与异常提交。'
                : '查看学生的评测记录与解题情况。'
          }}
        </p>
      </div>
      <div class="heading-actions">
        <span class="sync-state"
          ><i :class="{ pulse: hasPending }" />{{
            hasPending ? '正在同步评测' : `实时同步 · ${formatClock(liveNow)}`
          }}</span
        >
        <button class="secondary-btn" type="button" @click="refreshAll()">刷新</button>
        <button class="primary-btn" type="button" @click="router.push({ name: 'problems' })">
          继续刷题
        </button>
      </div>
    </header>

    <div v-if="canManage" class="scope-switch" aria-label="数据范围">
      <button :class="{ active: scope === 'SELF' }" type="button" @click="scope = 'SELF'">
        我的提交
      </button>
      <button :class="{ active: scope === 'MANAGED' }" type="button" @click="scope = 'MANAGED'">
        {{ managedScopeLabel }}
      </button>
    </div>

    <section class="metrics-grid" :aria-busy="overviewLoading">
      <article v-for="card in metricCards" :key="card.label" class="metric-card">
        <span class="metric-icon" :data-tone="card.tone">{{ card.icon }}</span>
        <div>
          <strong>{{ card.value }}</strong
          ><span>{{ card.label }}</span>
        </div>
      </article>
    </section>

    <section class="analytics-panel">
      <div class="section-title-row">
        <div>
          <h2>学习趋势</h2>
          <p>图表会随下方筛选条件同步变化</p>
        </div>
        <div class="range-switch">
          <button
            v-for="days in [7, 30, 90] as const"
            :key="days"
            :class="{ active: rangeDays === days }"
            type="button"
            @click="rangeDays = days"
          >
            {{ days }} 天
          </button>
        </div>
      </div>
      <div class="charts-grid">
        <article class="chart-card chart-card--wide">
          <h3>每日提交与通过</h3>
          <VChart class="chart" :option="trendOption" autoresize />
        </article>
        <article class="chart-card">
          <h3>判题结果</h3>
          <VChart class="chart" :option="verdictOption" autoresize />
        </article>
        <article class="chart-card">
          <h3>使用语言</h3>
          <VChart class="chart" :option="languageOption" autoresize />
        </article>
      </div>
    </section>

    <section class="records-panel">
      <div class="records-heading">
        <div>
          <h2>{{ scope === 'SELF' ? '我的提交' : auth.isAdmin ? '全站提交' : '学生提交' }}</h2>
          <span>共 {{ total }} 条</span>
        </div>
        <button class="mobile-filter-button" type="button" @click="filterOpen = true">筛选</button>
      </div>

      <div class="filter-bar" :class="{ 'filter-bar--open': filterOpen }">
        <div class="mobile-filter-head">
          <strong>筛选提交记录</strong><button type="button" @click="filterOpen = false">×</button>
        </div>
        <label
          ><span>题目</span
          ><input v-model="questionKeyword" placeholder="名称或 ID" @keyup.enter="applyFilters"
        /></label>
        <label v-if="scope === 'MANAGED'"
          ><span>用户</span
          ><input v-model="userKeyword" placeholder="昵称、用户名或 ID" @keyup.enter="applyFilters"
        /></label>
        <label
          ><span>结果</span
          ><select v-model="verdict">
            <option value="">全部结果</option>
            <option v-for="(meta, key) in verdictMeta" :key="key" :value="key">
              {{ meta.label }} {{ meta.abbr }}
            </option>
          </select></label
        >
        <label
          ><span>语言</span
          ><select v-model="language">
            <option value="">全部语言</option>
            <option value="cpp">C++</option>
            <option value="java">Java</option>
            <option value="python">Python</option>
          </select></label
        >
        <label><span>开始日期</span><input v-model="startDate" type="date" /></label>
        <label><span>结束日期</span><input v-model="endDate" type="date" /></label>
        <label
          ><span>排序</span
          ><select v-model="sort">
            <option value="DESC">最新优先</option>
            <option value="ASC">最早优先</option>
          </select></label
        >
        <div class="filter-actions">
          <button type="button" class="ghost-btn" @click="resetFilters">重置</button
          ><button type="button" class="primary-btn" @click="applyFilters">应用筛选</button>
        </div>
      </div>

      <div class="table-shell" :class="{ loading }">
        <table>
          <thead>
            <tr>
              <th>提交时间</th>
              <th>题目</th>
              <th v-if="scope === 'MANAGED'">用户</th>
              <th>评测结果</th>
              <th>语言</th>
              <th>运行时间</th>
              <th>内存</th>
              <th><span class="sr-only">操作</span></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.submissionId"
              :class="{
                highlight: highlightedIds.has(row.submissionId),
                judging: row.status === 0 || row.status === 1,
              }"
              tabindex="0"
              @click="openDetail(row)"
              @keyup.enter="openDetail(row)"
            >
              <td data-label="提交时间">
                <time>{{ formatDate(row.createTime) }}</time
                ><small class="live-age"
                  >{{ formatRelative(row.createTime)
                  }}<template v-if="row.globalSubmitNo">
                    · 第 {{ row.globalSubmitNo }} 次</template
                  ></small
                >
              </td>
              <td data-label="题目">
                <strong>{{ row.questionTitle || '题目已删除' }}</strong
                ><small>#{{ row.questionId }}</small>
              </td>
              <td v-if="scope === 'MANAGED'" data-label="用户">
                <div class="user-cell">
                  <img v-if="row.avatar" :src="row.avatar" alt="" /><span v-else>{{
                    (row.nickname || row.username || '用').slice(0, 1)
                  }}</span>
                  <div>
                    {{ row.nickname || row.username || '未知用户'
                    }}<small>@{{ row.username || row.userId }}</small>
                  </div>
                </div>
              </td>
              <td data-label="评测结果">
                <span class="verdict-chip" :data-tone="rowVerdict(row).tone"
                  ><b>{{ rowVerdict(row).icon }}</b
                  >{{ rowVerdict(row).label }} <em>{{ rowVerdict(row).abbr }}</em></span
                >
              </td>
              <td data-label="语言">
                <span class="language-chip">{{ languageLabel(row.language) }}</span>
              </td>
              <td data-label="运行时间">
                {{ row.runtimeMs == null ? '—' : `${row.runtimeMs} ms` }}
              </td>
              <td data-label="内存">{{ formatMemory(row.memoryKb) }}</td>
              <td class="row-action">查看详情 <span>›</span></td>
            </tr>
            <tr v-if="!loading && rows.length === 0">
              <td class="empty-state" :colspan="scope === 'MANAGED' ? 8 : 7">
                <span>⌁</span><strong>没有找到提交记录</strong>
                <p>调整筛选条件，或去题库完成一次新的挑战。</p>
                <button
                  class="primary-btn"
                  type="button"
                  @click="router.push({ name: 'problems' })"
                >
                  前往题库
                </button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <footer class="pagination">
        <label
          >每页
          <select v-model.number="pageSize">
            <option :value="15">15</option>
            <option :value="30">30</option>
            <option :value="50">50</option>
          </select>
          条</label
        >
        <div>
          <button
            type="button"
            :disabled="current <= 1"
            @click="
              current--
              loadPage()
            "
          >
            上一页</button
          ><span>{{ current }} / {{ totalPages }}</span
          ><button
            type="button"
            :disabled="current >= totalPages"
            @click="
              current++
              loadPage()
            "
          >
            下一页
          </button>
        </div>
      </footer>
    </section>

    <div v-if="detailOpen" class="drawer-backdrop" @click.self="closeDetail">
      <aside class="detail-drawer" :class="{ fullscreen: detailFullscreen }" aria-label="提交详情">
        <header>
          <div>
            <span>提交详情</span><small v-if="detail">#{{ detail.submissionId }}</small>
          </div>
          <div>
            <button type="button" @click="detailFullscreen = !detailFullscreen">
              {{ detailFullscreen ? '退出全屏' : '全屏' }}</button
            ><button class="drawer-close" type="button" aria-label="关闭" @click="closeDetail">
              ×
            </button>
          </div>
        </header>
        <div v-if="detailLoading" class="detail-loading">正在读取提交详情…</div>
        <template v-else-if="detail">
          <div class="detail-scroll">
            <section class="detail-summary">
              <span class="verdict-chip verdict-chip--large" :data-tone="rowVerdict(detail).tone"
                ><b>{{ rowVerdict(detail).icon }}</b
                >{{ rowVerdict(detail).label }} <em>{{ rowVerdict(detail).abbr }}</em></span
              >
              <h2>{{ detail.questionTitle }}</h2>
              <p>提交于 {{ formatDate(detail.createTime) }}</p>
              <dl>
                <div>
                  <dt>语言</dt>
                  <dd>{{ languageLabel(detail.language) }}</dd>
                </div>
                <div>
                  <dt>运行时间</dt>
                  <dd>{{ detail.runtimeMs == null ? '—' : `${detail.runtimeMs} ms` }}</dd>
                </div>
                <div>
                  <dt>内存</dt>
                  <dd>{{ formatMemory(detail.memoryKb) }}</dd>
                </div>
                <div>
                  <dt>代码长度</dt>
                  <dd>{{ detail.codeLength ?? 0 }} 字符</dd>
                </div>
              </dl>
            </section>
            <section v-if="detail.errorDetail" class="error-panel">
              <h3>错误详情</h3>
              <pre>{{ detail.errorDetail }}</pre>
            </section>
            <section class="code-panel">
              <div>
                <h3>提交代码</h3>
                <button type="button" @click="copyCode">复制代码</button>
              </div>
              <pre><code id="submission-code">{{ detail.code }}</code></pre>
            </section>
          </div>
          <footer class="drawer-footer">
            <span>{{
              detail.ownSubmission
                ? '这是你的提交，可以回到题目继续调整。'
                : '管理员只读查看，不会覆盖你的编辑器草稿。'
            }}</span
            ><button
              v-if="detail.ownSubmission"
              class="primary-btn"
              type="button"
              @click="continueProblem"
            >
              回到题目继续修改
            </button>
          </footer>
        </template>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.submission-workspace {
  min-height: calc(100vh - 58px);
  padding: 28px clamp(16px, 4vw, 64px) 56px;
  background: #f7f3e9;
  color: #202927;
}
.page-heading,
.analytics-panel,
.records-panel {
  max-width: 1600px;
  margin: 0 auto;
  background: #fff;
  border: 1px solid #e5e6df;
  border-radius: 18px;
}
.page-heading {
  min-height: 92px;
  padding: 22px 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 22px;
}
.page-heading h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: -0.03em;
}
.page-heading p {
  margin: 7px 0 0;
  color: #6b7673;
}
.heading-actions,
.filter-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}
.sync-state {
  font-size: 13px;
  color: #66736f;
  display: flex;
  align-items: center;
  gap: 7px;
}
.sync-state i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #24a674;
  box-shadow: 0 0 0 4px #dff4ea;
}
.primary-btn,
.secondary-btn,
.ghost-btn {
  min-height: 40px;
  padding: 0 17px;
  border-radius: 10px;
  border: 1px solid transparent;
  font-weight: 700;
  cursor: pointer;
}
.primary-btn {
  background: #128f80;
  color: #fff;
}
.secondary-btn {
  background: #fff;
  border-color: #cbd9d5;
  color: #146f65;
}
.ghost-btn {
  background: #f5f6f3;
  border-color: #e1e5e0;
  color: #53605d;
}
.scope-switch {
  max-width: 1600px;
  margin: 14px auto 0;
  display: flex;
  width: max-content;
  padding: 4px;
  background: #eaece6;
  border-radius: 11px;
}
.scope-switch button,
.range-switch button {
  border: 0;
  background: transparent;
  padding: 8px 14px;
  border-radius: 8px;
  color: #64706d;
  cursor: pointer;
}
.scope-switch button.active,
.range-switch button.active {
  background: #fff;
  color: #087b6e;
  font-weight: 700;
  box-shadow: 0 1px 3px #1f332214;
}
.metrics-grid {
  max-width: 1600px;
  margin: 16px auto;
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.metric-card {
  background: #fff;
  border: 1px solid #e4e6df;
  border-radius: 16px;
  padding: 18px;
  display: flex;
  align-items: center;
  gap: 14px;
}
.metric-card strong {
  display: block;
  font-size: 25px;
}
.metric-card span:not(.metric-icon) {
  font-size: 13px;
  color: #73807c;
}
.metric-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  font-size: 20px;
  background: #e7f6f3;
  color: #0d8c7d;
}
.metric-icon[data-tone='green'] {
  background: #e6f6ec;
  color: #1a9963;
}
.metric-icon[data-tone='blue'] {
  background: #eaf2ff;
  color: #387ed1;
}
.metric-icon[data-tone='amber'] {
  background: #fff1dd;
  color: #d47c16;
}
.analytics-panel {
  padding: 20px 22px;
  margin-bottom: 16px;
}
.section-title-row,
.records-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
.section-title-row h2,
.records-heading h2 {
  margin: 0;
  font-size: 19px;
}
.section-title-row p {
  margin: 4px 0 0;
  font-size: 12px;
  color: #84908c;
}
.range-switch {
  display: flex;
  padding: 3px;
  background: #f1f2ee;
  border-radius: 9px;
}
.charts-grid {
  margin-top: 18px;
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 14px;
}
.chart-card {
  min-width: 0;
  height: 280px;
  padding: 14px 14px 4px;
  border: 1px solid #eceee9;
  border-radius: 13px;
}
.chart-card h3 {
  margin: 0;
  font-size: 14px;
}
.chart {
  height: 235px;
  width: 100%;
}
.records-panel {
  padding: 20px 22px;
}
.records-heading > div {
  display: flex;
  align-items: baseline;
  gap: 10px;
}
.records-heading span {
  font-size: 13px;
  color: #7d8985;
}
.mobile-filter-button {
  display: none;
}
.filter-bar {
  display: grid;
  grid-template-columns: minmax(180px, 1.3fr) minmax(130px, 0.8fr) minmax(120px, 0.75fr) repeat(
      2,
      minmax(130px, 0.8fr)
    ) minmax(120px, 0.7fr) auto;
  gap: 10px;
  align-items: end;
  padding: 16px 0;
}
.filter-bar label {
  display: grid;
  gap: 5px;
  font-size: 12px;
  color: #66736f;
}
.filter-bar input,
.filter-bar select,
.pagination select {
  height: 40px;
  border: 1px solid #dce2de;
  border-radius: 9px;
  padding: 0 11px;
  background: #fff;
  color: #26312e;
  outline: none;
}
.filter-bar input:focus,
.filter-bar select:focus {
  border-color: #27a697;
  box-shadow: 0 0 0 3px #2a9d8f18;
}
.mobile-filter-head {
  display: none;
}
.table-shell {
  border: 1px solid #e3e7e3;
  border-radius: 13px;
  overflow: auto;
  max-height: 620px;
}
.table-shell.loading {
  opacity: 0.62;
  pointer-events: none;
}
table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}
thead {
  position: sticky;
  top: 0;
  z-index: 2;
}
th {
  background: #f4f6f4;
  color: #53615d;
  text-align: left;
  padding: 13px 14px;
  white-space: nowrap;
}
td {
  padding: 13px 14px;
  border-top: 1px solid #edf0ed;
  vertical-align: middle;
}
tbody tr {
  cursor: pointer;
  background: #fff;
  transition: background-color 0.16s;
}
tbody tr:hover {
  background: #f4faf8;
}
tbody tr.highlight {
  animation: row-flash 1.5s ease;
}
tbody tr.judging td:first-child {
  box-shadow: inset 3px 0 #e3a23a;
}
td strong {
  display: block;
  color: #22302d;
}
td small {
  display: block;
  color: #89938f;
  margin-top: 4px;
}
.row-action {
  color: #0d8275;
  white-space: nowrap;
}
.row-action span {
  font-size: 20px;
}
.verdict-chip,
.language-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 999px;
  padding: 5px 9px;
  font-weight: 700;
  white-space: nowrap;
}
.verdict-chip b {
  display: grid;
  place-items: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  font-style: normal;
}
.verdict-chip em {
  font-style: normal;
  font-size: 10px;
  opacity: 0.72;
}
.verdict-chip[data-tone='success'] {
  background: #e3f5ea;
  color: #187b50;
}
.verdict-chip[data-tone='danger'] {
  background: #fde8e6;
  color: #b23e38;
}
.verdict-chip[data-tone='warning'] {
  background: #fff0d8;
  color: #a86112;
}
.verdict-chip[data-tone='pending'] {
  background: #e9f0fa;
  color: #496a91;
}
.verdict-chip[data-tone='neutral'] {
  background: #ecefed;
  color: #58635f;
}
.language-chip {
  background: #f0f2f0;
  color: #4e5a56;
}
.user-cell {
  display: flex;
  align-items: center;
  gap: 9px;
}
.user-cell > img,
.user-cell > span {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  object-fit: cover;
  background: #dff3ef;
  display: grid;
  place-items: center;
  color: #087b6e;
  font-weight: 800;
}
.empty-state {
  text-align: center;
  padding: 58px !important;
}
.empty-state > span {
  display: block;
  font-size: 34px;
  color: #38a899;
}
.empty-state strong {
  font-size: 17px;
  margin-top: 8px;
}
.empty-state p {
  color: #77827f;
}
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 15px;
  color: #687470;
  font-size: 13px;
}
.pagination select {
  height: 34px;
}
.pagination > div {
  display: flex;
  align-items: center;
  gap: 12px;
}
.pagination button {
  height: 36px;
  padding: 0 13px;
  border: 1px solid #d7dfdb;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
.pagination button:disabled {
  opacity: 0.42;
  cursor: not-allowed;
}
.drawer-backdrop {
  position: fixed;
  inset: 0;
  background: #17211f66;
  z-index: 1000;
  display: flex;
  justify-content: flex-end;
}
.detail-drawer {
  height: 100%;
  width: min(680px, 94vw);
  background: #fbfaf6;
  box-shadow: -10px 0 30px #14241e24;
  display: flex;
  flex-direction: column;
}
.detail-drawer.fullscreen {
  width: 100vw;
}
.detail-drawer > header {
  height: 64px;
  padding: 0 18px 0 22px;
  border-bottom: 1px solid #e3e6e1;
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.detail-drawer > header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.detail-drawer > header span {
  font-size: 18px;
  font-weight: 800;
}
.detail-drawer > header small {
  color: #87918e;
}
.detail-drawer > header button,
.code-panel button {
  border: 1px solid #d8dfdb;
  background: #fff;
  border-radius: 8px;
  padding: 7px 11px;
  cursor: pointer;
}
.drawer-close {
  font-size: 22px !important;
  border: 0 !important;
}
.detail-loading {
  margin: auto;
  color: #687470;
}
.detail-scroll {
  padding: 20px;
  overflow: auto;
  flex: 1;
}
.detail-summary,
.error-panel,
.code-panel {
  background: #fff;
  border: 1px solid #e2e6e1;
  border-radius: 15px;
  padding: 18px;
  margin-bottom: 14px;
}
.verdict-chip--large {
  font-size: 14px;
  padding: 7px 11px;
}
.detail-summary h2 {
  margin: 16px 0 4px;
}
.detail-summary > p {
  margin: 0;
  color: #798480;
}
.detail-summary dl {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin: 18px 0 0;
}
.detail-summary dl div {
  padding: 11px;
  background: #f5f6f2;
  border-radius: 10px;
}
.detail-summary dt {
  font-size: 11px;
  color: #7a8581;
}
.detail-summary dd {
  margin: 5px 0 0;
  font-weight: 700;
}
.error-panel {
  border-color: #f0c9c4;
  background: #fff9f8;
}
.error-panel h3,
.code-panel h3 {
  margin: 0 0 12px;
}
.error-panel pre {
  white-space: pre-wrap;
  color: #963d37;
  font:
    12px/1.65 ui-monospace,
    monospace;
}
.code-panel > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.code-panel > pre {
  margin: 0;
  background: #102a33;
  color: #e8f5f3;
  border-radius: 11px;
  padding: 16px;
  overflow: auto;
  max-height: 55vh;
  font:
    13px/1.7 ui-monospace,
    SFMono-Regular,
    Consolas,
    monospace;
}
.drawer-footer {
  padding: 14px 18px;
  background: #fff;
  border-top: 1px solid #e1e5e0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.drawer-footer span {
  font-size: 12px;
  color: #6d7874;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
@keyframes row-flash {
  0%,
  100% {
    background: #fff;
  }
  35% {
    background: #dbf6eb;
  }
}
@media (prefers-reduced-motion: reduce) {
  * {
    animation: none !important;
    transition: none !important;
  }
}
@media (max-width: 1100px) {
  .charts-grid {
    grid-template-columns: 1fr 1fr;
  }
  .chart-card--wide {
    grid-column: 1/-1;
  }
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .filter-bar {
    grid-template-columns: repeat(3, 1fr);
  }
  .filter-actions {
    justify-content: flex-end;
  }
}
@media (max-width: 720px) {
  .submission-workspace {
    padding: 12px 10px 34px;
  }
  .page-heading {
    padding: 17px;
    align-items: flex-start;
  }
  .page-heading h1 {
    font-size: 25px;
  }
  .heading-actions .sync-state,
  .heading-actions .secondary-btn {
    display: none;
  }
  .metrics-grid {
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .metric-card {
    padding: 12px;
    gap: 9px;
  }
  .metric-icon {
    width: 36px;
    height: 36px;
  }
  .metric-card strong {
    font-size: 20px;
  }
  .analytics-panel,
  .records-panel {
    padding: 15px;
  }
  .charts-grid {
    grid-template-columns: 1fr;
  }
  .chart-card--wide {
    grid-column: auto;
  }
  .records-heading {
    margin-bottom: 12px;
  }
  .mobile-filter-button {
    display: block;
    border: 1px solid #cbd9d5;
    background: #fff;
    color: #087b6e;
    border-radius: 8px;
    padding: 8px 13px;
  }
  .filter-bar {
    display: none;
  }
  .filter-bar.filter-bar--open {
    position: fixed;
    z-index: 1100;
    inset: auto 0 0;
    display: grid;
    grid-template-columns: 1fr 1fr;
    max-height: 86vh;
    overflow: auto;
    background: #fff;
    padding: 18px;
    border-radius: 18px 18px 0 0;
    box-shadow: 0 -12px 35px #1526212b;
  }
  .mobile-filter-head {
    display: flex;
    grid-column: 1/-1;
    justify-content: space-between;
    align-items: center;
    font-size: 18px;
  }
  .mobile-filter-head button {
    border: 0;
    background: transparent;
    font-size: 25px;
  }
  .filter-actions {
    grid-column: 1/-1;
  }
  .table-shell {
    border: 0;
    max-height: none;
    overflow: visible;
  }
  table,
  tbody,
  tr,
  td {
    display: block;
  }
  thead {
    display: none;
  }
  tbody {
    display: grid;
    gap: 10px;
  }
  tbody tr {
    border: 1px solid #e1e5e1;
    border-radius: 13px;
    padding: 12px;
    position: relative;
  }
  td {
    border: 0;
    padding: 5px 0 5px 94px;
    min-height: 28px;
  }
  td::before {
    content: attr(data-label);
    position: absolute;
    left: 13px;
    color: #7b8682;
    font-size: 12px;
  }
  td[data-label='题目'] {
    padding-left: 0;
    padding-right: 25px;
    margin-bottom: 7px;
  }
  td[data-label='题目']::before {
    display: none;
  }
  .row-action {
    position: absolute;
    right: 12px;
    top: 15px;
    font-size: 0;
  }
  .row-action span {
    font-size: 24px;
  }
  .empty-state {
    padding: 40px 15px !important;
  }
  .empty-state::before {
    display: none;
  }
  .pagination {
    gap: 10px;
  }
  .pagination > label {
    display: none;
  }
  .detail-drawer {
    width: 100vw;
  }
  .detail-summary dl {
    grid-template-columns: 1fr 1fr;
  }
  .drawer-footer {
    align-items: stretch;
    flex-direction: column;
  }
  .drawer-footer .primary-btn {
    width: 100%;
  }
}
:global(html[data-theme='dark']) .submission-workspace {
  background: #111817;
  color: #e9f0ed;
}
:global(html[data-theme='dark']) .page-heading,
:global(html[data-theme='dark']) .analytics-panel,
:global(html[data-theme='dark']) .records-panel,
:global(html[data-theme='dark']) .metric-card,
:global(html[data-theme='dark']) .chart-card,
:global(html[data-theme='dark']) tbody tr,
:global(html[data-theme='dark']) .detail-summary,
:global(html[data-theme='dark']) .error-panel,
:global(html[data-theme='dark']) .code-panel {
  background: #19211f;
  border-color: #2e3a36;
}
:global(html[data-theme='dark']) th {
  background: #232d2a;
  color: #bac6c1;
}
:global(html[data-theme='dark']) td {
  border-color: #2b3532;
}
:global(html[data-theme='dark']) td strong,
:global(html[data-theme='dark']) .page-heading h1,
:global(html[data-theme='dark']) .section-title-row h2,
:global(html[data-theme='dark']) .records-heading h2 {
  color: #edf4f1;
}
:global(html[data-theme='dark']) .detail-drawer {
  background: #121a18;
}
:global(html[data-theme='dark']) .detail-drawer > header,
:global(html[data-theme='dark']) .drawer-footer {
  background: #19211f;
  border-color: #303b38;
}
.sync-state i.pulse {
  animation: sync-pulse 1.1s ease-in-out infinite;
}
.live-age {
  color: #118575 !important;
  font-variant-numeric: tabular-nums;
}
@keyframes sync-pulse {
  0%,
  100% {
    transform: scale(0.85);
    opacity: 0.7;
  }
  50% {
    transform: scale(1.2);
    opacity: 1;
  }
}
</style>
