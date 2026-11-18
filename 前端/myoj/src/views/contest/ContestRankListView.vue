<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Message } from '@arco-design/web-vue'
import { ContestControllerService } from '@generated'
import { isResultSuccess } from '@/api/result'
import { getBackendErrorMessage } from '@/api/httpError'
import { mapApiError } from '@/api/mapApiError'
import { formatSiteDate, localize as l } from '@/i18n/locale'

type ContestItem = {
  id: string
  title: string
  startTime?: string
  endTime?: string
  phase: string
}

type ArenaTab = 'all' | 'live' | 'soon' | 'past'

const loading = ref(false)
const list = ref<ContestItem[]>([])
const activeTab = ref<ArenaTab>('all')

/** 列表卡片开赛倒计时：所有「未开始」场次都显示，自动按 天/时/分/秒 降级 */
const nowTick = ref(Date.now())
let listTickTimer: ReturnType<typeof setInterval> | null = null

function parseContestInstant(v: unknown): number | null {
  if (v == null) return null
  if (typeof v === 'number' && Number.isFinite(v)) return v
  if (typeof v === 'string') {
    const trimmed = v.trim()
    if (!trimmed) return null
    const parsed = Date.parse(trimmed)
    if (!Number.isNaN(parsed)) return parsed
    const n = Number(trimmed)
    if (Number.isFinite(n)) return n
  }
  return null
}

function msUntilContestStart(iso?: string): number {
  const t = parseContestInstant(iso)
  if (t == null) return Number.POSITIVE_INFINITY
  return Math.max(0, t - nowTick.value)
}

function countdownDisplay(c: ContestItem): string | null {
  if (c.phase !== '未开始') return null
  const ms = msUntilContestStart(c.startTime)
  if (!(ms > 0)) return null
  const total = Math.floor(ms / 1000)
  const days = Math.floor(total / 86400)
  const hh = String(Math.floor((total % 86400) / 3600)).padStart(2, '0')
  const mm = String(Math.floor((total % 3600) / 60)).padStart(2, '0')
  const ss = String(total % 60).padStart(2, '0')
  return days > 0 ? `${days} ${l('天', 'days')} ${hh}:${mm}:${ss}` : `${hh}:${mm}:${ss}`
}

const liveContests = computed(() => list.value.filter((c) => c.phase === '进行中'))
const soonContests = computed(() => list.value.filter((c) => c.phase === '未开始'))
const pastContests = computed(() => list.value.filter((c) => c.phase === '已结束'))

const arenaStats = computed(() => ({
  total: list.value.length,
  live: liveContests.value.length,
  soon: soonContests.value.length,
  past: pastContests.value.length,
}))

function contestsForTab(tab: ArenaTab): ContestItem[] {
  if (tab === 'live') return liveContests.value
  if (tab === 'soon') return soonContests.value
  if (tab === 'past') return pastContests.value
  return list.value
}

function phaseRailClass(phase: string): string {
  if (phase === '进行中') return 'arena-card--live'
  if (phase === '未开始') return 'arena-card--soon'
  return 'arena-card--past'
}

function phaseLabelShort(phase: string): string {
  if (phase === '进行中') return l('实时赛况', 'Live')
  if (phase === '未开始') return l('即将开赛', 'Upcoming')
  return l('往期赛事', 'Past contest')
}

function displayPhase(phase: string) {
  if (phase === '进行中') return l('进行中', 'Live')
  if (phase === '未开始') return l('未开始', 'Upcoming')
  if (phase === '已结束') return l('已结束', 'Ended')
  return phase
}

async function loadList() {
  loading.value = true
  try {
    const data = await ContestControllerService.listContests()
    if (!isResultSuccess(data.code)) {
      Message.error(data.message || l('加载失败', 'Could not load contests'))
      return
    }
    list.value = (Array.isArray(data.data) ? data.data : []).map((c) => ({
      id: String(c.id ?? ''),
      title: c.title ?? '',
      startTime: c.startTime,
      endTime: c.endTime,
      phase: c.phase ?? '',
    }))
  } catch (e) {
    Message.error(
      mapApiError(e, getBackendErrorMessage(e, l('加载失败', 'Could not load contests'))),
    )
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadList()
  listTickTimer = setInterval(() => {
    nowTick.value = Date.now()
  }, 1000)
})

