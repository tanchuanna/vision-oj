<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { Message } from '@arco-design/web-vue'
import { OpenAPI } from '@generated'
import { useAuthStore } from '@/stores/auth'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import UserProfileModal from '@/components/teaching/UserProfileModal.vue'
import {
  approveRelation,
  fetchPendingRelations,
  inviteStudent,
  rejectRelation,
  type RelationVO,
} from '@/api/teachingRelations'
import { practiceAssignmentsApi, type PracticeAssignResult } from '@/api/practiceAssignments'

type Student = {
  id: string
  username: string
  nickname?: string
  avatar?: string
  accountStatus: number
  submitCount: number
  acceptedCount: number
  solvedCount: number
  passRate: number
  lastActiveTime?: string
  online?: boolean
}

const auth = useAuthStore()
const students = ref<Student[]>([])
const keyword = ref('')
const appliedKeyword = ref('')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(15)
const totalCount = ref(0)
const viewMode = ref<'list' | 'cards'>('list')
let loadSequence = 0
const failedAvatars = ref<string[]>([])

const modalVisible = ref(false)
const modalStudentId = ref('')
const modalOnline = ref(false)

// 邀请学生搜索
const inviteOpen = ref(false)
const inviteKeyword = ref('')
const inviteLoading = ref(false)
const inviteResults = ref<Student[]>([])
const invitingIds = ref<string[]>([])

// 待我审批的学生申请
const pendingList = ref<RelationVO[]>([])
const pendingActingId = ref('')

// 一键布置练习
const assignVisible = ref(false)
const assignTargets = ref<Student[]>([])
const assignCount = ref(5)
const assignTitle = ref('')
const assignRemark = ref('')
const assigning = ref(false)
const assignResult = ref<PracticeAssignResult | null>(null)
const assignSubtitle = computed(() => {
  const first = assignTargets.value[0]
  if (assignTargets.value.length === 1 && first) {
    return `为 ${displayName(first)} 生成一份个性化练习`
  }
  return `为当前页 ${assignTargets.value.length} 位学生各生成一份练习`
})

const totalStudents = computed(() => totalCount.value)
const totalPages = computed(() => Math.max(1, Math.ceil(totalCount.value / pageSize.value)))
const pageStart = computed(() =>
  totalCount.value ? (currentPage.value - 1) * pageSize.value + 1 : 0,
)
const pageEnd = computed(() => Math.min(totalCount.value, currentPage.value * pageSize.value))
const activeStudents = computed(
  () => students.value.filter((item) => item.accountStatus !== 0).length,
)
const onlineStudents = computed(() => students.value.filter((item) => item.online).length)
const totalSolved = computed(() =>
  students.value.reduce((sum, item) => sum + Number(item.solvedCount || 0), 0),
)
const averagePassRate = computed(() => {
  if (!students.value.length) return 0
  return Math.round(
    students.value.reduce((sum, item) => sum + normalizedRate(item.passRate), 0) /
      students.value.length,
  )
})

function normalizedRate(value?: number) {
  const rate = Number(value || 0)
  return Math.max(0, Math.min(100, rate <= 1 && rate > 0 ? rate * 100 : rate))
}
function displayName(student: Student) {
  return student.nickname?.trim() || student.username?.trim() || '未命名学生'
}
function displayNameRaw(nickname?: string, username?: string) {
  return nickname?.trim() || username?.trim() || '未命名学生'
}
function statusPillText(student: Student) {
  if (student.accountStatus === 0) return '已停用'
  return student.online ? '在线' : '离线'
}
function statusPillClass(student: Student) {
  if (student.accountStatus === 0) return 'status-pill--disabled'
  return student.online ? 'status-pill--online' : 'status-pill--offline'
}
function avatarUrl(student: Student) {
  if (failedAvatars.value.includes(String(student.id))) return ''
  return resolveMediaUrl(student.avatar)
}
function onAvatarError(student: Student) {
  const id = String(student.id)
  if (!failedAvatars.value.includes(id)) failedAvatars.value.push(id)
}
function avatarUrlRaw(id: string, avatar?: string) {
  if (failedAvatars.value.includes(String(id))) return ''
  return resolveMediaUrl(avatar)
}
function onAvatarErrorRaw(id: string) {
  if (!failedAvatars.value.includes(String(id))) failedAvatars.value.push(String(id))
}
function formatLastActive(raw?: string) {
  if (!raw) return '暂无活跃记录'
  const time = new Date(raw).getTime()
  if (!Number.isFinite(time)) return raw
  const diff = Date.now() - time
  if (diff < 60_000) return '刚刚活跃'
  if (diff < 3_600_000) return `${Math.max(1, Math.floor(diff / 60_000))} 分钟前活跃`
  if (diff < 86_400_000) return `${Math.max(1, Math.floor(diff / 3_600_000))} 小时前活跃`
  if (diff < 7 * 86_400_000) return `${Math.max(1, Math.floor(diff / 86_400_000))} 天前活跃`
  return new Date(time).toLocaleDateString('zh-CN')
}

function openProfile(s: Student) {
  modalStudentId.value = String(s.id)
  modalOnline.value = !!s.online
  modalVisible.value = true
}
function openProfileFromResult(s: Student) {
  modalStudentId.value = String(s.id)
  modalOnline.value = !!s.online
  modalVisible.value = true
  inviteOpen.value = false
}

