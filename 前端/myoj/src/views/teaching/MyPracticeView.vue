<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useRouter } from 'vue-router'
import {
  type PracticeAssignment,
  practiceAssignmentsApi,
  type PracticeItem,
} from '@/api/practiceAssignments'

const router = useRouter()
const loading = ref(true)
const assignments = ref<PracticeAssignment[]>([])

const activeCount = computed(
  () => assignments.value.filter((item) => (item.progress ?? 0) < 100).length,
)
const doneCount = computed(
  () => assignments.value.filter((item) => (item.progress ?? 0) >= 100).length,
)
const pendingQuestions = computed(() =>
  assignments.value.reduce(
    (sum, item) => sum + Math.max(0, (item.itemCount ?? 0) - (item.solvedCount ?? 0)),
    0,
  ),
)

async function load() {
  loading.value = true
  try {
    assignments.value = await practiceAssignmentsApi.mine()
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '加载练习失败')
  } finally {
    loading.value = false
  }
}

function formatDate(raw?: string) {
  if (!raw) return '—'
  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return '—'
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(
    date.getDate(),
  ).padStart(2, '0')}`
}

function openProblem(item: PracticeItem) {
  router.push({ name: 'problem-solve', params: { id: item.questionId } })
}

function startAssignment(assignment: PracticeAssignment) {
  const next = (assignment.items ?? []).find((item) => !item.solved) ?? assignment.items?.[0]
  if (next) openProblem(next)
}

function firstUnsolvedText(assignment: PracticeAssignment) {
  const next = (assignment.items ?? []).find((item) => !item.solved)
  return next ? '继续练习' : '回顾题目'
}

onMounted(load)
</script>

<template>
  <main class="practice-page">
    <header class="practice-heading">
      <div>
        <p class="practice-eyebrow">学习中心</p>
        <h1>我的练习</h1>
        <p class="practice-sub">老师根据你的薄弱知识点布置的个性化练习，通过后会自动标记完成。</p>
      </div>
      <button type="button" class="practice-refresh" :disabled="loading" @click="load">刷新</button>
    </header>

    <section class="practice-stats">
      <div class="practice-stat">
        <span>进行中</span>
        <strong>{{ activeCount }}</strong>
      </div>
      <div class="practice-stat">
        <span>已完成</span>
        <strong>{{ doneCount }}</strong>
      </div>
      <div class="practice-stat">
        <span>待完成题目</span>
        <strong>{{ pendingQuestions }}</strong>
      </div>
    </section>

    <div v-if="loading" class="practice-empty">正在加载练习…</div>

    <div v-else-if="!assignments.length" class="practice-empty">
      <div class="practice-empty__icon">练</div>
      <h2>暂时没有练习</h2>
      <p>老师布置后会出现在这里，也可以先去题库自主练习。</p>
      <RouterLink class="practice-link" to="/problems">前往题库</RouterLink>
    </div>

    <section v-else class="practice-list">
      <article v-for="assignment in assignments" :key="assignment.id" class="practice-card">
        <header class="practice-card__head">
          <div class="practice-card__title">
            <h2>{{ assignment.title }}</h2>
            <p>
              {{ assignment.teacherName || '老师' }} 布置 · {{ formatDate(assignment.createTime) }}
              <span v-if="assignment.dueTime"> · 截止 {{ formatDate(assignment.dueTime) }}</span>
            </p>
          </div>
          <span
            class="practice-progress__label"
            :class="{ 'is-done': (assignment.progress ?? 0) >= 100 }"
          >
            {{ assignment.progress ?? 0 }}%
          </span>
        </header>

        <div class="practice-progress" aria-hidden="true">
          <span :style="{ width: `${Math.min(100, Math.max(0, assignment.progress ?? 0))}%` }" />
        </div>

        <p v-if="assignment.remark" class="practice-remark">{{ assignment.remark }}</p>

        <ol class="practice-items">
          <li v-for="item in assignment.items" :key="item.questionId">
            <button
              type="button"
              class="practice-item"
              :class="{ 'is-solved': item.solved }"
              @click="openProblem(item)"
            >
              <span class="practice-item__index">{{ item.sortOrder }}</span>
              <span class="practice-item__main">
                <strong>{{ item.title }}</strong>
                <small>
                  <span v-if="item.difficulty" class="practice-tag">{{ item.difficulty }}</span>
                  <span v-if="item.focusTag" class="practice-tag practice-tag--focus">{{
                    item.focusTag
                  }}</span>
                  <span v-if="item.reason" class="practice-item__reason">{{ item.reason }}</span>
                </small>
              </span>
              <span class="practice-item__state">{{ item.solved ? '已完成' : '去完成' }}</span>
            </button>
          </li>
        </ol>

        <footer class="practice-card__foot">
          <span>已完成 {{ assignment.solvedCount ?? 0 }} / {{ assignment.itemCount ?? 0 }}</span>
          <button type="button" class="practice-start" @click="startAssignment(assignment)">
            {{ (assignment.progress ?? 0) >= 100 ? '回顾题目' : firstUnsolvedText(assignment) }}
          </button>
        </footer>
      </article>
    </section>
  </main>
</template>

<style scoped>
.practice-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
  max-width: 1100px;
  margin: 0 auto;
  padding: 28px 22px 48px;
}
.practice-eyebrow {
  margin: 0 0 4px;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.practice-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}
.practice-heading h1 {
  margin: 0;
  color: var(--ink);
  font-size: 26px;
  line-height: 1.2;
}
.practice-sub {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 14px;
}
.practice-refresh {
  min-height: 38px;
  padding: 0 18px;
  border: 1px solid #c9e4df;
  border-radius: 10px;
  background: #fff;
  color: #087f73;
  font-weight: 650;
  cursor: pointer;
}
.practice-refresh:hover:not(:disabled) {
  background: #eff9f7;
}
.practice-refresh:disabled {
  opacity: 0.55;
  cursor: wait;
}
.practice-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.practice-stat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: #fff;
}
.practice-stat span {
  color: var(--muted);
  font-size: 13px;
}
.practice-stat strong {
  color: #087f73;
  font-size: 24px;
  line-height: 1;
}
.practice-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 64px 20px;
  border: 1px dashed #ccd8d5;
  border-radius: 18px;
  background: #fff;
  color: var(--muted);
  text-align: center;
}
.practice-empty__icon {
  display: grid;
  place-items: center;
  width: 52px;
  height: 52px;
  border-radius: 16px;
  background: #e7f5f2;
  color: #168a7d;
  font-size: 20px;
  font-weight: 800;
}
.practice-empty h2 {
  margin: 8px 0 0;
  color: var(--ink);
  font-size: 18px;
}
.practice-empty p {
  margin: 0;
  font-size: 14px;
}
.practice-link {
  display: inline-flex;
  align-items: center;
  min-height: 38px;
  margin-top: 6px;
  padding: 0 18px;
  border-radius: 10px;
  background: #0f9b8e;
  color: #fff;
  font-weight: 650;
  text-decoration: none;
}
.practice-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.practice-card {
  padding: 20px 22px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(16, 60, 54, 0.04);
}
.practice-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.practice-card__title h2 {
  margin: 0;
  color: var(--ink);
  font-size: 17px;
}
.practice-card__title p {
  margin: 5px 0 0;
  color: var(--muted);
  font-size: 12.5px;
}
.practice-progress__label {
  flex: none;
  color: #0f766e;
  font-size: 20px;
  font-weight: 800;
}
.practice-progress__label.is-done {
  color: #16a34a;
}
.practice-progress {
  height: 7px;
  margin: 12px 0;
  overflow: hidden;
  border-radius: 999px;
  background: #eef3f2;
}
.practice-progress span {
  display: block;
  height: 100%;
  border-radius: 999px;
  background: linear-gradient(90deg, #34d399, #0f9b8e);
  transition: width 0.3s ease;
}
.practice-remark {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7faf9;
  color: #52615e;
  font-size: 13px;
}
.practice-items {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.practice-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 11px 12px;
  border: 1px solid #eef1f0;
  border-radius: 12px;
  background: #fff;
  text-align: left;
  cursor: pointer;
  transition:
    border-color 0.15s ease,
    background 0.15s ease;
}
.practice-item:hover {
  border-color: #9ad6cc;
  background: #f9fdfc;
}
.practice-item.is-solved {
  background: #f7fbf8;
}
.practice-item__index {
  flex: none;
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: #e7f5f2;
  color: #0f766e;
  font-size: 12px;
  font-weight: 700;
}
.practice-item.is-solved .practice-item__index {
  background: #dcfce7;
  color: #15803d;
}
.practice-item__main {
  flex: 1;
  min-width: 0;
}
.practice-item__main strong {
  display: block;
  overflow: hidden;
  color: var(--ink);
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.practice-item__main small {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}
.practice-tag {
  padding: 1px 7px;
  border-radius: 999px;
  background: #f1f4f3;
  color: #52615e;
  font-size: 11px;
}
.practice-tag--focus {
  background: #eef2ff;
  color: #4338ca;
}
.practice-item__reason {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.practice-item__state {
  flex: none;
  color: #0f9b8e;
  font-size: 12px;
  font-weight: 650;
}
.practice-item.is-solved .practice-item__state {
  color: #15803d;
}
.practice-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 14px;
  padding-top: 13px;
  border-top: 1px solid #eef1f0;
  color: var(--muted);
  font-size: 12.5px;
}
.practice-start {
  min-height: 36px;
  padding: 0 16px;
  border: 0;
  border-radius: 9px;
  background: #0f9b8e;
  color: #fff;
  font-weight: 650;
  cursor: pointer;
}
.practice-start:hover {
  background: #0b857a;
}
@media (max-width: 680px) {
  .practice-page {
    padding: 20px 14px 40px;
  }
  .practice-heading {
    flex-direction: column;
  }
  .practice-stats {
    grid-template-columns: 1fr;
  }
  .practice-card {
    padding: 16px;
  }
  .practice-item__reason {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .practice-progress span,
  .practice-item {
    transition: none;
  }
}
</style>