onBeforeUnmount(() => {
  if (listTickTimer != null) {
    clearInterval(listTickTimer)
    listTickTimer = null
  }
})

function formatDateTime(iso?: string): string {
  if (!iso) return '—'
  const t = Date.parse(iso)
  if (Number.isNaN(t)) return iso
  return formatSiteDate(new Date(t), {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}
</script>

<template>
  <div class="arena-hub oj-spotlight">
    <!-- 沉浸式顶区：自有「赛场枢纽」语言，非第三方站点复刻 -->
    <header class="arena-hero">
      <div class="arena-hero__mesh" aria-hidden="true" />
      <div class="arena-hero__orb arena-hero__orb--a" aria-hidden="true" />
      <div class="arena-hero__orb arena-hero__orb--b" aria-hidden="true" />

      <div class="arena-hero__grid">
        <div class="arena-hero__copy">
          <p class="arena-hero__eyebrow">{{ l('限时比赛', 'Timed contests') }}</p>
          <span class="arena-hero__rule" aria-hidden="true" />
          <h1 class="arena-hero__title">{{ l('比赛中心', 'Contest center') }}</h1>
          <p class="arena-hero__sub">
            {{
              l(
                '查看赛程、报名并进入比赛题目。单场成绩在比赛详情查看；',
                'Browse contests, register and solve contest problems. View contest results in each contest; ',
              )
            }}
            <RouterLink class="arena-hero__inline-link" to="/rankings">{{
              l('全站排行榜', 'global leaderboard')
            }}</RouterLink>
            {{ l('展示日常练习成绩。', ' shows practice results.') }}
          </p>
        </div>

        <aside class="arena-hero__stats" :aria-label="l('赛况概览', 'Contest overview')">
          <div class="arena-stat arena-stat--total">
            <span class="arena-stat__num">{{ arenaStats.total }}</span>
            <span class="arena-stat__lab">{{ l('赛事总数', 'Total') }}</span>
          </div>
          <div class="arena-stat arena-stat--live">
            <span class="arena-stat__num">{{ arenaStats.live }}</span>
            <span class="arena-stat__lab">{{ l('进行中', 'Live') }}</span>
          </div>
          <div class="arena-stat arena-stat--soon">
            <span class="arena-stat__num">{{ arenaStats.soon }}</span>
            <span class="arena-stat__lab">{{ l('未开始', 'Upcoming') }}</span>
          </div>
          <div class="arena-stat arena-stat--past">
            <span class="arena-stat__num">{{ arenaStats.past }}</span>
            <span class="arena-stat__lab">{{ l('已归档', 'Past') }}</span>
          </div>
        </aside>
      </div>
    </header>

    <div class="arena-shell">
      <details class="arena-guide-details">
        <summary>
          {{ l('参赛说明', 'Contest guide') }}
          <span>{{
            l('报名、榜单刷新与排名规则', 'Registration, standings and ranking rules')
          }}</span>
        </summary>
        <section class="arena-guide" :aria-label="l('参赛指南', 'Contest guide')">
          <div class="arena-guide__card">
            <span class="arena-guide__glyph">①</span>
            <div>
              <h3 class="arena-guide__h">{{ l('参赛路径', 'How to join') }}</h3>
              <p class="arena-guide__p">
                {{
                  l(
                    '进入场次 → 开赛后再报名 → 从本题卡片进做题页。',
                    'Open a contest, register after it starts, then open a problem from its contest page.',
                  )
                }}
              </p>
            </div>
          </div>
          <div class="arena-guide__card">
            <span class="arena-guide__glyph">②</span>
            <div>
              <h3 class="arena-guide__h">{{ l('榜单刷新', 'Standings') }}</h3>
              <p class="arena-guide__p">
                {{
                  l(
                    '开赛后可看榜；进行中约每 12 秒自动刷新，也可手动刷新。',
                    'Standings become available after the start and refresh about every 12 seconds.',
                  )
                }}
              </p>
            </div>
          </div>
          <div class="arena-guide__card">
            <span class="arena-guide__glyph">③</span>
            <div>
              <h3 class="arena-guide__h">{{ l('排名规则', 'Ranking rules') }}</h3>
              <p class="arena-guide__p">
                {{
                  l(
                    '通过题数优先，相同则总罚时（分钟）更少更靠前。',
                    'More solved problems rank first; ties use lower total penalty time.',
                  )
                }}
              </p>
            </div>
          </div>
        </section>
      </details>

      <nav class="arena-tabs" :aria-label="l('筛选赛事', 'Filter contests')">
        <button
          type="button"
          class="arena-tab"
          :class="{ 'arena-tab--on': activeTab === 'all' }"
          @click="activeTab = 'all'"
        >
          {{ l('全部', 'All') }}
          <span class="arena-tab__dot" />
        </button>
        <button
          type="button"
          class="arena-tab"
          :class="{ 'arena-tab--on': activeTab === 'live' }"
          @click="activeTab = 'live'"
        >
          {{ l('进行中', 'Live') }}
          <span v-if="arenaStats.live" class="arena-tab__badge">{{ arenaStats.live }}</span>
        </button>
        <button
          type="button"
          class="arena-tab"
          :class="{ 'arena-tab--on': activeTab === 'soon' }"
          @click="activeTab = 'soon'"
        >
          {{ l('即将开始', 'Upcoming') }}
          <span v-if="arenaStats.soon" class="arena-tab__badge">{{ arenaStats.soon }}</span>
        </button>
        <button
          type="button"
          class="arena-tab"
          :class="{ 'arena-tab--on': activeTab === 'past' }"
          @click="activeTab = 'past'"
        >
          {{ l('已结束', 'Ended') }}
          <span v-if="arenaStats.past" class="arena-tab__badge arena-tab__badge--muted">{{
            arenaStats.past
          }}</span>
        </button>
      </nav>

      <a-spin :loading="loading" class="arena-spin">
        <a-empty v-if="!loading && !list.length" class="arena-empty">
          <template #image>
            <div class="arena-empty__glyph" aria-hidden="true">◇</div>
          </template>
          <template #description>
            <p class="arena-empty__title">{{ l('当前没有开放赛事', 'No contests available') }}</p>
            <p class="arena-empty__desc">
              {{
                l(
                  '新赛程上架后将出现在这里；你也可以先去',
                  'New contests will appear here. You can practice in the ',
                )
              }}<RouterLink class="arena-empty__link" :to="{ name: 'problems' }">{{
                l('题库', 'problem set')
              }}</RouterLink
              >{{ l('热身。', ' first.') }}
            </p>
          </template>
        </a-empty>

        <template v-else-if="!loading && list.length">
          <!-- 分区视图：仅在「全部」下分三段，层次更清晰 -->
          <template v-if="activeTab === 'all'">
            <section v-if="liveContests.length" class="arena-block">
              <div class="arena-block__head">
                <span class="arena-block__rail arena-block__rail--live" />
                <h2 class="arena-block__title">{{ l('进行中', 'Live') }}</h2>
                <span class="arena-block__hint">{{
                  l('正在进行的场次优先展示', 'Active contests appear first')
                }}</span>
              </div>
              <div class="arena-card-list">
                <RouterLink
                  v-for="c in liveContests"
                  :key="c.id"
                  class="arena-card-link"
                  :to="{ name: 'contest-rank-detail', params: { contestId: c.id } }"
                >
                  <article class="arena-card" :class="phaseRailClass(c.phase)">
                    <div class="arena-card__rail-meta">
                      <span class="arena-card__phase-en">{{ phaseLabelShort(c.phase) }}</span>
                      <span class="arena-card__phase-zh">
                        <span
                          v-if="c.phase === '进行中'"
                          class="arena-card__live"
                          aria-hidden="true"
                        />
                        {{ displayPhase(c.phase) }}
                      </span>
                    </div>
                    <div class="arena-card__body">
                      <h3 class="arena-card__title">{{ c.title }}</h3>
                      <p class="arena-card__window">
                        <time>{{ formatDateTime(c.startTime) }}</time>
                        <span class="arena-card__arrow-icon">→</span>
                        <time>{{ formatDateTime(c.endTime) }}</time>
                      </p>
                    </div>
                    <div class="arena-card__action">
                      <span>{{ l('进入赛场', 'Enter contest') }}</span>
                      <span class="arena-card__chev">›</span>
                    </div>
                  </article>
                </RouterLink>
              </div>
            </section>

            <section v-if="soonContests.length" class="arena-block">
              <div class="arena-block__head">
                <span class="arena-block__rail arena-block__rail--soon" />
                <h2 class="arena-block__title">{{ l('即将开始', 'Upcoming') }}</h2>
                <span class="arena-block__hint">{{
                  l(
                    '开赛后方可报名；卡片内实时显示距开赛时间',
                    'Registration opens when the contest starts',
                  )
                }}</span>
              </div>
              <div class="arena-card-list">
                <RouterLink
                  v-for="c in soonContests"
                  :key="c.id"
                  class="arena-card-link"
                  :to="{ name: 'contest-rank-detail', params: { contestId: c.id } }"
                >
                  <article class="arena-card" :class="phaseRailClass(c.phase)">
                    <div class="arena-card__rail-meta">
                      <span class="arena-card__phase-en">{{ phaseLabelShort(c.phase) }}</span>
                      <span class="arena-card__phase-zh">{{ displayPhase(c.phase) }}</span>
                    </div>
                    <div class="arena-card__body">
                      <h3 class="arena-card__title">{{ c.title }}</h3>
                      <p class="arena-card__window">
                        <time>{{ formatDateTime(c.startTime) }}</time>
                        <span class="arena-card__arrow-icon">→</span>
                        <time>{{ formatDateTime(c.endTime) }}</time>
                      </p>
                      <div v-if="countdownDisplay(c)" class="arena-card__premiere">
                        <span class="arena-card__premiere-label">{{
                          l('距开赛', 'Starts in')
                        }}</span>
                        <span class="arena-card__premiere-time" aria-live="polite">{{
                          countdownDisplay(c)
                        }}</span>
                      </div>
                    </div>
                    <div class="arena-card__action">
                      <span>{{ l('预告 / 报名', 'Preview / register') }}</span>
                      <span class="arena-card__chev">›</span>
                    </div>
                  </article>
                </RouterLink>
              </div>
            </section>

            <section v-if="pastContests.length" class="arena-block arena-block--past">
              <div class="arena-block__head">
                <span class="arena-block__rail arena-block__rail--past" />
                <h2 class="arena-block__title">{{ l('已归档', 'Past contests') }}</h2>
                <span class="arena-block__hint">{{
                  l('可回看榜单与题目说明', 'Review standings and problems')
                }}</span>
              </div>
              <div class="arena-card-list">
                <RouterLink
                  v-for="c in pastContests"
                  :key="c.id"
                  class="arena-card-link"
                  :to="{ name: 'contest-rank-detail', params: { contestId: c.id } }"
                >
                  <article class="arena-card arena-card--past" :class="phaseRailClass(c.phase)">
                    <div class="arena-card__rail-meta">
                      <span class="arena-card__phase-en">{{ phaseLabelShort(c.phase) }}</span>
                      <span class="arena-card__phase-zh">{{ displayPhase(c.phase) }}</span>
                    </div>
                    <div class="arena-card__body">
                      <h3 class="arena-card__title">{{ c.title }}</h3>
                      <p class="arena-card__window">
                        <time>{{ formatDateTime(c.startTime) }}</time>
                        <span class="arena-card__arrow-icon">→</span>
                        <time>{{ formatDateTime(c.endTime) }}</time>
                      </p>
                    </div>
                    <div class="arena-card__action arena-card__action--muted">
                      <span>{{ l('回顾', 'Review') }}</span>
                      <span class="arena-card__chev">›</span>
                    </div>
                  </article>
                </RouterLink>
              </div>
            </section>
          </template>

          <!-- 单筛选：扁平列表 -->
          <div v-else class="arena-card-list arena-card-list--flat">
            <template v-if="contestsForTab(activeTab).length">
              <RouterLink
                v-for="c in contestsForTab(activeTab)"
                :key="c.id"
                class="arena-card-link"
                :to="{ name: 'contest-rank-detail', params: { contestId: c.id } }"
              >
                <article class="arena-card" :class="phaseRailClass(c.phase)">
                  <div class="arena-card__rail-meta">
                    <span class="arena-card__phase-en">{{ phaseLabelShort(c.phase) }}</span>
                    <span class="arena-card__phase-zh">
                      <span
                        v-if="c.phase === '进行中'"
                        class="arena-card__live"
                        aria-hidden="true"
                      />
                      {{ displayPhase(c.phase) }}
                    </span>
                  </div>
                  <div class="arena-card__body">
                    <h3 class="arena-card__title">{{ c.title }}</h3>
                    <p class="arena-card__window">
                      <time>{{ formatDateTime(c.startTime) }}</time>
                      <span class="arena-card__arrow-icon">→</span>
                      <time>{{ formatDateTime(c.endTime) }}</time>
                    </p>
                    <div v-if="countdownDisplay(c)" class="arena-card__premiere">
                      <span class="arena-card__premiere-label">{{ l('距开赛', 'Starts in') }}</span>
                      <span class="arena-card__premiere-time" aria-live="polite">{{
                        countdownDisplay(c)
                      }}</span>
                    </div>
                  </div>
                  <div
                    class="arena-card__action"
                    :class="{ 'arena-card__action--muted': c.phase === '已结束' }"
                  >
                    <span>{{
                      c.phase === '已结束'
                        ? l('回顾', 'Review')
                        : c.phase === '进行中'
                          ? l('进入赛场', 'Enter contest')
                          : l('预告 / 报名', 'Preview / register')
                    }}</span>
                    <span class="arena-card__chev">›</span>
                  </div>
                </article>
              </RouterLink>
            </template>
            <p v-else class="arena-filter-empty">
              {{
                l(
                  '该分类下暂无赛事，试试其它筛选。',
                  'No contests in this category. Try another filter.',
                )
              }}
            </p>
          </div>
        </template>
      </a-spin>
    </div>
  </div>
</template>

<style scoped>
.arena-hub {
  --arena-max: min(1180px, 100%);
  margin: 0 auto;
  padding-bottom: 64px;
}

/* —— Hero：大面积网格 + 双子星云，区别于常见竞赛站横幅 —— */
.arena-hero {
  position: relative;
  margin: 0 0 28px;
  padding: clamp(24px, 3vw, 34px) clamp(20px, 4vw, 40px) clamp(26px, 3vw, 36px);
  border-radius: 0 0 clamp(20px, 4vw, 32px) clamp(20px, 4vw, 32px);
  overflow: hidden;
  border-bottom: 1px solid color-mix(in srgb, var(--app-accent) 22%, var(--color-border-2));
  background: linear-gradient(
    165deg,
    color-mix(in srgb, var(--app-accent) 12%, var(--color-bg-1)) 0%,
    var(--color-bg-1) 42%,
    color-mix(in srgb, var(--app-accent-2) 8%, var(--color-bg-2)) 100%
  );
}

.arena-hero__mesh {
  pointer-events: none;
  position: absolute;
  inset: 0;
  opacity: 0.45;
  background-image:
    linear-gradient(color-mix(in srgb, var(--app-accent) 18%, transparent) 1px, transparent 1px),
    linear-gradient(
      90deg,
      color-mix(in srgb, var(--app-accent) 18%, transparent) 1px,
      transparent 1px
    );
  background-size: 36px 36px;
  mask-image: radial-gradient(ellipse 95% 80% at 70% 20%, #000 15%, transparent 65%);
  -webkit-mask-image: radial-gradient(ellipse 95% 80% at 70% 20%, #000 15%, transparent 65%);
}

.arena-hero__orb {
  pointer-events: none;
  position: absolute;
  border-radius: 50%;
  filter: blur(56px);
}

.arena-hero__orb--a {
  width: min(72vw, 420px);
  height: min(72vw, 420px);
  top: -40%;
  right: -8%;
  background: radial-gradient(
    closest-side,
    color-mix(in srgb, var(--app-accent) 38%, transparent),
    transparent 72%
  );
  opacity: 0.55;
}

.arena-hero__orb--b {
  width: min(48vw, 280px);
  height: min(48vw, 280px);
  bottom: -35%;
  left: -5%;
  background: radial-gradient(
    closest-side,
    color-mix(in srgb, var(--app-accent-2) 28%, transparent),
    transparent 70%
  );
  opacity: 0.42;
}

.arena-hero__grid {
  position: relative;
  z-index: 1;
  max-width: var(--arena-max);
  margin: 0 auto;
  display: grid;
  gap: 28px;
  align-items: start;
}

@media (min-width: 900px) {
  .arena-hero__grid {
    grid-template-columns: 1fr minmax(260px, 320px);
    gap: 40px;
  }
}

.arena-hero__eyebrow {
  margin: 0 0 10px;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: var(--app-accent);
}

.arena-hero__rule {
  display: block;
  width: min(104px, 26vw);
  height: 2px;
  margin: 0 0 20px;
  border-radius: 2px;
  background: linear-gradient(
    90deg,
    var(--app-accent),
    color-mix(in srgb, var(--app-accent-2) 62%, var(--app-accent))
  );
}

.arena-hero__title {
  margin: 0 0 14px;
  font-family: var(--app-font-display);
  font-size: clamp(2rem, 5vw, 2.65rem);
  font-weight: 700;
  letter-spacing: -0.03em;
  line-height: 1.12;
  color: var(--color-text-1);
}

.arena-hero__sub {
  margin: 0;
  max-width: 52ch;
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-text-2);
}

.arena-hero__inline-link {
  color: var(--app-accent);
  font-weight: 700;
  text-decoration: none;
  border-bottom: 1px solid color-mix(in srgb, var(--app-accent) 45%, transparent);
}

.arena-hero__inline-link:hover {
  border-bottom-color: var(--app-accent);
}

.arena-hero__stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.arena-stat {
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid var(--color-border-2);
  background: color-mix(in srgb, var(--color-bg-2) 88%, #ffffff 12%);
  display: flex;
  flex-direction: column;
  gap: 4px;
  box-shadow: 0 8px 24px color-mix(in srgb, var(--color-text-1) 5%, transparent);
}

.arena-stat__num {
  font-family: ui-monospace, 'Cascadia Code', Menlo, sans-serif;
  font-size: 1.75rem;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-1);
  line-height: 1;
}

.arena-stat__lab {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
}

.arena-stat--live .arena-stat__num {
  color: var(--app-accent);
}

.arena-stat--soon .arena-stat__num {
  color: color-mix(in srgb, var(--app-accent-2) 85%, var(--color-text-1));
}

.arena-shell {
  max-width: var(--arena-max);
  margin: 0 auto;
  padding: 0 20px;
}

.arena-guide {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.arena-guide-details {
  max-width: var(--arena-max);
  margin: 0 auto 18px;
}

.arena-guide-details summary {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 10px 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  font-weight: 700;
  cursor: pointer;
  list-style: none;
}

.arena-guide-details summary::-webkit-details-marker {
  display: none;
}
.arena-guide-details summary span {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
}
.arena-guide-details summary::after {
  content: '⌄';
  margin-left: auto;
  color: var(--app-accent);
  transition: transform 0.2s ease;
}
.arena-guide-details[open] summary::after {
  transform: rotate(180deg);
}
.arena-guide-details summary:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}
@media (prefers-reduced-motion: reduce) {
  .arena-guide-details summary::after {
    transition: none;
  }
}

.arena-guide__card {
  display: flex;
  gap: 14px;
  padding: 16px 18px;
  border-radius: 16px;
  border: 1px solid var(--color-border-2);
  background: var(--color-bg-2);
  box-shadow: 0 4px 16px color-mix(in srgb, var(--color-text-1) 4%, transparent);
}

.arena-guide__glyph {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 14px;
  font-weight: 800;
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 14%, transparent);
}

.arena-guide__h {
  margin: 0 0 6px;
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text-1);
}

.arena-guide__p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

/* —— 分段 Tab（胶囊 + 微交互） —— */
.arena-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 22px;
  padding: 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--color-fill-2) 100%, transparent);
  border: 1px solid var(--color-border-2);
  width: fit-content;
  max-width: 100%;
}

