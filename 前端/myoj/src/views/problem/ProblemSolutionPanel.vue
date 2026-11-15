<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { renderMarkdownToHtml } from '@/utils/renderMarkdown'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import {
  questionSolutionApi,
  type SolutionComment,
  type SolutionDetail,
  type SolutionEligibility,
  type SolutionListItem,
  type SolutionSavePayload,
} from '@/api/questionSolution'

const props = defineProps<{ questionId: string; questionTitle?: string }>()
const auth = useAuthStore()
const loading = ref(false)
const keyword = ref('')
const eligibility = ref<SolutionEligibility>({
  accepted: false,
  canWrite: false,
  canViewFull: false,
  admin: false,
})
const solutions = ref<SolutionListItem[]>([])
const selected = ref<SolutionDetail | null>(null)
const comments = ref<SolutionComment[]>([])
const commentText = ref('')
const editorOpen = ref(false)
const saving = ref(false)
const reviewMode = ref(false)
const reviewRejectOpen = ref(false)
const reviewRejectReason = ref('')
const reviewSaving = ref(false)
const reviewRejectPresets = [
  '思路说明不够完整',
  '复杂度分析需要补充',
  '参考代码无法通过',
  '内容包含与本题无关的信息',
]
const form = reactive<SolutionSavePayload>({
  questionId: props.questionId,
  title: '',
  summary: '',
  hintContent: '',
  content: '',
})

const statusText = computed(() => {
  const s = selected.value?.reviewStatus
  return s === 0 ? '草稿' : s === 1 ? '待审核' : s === 2 ? '已发布' : s === 3 ? '已驳回' : ''
})
const fullHtml = computed(() => renderMarkdownToHtml(selected.value?.content || ''))
const hintHtml = computed(() => renderMarkdownToHtml(selected.value?.hintContent || ''))

function avatar(url?: string) {
  return resolveMediaUrl(url || '') || ''
}
function dateText(raw?: string) {
  if (!raw) return ''
  const d = new Date(raw)
  return Number.isNaN(d.getTime()) ? raw : d.toLocaleDateString('zh-CN')
}
function generatedTitle() {
  return `${props.questionTitle?.trim() || '本题'} · 解题思路`
}
function generatedSummary(content: string, hint: string) {
  const plain = (content || hint)
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/[#>*_`\[\]()~-]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  return (plain || '分享本题的分析过程、算法思路与实现方法').slice(0, 120)
}
function resetForm() {
  Object.assign(form, {
    id: undefined,
    questionId: props.questionId,
    title: generatedTitle(),
    summary: '分享本题的分析过程、算法思路与实现方法',
    hintContent: '',
    content: '',
  })
}

async function load() {
  if (!/^\d+$/.test(props.questionId)) return
  loading.value = true
  try {
    const [access, page] = await Promise.all([
      questionSolutionApi.eligibility(props.questionId),
      questionSolutionApi.page(props.questionId, keyword.value),
    ])
    eligibility.value = access
    solutions.value = (page.records || []).filter((item) => item.questionId === props.questionId)
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '题解加载失败')
  } finally {
    loading.value = false
  }
}

async function search() {
  await load()
}
async function openSolution(id: string, adminPending = false) {
  try {
    const detail = adminPending
      ? await questionSolutionApi.adminDetail(id)
      : await questionSolutionApi.detail(id)
    if (detail.questionId !== props.questionId) throw new Error('题解与当前题目不匹配，已阻止打开')
    selected.value = detail
    comments.value = []
    if (selected.value.accessLevel === 'FULL' && selected.value.reviewStatus === 2) {
      comments.value = (await questionSolutionApi.comments(id)).records || []
    }
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '题解打开失败')
  }
}

async function writeSolution() {
  if (!auth.isLoggedIn) return void Message.warning('请先登录，再完成本题后编写题解')
  if (!eligibility.value.canWrite) return void Message.warning('通过本题后才能编写题解')
  try {
    const mine = await questionSolutionApi.mine(props.questionId)
    resetForm()
    if (mine)
      Object.assign(form, {
        id: mine.id,
        questionId: props.questionId,
        title: mine.title,
        summary: mine.summary,
        hintContent: mine.hintContent,
        content: mine.content || '',
      })
    selected.value = mine
    editorOpen.value = true
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '无法打开题解编辑器')
  }
}

async function saveDraft(submit = false) {
  saving.value = true
  try {
    const payload = {
      ...form,
      questionId: props.questionId,
      title: generatedTitle(),
      summary: generatedSummary(form.content, form.hintContent),
    }
    const id = await questionSolutionApi.save(payload)
    form.id = id
    if (submit) {
      await questionSolutionApi.submitReview(id)
      Message.success('题解已提交审核，审核通过后会公开展示')
      editorOpen.value = false
    } else Message.success('草稿已保存')
    await load()
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function toggleLike() {
  if (!selected.value) return
  try {
    const result = await questionSolutionApi.toggleLike(selected.value.id)
    selected.value.liked = result.liked
    selected.value.likeCount = result.likeCount
  } catch (error) {
    Message.warning(error instanceof Error ? error.message : '通过本题后才能点赞')
  }
}

async function addComment() {
  if (!selected.value || !commentText.value.trim()) return
  try {
    await questionSolutionApi.addComment(selected.value.id, commentText.value)
    commentText.value = ''
    comments.value = (await questionSolutionApi.comments(selected.value.id)).records || []
    selected.value.commentCount += 1
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '评论失败')
  }
}

async function removeComment(item: SolutionComment) {
  try {
    await questionSolutionApi.deleteComment(item.id)
    comments.value = comments.value.filter((v) => v.id !== item.id)
    if (selected.value) selected.value.commentCount = Math.max(0, selected.value.commentCount - 1)
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '删除失败')
  }
}