async function getAuthHeaders() {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

async function load() {
  const sequence = ++loadSequence
  loading.value = true
  try {
    const base = OpenAPI.BASE.replace(/\/+$/, '')
    const { data } = await axios.get(`${base}/teaching/students/page`, {
      params: {
        current: currentPage.value,
        size: pageSize.value,
        keyword: appliedKeyword.value || undefined,
      },
      headers: await getAuthHeaders(),
    })
    if (sequence !== loadSequence) return
    if (data?.code !== 0 && data?.code !== 200) throw new Error(data?.message || '加载失败')
    students.value = Array.isArray(data?.data?.records) ? data.data.records : []
    totalCount.value = Math.max(0, Number(data?.data?.total) || 0)
    if (currentPage.value > totalPages.value) {
      currentPage.value = totalPages.value
      void load()
    }
  } catch (error) {
    if (sequence !== loadSequence) return
    Message.error(error instanceof Error ? error.message : '学生数据加载失败，请稍后重试')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}
function searchDirectory() {
  appliedKeyword.value = keyword.value.trim()
  currentPage.value = 1
  void load()
}
function resetSearch() {
  keyword.value = ''
  appliedKeyword.value = ''
  currentPage.value = 1
  void load()
}
function changePage(page: number) {
  if (page < 1 || page > totalPages.value || page === currentPage.value) return
  currentPage.value = page
  void load()
}
function changePageSize(event: Event) {
  pageSize.value = Number((event.target as HTMLSelectElement).value) || 15
  currentPage.value = 1
  void load()
}

async function searchStudents() {
  inviteLoading.value = true
  try {
    const base = OpenAPI.BASE.replace(/\/+$/, '')
    const { data } = await axios.get(`${base}/teaching/students/search`, {
      params: { current: 1, size: 20, keyword: inviteKeyword.value.trim() || undefined },
      headers: await getAuthHeaders(),
    })
    if (data?.code !== 0 && data?.code !== 200) throw new Error(data?.message || '搜索失败')
    inviteResults.value = Array.isArray(data?.data?.records) ? data.data.records : []
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '搜索失败，请稍后重试')
  } finally {
    inviteLoading.value = false
  }
}

function toggleInvitePanel() {
  inviteOpen.value = !inviteOpen.value
  if (inviteOpen.value) {
    inviteKeyword.value = ''
    void searchStudents()
  } else {
    inviteResults.value = []
  }
}

async function onInvite(s: Student) {
  const id = String(s.id)
  if (invitingIds.value.includes(id)) return
  invitingIds.value.push(id)
  try {
    const ok = await inviteStudent(id)
    if (ok) {
      Message.success(`已向 ${displayName(s)} 发出邀请`)
    } else {
      Message.error('邀请失败，可能已存在关系')
    }
  } finally {
    invitingIds.value = invitingIds.value.filter((x) => x !== id)
  }
}

function peerDisplayName(r: RelationVO) {
  return r.peerNickname?.trim() || r.peerUsername?.trim() || '该学生'
}
function peerAvatarUrl(r: RelationVO) {
  if (failedAvatars.value.includes(String(r.peerId))) return ''
  return resolveMediaUrl(r.peerAvatar)
}
function onPeerAvatarError(r: RelationVO) {
  const id = String(r.peerId)
  if (!failedAvatars.value.includes(id)) failedAvatars.value.push(id)
}
function openProfileByPending(r: RelationVO) {
  modalStudentId.value = String(r.peerId)
  modalOnline.value = false
  modalVisible.value = true
}

async function loadPending() {
  pendingList.value = await fetchPendingRelations()
}
async function onPendingApprove(r: RelationVO) {
  if (pendingActingId.value) return
  pendingActingId.value = String(r.id)
  try {
    const ok = await approveRelation(String(r.id))
    if (ok) {
      Message.success(`已同意 ${peerDisplayName(r)} 的申请`)
      await Promise.all([loadPending(), load()])
    } else {
      Message.error('操作失败')
    }
  } finally {
    pendingActingId.value = ''
  }
}
async function onPendingReject(r: RelationVO) {
  if (pendingActingId.value) return
  pendingActingId.value = String(r.id)
  try {
    const ok = await rejectRelation(String(r.id))
    if (ok) {
      Message.success('已拒绝')
      void loadPending()
    } else {
      Message.error('操作失败')
    }
  } finally {
    pendingActingId.value = ''
  }
}

function resetAssignForm() {
  assignCount.value = 5
  assignTitle.value = ''
  assignRemark.value = ''
  assignResult.value = null
}

function openAssignAll() {
  const targets = students.value.filter((item) => item.accountStatus !== 0)
  if (!targets.length) {
    Message.warning('当前页没有可布置练习的学生')
    return
  }
  assignTargets.value = targets
  resetAssignForm()
  assignVisible.value = true
}

function openAssignOne(student: Student) {
  if (student.accountStatus === 0) {
    Message.warning('该学生账号已停用')
    return
  }
  assignTargets.value = [student]
  resetAssignForm()
  assignVisible.value = true
}

function closeAssign() {
  if (!assigning.value) assignVisible.value = false
}

async function submitAssign() {
  if (assigning.value || !assignTargets.value.length) return
  assigning.value = true
  try {
    const result = await practiceAssignmentsApi.assign({
      studentIds: assignTargets.value.map((item) => String(item.id)),
      questionCount: assignCount.value,
      title: assignTitle.value.trim() || undefined,
      remark: assignRemark.value.trim() || undefined,
    })
    assignResult.value = result
    if (result.created > 0) {
      Message.success(`已为 ${result.created} 位学生布置 ${result.totalQuestions} 道题`)
    } else {
      Message.warning('没有生成练习，学生可能已没有可推荐题目')
    }
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '布置失败，请稍后重试')
  } finally {
    assigning.value = false
  }
}

onMounted(() => {
  void load()
  void loadPending()
})
</script>