.arena-tab {
  appearance: none;
  border: none;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 18px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-2);
  background: transparent;
  transition:
    background 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease;
}

.arena-tab:hover {
  color: var(--color-text-1);
  background: color-mix(in srgb, var(--color-bg-2) 90%, transparent);
}

.arena-tab--on {
  color: var(--color-text-1);
  background: var(--color-bg-2);
  box-shadow: 0 4px 14px color-mix(in srgb, var(--color-text-1) 8%, transparent);
}

.arena-tab__badge {
  min-width: 1.25rem;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 800;
  background: color-mix(in srgb, var(--app-accent) 18%, transparent);
  color: var(--app-accent);
}

.arena-tab__badge--muted {
  background: var(--color-fill-3);
  color: var(--color-text-3);
}

.arena-tab__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--color-text-4);
  opacity: 0.5;
}

.arena-tab--on .arena-tab__dot {
  background: var(--app-accent);
  opacity: 1;
  box-shadow: 0 0 10px color-mix(in srgb, var(--app-accent) 55%, transparent);
}

.arena-spin {
  width: 100%;
  min-height: 220px;
}

.arena-empty__glyph {
  font-size: 56px;
  line-height: 1;
  color: color-mix(in srgb, var(--app-accent) 55%, var(--color-text-3));
  opacity: 0.85;
}