async function loadReviewQueue() {
  try {
    reviewMode.value = true
    selected.value = null
    const page = await questionSolutionApi.adminPage(props.questionId, 1)
    solutions.value = (page.records || []).filter((item) => item.questionId === props.questionId)
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '审核队列加载失败')
  }
}

async function review(approved: boolean) {
  if (!selected.value) return
  if (!approved) {
    reviewRejectReason.value = ''
    reviewRejectOpen.value = true
    return
  }
  reviewSaving.value = true
  try {
    await questionSolutionApi.review(selected.value.id, true, '')
    Message.success('题解已发布')
    selected.value = null
    await loadReviewQueue()
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '审核失败')
  } finally {
    reviewSaving.value = false
  }
}

function useRejectPreset(reason: string) {
  reviewRejectReason.value = reason
}

function closeRejectDialog() {
  if (reviewSaving.value) return
  reviewRejectOpen.value = false
  reviewRejectReason.value = ''
}

async function confirmReject() {
  const detail = selected.value
  const reason = reviewRejectReason.value.trim()
  if (!detail) return
  if (reason.length < 4) return void Message.warning('请填写至少 4 个字的具体驳回原因')
  reviewSaving.value = true
  try {
    await questionSolutionApi.review(detail.id, false, reason)
    Message.success('已驳回题解，作者可以根据原因修改后重新提交')
    reviewRejectOpen.value = false
    reviewRejectReason.value = ''
    selected.value = null
    await loadReviewQueue()
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '驳回失败')
  } finally {
    reviewSaving.value = false
  }
}

async function setOfficial() {
  if (!selected.value) return
  try {
    await questionSolutionApi.official(selected.value.id, !selected.value.official)
    selected.value.official = !selected.value.official
    Message.success(selected.value.official ? '已设为官方题解' : '已取消官方标记')
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '操作失败')
  }
}

function closeDetail() {
  selected.value = null
  comments.value = []
}
watch(
  () => props.questionId,
  async () => {
    resetForm()
    selected.value = null
    reviewMode.value = false
    await load()
  },
)
onMounted(load)
</script>