<template>
  <main class="teaching-page">
    <section class="page-heading">
      <div>
        <div class="heading-kicker"><span class="kicker-dot" />教学管理</div>
        <h1>学生学习概览</h1>
        <p>
          {{
            auth.isAdmin
              ? '查看全站学生的练习进度与最近活跃情况。'
              : '这里仅展示由管理员分配给你的学生，不包含联系方式等敏感资料。'
          }}
        </p>
      </div>
      <span class="scope-chip">{{
        auth.isAdmin ? '管理范围：全部学生' : '管理范围：我的学生'
      }}</span>
    </section>

    <section class="summary-grid" aria-label="学生概览">
      <article class="summary-card">
        <span class="summary-icon summary-icon--teal">人</span>
        <div>
          <strong>{{ totalStudents }}</strong
          ><span>{{ appliedKeyword ? '匹配学生' : '当前学生' }}</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="summary-icon summary-icon--green">✓</span>
        <div>
          <strong>{{ activeStudents }}</strong
          ><span>本页正常账号</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="summary-icon summary-icon--teal">●</span>
        <div>
          <strong>{{ onlineStudents }}</strong
          ><span>本页在线</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="summary-icon summary-icon--blue">题</span>
        <div>
          <strong>{{ totalSolved }}</strong
          ><span>本页解决题目</span>
        </div>
      </article>
      <article class="summary-card">
        <span class="summary-icon summary-icon--orange">%</span>
        <div>
          <strong>{{ averagePassRate }}%</strong><span>本页平均通过率</span>
        </div>
      </article>
    </section>

    <!-- 待我审批的学生申请 -->
    <section v-if="pendingList.length" class="pending-card">
      <div class="pending-card__head">
        <h2>
          <span class="pending-badge">{{ pendingList.length }}</span> 待审批的学生申请
        </h2>
        <p>有学生申请加入你的管理，同意后即可建立师生关系。</p>
      </div>
      <ul class="pending-list">
        <li v-for="r in pendingList" :key="r.id" class="pending-item">
          <div class="pending-item__avatar" @click="openProfileByPending(r)">
            <img
              v-if="peerAvatarUrl(r)"
              :src="peerAvatarUrl(r)"
              :alt="`${peerDisplayName(r)}的头像`"
              @error="onPeerAvatarError(r)"
            />
            <span v-else>{{ peerDisplayName(r).slice(0, 1).toUpperCase() }}</span>
          </div>
          <div class="pending-item__info" @click="openProfileByPending(r)">
            <strong>{{ peerDisplayName(r) }}</strong>
            <span>申请加入你的管理</span>
          </div>
          <div class="pending-item__actions">
            <button
              class="btn-approve"
              :disabled="pendingActingId === String(r.id)"
              @click="onPendingApprove(r)"
            >
              同意
            </button>
            <button
              class="btn-reject"
              :disabled="pendingActingId === String(r.id)"
              @click="onPendingReject(r)"
            >
              拒绝
            </button>
          </div>
        </li>
      </ul>
    </section>

    <section class="directory-card">
      <header class="directory-head">
        <div>
          <h2>学生目录</h2>
          <p>快速查看练习表现，点击学生可继续查看提交与学习记录。</p>
        </div>
        <form class="search-form" role="search" @submit.prevent="searchDirectory">
          <label class="search-box"
            ><span aria-hidden="true">⌕</span
            ><input v-model="keyword" placeholder="搜索昵称或用户名" aria-label="搜索学生"
          /></label>
          <button type="submit" class="primary-button" :disabled="loading">查询</button>
          <button
            v-if="keyword || appliedKeyword"
            type="button"
            class="plain-button"
            @click="resetSearch"
          >
            清除
          </button>
          <button
            type="button"
            class="primary-button primary-button--accent"
            @click="toggleInvitePanel"
          >
            {{ inviteOpen ? '收起' : '+ 邀请学生' }}
          </button>
          <button
            type="button"
            class="primary-button primary-button--assign"
            @click="openAssignAll"
          >
            一键布置练习
          </button>
        </form>
      </header>

      <div class="directory-toolbar">
        <span
          >共 {{ totalCount }} 位学生<span v-if="totalCount"
            >，当前显示 {{ pageStart }}–{{ pageEnd }} 位</span
          ></span
        >
        <div class="view-switch" role="group" aria-label="学生显示方式">
          <button
            type="button"
            :aria-pressed="viewMode === 'list'"
            :class="{ 'is-active': viewMode === 'list' }"
            @click="viewMode = 'list'"
          >
            列表
          </button>
          <button
            type="button"
            :aria-pressed="viewMode === 'cards'"
            :class="{ 'is-active': viewMode === 'cards' }"
            @click="viewMode = 'cards'"
          >
            卡片
          </button>
        </div>
      </div>

      <!-- 邀请学生搜索面板 -->
      <div v-if="inviteOpen" class="invite-panel">
        <div class="invite-panel__head">
          <h3>邀请学生加入管理</h3>
          <p>下方为推荐学生（按注册时间排序），可直接点击「邀请」，也可在搜索框中筛选。</p>
        </div>
        <div class="invite-panel__search">
          <input
            v-model="inviteKeyword"
            placeholder="搜索昵称或用户名（可选）"
            @keyup.enter="searchStudents"
          />
          <button class="primary-button" :disabled="inviteLoading" @click="searchStudents">
            搜索
          </button>
        </div>
        <div v-if="inviteLoading" class="invite-panel__hint">加载中…</div>
        <ul v-else-if="inviteResults.length" class="invite-list">
          <li v-for="s in inviteResults" :key="s.id" class="invite-item">
            <div class="invite-item__avatar" @click="openProfileFromResult(s)">
              <img
                v-if="avatarUrlRaw(s.id, s.avatar)"
                :src="avatarUrlRaw(s.id, s.avatar)"
                :alt="`${displayNameRaw(s.nickname, s.username)}的头像`"
                @error="onAvatarErrorRaw(s.id)"
              />
              <span v-else>{{
                displayNameRaw(s.nickname, s.username).slice(0, 1).toUpperCase()
              }}</span>
              <i :class="{ 'is-offline': !s.online }" />
            </div>
            <div class="invite-item__info" @click="openProfileFromResult(s)">
              <strong>{{ displayNameRaw(s.nickname, s.username) }}</strong>
              <span>@{{ s.username }}</span>
            </div>
            <button
              class="invite-item__btn"
              :disabled="invitingIds.includes(String(s.id))"
              @click="onInvite(s)"
            >
              {{ invitingIds.includes(String(s.id)) ? '邀请中…' : '邀请' }}
            </button>
          </li>
        </ul>
        <p v-else-if="inviteKeyword" class="invite-panel__hint">没有找到匹配的学生</p>
        <p v-else class="invite-panel__hint">暂无学生</p>
      </div>

      <div v-if="loading" class="student-grid" aria-label="正在加载学生">
        <article v-for="index in 6" :key="index" class="student-card student-card--skeleton">
          <span class="skeleton skeleton--avatar" /><span class="skeleton skeleton--line" /><span
            class="skeleton skeleton--wide"
          />
        </article>
      </div>
      <div
        v-else-if="students.length && viewMode === 'list'"
        class="student-list"
        role="table"
        aria-label="学生列表"
      >
        <div class="student-list__head" role="row">
          <span role="columnheader">学生</span><span role="columnheader">状态</span
          ><span role="columnheader">已解决</span><span role="columnheader">总提交</span
          ><span role="columnheader">通过率</span><span role="columnheader">最近活跃</span
          ><span role="columnheader">操作</span>
        </div>
        <div v-for="student in students" :key="student.id" class="student-list__row" role="row">
          <div class="student-list__person" role="cell">
            <button
              type="button"
              class="student-list__avatar"
              :aria-label="`查看${displayName(student)}的资料`"
              @click="openProfile(student)"
            >
              <img
                v-if="avatarUrl(student)"
                :src="avatarUrl(student)"
                :alt="`${displayName(student)}的头像`"
                @error="onAvatarError(student)"
              />
              <span v-else>{{ displayName(student).slice(0, 1).toUpperCase() }}</span>
            </button>
            <div>
              <strong>{{ displayName(student) }}</strong
              ><small>@{{ student.username }}</small>
            </div>
          </div>
          <span role="cell"
            ><span class="status-pill" :class="statusPillClass(student)">{{
              statusPillText(student)
            }}</span></span
          >
          <strong role="cell">{{ student.solvedCount || 0 }}</strong>
          <span role="cell">{{ student.submitCount || 0 }}</span>
          <span role="cell">{{ Math.round(normalizedRate(student.passRate)) }}%</span>
          <span role="cell" class="student-list__active">{{
            formatLastActive(student.lastActiveTime)
          }}</span>
          <div role="cell" class="student-list__actions">
            <button type="button" @click="openProfile(student)">资料</button
            ><button type="button" class="is-assign" @click="openAssignOne(student)">
              布置练习</button
            ><RouterLink
              :to="{
                path: '/submissions',
                query: { scope: 'MANAGED', userId: student.id, studentName: displayName(student) },
              }"
              >提交记录</RouterLink
            >
          </div>
        </div>
      </div>
      <div v-else-if="students.length" class="student-grid">
        <article v-for="student in students" :key="student.id" class="student-card">
          <header class="student-identity">
            <div class="avatar-wrap" @click="openProfile(student)">
              <img
                v-if="avatarUrl(student)"
                :src="avatarUrl(student)"
                :alt="`${displayName(student)}的头像`"
                @error="onAvatarError(student)"
              />
              <span v-else>{{ displayName(student).slice(0, 1).toUpperCase() }}</span
              ><i :class="{ 'is-offline': !student.online }" aria-hidden="true" />
            </div>
            <div class="identity-copy" @click="openProfile(student)">
              <h3>{{ displayName(student) }}</h3>
              <p>@{{ student.username }}</p>
            </div>
            <span class="status-pill" :class="statusPillClass(student)">{{
              statusPillText(student)
            }}</span>
          </header>
          <p class="last-active">{{ formatLastActive(student.lastActiveTime) }}</p>
          <div class="student-metrics">
            <div>
              <strong>{{ student.solvedCount || 0 }}</strong
              ><span>已解决</span>
            </div>
            <div>
              <strong>{{ student.submitCount || 0 }}</strong
              ><span>总提交</span>
            </div>
            <div>
              <strong>{{ student.acceptedCount || 0 }}</strong
              ><span>通过次数</span>
            </div>
          </div>
          <div class="pass-rate">
            <div>
              <span>提交通过率</span
              ><strong>{{ Math.round(normalizedRate(student.passRate)) }}%</strong>
            </div>
            <span class="rate-track"
              ><i :style="{ width: `${normalizedRate(student.passRate)}%` }"
            /></span>
          </div>
          <div class="student-actions">
            <button class="student-action student-action--view" @click="openProfile(student)">
              查看资料
            </button>
            <button class="student-action student-action--assign" @click="openAssignOne(student)">
              布置练习
            </button>
            <RouterLink
              class="student-action"
              :to="{
                path: '/submissions',
                query: { scope: 'MANAGED', userId: student.id, studentName: displayName(student) },
              }"
              >提交记录 <span aria-hidden="true">→</span></RouterLink
            >
          </div>
        </article>
      </div>
      <div v-else class="empty-state">
        <span class="empty-icon">人</span>
        <h3>{{ appliedKeyword ? '没有找到匹配的学生' : '暂时没有可管理的学生' }}</h3>
        <p>
          {{
            appliedKeyword
              ? '请尝试更换昵称或用户名关键词。'
              : auth.isAdmin
                ? '学生注册后会显示在这里。'
                : '请联系管理员为你分配学生。'
          }}
        </p>
        <button v-if="appliedKeyword" type="button" class="plain-button" @click="resetSearch">
          查看全部学生
        </button>
      </div>
      <footer v-if="totalCount > 0" class="directory-pagination">
        <label
          >每页
          <select :value="pageSize" aria-label="每页显示学生数量" @change="changePageSize">
            <option :value="15">15</option>
            <option :value="30">30</option>
            <option :value="50">50</option>
          </select>
          位</label
        >
        <nav aria-label="学生列表翻页">
          <button
            type="button"
            :disabled="loading || currentPage <= 1"
            @click="changePage(currentPage - 1)"
          >
            上一页</button
          ><span>第 {{ currentPage }} / {{ totalPages }} 页</span
          ><button
            type="button"
            :disabled="loading || currentPage >= totalPages"
            @click="changePage(currentPage + 1)"
          >
            下一页
          </button>
        </nav>
      </footer>
    </section>

    <div v-if="assignVisible" class="assign-overlay" @click.self="closeAssign">
      <section class="assign-modal" role="dialog" aria-modal="true" aria-label="一键布置练习">
        <header class="assign-modal__head">
          <div>
            <h3>一键布置练习</h3>
            <p>{{ assignSubtitle }}</p>
          </div>
          <button type="button" class="assign-close" aria-label="关闭" @click="closeAssign">
            ×
          </button>
        </header>

        <div v-if="!assignResult" class="assign-body">
          <label class="assign-field">
            <span>每人题目数</span>
            <select v-model.number="assignCount">
              <option :value="3">3 道 · 约 20 分钟</option>
              <option :value="5">5 道 · 约 35 分钟</option>
              <option :value="8">8 道 · 约 1 小时</option>
            </select>
          </label>
          <label class="assign-field">
            <span>练习标题</span>
            <input
              v-model="assignTitle"
              maxlength="120"
              placeholder="留空自动生成，例如：个性化练习 · 2026-11-18"
            />
          </label>
          <label class="assign-field">
            <span>给学生的说明</span>
            <textarea
              v-model="assignRemark"
              maxlength="500"
              rows="3"
              placeholder="可选，例如：先复盘薄弱知识点，再提交。"
            />
          </label>
          <p class="assign-hint">
            题目按每位学生最近的提交记录与薄弱知识点生成；已通过的题目不会重复出现。
          </p>
        </div>

        <div v-else class="assign-result">
          <strong>{{ assignResult.created }} 位学生已生成练习</strong>
          <p>
            共 {{ assignResult.totalQuestions }} 道题，平均每人
            {{
              assignResult.created
                ? Math.round(assignResult.totalQuestions / assignResult.created)
                : 0
            }}
            道。
          </p>
          <p v-if="assignResult.skipped" class="assign-skip">
            跳过 {{ assignResult.skipped }} 位：{{
              (assignResult.skippedStudentNames || []).join('、') || '暂无可推荐题目'
            }}
          </p>
        </div>

        <footer class="assign-actions">
          <template v-if="!assignResult">
            <button type="button" class="plain-button" :disabled="assigning" @click="closeAssign">
              取消
            </button>
            <button
              type="button"
              class="primary-button"
              :disabled="assigning || !assignTargets.length"
              @click="submitAssign"
            >
              {{ assigning ? '生成中…' : '确认布置' }}
            </button>
          </template>
          <button v-else type="button" class="primary-button" @click="closeAssign">完成</button>
        </footer>
      </section>
    </div>

    <UserProfileModal
      :visible="modalVisible"
      :user-id="modalStudentId"
      :online="modalOnline"
      @close="modalVisible = false"
      @changed="load()"
    />
  </main>