.arena-empty__title {
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 700;
  color: var(--color-text-1);
}

.arena-empty__desc {
  margin: 0;
  font-size: 14px;
  color: var(--color-text-3);
}

/* —— 分区标题 —— */
.arena-block {
  margin-bottom: 28px;
}

.arena-block--past {
  opacity: 0.96;
}

.arena-block__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 14px;
}

.arena-block__rail {
  width: 4px;
  height: 22px;
  border-radius: 4px;
}

.arena-block__rail--live {
  background: linear-gradient(
    180deg,
    var(--app-accent),
    color-mix(in srgb, var(--app-accent) 60%, #38bdf8)
  );
  box-shadow: 0 0 14px color-mix(in srgb, var(--app-accent) 45%, transparent);
}

.arena-block__rail--soon {
  background: linear-gradient(
    180deg,
    var(--app-accent-2),
    color-mix(in srgb, var(--app-accent-3) 70%, var(--app-accent-2))
  );
}

.arena-block__rail--past {
  background: var(--color-fill-3);
}

.arena-block__title {
  margin: 0;
  font-size: 1.05rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  color: var(--color-text-1);
}

.arena-block__hint {
  font-size: 12px;
  color: var(--color-text-3);
  flex: 1 1 100%;
}

@media (min-width: 640px) {
  .arena-block__hint {
    flex: 0 1 auto;
    margin-left: auto;
  }
}

/* —— 赛事卡片：左侧竖轨 + 横向信息流 —— */
.arena-card-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.arena-card-link {
  text-decoration: none;
  color: inherit;
  display: block;
}

.arena-card {
  position: relative;
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px 20px;
  align-items: center;
  padding: 18px 20px;
  border-radius: 18px;
  border: 1px solid var(--color-border-2);
  background: linear-gradient(
    135deg,
    color-mix(in srgb, var(--color-bg-2) 96%, var(--app-accent) 4%) 0%,
    var(--color-bg-2) 100%
  );
  box-shadow: 0 6px 22px color-mix(in srgb, var(--color-text-1) 5%, transparent);
  transition:
    transform 0.22s ease,
    box-shadow 0.22s ease,
    border-color 0.22s ease;
  overflow: hidden;
}

.arena-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 4px;
  border-radius: 4px;
  background: var(--color-fill-3);
}