<template>
  <section class="solution-panel" :aria-busy="loading">
    <header v-if="!selected" class="solution-toolbar">
      <div>
        <h2>{{ reviewMode ? '题解审核' : '题解列表' }}</h2>
        <span>共 {{ solutions.length }} 篇</span>
      </div>
      <div class="solution-toolbar__actions">
        <button
          v-if="eligibility.admin && !reviewMode"
          class="ghost-btn"
          type="button"
          @click="loadReviewQueue"
        >
          审核队列
        </button>
        <button
          v-if="reviewMode"
          class="ghost-btn"
          type="button"
          @click="
            reviewMode = false
            load()
          "
        >
          返回公开题解
        </button>
        <button
          v-if="!reviewMode"
          class="write-btn"
          type="button"
          :class="{ locked: !eligibility.canWrite }"
          @click="writeSolution"
        >
          ＋ 写题解
        </button>
      </div>
    </header>

    <div v-if="!selected && !reviewMode" class="solution-search">
      <span aria-hidden="true">⌕</span
      ><input v-model="keyword" placeholder="搜索题解标题或摘要" @keyup.enter="search" /><button
        type="button"
        @click="search"
      >
        搜索
      </button>
    </div>

    <div v-if="!selected && !eligibility.canViewFull && !reviewMode" class="unlock-notice">
      <span>🔒</span>
      <div>
        <strong>通过本题后可解锁完整题解</strong>
        <p>现在可以查看作者提供的安全提示，不会直接展示完整代码。</p>
      </div>
    </div>

    <div v-if="!selected && loading" class="solution-state">正在加载题解…</div>
    <div v-else-if="!selected && !solutions.length" class="solution-state solution-state--empty">
      <span>▤</span
      ><strong>{{ reviewMode ? '暂时没有待审核题解' : '这道题还没有已发布题解' }}</strong>
      <p>
        {{
          eligibility.canWrite
            ? '你已经通过本题，可以成为第一位分享解法的人。'
            : '先独立完成题目，通过后即可查看完整题解并分享自己的思路。'
        }}
      </p>
    </div>
    <div v-else-if="!selected" class="solution-list">
      <button
        v-for="item in solutions"
        :key="item.id"
        class="solution-card"
        type="button"
        @click="openSolution(item.id, reviewMode)"
      >
        <div class="solution-card__top">
          <h3>{{ item.authorName || '用户' }} 的解题思路</h3>
          <span v-if="item.official" class="official-badge">官方题解</span
          ><span v-if="reviewMode" class="pending-badge">待审核</span>
        </div>
        <p>{{ item.summary }}</p>
        <footer>
          <span class="author"
            ><span class="avatar"
              ><img v-if="avatar(item.authorAvatar)" :src="avatar(item.authorAvatar)" alt="" />{{
                avatar(item.authorAvatar) ? '' : (item.authorName || '用').slice(0, 1)
              }}</span
            >{{ item.authorName || '用户' }}</span
          ><span>◉ {{ item.viewCount || 0 }}</span
          ><span>♡ {{ item.likeCount || 0 }}</span
          ><span>💬 {{ item.commentCount || 0 }}</span
          ><time>{{ dateText(item.publishedAt || item.createTime) }}</time>
        </footer>
      </button>
    </div>

    <div v-if="selected" class="solution-detail">
      <article class="solution-reader">
        <header class="solution-detail__nav">
          <button class="back-button" type="button" @click="closeDetail">
            <span aria-hidden="true">←</span> 返回题解列表
          </button>
          <div class="solution-detail__badges">
            <span v-if="selected.official" class="official-badge">✓ 官方题解</span>
            <span v-if="reviewMode || selected.mine" class="pending-badge">{{ statusText }}</span>
          </div>
        </header>

        <header class="solution-detail__hero">
          <p class="solution-detail__eyebrow">{{ reviewMode ? '题解审核' : '解题思路' }}</p>
          <h2>{{ selected.title }}</h2>
          <p class="solution-detail__summary">{{ selected.summary }}</p>
          <div class="solution-detail__author">
            <span class="avatar avatar--large"
              ><img
                v-if="avatar(selected.authorAvatar)"
                :src="avatar(selected.authorAvatar)"
                alt=""
              />{{
                avatar(selected.authorAvatar) ? '' : (selected.authorName || '用').slice(0, 1)
              }}</span
            >
            <div>
              <strong>{{ selected.authorName || '用户' }}</strong
              ><span
                >{{ reviewMode ? '提交于' : '发布于' }}
                {{ dateText(selected.publishedAt || selected.createTime) }}</span
              >
            </div>
            <div class="solution-detail__metrics">
              <span>◉ {{ selected.viewCount || 0 }} 阅读</span
              ><span>♡ {{ selected.likeCount || 0 }} 点赞</span
              ><span>💬 {{ selected.commentCount || 0 }} 讨论</span>
            </div>
          </div>
        </header>

        <div v-if="reviewMode" class="review-notice">
          <span>!</span>
          <div>
            <strong>这篇题解正在等待审核</strong>
            <p>请检查安全提示是否泄露完整答案，并确认正文思路、复杂度与代码说明清晰。</p>
          </div>
        </div>

        <section v-if="selected.accessLevel === 'FULL'" class="solution-content-card">
          <div class="solution-content-card__title"><span>▤</span><strong>题解正文</strong></div>
          <div class="solution-markdown" v-html="fullHtml" />
        </section>
        <section v-else class="hint-view">
          <div class="hint-view__head">
            <span>💡</span>
            <div>
              <strong>安全提示</strong>
              <p>以下内容只提供思路方向，不包含可直接提交的完整答案。</p>
            </div>
          </div>
          <div class="solution-markdown" v-html="hintHtml" />
          <div class="hint-lock">🔒 通过本题后可查看完整题解、参考代码和讨论区</div>
        </section>

        <div
          v-if="selected.accessLevel === 'FULL' && selected.reviewStatus === 2"
          class="solution-actions"
        >
          <button type="button" :class="{ liked: selected.liked }" @click="toggleLike">
            ♡ {{ selected.liked ? '已点赞' : '点赞' }} · {{ selected.likeCount }}</button
          ><button v-if="eligibility.admin" type="button" @click="setOfficial">
            {{ selected.official ? '取消官方' : '设为官方题解' }}
          </button>
        </div>
        <section
          v-if="selected.accessLevel === 'FULL' && selected.reviewStatus === 2"
          class="comments"
        >
          <h3>讨论 {{ selected.commentCount }}</h3>
          <div class="comment-compose">
            <textarea
              v-model="commentText"
              maxlength="2000"
              placeholder="交流思路和复杂度，请不要粘贴无关内容"
            /><button type="button" @click="addComment">发表评论</button>
          </div>
          <article v-for="item in comments" :key="item.id">
            <span class="avatar"
              ><img v-if="avatar(item.authorAvatar)" :src="avatar(item.authorAvatar)" alt="" />{{
                avatar(item.authorAvatar) ? '' : (item.authorName || '用').slice(0, 1)
              }}</span
            >
            <div>
              <header>
                <strong>{{ item.authorName || '用户' }}</strong
                ><time>{{ dateText(item.createTime) }}</time>
              </header>
              <p>{{ item.content }}</p>
              <button
                v-if="item.mine || eligibility.admin"
                type="button"
                @click="removeComment(item)"
              >
                删除
              </button>
            </div>
          </article>
        </section>
      </article>

      <div v-if="reviewMode && eligibility.admin" class="review-actions">
        <span>审核后将立即影响用户是否能看到完整题解</span
        ><button type="button" class="reject" :disabled="reviewSaving" @click="review(false)">
          驳回并说明原因</button
        ><button type="button" class="approve" :disabled="reviewSaving" @click="review(true)">
          {{ reviewSaving ? '处理中…' : '审核通过并发布' }}
        </button>
      </div>
    </div>

    <Teleport to="body">
      <div
        v-if="editorOpen"
        class="editor-modal"
        role="dialog"
        aria-modal="true"
        aria-label="题解编辑器"
        @click.self="editorOpen = false"
      >
        <div class="editor-sheet">
          <header class="editor-sheet__head">
            <div>
              <span class="editor-sheet__eyebrow">WRITE A SOLUTION</span>
              <h2>分享这道题的解法</h2>
              <p>{{ questionTitle || '当前题目' }} · 审核通过后向符合权限的用户展示</p>
            </div>
            <button type="button" aria-label="关闭题解编辑器" @click="editorOpen = false">×</button>
          </header>
          <div class="editor-sheet__guide">
            <span>1</span>
            <div>
              <strong>先给一个方向</strong>
              <p>这部分提供给尚未通过的同学，只写思路入口和边界提醒，不放完整代码。</p>
            </div>
          </div>
          <label class="editor-field"
            ><span
              >思路提示 <small>{{ form.hintContent.length }} / 5000</small></span
            ><textarea
              v-model="form.hintContent"
              maxlength="5000"
              rows="6"
              placeholder="例如：先观察数据范围，考虑使用双指针维护区间；注意空数组和重复元素……"
            />
          </label>
          <div class="editor-sheet__guide editor-sheet__guide--full">
            <span>2</span>
            <div>
              <strong>完整讲清楚你的解法</strong>
              <p>建议包含核心思路、正确性说明、复杂度分析和参考代码，支持 Markdown。</p>
            </div>
          </div>
          <label class="editor-field"
            ><span
              >完整题解 <small>{{ form.content.length }} / 100000</small></span
            ><textarea
              v-model="form.content"
              maxlength="100000"
              rows="16"
              placeholder="## 思路&#10;&#10;说明为什么这样做……&#10;&#10;## 复杂度&#10;&#10;- 时间复杂度：&#10;- 空间复杂度：&#10;&#10;## 参考代码&#10;&#10;```cpp&#10;// 在这里写代码&#10;```"
            />
          </label>
          <p v-if="selected?.reviewReason" class="reject-reason">
            上次驳回原因：{{ selected.reviewReason }}
          </p>
          <footer>
            <p>保存草稿不会公开，提交后由管理员审核。</p>
            <div>
              <button type="button" class="ghost-btn" :disabled="saving" @click="saveDraft(false)">
                保存草稿</button
              ><button type="button" class="write-btn" :disabled="saving" @click="saveDraft(true)">
                {{ saving ? '保存中…' : '提交审核' }}
              </button>
            </div>
          </footer>
        </div>
      </div>
    </Teleport>

    <Teleport to="body">
      <div
        v-if="reviewRejectOpen"
        class="review-reject-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="review-reject-title"
        @click.self="closeRejectDialog"
      >
        <section class="review-reject-card">
          <header>
            <div class="review-reject-card__mark" aria-hidden="true">!</div>
            <div>
              <p>题解审核</p>
              <h2 id="review-reject-title">驳回并给出修改建议</h2>
            </div>
            <button
              type="button"
              aria-label="关闭"
              :disabled="reviewSaving"
              @click="closeRejectDialog"
            >
              ×
            </button>
          </header>
          <div class="review-reject-card__context">
            <span>当前题解</span>
            <strong>{{ selected?.title || '解题思路' }}</strong>
            <small>作者：{{ selected?.authorName || '用户' }}</small>
          </div>
          <div class="review-reject-card__presets">
            <span>常用原因</span>
            <button
              v-for="reason in reviewRejectPresets"
              :key="reason"
              type="button"
              :class="{ active: reviewRejectReason === reason }"
              @click="useRejectPreset(reason)"
            >
              {{ reason }}
            </button>
          </div>
          <label class="review-reject-card__field">
            <span
              ><strong>具体原因</strong><small>{{ reviewRejectReason.length }} / 500</small></span
            >
            <textarea
              v-model="reviewRejectReason"
              maxlength="500"
              rows="5"
              autofocus
              placeholder="请说明需要修改的内容，帮助作者快速完善题解……"
              @keydown.ctrl.enter.prevent="confirmReject"
            />
          </label>
          <p class="review-reject-card__tip">驳回后题解不会公开，作者可在修改后重新提交审核。</p>
          <footer>
            <button
              type="button"
              class="ghost-btn"
              :disabled="reviewSaving"
              @click="closeRejectDialog"
            >
              取消</button
            ><button
              type="button"
              class="review-reject-card__confirm"
              :disabled="reviewSaving || reviewRejectReason.trim().length < 4"
              @click="confirmReject"
            >
              {{ reviewSaving ? '正在驳回…' : '确认驳回' }}
            </button>
          </footer>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.solution-panel {
  position: relative;
  min-height: 100%;
  padding: 18px;
  color: var(--app-text);
  background: var(--app-surface);
}
.solution-toolbar,
.solution-toolbar > div,
.solution-toolbar__actions,
.solution-card__top,
.solution-card footer,
.solution-detail > header,
.solution-detail__author,
.solution-actions,
.review-actions,
.editor-sheet > header,
.editor-sheet > footer,
.comments article,
.comments article header {
  display: flex;
  align-items: center;
}
.solution-toolbar {
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}
.solution-toolbar h2 {
  margin: 0;
  font-size: 20px;
}
.solution-toolbar > div:first-child {
  gap: 10px;
}
.solution-toolbar span {
  color: var(--app-text-muted);
  font-size: 13px;
}
.solution-toolbar__actions {
  gap: 9px;
}
.write-btn,
.ghost-btn,
.solution-search button,
.comment-compose button {
  min-height: 40px;
  border: 1px solid var(--app-accent);
  border-radius: 10px;
  padding: 0 15px;
  font-weight: 800;
  cursor: pointer;
}
.write-btn,
.solution-search button,
.comment-compose button {
  background: var(--app-accent);
  color: #fff;
}
.write-btn.locked {
  border-color: var(--app-border);
  background: var(--app-surface-muted);
  color: var(--app-text-muted);
}
.ghost-btn {
  background: var(--app-surface);
  color: var(--app-accent);
}
.solution-search {
  display: grid;
  grid-template-columns: 24px 1fr auto;
  align-items: center;
  max-width: 620px;
  margin-bottom: 14px;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  background: var(--app-surface);
  padding-left: 12px;
}
.solution-search input {
  height: 42px;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--app-text);
}
.solution-search button {
  border-radius: 0 11px 11px 0;
}
.unlock-notice {
  display: flex;
  gap: 12px;
  margin-bottom: 14px;
  border: 1px solid color-mix(in srgb, var(--app-accent) 35%, var(--app-border));
  border-radius: 13px;
  padding: 12px 14px;
  background: color-mix(in srgb, var(--app-accent) 7%, var(--app-surface));
}
.unlock-notice strong,
.unlock-notice p {
  display: block;
  margin: 0;
}
.unlock-notice p {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.solution-list {
  display: grid;
  gap: 11px;
}
.solution-card {
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: 14px;
  padding: 16px;
  text-align: left;
  background: var(--app-surface);
  color: var(--app-text);
  cursor: pointer;
  transition:
    border-color 0.16s,
    background 0.16s;
}
.solution-card:hover {
  border-color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 4%, var(--app-surface));
}
.solution-card__top {
  justify-content: space-between;
  gap: 12px;
}
.solution-card h3 {
  margin: 0;
  font-size: 16px;
}
.solution-card > p {
  margin: 8px 0 14px;
  color: var(--app-text-muted);
  line-height: 1.6;
}
.solution-card footer {
  gap: 14px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.solution-card footer time {
  margin-left: auto;
}
.author {
  display: flex;
  align-items: center;
  gap: 7px;
  margin-right: auto;
  color: var(--app-text);
}
.avatar {
  display: inline-grid;
  width: 28px;
  height: 28px;
  place-items: center;
  overflow: hidden;
  border-radius: 50%;
  background: color-mix(in srgb, var(--app-accent) 15%, var(--app-surface));
  color: var(--app-accent);
  font-weight: 800;
}
.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.official-badge,
.pending-badge {
  display: inline-flex;
  border-radius: 6px;
  padding: 4px 8px;
  font-size: 11px;
  font-weight: 800;
}
.official-badge {
  background: #16b978;
  color: #fff;
}
.pending-badge {
  background: #fff0d4;
  color: #a85b00;
}
.solution-state {
  padding: 55px 18px;
  text-align: center;
  color: var(--app-text-muted);
}
.solution-state--empty {
  display: grid;
  place-items: center;
  gap: 8px;
  border: 1px dashed var(--app-border);
  border-radius: 15px;
}
.solution-state--empty > span {
  font-size: 32px;
  color: var(--app-accent);
}
.solution-state--empty strong {
  color: var(--app-text);
  font-size: 17px;
}
.solution-state--empty p {
  margin: 0;
}
.solution-detail {
  position: absolute;
  z-index: 4;
  inset: 0;
  overflow: auto;
  padding: 20px;
  background: var(--app-surface);
}
.solution-detail > header {
  justify-content: space-between;
}
.solution-detail > header > button {
  border: 0;
  background: transparent;
  color: var(--app-accent);
  font-weight: 800;
  cursor: pointer;
}
.solution-detail h2 {
  margin: 24px 0 8px;
  font-size: 25px;
}
.solution-detail__summary {
  color: var(--app-text-muted);
}
.solution-detail__author {
  gap: 9px;
  margin: 14px 0 22px;
}
.solution-detail__author > span:last-child {
  color: var(--app-text-muted);
  font-size: 12px;
}
.solution-markdown {
  font-size: 15px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.solution-markdown :deep(pre) {
  overflow: auto;
  border-radius: 10px;
  padding: 13px;
  background: #102733;
  color: #e6f4f1;
}
.solution-markdown :deep(img) {
  max-width: 100%;
  border-radius: 10px;
}
.hint-view {
  border: 1px solid color-mix(in srgb, #d8901d 35%, var(--app-border));
  border-radius: 14px;
  padding: 16px;
  background: color-mix(in srgb, #f2b84b 7%, var(--app-surface));
}
.hint-view__head {
  display: flex;
  gap: 10px;
}
.hint-view__head strong,
.hint-view__head p {
  display: block;
  margin: 0;
}
.hint-view__head p {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.hint-lock {
  margin-top: 16px;
  border-top: 1px solid var(--app-border);
  padding-top: 12px;
  text-align: center;
  color: var(--app-text-muted);
  font-size: 12px;
}
.solution-actions,
.review-actions {
  gap: 10px;
  margin: 20px 0;
}
.solution-actions button,
.review-actions button {
  min-height: 40px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 0 14px;
  background: var(--app-surface);
  color: var(--app-text);
  cursor: pointer;
}
.solution-actions button.liked {
  border-color: #e05a68;
  color: #c63d4a;
}
.review-actions {
  justify-content: flex-end;
}
.review-actions .reject {
  border-color: #d85151;
  color: #b42318;
}
.review-actions .approve {
  border-color: #15966f;
  background: #15966f;
  color: #fff;
}
.comments {
  margin-top: 22px;
  border-top: 1px solid var(--app-border);
  padding-top: 18px;
}
.comment-compose {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
}
.comment-compose textarea {
  min-height: 76px;
  resize: vertical;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 10px;
  background: var(--app-surface);
  color: var(--app-text);
}
.comments article {
  align-items: flex-start;
  gap: 10px;
  border-bottom: 1px solid var(--app-border);
  padding: 13px 0;
}
.comments article > div {
  flex: 1;
}
.comments article header {
  gap: 10px;
}
.comments article time {
  color: var(--app-text-muted);
  font-size: 11px;
}
.comments article p {
  margin: 6px 0;
  white-space: pre-wrap;
}
.comments article button {
  border: 0;
  background: transparent;
  color: #b42318;
  font-size: 11px;
  cursor: pointer;
}
.editor-modal {
  position: fixed;
  z-index: 1200;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(20, 27, 26, 0.48);
}
.editor-sheet {
  width: min(820px, 96vw);
  max-height: 92vh;
  overflow: auto;
  border-radius: 18px;
  padding: 20px;
  background: var(--app-surface);
  box-shadow: 0 24px 70px rgba(0, 0, 0, 0.24);
}
.editor-sheet > header {
  justify-content: space-between;
}
.editor-sheet h2,
.editor-sheet p {
  margin: 0;
}
.editor-sheet header p {
  margin-top: 4px;
  color: var(--app-text-muted);
  font-size: 12px;
}
.editor-sheet header > button {
  border: 0;
  background: transparent;
  color: var(--app-text-muted);
  font-size: 26px;
  cursor: pointer;
}
.editor-sheet label {
  display: grid;
  gap: 6px;
  margin-top: 14px;
  font-weight: 800;
}
.editor-sheet label small {
  color: var(--app-text-muted);
  font-weight: 400;
}
.editor-sheet input,
.editor-sheet textarea {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 10px 12px;
  background: var(--app-surface);
  color: var(--app-text);
  font: inherit;
}
.editor-sheet textarea {
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  line-height: 1.55;
}
.editor-sheet > footer {
  justify-content: flex-end;
  gap: 10px;
  margin-top: 16px;
}
.reject-reason {
  margin-top: 12px !important;
  border-radius: 9px;
  padding: 9px;
  background: #fff1f0;
  color: #b42318;
}
.editor-sheet button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
@media (max-width: 700px) {
  .solution-panel {
    padding: 12px;
  }
  .solution-toolbar {
    align-items: flex-start;
  }
  .solution-toolbar__actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }
  .solution-card footer {
    flex-wrap: wrap;
  }
  .solution-card footer time {
    width: 100%;
    margin-left: 36px;
  }
  .comment-compose {
    grid-template-columns: 1fr;
  }
  .editor-modal {
    padding: 8px;
  }
  .solution-detail {
    padding: 15px;
  }
}
/* 题解详情使用正常文档流，由左侧题面区域负责滚动，避免绝对覆盖造成高度截断。 */
.solution-detail {
  position: relative;
  inset: auto;
  min-height: 100%;
  overflow: visible;
  padding: 0;
  background: var(--app-bg);
  scrollbar-gutter: stable;
}
.solution-detail__nav {
  position: relative;
  display: flex;
  z-index: auto;
  top: auto;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  margin: 0 0 12px;
  padding: 0 2px;
  background: transparent;
}
.back-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 40px;
  border: 1px solid var(--app-border);
  border-radius: 11px;
  padding: 0 13px;
  background: var(--app-surface-strong);
  color: var(--app-accent);
  font-weight: 800;
  cursor: pointer;
  box-shadow: 0 3px 10px rgba(70, 41, 14, 0.04);
  transition:
    border-color 0.16s ease,
    background 0.16s ease,
    transform 0.16s ease;
}
.back-button:hover {
  border-color: color-mix(in srgb, var(--app-accent) 40%, var(--app-border));
  background: color-mix(in srgb, var(--app-accent) 7%, var(--app-surface-strong));
  transform: translateX(-2px);
}
.back-button:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}
.back-button span {
  font-size: 18px;
}
.solution-detail__badges {
  display: flex;
  align-items: center;
  gap: 8px;
}
.solution-reader {
  width: min(880px, calc(100% - 32px));
  margin: 18px auto 72px;
}
.solution-detail__hero {
  border: 1px solid var(--app-border);
  border-radius: 18px;
  padding: 25px 28px;
  background: var(--app-surface-strong);
  box-shadow: 0 16px 38px rgba(70, 41, 14, 0.07);
}
.solution-detail__eyebrow {
  margin: 0 0 8px;
  color: var(--app-accent);
  font-size: 12px;
  font-weight: 900;
  letter-spacing: 0.12em;
}
.solution-detail .solution-detail__hero h2 {
  margin: 0;
  font-size: clamp(23px, 3vw, 31px);
  line-height: 1.25;
  letter-spacing: -0.02em;
}
.solution-detail__summary {
  max-width: 720px;
  margin: 10px 0 0;
  color: var(--app-text-muted);
  font-size: 15px;
  line-height: 1.7;
}
.solution-detail__author {
  gap: 11px;
  margin: 22px 0 0;
  border-top: 1px solid var(--app-border);
  padding-top: 17px;
}
.avatar--large {
  width: 38px;
  height: 38px;
}
.solution-detail__author > div:nth-child(2) {
  display: grid;
  gap: 3px;
}
.solution-detail__author > div:nth-child(2) > span {
  color: var(--app-text-muted);
  font-size: 11px;
}
.solution-detail__metrics {
  display: flex !important;
  gap: 14px !important;
  margin-left: auto;
  color: var(--app-text-muted);
  font-size: 12px;
}
.review-notice {
  display: flex;
  gap: 11px;
  margin: 16px 0;
  border: 1px solid rgba(217, 119, 6, 0.3);
  border-radius: 13px;
  padding: 13px 15px;
  background: #fff7e7;
  color: #7a4510;
}
.review-notice > span {
  display: grid;
  width: 26px;
  height: 26px;
  flex: none;
  place-items: center;
  border-radius: 50%;
  background: #f59e0b;
  color: #fff;
  font-weight: 900;
}
.review-notice strong,
.review-notice p {
  display: block;
  margin: 0;
}
.review-notice p {
  margin-top: 3px;
  font-size: 12px;
  line-height: 1.55;
}
.solution-content-card {
  margin-top: 16px;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  padding: 25px 28px;
  background: var(--app-surface-strong);
  box-shadow: 0 14px 34px rgba(70, 41, 14, 0.05);
}
.solution-content-card__title {
  display: flex;
  align-items: center;
  gap: 9px;
  margin-bottom: 19px;
  border-bottom: 1px solid var(--app-border);
  padding-bottom: 13px;
  color: var(--app-text);
}
.solution-content-card__title > span {
  color: var(--app-accent);
  font-size: 19px;
}
.solution-markdown {
  line-height: 1.85;
}
.solution-markdown :deep(h1),
.solution-markdown :deep(h2),
.solution-markdown :deep(h3) {
  margin-top: 1.5em;
  line-height: 1.4;
}
.solution-markdown :deep(p) {
  margin: 0.8em 0;
}
.solution-markdown :deep(pre) {
  border: 1px solid rgba(98, 199, 183, 0.16);
  border-radius: 12px;
  padding: 15px;
}
.solution-markdown :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
}
.solution-detail .hint-view {
  margin-top: 16px;
  border-radius: 16px;
  padding: 20px;
  background: color-mix(in srgb, #f2b84b 8%, var(--app-surface-strong));
}
.solution-detail .solution-actions {
  margin: 18px 0;
}
.solution-detail .comments {
  margin-top: 22px;
  border: 1px solid var(--app-border);
  border-radius: 16px;
  padding: 18px 20px;
  background: var(--app-surface-strong);
}
.solution-detail .comment-compose textarea {
  background: var(--app-bg);
}
.review-actions {
  position: sticky;
  z-index: 6;
  bottom: 0;
  justify-content: flex-end;
  gap: 10px;
  margin: 0;
  border-top: 1px solid var(--app-border);
  padding: 12px 22px;
  background: color-mix(in srgb, var(--app-bg) 96%, #fff 4%);
  box-shadow: 0 -10px 30px rgba(70, 41, 14, 0.08);
}
.review-actions > span {
  margin-right: auto;
  color: var(--app-text-muted);
  font-size: 12px;
}
.review-actions button {
  min-height: 42px;
  border-radius: 11px;
  padding: 0 15px;
  font-weight: 700;
}
.review-reject-modal {
  position: fixed;
  z-index: 1400;
  inset: 0;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(31, 35, 34, 0.56);
  backdrop-filter: blur(9px);
}
.review-reject-card {
  box-sizing: border-box;
  width: min(560px, calc(100vw - 32px));
  overflow: hidden;
  border: 1px solid var(--app-border);
  border-radius: 20px;
  background: var(--app-bg);
  box-shadow: 0 28px 90px rgba(20, 25, 24, 0.3);
}
.review-reject-card > header {
  display: grid;
  grid-template-columns: 44px 1fr 40px;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--app-border);
  padding: 20px 22px;
  background: var(--app-surface-strong);
}
.review-reject-card__mark {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 13px;
  background: #fff0e8;
  color: #c2410c;
  font-size: 21px;
  font-weight: 900;
}
.review-reject-card > header p,
.review-reject-card > header h2 {
  margin: 0;
}
.review-reject-card > header p {
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 800;
}
.review-reject-card > header h2 {
  margin-top: 3px;
  font-size: 19px;
}
.review-reject-card > header > button {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: var(--app-text-muted);
  font-size: 24px;
  cursor: pointer;
}
.review-reject-card > header > button:hover {
  background: var(--app-hover);
  color: var(--app-text);
}
.review-reject-card__context {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 3px 10px;
  margin: 18px 22px 0;
  border: 1px solid var(--app-border);
  border-radius: 12px;
  padding: 12px 14px;
  background: var(--app-surface-strong);
}
.review-reject-card__context span {
  grid-row: 1/3;
  color: var(--app-text-muted);
  font-size: 11px;
}
.review-reject-card__context strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.review-reject-card__context small {
  color: var(--app-text-muted);
}
.review-reject-card__presets {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 17px 22px 0;
}
.review-reject-card__presets > span {
  width: 100%;
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 800;
}
.review-reject-card__presets button {
  min-height: 34px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  padding: 0 12px;
  background: var(--app-surface-strong);
  color: var(--app-text-muted);
  cursor: pointer;
  font-size: 12px;
}
.review-reject-card__presets button:hover,
.review-reject-card__presets button.active {
  border-color: #e07a4f;
  background: #fff1e9;
  color: #a63f19;
}
.review-reject-card__field {
  display: grid;
  gap: 8px;
  margin: 17px 22px 0;
}
.review-reject-card__field > span {
  display: flex;
  justify-content: space-between;
}
.review-reject-card__field small {
  color: var(--app-text-muted);
  font-weight: 400;
}
.review-reject-card__field textarea {
  box-sizing: border-box;
  width: 100%;
  min-height: 116px;
  resize: vertical;
  border: 1px solid var(--app-border-strong);
  border-radius: 12px;
  padding: 12px 14px;
  background: var(--app-surface-strong);
  color: var(--app-text);
  font: inherit;
  line-height: 1.65;
  outline: 0;
}
.review-reject-card__field textarea:focus {
  border-color: #d95f32;
  box-shadow: 0 0 0 3px rgba(217, 95, 50, 0.12);
}
.review-reject-card__tip {
  margin: 10px 22px 0;
  color: var(--app-text-muted);
  font-size: 12px;
}
.review-reject-card > footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  border-top: 1px solid var(--app-border);
  padding: 14px 22px;
  background: var(--app-surface-strong);
}
.review-reject-card > footer button {
  min-height: 42px;
  border-radius: 11px;
  padding: 0 18px;
  font-weight: 800;
  cursor: pointer;
}
.review-reject-card__confirm {
  border: 1px solid #c94727;
  background: #c94727;
  color: #fff;
}
.review-reject-card__confirm:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.editor-sheet {
  border: 1px solid var(--app-border);
  background: var(--app-bg);
}
.editor-sheet input,
.editor-sheet textarea {
  background: var(--app-surface-strong);
}
html[data-theme='dark'] .review-notice {
  border-color: rgba(251, 191, 36, 0.28);
  background: #322817;
  color: #ffe7b0;
}
@media (max-width: 700px) {
  .solution-detail__nav {
    margin-bottom: 10px;
    padding: 0;
  }
  .back-button {
    padding: 0 11px;
  }
  .solution-reader {
    width: calc(100% - 20px);
    margin-top: 10px;
    margin-bottom: 48px;
  }
  .solution-detail__hero,
  .solution-content-card {
    border-radius: 14px;
    padding: 18px;
  }
  .solution-detail__author {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .solution-detail__metrics {
    width: 100%;
    margin-left: 49px;
  }
  .review-actions {
    flex-wrap: wrap;
    padding: 10px;
  }
  .review-actions > span {
    width: 100%;
    margin: 0;
  }
  .review-actions button {
    flex: 1;
  }
  .solution-detail__badges .official-badge {
    display: none;
  }
}
/* 全页面题解创作器：通过 Teleport 脱离左右分栏。 */
.editor-modal {
  overflow: auto;
  padding: 32px;
  background: rgba(24, 29, 28, 0.58);
  backdrop-filter: blur(10px);
}
.editor-sheet {
  width: min(900px, calc(100vw - 32px));
  max-height: none;
  margin: auto;
  border-radius: 22px;
  padding: 0;
  background: var(--app-bg);
  box-shadow: 0 30px 90px rgba(0, 0, 0, 0.3);
}
.editor-sheet__head {
  position: sticky;
  z-index: 2;
  top: 0;
  justify-content: space-between;
  border-bottom: 1px solid var(--app-border);
  border-radius: 22px 22px 0 0;
  padding: 22px 28px 18px;
  background: color-mix(in srgb, var(--app-bg) 96%, #fff 4%);
}
.editor-sheet__eyebrow {
  display: block;
  margin-bottom: 5px;
  color: var(--app-accent);
  font-size: 10px;
  font-weight: 900;
  letter-spacing: 0.14em;
}
.editor-sheet__head h2 {
  font-size: 24px;
  line-height: 1.25;
}
.editor-sheet__head > button {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 50%;
  background: var(--app-surface-strong);
  font-size: 22px;
}
.editor-sheet__guide {
  display: flex;
  gap: 12px;
  margin: 24px 28px 0;
  border-radius: 14px;
  padding: 14px 16px;
  background: color-mix(in srgb, var(--app-accent) 7%, var(--app-surface-strong));
}
.editor-sheet__guide > span {
  display: grid;
  width: 28px;
  height: 28px;
  flex: none;
  place-items: center;
  border-radius: 9px;
  background: var(--app-accent);
  color: #fff;
  font-weight: 900;
}
.editor-sheet__guide strong,
.editor-sheet__guide p {
  display: block;
  margin: 0;
}
.editor-sheet__guide p {
  margin-top: 3px;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.55;
}
.editor-sheet__guide--full {
  background: color-mix(in srgb, var(--app-accent-2) 8%, var(--app-surface-strong));
}
.editor-sheet__guide--full > span {
  background: var(--app-accent-2);
}
.editor-field {
  margin: 14px 28px 0 !important;
}
.editor-field > span {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.editor-field > span small {
  font-size: 11px;
}
.editor-field textarea {
  min-height: 150px;
  border-color: var(--app-border-strong);
  padding: 14px 16px;
  line-height: 1.7;
  transition:
    border-color 0.16s,
    box-shadow 0.16s;
}
.editor-field textarea:focus {
  border-color: var(--app-accent);
  outline: 0;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--app-accent) 12%, transparent);
}
.editor-field:nth-of-type(2) textarea {
  min-height: 310px;
}
.editor-sheet > .reject-reason {
  margin: 14px 28px 0 !important;
}
.editor-sheet > footer {
  position: sticky;
  bottom: 0;
  justify-content: space-between;
  margin-top: 24px;
  border-top: 1px solid var(--app-border);
  border-radius: 0 0 22px 22px;
  padding: 15px 28px;
  background: color-mix(in srgb, var(--app-bg) 96%, #fff 4%);
}
.editor-sheet > footer > p {
  color: var(--app-text-muted);
  font-size: 12px;
}
.editor-sheet > footer > div {
  display: flex;
  gap: 10px;
}
@media (max-width: 700px) {
  .editor-modal {
    padding: 0;
  }
  .editor-sheet {
    width: 100%;
    min-height: 100dvh;
    border: 0;
    border-radius: 0;
  }
  .editor-sheet__head {
    border-radius: 0;
    padding: 18px 16px;
  }
  .editor-sheet__guide,
  .editor-field {
    margin-right: 16px !important;
    margin-left: 16px !important;
  }
  .editor-sheet > footer {
    align-items: stretch;
    flex-direction: column;
    border-radius: 0;
    padding: 12px 16px;
  }
  .editor-sheet > footer > div {
    display: grid;
    grid-template-columns: 1fr 1fr;
  }
  .editor-sheet > footer button {
    width: 100%;
  }
}
@media (max-width: 560px) {
  .review-reject-modal {
    align-items: end;
    padding: 0;
  }
  .review-reject-card {
    width: 100%;
    border-right: 0;
    border-bottom: 0;
    border-left: 0;
    border-radius: 20px 20px 0 0;
  }
  .review-reject-card > header {
    padding: 17px 16px;
  }
  .review-reject-card__context,
  .review-reject-card__presets,
  .review-reject-card__field {
    margin-right: 16px;
    margin-left: 16px;
  }
  .review-reject-card__tip {
    margin-right: 16px;
    margin-left: 16px;
  }
  .review-reject-card > footer {
    padding: 12px 16px;
  }
  .review-reject-card > footer button {
    flex: 1;
  }
}
</style>