</template>

<style scoped>
.teaching-page {
  --ink: #172326;
  --muted: #667673;
  --line: #dfe7e4;
  --surface: #fff;
  --teal: #0e8f82;
  max-width: 1500px;
  margin: 0 auto;
  padding: 26px 28px 44px;
  color: var(--ink);
}
.page-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  margin-bottom: 20px;
}
.heading-kicker {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--teal);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
}
.kicker-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #1bb9a6;
}
.page-heading h1 {
  margin: 7px 0 5px;
  font-size: 30px;
  line-height: 1.2;
  letter-spacing: -0.03em;
}
.page-heading p,
.directory-head p {
  margin: 0;
  color: var(--muted);
  font-size: 14px;
}
.scope-chip {
  flex: none;
  border: 1px solid #bcded8;
  background: #edf8f6;
  color: #187b71;
  border-radius: 999px;
  padding: 8px 13px;
  font-size: 13px;
  font-weight: 600;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}
.summary-card {
  display: flex;
  align-items: center;
  gap: 13px;
  min-height: 92px;
  padding: 17px 18px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: var(--surface);
}
.summary-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  font-size: 15px;
  font-weight: 800;
}
.summary-icon--teal {
  color: #087f73;
  background: #ddf4ef;
}
.summary-icon--green {
  color: #198754;
  background: #e5f5ea;
}
.summary-icon--blue {
  color: #3679c9;
  background: #e8f1fc;
}
.summary-icon--orange {
  color: #c66a10;
  background: #fff0df;
}
.summary-card div {
  display: flex;
  flex-direction: column;
}
.summary-card strong {
  font-size: 23px;
  line-height: 1.15;
}
.summary-card span:last-child {
  margin-top: 4px;
  color: var(--muted);
  font-size: 12px;
}
.directory-card {
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--surface);
  overflow: hidden;
}
.directory-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 22px;
  border-bottom: 1px solid var(--line);
}
.directory-head h2 {
  margin: 0 0 4px;
  font-size: 19px;
}
.search-form {
  display: flex;
  align-items: center;
  gap: 8px;
}
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 260px;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid #ccd8d5;
  border-radius: 10px;
  background: #fff;
  color: #84918e;
}
.search-box:focus-within {
  border-color: #45a99e;
  box-shadow: 0 0 0 3px rgba(14, 143, 130, 0.1);
}
.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--ink);
}
.primary-button,
.plain-button {
  min-height: 42px;
  padding: 0 15px;
  border-radius: 10px;
  font-weight: 650;
  cursor: pointer;
}
.primary-button {
  border: 1px solid var(--teal);
  background: var(--teal);
  color: #fff;
}
.plain-button {
  border: 1px solid #ccd8d5;
  background: #fff;
  color: #42524f;
}
.primary-button:disabled {
  opacity: 0.6;
  cursor: wait;
}
.student-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  padding: 18px;
}
.student-card {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 17px;
  border: 1px solid var(--line);
  border-radius: 15px;
  background: #fff;
  transition:
    border-color 0.16s ease,
    box-shadow 0.16s ease;
}
.student-card:hover {
  border-color: #a8d4cd;
  box-shadow: 0 8px 22px rgba(25, 66, 59, 0.07);
}
.student-identity {
  display: flex;
  align-items: center;
  gap: 11px;
}
.avatar-wrap {
  position: relative;
  flex: none;
  cursor: pointer;
}
.avatar-wrap img,
.avatar-wrap > span {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
  background: #dff4ef;
  color: #087f73;
  font-size: 18px;
  font-weight: 750;
}
.avatar-wrap i {
  position: absolute;
  right: 1px;
  bottom: 1px;
  width: 11px;
  height: 11px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #22ad72;
}
.avatar-wrap i.is-offline {
  background: #aab4b2;
}
.identity-copy {
  min-width: 0;
  cursor: pointer;
}
.identity-copy h3 {
  overflow: hidden;
  margin: 0 0 3px;
  font-size: 16px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.identity-copy p {
  overflow: hidden;
  margin: 0;
  color: #7b8885;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.status-pill {
  margin-left: auto;
  border-radius: 999px;
  padding: 5px 9px;
  background: #e7f6ed;
  color: #247b4f;
  font-size: 11px;
  font-weight: 700;
}
.status-pill--disabled {
  background: #f0f1f1;
  color: #77807e;
}
.status-pill--online {
  background: #e7f6ed;
  color: #247b4f;
}
.status-pill--offline {
  background: #f0f1f1;
  color: #8a9491;
}
.last-active {
  margin: 13px 0 0;
  color: #7b8885;
  font-size: 12px;
}
.student-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 15px 0;
  padding: 13px 0;
  border-block: 1px solid #edf1ef;
}
.student-metrics div {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.student-metrics div + div {
  border-left: 1px solid #edf1ef;
  padding-left: 10px;
}
.student-metrics strong {
  font-size: 18px;
}
.student-metrics span,
.pass-rate span {
  color: #778480;
  font-size: 11px;
}
.pass-rate > div {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pass-rate strong {
  font-size: 12px;
}
.rate-track {
  display: block;
  height: 6px;
  margin-top: 7px;
  border-radius: 999px;
  background: #edf2f0;
  overflow: hidden;
}
.rate-track i {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #1aa493;
}
.student-actions {
  display: flex;
  gap: 8px;
  margin-top: 14px;
}
.student-action {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  min-height: 38px;
  padding: 0 12px;
  border-radius: 10px;
  background: #f1f8f6;
  color: #087f73;
  font-size: 13px;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
  transition:
    background 0.15s ease,
    transform 0.1s ease;
  border: 0;
}
.student-action:hover {
  background: #e4f3f0;
}
.student-action:active {
  transform: translateY(1px);
}
.student-action--view {
  background: #eef2ff;
  color: #4f46e5;
}
.student-action--view:hover {
  background: #e0e7ff;
}
/* 邀请面板 */
.invite-panel {
  padding: 18px 22px;
  border-bottom: 1px solid var(--line);
  background: linear-gradient(180deg, #fafbfc 0%, #fff 100%);
}
.invite-panel__head h3 {
  margin: 0 0 4px;
  font-size: 16px;
  color: var(--ink);
}
.invite-panel__head p {
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 13px;
}
.invite-panel__search {
  display: flex;
  gap: 8px;
}
.invite-panel__search input {
  flex: 1;
  min-height: 40px;
  padding: 0 12px;
  border: 1px solid #ccd8d5;
  border-radius: 10px;
  outline: 0;
  font-size: 14px;
}
.invite-panel__search input:focus {
  border-color: #45a99e;
  box-shadow: 0 0 0 3px rgba(14, 143, 130, 0.1);
}
.invite-panel__hint {
  margin: 12px 0 0;
  color: var(--muted);
  font-size: 13px;
}
.invite-list {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.invite-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: #fff;
}
.invite-item__avatar {
  position: relative;
  cursor: pointer;
}
.invite-item__avatar img,
.invite-item__avatar > span {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  background: #eef2ff;
  color: #4f46e5;
  font-size: 15px;
  font-weight: 750;
}
.invite-item__avatar i {
  position: absolute;
  right: 0;
  bottom: 0;
  width: 9px;
  height: 9px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #22ad72;
}
.invite-item__avatar i.is-offline {
  background: #aab4b2;
}
.invite-item__info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.invite-item__info strong {
  display: block;
  font-size: 14px;
  color: var(--ink);
}
.invite-item__info span {
  color: #7b8885;
  font-size: 12px;
}
.invite-item__btn {
  min-height: 34px;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  background: #4f46e5;
  color: #fff;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: background 0.15s ease;
}
.invite-item__btn:hover:not(:disabled) {
  background: #4338ca;
}
.invite-item__btn:disabled {
  opacity: 0.6;
  cursor: wait;
}
.primary-button--accent {
  background: linear-gradient(135deg, #4f46e5, #7c3aed);
}
/* 待审批申请 */
.pending-card {
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid #ddd6fe;
  border-radius: 16px;
  background: linear-gradient(135deg, #f5f3ff 0%, #fff 60%);
}
.pending-card__head h2 {
  margin: 0 0 4px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 17px;
  color: #5b21b6;
}
.pending-card__head p {
  margin: 0;
  color: #6d28d9;
  font-size: 13px;
}
.pending-badge {
  display: inline-grid;
  place-items: center;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #7c3aed;
  color: #fff;
  font-size: 12px;
  font-weight: 800;
}
.pending-list {
  list-style: none;
  margin: 14px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pending-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid #ddd6fe;
  border-radius: 12px;
  background: #fff;
}
.pending-item__avatar {
  flex: none;
  cursor: pointer;
}
.pending-item__avatar img,
.pending-item__avatar > span {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  background: #ede9fe;
  color: #5b21b6;
  font-size: 16px;
  font-weight: 750;
}
.pending-item__info {
  flex: 1;
  min-width: 0;
  cursor: pointer;
}
.pending-item__info strong {
  display: block;
  font-size: 14px;
  color: var(--ink);
}
.pending-item__info span {
  color: #6d28d9;
  font-size: 12px;
}
.pending-item__actions {
  display: flex;
  gap: 8px;
}
.btn-approve,
.btn-reject {
  min-height: 34px;
  padding: 0 16px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  border: 0;
  transition: background 0.15s ease;
}
.btn-approve {
  background: #16a34a;
  color: #fff;
}
.btn-approve:hover:not(:disabled) {
  background: #15803d;
}
.btn-reject {
  background: #fee2e2;
  color: #dc2626;
}
.btn-reject:hover:not(:disabled) {
  background: #fecaca;
}
.btn-approve:disabled,
.btn-reject:disabled {
  opacity: 0.55;
  cursor: wait;
}
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 70px 20px;
  text-align: center;
}
.empty-icon {
  display: grid;
  place-items: center;
  width: 50px;
  height: 50px;
  border-radius: 15px;
  background: #e7f5f2;
  color: #168a7d;
  font-weight: 800;
}
.empty-state h3 {
  margin: 15px 0 6px;
}
.empty-state p {
  margin: 0 0 16px;
  color: var(--muted);
}
.student-card--skeleton {
  min-height: 270px;
}
.skeleton {
  display: block;
  border-radius: 8px;
  background: linear-gradient(90deg, #edf1f0, #f7f8f8, #edf1f0);
  background-size: 200% 100%;
  animation: skeleton 1.2s linear infinite;
}
.skeleton--avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
}
.skeleton--line {
  width: 44%;
  height: 14px;
  margin-top: 18px;
}
.skeleton--wide {
  width: 100%;
  height: 90px;
  margin-top: 22px;
}
@keyframes skeleton {
  to {
    background-position: -200% 0;
  }
}
.directory-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 22px;
  border-bottom: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}
.view-switch {
  display: flex;
  gap: 3px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #f5f8f7;
}
.view-switch button {
  min-height: 34px;
  padding: 0 13px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #52615e;
  cursor: pointer;
}
.view-switch button.is-active {
  background: #fff;
  color: #087f73;
  box-shadow: 0 1px 5px #1c46421a;
  font-weight: 700;
}
.student-list__head,
.student-list__row {
  display: grid;
  grid-template-columns: minmax(170px, 2fr) 75px minmax(65px, 0.6fr) minmax(65px, 0.6fr) minmax(
      65px,
      0.6fr
    ) minmax(120px, 1fr) 236px;
  align-items: center;
  gap: 12px;
  padding: 12px 22px;
}
.student-list__head {
  background: #f8faf9;
  color: #667673;
  font-size: 12px;
  font-weight: 700;
}
.student-list__row {
  min-height: 74px;
  border-top: 1px solid #eef1f0;
  font-size: 14px;
}
.student-list__row:hover {
  background: #f9fcfb;
}
.student-list__person {
  display: flex;
  align-items: center;
  gap: 11px;
  min-width: 0;
}
.student-list__person > div {
  min-width: 0;
}
.student-list__person strong,
.student-list__person small {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.student-list__person small {
  margin-top: 3px;
  color: var(--muted);
  font-size: 12px;
}
.student-list__avatar {
  flex: none;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  overflow: hidden;
  border: 0;
  border-radius: 50%;
  background: #dff4ef;
  color: #087f73;
  cursor: pointer;
}
.student-list__avatar img,
.student-list__avatar span {
  width: 40px;
  height: 40px;
  object-fit: cover;
  display: grid;
  place-items: center;
}
.student-list__active {
  color: var(--muted);
  font-size: 12px;
}
.student-list__actions {
  display: flex;
  gap: 8px;
}
.student-list__actions button,
.student-list__actions a {
  display: grid;
  place-items: center;
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid #c9e4df;
  border-radius: 9px;
  background: #fff;
  color: #087f73;
  font-size: 12px;
  font-weight: 650;
  text-decoration: none;
  cursor: pointer;
  white-space: nowrap;
}
.student-list__actions button:hover,
.student-list__actions a:hover {
  background: #eff9f7;
}
.directory-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 15px 22px;
  border-top: 1px solid var(--line);
  color: var(--muted);
  font-size: 13px;
}
.directory-pagination label,
.directory-pagination nav {
  display: flex;
  align-items: center;
  gap: 9px;
}
.directory-pagination select,
.directory-pagination button {
  min-height: 38px;
  padding: 0 10px;
  border: 1px solid #ccd8d5;
  border-radius: 9px;
  background: #fff;
  color: var(--ink);
  cursor: pointer;
}
.directory-pagination button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.directory-pagination button:not(:disabled):hover {
  border-color: #5bad9f;
  color: #087f73;
}
@media (max-width: 900px) {
  .student-list__head,
  .student-list__row {
    grid-template-columns: minmax(155px, 2fr) 70px 60px 70px 230px;
  }
  .student-list__head > :nth-child(4),
  .student-list__row > :nth-child(4),
  .student-list__head > :nth-child(6),
  .student-list__row > :nth-child(6) {
    display: none;
  }
}
@media (max-width: 680px) {
  .directory-toolbar {
    padding: 10px 16px;
  }
  .student-list__head {
    display: none;
  }
  .student-list__row {
    grid-template-columns: 1fr auto;
    gap: 10px;
    padding: 15px 16px;
  }
  .student-list__row > :nth-child(2) {
    justify-self: end;
  }
  .student-list__row > :nth-child(3),
  .student-list__row > :nth-child(4),
  .student-list__row > :nth-child(5),
  .student-list__row > :nth-child(6) {
    display: none;
  }
  .student-list__actions {
    grid-column: 1/-1;
  }
  .student-list__actions button,
  .student-list__actions a {
    flex: 1;
  }
  .directory-pagination {
    flex-wrap: wrap;
    padding: 14px 16px;
  }
  .directory-pagination nav {
    width: 100%;
    justify-content: space-between;
  }
}
@media (max-width: 1100px) {
  .summary-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .student-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .directory-head {
    align-items: flex-start;
    flex-direction: column;
  }
  .search-form {
    width: 100%;
  }
  .search-box {
    flex: 1;
    width: auto;
  }
}
@media (max-width: 680px) {
  .teaching-page {
    padding: 18px 14px 32px;
  }
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .page-heading h1 {
    font-size: 25px;
  }
  .summary-grid {
    gap: 8px;
  }
  .summary-card {
    min-height: 80px;
    padding: 13px;
  }
  .summary-icon {
    width: 36px;
    height: 36px;
  }
  .summary-card strong {
    font-size: 20px;
  }
  .directory-head {
    padding: 16px;
  }
  .search-form {
    flex-wrap: wrap;
  }
  .search-box {
    flex-basis: 100%;
  }
  .primary-button,
  .plain-button {
    flex: 1;
  }
  .student-grid {
    grid-template-columns: 1fr;
    padding: 12px;
  }
  .scope-chip {
    align-self: flex-start;
  }
}
@media (prefers-reduced-motion: reduce) {
  .student-card,
  .skeleton {
    transition: none;
    animation: none;
  }
}
/* 一键布置练习 */
.primary-button--assign {
  border-color: #7c3aed;
  background: linear-gradient(135deg, #7c3aed, #5b21b6);
  color: #fff;
}
.student-action--assign {
  background: #f3e8ff;
  color: #6d28d9;
}
.student-action--assign:hover {
  background: #ede9fe;
}
.student-list__actions .is-assign {
  border-color: #ddd6fe;
  background: #f5f3ff;
  color: #6d28d9;
}
.student-list__actions .is-assign:hover {
  background: #ede9fe;
}
.assign-overlay {
  position: fixed;
  inset: 0;
  z-index: 60;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgba(15, 23, 42, 0.42);
  backdrop-filter: blur(2px);
}
.assign-modal {
  width: min(520px, 100%);
  max-height: 90vh;
  overflow: auto;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.24);
}
.assign-modal__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  border-bottom: 1px solid var(--line);
}
.assign-modal__head h3 {
  margin: 0 0 4px;
  font-size: 18px;
}
.assign-modal__head p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.assign-close {
  width: 32px;
  height: 32px;
  border: 0;
  border-radius: 9px;
  background: #f1f4f3;
  color: #52615e;
  font-size: 20px;
  line-height: 1;
  cursor: pointer;
}
.assign-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 22px;
}
.assign-field {
  display: flex;
  flex-direction: column;
  gap: 7px;
  color: #42524f;
  font-size: 13px;
  font-weight: 650;
}
.assign-field input,
.assign-field select,
.assign-field textarea {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #ccd8d5;
  border-radius: 10px;
  background: #fff;
  color: var(--ink);
  font: inherit;
  font-weight: 400;
  outline: 0;
}
.assign-field input:focus,
.assign-field select:focus,
.assign-field textarea:focus {
  border-color: #45a99e;
  box-shadow: 0 0 0 3px rgba(14, 143, 130, 0.1);
}
.assign-field textarea {
  resize: vertical;
  min-height: 76px;
}
.assign-hint {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f7faf9;
  color: #6b7a77;
  font-size: 12px;
}
.assign-result {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 24px 22px;
}
.assign-result strong {
  color: #087f73;
  font-size: 18px;
}
.assign-result p {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}
.assign-result .assign-skip {
  color: #b45309;
}
.assign-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding: 16px 22px;
  border-top: 1px solid var(--line);
}
.assign-actions .plain-button,
.assign-actions .primary-button {
  min-width: 96px;
}
@media (max-width: 680px) {
  .assign-overlay {
    padding: 12px;
  }
  .assign-body,
  .assign-modal__head,
  .assign-actions {
    padding: 16px;
  }
  .assign-actions .plain-button,
  .assign-actions .primary-button {
    flex: 1;
    min-width: 0;
  }
}
</style>