.arena-card--live::before {
  background: linear-gradient(
    180deg,
    var(--app-accent),
    color-mix(in srgb, var(--app-accent) 50%, #22d3ee)
  );
  box-shadow: 0 0 16px color-mix(in srgb, var(--app-accent) 35%, transparent);
}

.arena-card--soon::before {
  background: linear-gradient(180deg, var(--app-accent-2), var(--app-accent-3));
}

.arena-card--past::before {
  background: var(--color-border-2);
}

.arena-card-link:hover .arena-card {
  transform: translateY(-3px);
  border-color: color-mix(in srgb, var(--app-accent) 35%, var(--color-border-2));
  box-shadow:
    0 16px 40px color-mix(in srgb, var(--app-accent) 12%, transparent),
    0 8px 24px color-mix(in srgb, var(--color-text-1) 6%, transparent);
}

.arena-card__rail-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 52px;
}

.arena-card__phase-en {
  font-size: 10px;
  font-weight: 800;
  letter-spacing: 0.12em;
  color: var(--color-text-3);
}

.arena-card__phase-zh {
  font-size: 12px;
  font-weight: 700;
  color: var(--color-text-2);
}

.arena-card--live .arena-card__phase-en {
  color: var(--app-accent);
}

.arena-card__title {
  margin: 0 0 8px;
  font-size: 1.08rem;
  font-weight: 800;
  letter-spacing: -0.02em;
  line-height: 1.35;
  color: var(--color-text-1);
}

