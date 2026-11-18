<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart } from 'echarts/charts'
import { LegendComponent, TooltipComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import {
  getQuestionStatistics,
  type QuestionRankingItem,
  type QuestionStatistics,
} from '@/api/questionStatistics'
import { formatSiteDate, localize as l } from '@/i18n/locale'

use([CanvasRenderer, PieChart, LegendComponent, TooltipComponent])

const props = defineProps<{ questionId: string }>()
const loading = ref(false)
const errorMessage = ref('')
const statistics = ref<QuestionStatistics | null>(null)
const rankingMode = ref<'runtime' | 'memory'>('runtime')

const chartOption = computed(() => {
  const accepted = statistics.value?.acceptedSubmissions ?? 0
  const failed = statistics.value?.failedSubmissions ?? 0
  return {
    color: ['#22b573', '#f05b3d'],
    tooltip: {
      trigger: 'item',
      formatter: l('{b}<br/>{c} 次（{d}%）', '{b}<br/>{c} submissions ({d}%)'),
      borderWidth: 0,
      backgroundColor: 'rgba(22, 30, 35, .92)',
      textStyle: { color: '#fff' },
    },
    legend: {
      top: 0,
      left: 'center',
      icon: 'roundRect',
      itemWidth: 15,
      itemHeight: 8,
      textStyle: { color: '#64706c', fontSize: 12 },
    },
    series: [
      {
        name: l('提交分布', 'Submission distribution'),
        type: 'pie',
        radius: ['52%', '72%'],
        center: ['50%', '57%'],
        minAngle: 3,
        avoidLabelOverlap: true,
        padAngle: 2,
        itemStyle: { borderColor: '#fffdf8', borderWidth: 3, borderRadius: 5 },
        label: { show: false },
        emphasis: { scaleSize: 6 },
        data: [
          { value: accepted, name: l('通过', 'Passed') },
          { value: failed, name: l('未通过', 'Failed') },
        ],
      },
    ],
  }
})

const activeRanking = computed<QuestionRankingItem[]>(() =>
  rankingMode.value === 'runtime'
    ? (statistics.value?.fastestRankings ?? [])
    : (statistics.value?.memoryRankings ?? []),
)
const activeBeatPercent = computed(() =>
  rankingMode.value === 'runtime'
    ? statistics.value?.beatRuntimePercent
    : statistics.value?.beatMemoryPercent,
)

function formatMemory(kb?: number) {
  if (kb == null) return '—'
  if (kb >= 1024) return `${(kb / 1024).toFixed(kb >= 10240 ? 0 : 1)} MB`
  return `${kb} KB`
}

function formatTime(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : formatSiteDate(date, { dateStyle: 'medium', timeStyle: 'short', hour12: false })
}

function avatarText(name?: string) {
  return (name?.trim().slice(0, 1) || '码').toUpperCase()
}

async function loadStatistics() {
  if (!props.questionId) return
  loading.value = true
  errorMessage.value = ''
  try {
    statistics.value = await getQuestionStatistics(props.questionId)
  } catch (error) {
    errorMessage.value =
      error instanceof Error
        ? error.message
        : l('统计数据暂时无法加载', 'Statistics are temporarily unavailable')
  } finally {
    loading.value = false
  }
}

watch(() => props.questionId, loadStatistics)
onMounted(loadStatistics)
</script>

<template>
  <section class="statistics-panel" :aria-label="l('题目统计', 'Problem statistics')">
    <div v-if="loading" class="statistics-state" aria-live="polite">
      <span class="statistics-spinner" />{{
        l('正在整理本题提交数据…', 'Loading submission statistics…')
      }}
    </div>
    <div v-else-if="errorMessage" class="statistics-state statistics-state--error">
      <div>
        <strong>{{ l('统计数据暂时没有加载出来', 'Could not load statistics') }}</strong
        ><span>{{ errorMessage }}</span>
      </div>
      <button type="button" @click="loadStatistics">{{ l('重新加载', 'Reload') }}</button>
    </div>
    <template v-else-if="statistics">
      <div class="statistics-metrics">
        <article class="metric-card metric-card--teal">
          <span class="metric-card__icon" aria-hidden="true">▤</span>
          <div>
            <strong>{{ statistics.totalSubmissions }}</strong
            ><span>{{ l('总提交', 'Submissions') }}</span>
          </div>
        </article>
        <article class="metric-card metric-card--green">
          <span class="metric-card__icon" aria-hidden="true">✓</span>
          <div>
            <strong>{{ statistics.acceptedSubmissions }}</strong
            ><span>{{ l('通过次数', 'Accepted') }}</span>
          </div>
        </article>
        <article class="metric-card metric-card--blue">
          <span class="metric-card__icon" aria-hidden="true">↗</span>
          <div>
            <strong>{{ statistics.passRate.toFixed(2) }}%</strong
            ><span>{{ l('通过率', 'Acceptance') }}</span>
          </div>
        </article>
        <article class="metric-card metric-card--amber">
          <span class="metric-card__icon" aria-hidden="true">⚡</span>
          <div>
            <strong>{{
              activeBeatPercent == null ? '—' : `${activeBeatPercent.toFixed(2)}%`
            }}</strong>
            <span>{{
              statistics.acceptedByCurrentUser
                ? l('击败用户', 'Beats users')
                : l('AC 后可查看', 'Available after AC')
            }}</span>
          </div>
        </article>
      </div>

      <article class="statistics-card distribution-card">
        <header>
          <div>
            <h3>{{ l('提交分布', 'Submission distribution') }}</h3>
            <p>{{ l('通过与未通过的真实提交占比', 'Accepted and failed submissions') }}</p>
          </div>
          <span
            >{{ l('共', 'Total') }} {{ statistics.totalSubmissions }}
            {{ l('次', 'submissions') }}</span
          >
        </header>
        <div v-if="statistics.totalSubmissions" class="distribution-chart-wrap">
          <VChart class="distribution-chart" :option="chartOption" autoresize />
          <div class="distribution-chart__center" aria-hidden="true">
            <strong>{{ statistics.passRate.toFixed(2) }}%</strong
            ><span>{{ l('通过率', 'Acceptance') }}</span>
          </div>
        </div>
        <div v-else class="statistics-empty statistics-empty--chart">
          <span>◎</span><strong>{{ l('还没有提交数据', 'No submissions yet') }}</strong>
          <p>
            {{
              l(
                '完成第一次提交后，这里会展示通过分布。',
                'The chart will appear after the first submission.',
              )
            }}
          </p>
        </div>
      </article>

      <article class="statistics-card ranking-card">
        <header class="ranking-card__head">
          <div>
            <h3>{{ l('解题排行', 'Solution rankings') }}</h3>
            <p>
              {{
                l(
                  '每位用户仅展示最好的一次 Accepted 记录',
                  'Each user appears once with their best accepted submission',
                )
              }}
            </p>
          </div>
          <div class="ranking-tabs" role="tablist" :aria-label="l('排行榜指标', 'Ranking metric')">
            <button
              type="button"
              :class="{ active: rankingMode === 'runtime' }"
              @click="rankingMode = 'runtime'"
            >
              {{ l('最快用时', 'Fastest') }}
            </button>
            <button
              type="button"
              :class="{ active: rankingMode === 'memory' }"
              @click="rankingMode = 'memory'"
            >
              {{ l('最低内存', 'Lowest memory') }}
            </button>
          </div>
        </header>

        <div v-if="activeRanking.length" class="ranking-table-wrap">
          <table class="ranking-table">
            <thead>
              <tr>
                <th>{{ l('排名', 'Rank') }}</th>
                <th>{{ l('用户', 'User') }}</th>
                <th>{{ l('执行用时', 'Runtime') }}</th>
                <th>{{ l('内存消耗', 'Memory') }}</th>
                <th>{{ l('语言', 'Language') }}</th>
                <th>{{ l('代码长度', 'Code size') }}</th>
                <th>{{ l('提交时间', 'Submitted') }}</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="item in activeRanking"
                :key="`${rankingMode}-${item.rankNo}-${item.userName}`"
              >
                <td>
                  <span class="rank-number" :class="`rank-number--${item.rankNo}`">{{
                    item.rankNo
                  }}</span>
                </td>
                <td>
                  <div class="ranking-user">
                    <img
                      v-if="item.userAvatar"
                      :src="item.userAvatar"
                      alt=""
                      @error="($event.currentTarget as HTMLImageElement).style.display = 'none'"
                    />
                    <span v-else>{{ avatarText(item.userName) }}</span
                    ><strong>{{ item.userName || l('匿名用户', 'Anonymous') }}</strong>
                  </div>
                </td>
                <td>{{ item.runtimeMs == null ? '—' : `${item.runtimeMs} ms` }}</td>
                <td>{{ formatMemory(item.memoryKb) }}</td>
                <td>
                  <span class="language-chip">{{ item.language || '—' }}</span>
                </td>
                <td>{{ item.codeLength == null ? '—' : `${item.codeLength} B` }}</td>
                <td>{{ formatTime(item.createTime) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="statistics-empty">
          <span>⌁</span><strong>{{ l('暂无有效排名', 'No rankings yet') }}</strong>
          <p>
            {{
              l(
                '排行榜只统计评测通过且含资源数据的提交。',
                'Only accepted submissions with resource data appear here.',
              )
            }}
          </p>
        </div>
      </article>
    </template>
  </section>
</template>

<style scoped>
.statistics-panel {
  display: grid;
  gap: 18px;
  min-height: 100%;
  padding: 4px 2px 28px;
  color: var(--app-text-main);
}
.statistics-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}
.metric-card {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 92px;
  padding: 16px 18px;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: var(--app-surface);
  box-shadow: 0 5px 18px rgba(67, 54, 36, 0.045);
}
.metric-card__icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
  border-radius: 12px;
  font-weight: 800;
  font-size: 20px;
}
.metric-card--teal .metric-card__icon {
  color: #0f9f92;
  background: #e2f7f3;
}
.metric-card--green .metric-card__icon {
  color: #19a663;
  background: #e5f8ed;
}
.metric-card--blue .metric-card__icon {
  color: #3788e8;
  background: #eaf2ff;
}
.metric-card--amber .metric-card__icon {
  color: #e78a12;
  background: #fff2dd;
}
.metric-card strong {
  display: block;
  font-size: 24px;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
}
.metric-card div > span {
  display: block;
  margin-top: 6px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.statistics-card {
  border: 1px solid var(--app-border);
  border-radius: 14px;
  background: var(--app-surface);
  overflow: hidden;
  box-shadow: 0 5px 18px rgba(67, 54, 36, 0.04);
}
.statistics-card > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-height: 66px;
  padding: 13px 18px;
  border-bottom: 1px solid var(--app-border);
}
.statistics-card h3 {
  margin: 0;
  font-size: 16px;
}
.statistics-card header p {
  margin: 4px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
}
.statistics-card header > span {
  color: var(--app-text-muted);
  font-size: 12px;
}
.distribution-chart-wrap {
  position: relative;
  height: 330px;
}
.distribution-chart {
  width: 100%;
  height: 100%;
}
.distribution-chart__center {
  position: absolute;
  left: 50%;
  top: 58%;
  display: grid;
  transform: translate(-50%, -50%);
  text-align: center;
  pointer-events: none;
}
.distribution-chart__center strong {
  font-size: 24px;
}
.distribution-chart__center span {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.ranking-tabs {
  display: flex;
  padding: 3px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: color-mix(in srgb, var(--app-surface) 75%, var(--app-bg) 25%);
}
.ranking-tabs button {
  min-height: 34px;
  padding: 0 14px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--app-text-muted);
  cursor: pointer;
}
.ranking-tabs button.active {
  color: #fff;
  background: #15a79a;
  box-shadow: 0 3px 9px rgba(21, 167, 154, 0.2);
}
.ranking-table-wrap {
  width: 100%;
  overflow-x: auto;
}
.ranking-table {
  width: 100%;
  border-collapse: collapse;
  min-width: 820px;
  font-size: 13px;
}
.ranking-table th {
  padding: 11px 16px;
  color: var(--app-text-muted);
  background: color-mix(in srgb, var(--app-surface) 76%, var(--app-bg) 24%);
  text-align: left;
  font-size: 12px;
  font-weight: 650;
}
.ranking-table td {
  padding: 13px 16px;
  border-top: 1px solid color-mix(in srgb, var(--app-border) 72%, transparent);
  color: var(--app-text-muted);
  font-variant-numeric: tabular-nums;
}
.ranking-table tbody tr:hover {
  background: color-mix(in srgb, #16a89a 5%, var(--app-surface));
}
.rank-number {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  font-weight: 800;
  color: var(--app-text-muted);
  background: color-mix(in srgb, var(--app-border) 50%, transparent);
}
.rank-number--1 {
  color: #a76500;
  background: #fff1c6;
}
.rank-number--2 {
  color: #65717a;
  background: #edf1f3;
}
.rank-number--3 {
  color: #9b5d32;
  background: #f8e2d3;
}
.ranking-user {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 150px;
  color: var(--app-text-main);
}
.ranking-user img,
.ranking-user > span {
  width: 30px;
  height: 30px;
  flex: 0 0 30px;
  border-radius: 50%;
  object-fit: cover;
}
.ranking-user > span {
  display: grid;
  place-items: center;
  color: #0c7f74;
  background: #dcf5f0;
  font-weight: 800;
}
.ranking-user strong {
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.language-chip {
  display: inline-flex;
  padding: 3px 8px;
  border-radius: 6px;
  color: #0d7f75;
  background: #e5f6f3;
  font-size: 11px;
}
.statistics-state,
.statistics-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  min-height: 210px;
  border: 1px dashed var(--app-border);
  border-radius: 14px;
  color: var(--app-text-muted);
  background: var(--app-surface);
  text-align: center;
}
.statistics-state--error {
  justify-content: space-between;
  padding: 24px;
}
.statistics-state--error div {
  display: grid;
  gap: 6px;
  text-align: left;
}
.statistics-state--error strong,
.statistics-empty strong {
  color: var(--app-text-main);
}
.statistics-state--error button {
  min-height: 40px;
  padding: 0 16px;
  border: 1px solid #13a093;
  border-radius: 9px;
  color: #0b8177;
  background: transparent;
  cursor: pointer;
}
.statistics-spinner {
  width: 18px;
  height: 18px;
  border: 2px solid #bde5df;
  border-top-color: #0d9e91;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
.statistics-empty {
  min-height: 190px;
  flex-direction: column;
  gap: 6px;
  border: 0;
  border-radius: 0;
}
.statistics-empty--chart {
  min-height: 330px;
}
.statistics-empty > span {
  font-size: 30px;
  color: #15a79a;
}
.statistics-empty p {
  margin: 0;
  font-size: 12px;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
@media (max-width: 1100px) {
  .statistics-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 680px) {
  .statistics-panel {
    gap: 12px;
  }
  .statistics-metrics {
    grid-template-columns: 1fr 1fr;
    gap: 9px;
  }
  .metric-card {
    min-height: 80px;
    padding: 12px;
    gap: 10px;
  }
  .metric-card__icon {
    width: 36px;
    height: 36px;
    flex-basis: 36px;
  }
  .metric-card strong {
    font-size: 19px;
  }
  .statistics-card > header {
    align-items: flex-start;
    flex-direction: column;
  }
  .ranking-tabs {
    width: 100%;
  }
  .ranking-tabs button {
    flex: 1;
  }
  .distribution-chart-wrap {
    height: 270px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .statistics-spinner {
    animation-duration: 1.6s;
  }
}
</style>