.arena-card__window {
  margin: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-3);
}

.arena-card__arrow-icon {
  opacity: 0.45;
  font-weight: 400;
}

.arena-card__premiere {
  margin-top: 10px;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 12px;
  border: 1px solid color-mix(in srgb, var(--app-accent) 35%, var(--color-border-2));
  background: color-mix(in srgb, var(--app-accent) 10%, var(--color-bg-1));
}

.arena-card__premiere-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  color: var(--app-accent);
}

.arena-card__premiere-time {
  font-family: ui-monospace, Menlo, sans-serif;
  font-size: 15px;
  font-weight: 800;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-1);
}

.arena-card__premiere-colon {
  opacity: 0.55;
  padding: 0 2px;
}

.arena-card__action {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 700;
  color: var(--app-accent);
  white-space: nowrap;
}

.arena-card__action--muted {
  color: var(--color-text-3);
}

.arena-card__chev {
  font-size: 20px;
  font-weight: 300;
  opacity: 0.85;
}

.arena-filter-empty {
  margin: 24px 0;
  padding: 28px;
  text-align: center;
  border-radius: 16px;
  border: 1px dashed var(--color-border-2);
  color: var(--color-text-3);
  font-size: 14px;
}

@media (max-width: 640px) {
  .arena-card {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .arena-card__rail-meta {
    flex-direction: row;
    align-items: baseline;
    gap: 10px;
    min-width: 0;
  }

  .arena-card__action {
    justify-content: flex-start;
    padding-top: 4px;
    border-top: 1px dashed var(--color-border-3);
  }
}

/* —— 列表细化：LIVE 呼吸点、倒计时数字、进入箭头 —— */
.arena-card__phase-zh {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.arena-card__live {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px color-mix(in srgb, #22c55e 20%, transparent);
  animation: arena-live-pulse 1.7s ease-in-out infinite;
}

@keyframes arena-live-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.45;
    transform: scale(0.85);
  }
}

.arena-card__premiere-time {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.02em;
}

.arena-card__chev {
  transition: transform 0.2s ease;
}

.arena-card-link:hover .arena-card__chev {
  transform: translateX(4px);
}

.arena-empty__link {
  color: var(--app-accent);
  font-weight: 700;
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 暗黑：与首页首屏 / 功能入口高亮一致，见 @/styles/oj-spotlight-pages.css */
</style>
