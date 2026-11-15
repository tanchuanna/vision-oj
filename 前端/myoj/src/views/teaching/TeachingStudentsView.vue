<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { Message } from '@arco-design/web-vue'
import { OpenAPI } from '@generated'
import { useAuthStore } from '@/stores/auth'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import UserProfileModal from '@/components/teaching/UserProfileModal.vue'
import { approveRelation, fetchPendingRelations, inviteStudent, rejectRelation, type RelationVO } from '@/api/teachingRelations'

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
const loading = ref(false)
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

const totalStudents = computed(() => students.value.length)
const activeStudents = computed(() => students.value.filter((item) => item.accountStatus !== 0).length)
const onlineStudents = computed(() => students.value.filter((item) => item.online).length)
const totalSolved = computed(() => students.value.reduce((sum, item) => sum + Number(item.solvedCount || 0), 0))
const averagePassRate = computed(() => {
  if (!students.value.length) return 0
  return Math.round(students.value.reduce((sum, item) => sum + normalizedRate(item.passRate), 0) / students.value.length)
})

function normalizedRate(value?: number) {
  const rate = Number(value || 0)
  return Math.max(0, Math.min(100, rate <= 1 && rate > 0 ? rate * 100 : rate))
}
function displayName(student: Student) { return student.nickname?.trim() || student.username?.trim() || '未命名学生' }
function displayNameRaw(nickname?: string, username?: string) { return nickname?.trim() || username?.trim() || '未命名学生' }
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
  const token = typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

async function load() {
  loading.value = true
  try {
    const base = OpenAPI.BASE.replace(/\/+$/, '')
    const { data } = await axios.get(`${base}/teaching/students/page`, {
      params: { current: 1, size: 50, keyword: keyword.value.trim() || undefined },
      headers: await getAuthHeaders(),
    })
    if (data?.code !== 0 && data?.code !== 200) throw new Error(data?.message || '加载失败')
    students.value = Array.isArray(data?.data?.records) ? data.data.records : []
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '学生数据加载失败，请稍后重试')
  } finally { loading.value = false }
}
function resetSearch() { keyword.value = ''; void load() }

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
  } finally { inviteLoading.value = false }
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

function peerDisplayName(r: RelationVO) { return r.peerNickname?.trim() || r.peerUsername?.trim() || '该学生' }
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

onMounted(() => { void load(); void loadPending() })
</script>

<template>
  <main class="teaching-page">
    <section class="page-heading">
      <div>
        <div class="heading-kicker"><span class="kicker-dot" />教学管理</div>
        <h1>学生学习概览</h1>
        <p>{{ auth.isAdmin ? '查看全站学生的练习进度与最近活跃情况。' : '这里仅展示由管理员分配给你的学生，不包含联系方式等敏感资料。' }}</p>
      </div>
      <span class="scope-chip">{{ auth.isAdmin ? '管理范围：全部学生' : '管理范围：我的学生' }}</span>
    </section>

    <section class="summary-grid" aria-label="学生概览">
      <article class="summary-card"><span class="summary-icon summary-icon--teal">人</span><div><strong>{{ totalStudents }}</strong><span>当前学生</span></div></article>
      <article class="summary-card"><span class="summary-icon summary-icon--green">✓</span><div><strong>{{ activeStudents }}</strong><span>正常账号</span></div></article>
      <article class="summary-card"><span class="summary-icon summary-icon--teal">●</span><div><strong>{{ onlineStudents }}</strong><span>在线</span></div></article>
      <article class="summary-card"><span class="summary-icon summary-icon--blue">题</span><div><strong>{{ totalSolved }}</strong><span>累计解决题目</span></div></article>
      <article class="summary-card"><span class="summary-icon summary-icon--orange">%</span><div><strong>{{ averagePassRate }}%</strong><span>平均通过率</span></div></article>
    </section>

    <!-- 待我审批的学生申请 -->
    <section v-if="pendingList.length" class="pending-card">
      <div class="pending-card__head">
        <h2><span class="pending-badge">{{ pendingList.length }}</span> 待审批的学生申请</h2>
        <p>有学生申请加入你的管理，同意后即可建立师生关系。</p>
      </div>
      <ul class="pending-list">
        <li v-for="r in pendingList" :key="r.id" class="pending-item">
          <div class="pending-item__avatar" @click="openProfileByPending(r)">
            <img v-if="peerAvatarUrl(r)" :src="peerAvatarUrl(r)" :alt="`${peerDisplayName(r)}的头像`" @error="onPeerAvatarError(r)" />
            <span v-else>{{ peerDisplayName(r).slice(0, 1).toUpperCase() }}</span>
          </div>
          <div class="pending-item__info" @click="openProfileByPending(r)">
            <strong>{{ peerDisplayName(r) }}</strong>
            <span>申请加入你的管理</span>
          </div>
          <div class="pending-item__actions">
            <button class="btn-approve" :disabled="pendingActingId === String(r.id)" @click="onPendingApprove(r)">同意</button>
            <button class="btn-reject" :disabled="pendingActingId === String(r.id)" @click="onPendingReject(r)">拒绝</button>
          </div>
        </li>
      </ul>
    </section>

    <section class="directory-card">
      <header class="directory-head">
        <div><h2>学生目录</h2><p>快速查看练习表现，点击学生可继续查看提交与学习记录。</p></div>
        <form class="search-form" role="search" @submit.prevent="load">
          <label class="search-box"><span aria-hidden="true">⌕</span><input v-model="keyword" placeholder="搜索昵称或用户名" aria-label="搜索学生" /></label>
          <button type="submit" class="primary-button" :disabled="loading">查询</button>
          <button v-if="keyword" type="button" class="plain-button" @click="resetSearch">清除</button>
          <button type="button" class="primary-button primary-button--accent" @click="toggleInvitePanel">
            {{ inviteOpen ? '收起' : '+ 邀请学生' }}
          </button>
        </form>
      </header>

      <!-- 邀请学生搜索面板 -->
      <div v-if="inviteOpen" class="invite-panel">
        <div class="invite-panel__head">
          <h3>邀请学生加入管理</h3>
          <p>下方为推荐学生（按注册时间排序），可直接点击「邀请」，也可在搜索框中筛选。</p>
        </div>
        <div class="invite-panel__search">
          <input v-model="inviteKeyword" placeholder="搜索昵称或用户名（可选）" @keyup.enter="searchStudents" />
          <button class="primary-button" :disabled="inviteLoading" @click="searchStudents">搜索</button>
        </div>
        <div v-if="inviteLoading" class="invite-panel__hint">加载中…</div>
        <ul v-else-if="inviteResults.length" class="invite-list">
          <li v-for="s in inviteResults" :key="s.id" class="invite-item">
            <div class="invite-item__avatar" @click="openProfileFromResult(s)">
              <img v-if="avatarUrlRaw(s.id, s.avatar)" :src="avatarUrlRaw(s.id, s.avatar)" :alt="`${displayNameRaw(s.nickname, s.username)}的头像`" @error="onAvatarErrorRaw(s.id)" />
              <span v-else>{{ displayNameRaw(s.nickname, s.username).slice(0, 1).toUpperCase() }}</span>
              <i :class="{ 'is-offline': !s.online }" />
            </div>
            <div class="invite-item__info" @click="openProfileFromResult(s)">
              <strong>{{ displayNameRaw(s.nickname, s.username) }}</strong>
              <span>@{{ s.username }}</span>
            </div>
            <button class="invite-item__btn" :disabled="invitingIds.includes(String(s.id))" @click="onInvite(s)">
              {{ invitingIds.includes(String(s.id)) ? '邀请中…' : '邀请' }}
            </button>
          </li>
        </ul>
        <p v-else-if="inviteKeyword" class="invite-panel__hint">没有找到匹配的学生</p>
        <p v-else class="invite-panel__hint">暂无学生</p>
      </div>

      <div v-if="loading" class="student-grid" aria-label="正在加载学生">
        <article v-for="index in 6" :key="index" class="student-card student-card--skeleton"><span class="skeleton skeleton--avatar" /><span class="skeleton skeleton--line" /><span class="skeleton skeleton--wide" /></article>
      </div>
      <div v-else-if="students.length" class="student-grid">
        <article v-for="student in students" :key="student.id" class="student-card">
          <header class="student-identity">
            <div class="avatar-wrap" @click="openProfile(student)">
              <img v-if="avatarUrl(student)" :src="avatarUrl(student)" :alt="`${displayName(student)}的头像`" @error="onAvatarError(student)" />
              <span v-else>{{ displayName(student).slice(0, 1).toUpperCase() }}</span><i :class="{ 'is-offline': !student.online }" aria-hidden="true" />
            </div>
            <div class="identity-copy" @click="openProfile(student)"><h3>{{ displayName(student) }}</h3><p>@{{ student.username }}</p></div>
            <span class="status-pill" :class="statusPillClass(student)">{{ statusPillText(student) }}</span>
          </header>
          <p class="last-active">{{ formatLastActive(student.lastActiveTime) }}</p>
          <div class="student-metrics">
            <div><strong>{{ student.solvedCount || 0 }}</strong><span>已解决</span></div><div><strong>{{ student.submitCount || 0 }}</strong><span>总提交</span></div><div><strong>{{ student.acceptedCount || 0 }}</strong><span>通过次数</span></div>
          </div>
          <div class="pass-rate"><div><span>提交通过率</span><strong>{{ Math.round(normalizedRate(student.passRate)) }}%</strong></div><span class="rate-track"><i :style="{ width: `${normalizedRate(student.passRate)}%` }" /></span></div>
          <div class="student-actions">
            <button class="student-action student-action--view" @click="openProfile(student)">查看资料</button>
            <RouterLink class="student-action" :to="{ path: '/submissions', query: { scope: 'MANAGED', userId: student.id, studentName: displayName(student) } }">提交记录 <span aria-hidden="true">→</span></RouterLink>
          </div>
        </article>
      </div>
      <div v-else class="empty-state">
        <span class="empty-icon">人</span><h3>{{ keyword ? '没有找到匹配的学生' : '暂时没有可管理的学生' }}</h3>
        <p>{{ keyword ? '请尝试更换昵称或用户名关键词。' : (auth.isAdmin ? '学生注册后会显示在这里。' : '请联系管理员为你分配学生。') }}</p>
        <button v-if="keyword" type="button" class="plain-button" @click="resetSearch">查看全部学生</button>
      </div>
    </section>

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
.teaching-page{--ink:#172326;--muted:#667673;--line:#dfe7e4;--surface:#fff;--teal:#0e8f82;max-width:1500px;margin:0 auto;padding:26px 28px 44px;color:var(--ink)}
.page-heading{display:flex;justify-content:space-between;align-items:center;gap:24px;margin-bottom:20px}.heading-kicker{display:flex;align-items:center;gap:8px;color:var(--teal);font-size:12px;font-weight:700;letter-spacing:.12em}.kicker-dot{width:7px;height:7px;border-radius:50%;background:#1bb9a6}.page-heading h1{margin:7px 0 5px;font-size:30px;line-height:1.2;letter-spacing:-.03em}.page-heading p,.directory-head p{margin:0;color:var(--muted);font-size:14px}.scope-chip{flex:none;border:1px solid #bcded8;background:#edf8f6;color:#187b71;border-radius:999px;padding:8px 13px;font-size:13px;font-weight:600}
.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin-bottom:16px}.summary-card{display:flex;align-items:center;gap:13px;min-height:92px;padding:17px 18px;border:1px solid var(--line);border-radius:15px;background:var(--surface)}.summary-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:12px;font-size:15px;font-weight:800}.summary-icon--teal{color:#087f73;background:#ddf4ef}.summary-icon--green{color:#198754;background:#e5f5ea}.summary-icon--blue{color:#3679c9;background:#e8f1fc}.summary-icon--orange{color:#c66a10;background:#fff0df}.summary-card div{display:flex;flex-direction:column}.summary-card strong{font-size:23px;line-height:1.15}.summary-card span:last-child{margin-top:4px;color:var(--muted);font-size:12px}
.directory-card{border:1px solid var(--line);border-radius:18px;background:var(--surface);overflow:hidden}.directory-head{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 22px;border-bottom:1px solid var(--line)}.directory-head h2{margin:0 0 4px;font-size:19px}.search-form{display:flex;align-items:center;gap:8px}.search-box{display:flex;align-items:center;gap:8px;width:260px;min-height:42px;padding:0 12px;border:1px solid #ccd8d5;border-radius:10px;background:#fff;color:#84918e}.search-box:focus-within{border-color:#45a99e;box-shadow:0 0 0 3px rgba(14,143,130,.1)}.search-box input{width:100%;border:0;outline:0;background:transparent;color:var(--ink)}.primary-button,.plain-button{min-height:42px;padding:0 15px;border-radius:10px;font-weight:650;cursor:pointer}.primary-button{border:1px solid var(--teal);background:var(--teal);color:#fff}.plain-button{border:1px solid #ccd8d5;background:#fff;color:#42524f}.primary-button:disabled{opacity:.6;cursor:wait}
.student-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;padding:18px}.student-card{display:flex;flex-direction:column;min-width:0;padding:17px;border:1px solid var(--line);border-radius:15px;background:#fff;transition:border-color .16s ease,box-shadow .16s ease}.student-card:hover{border-color:#a8d4cd;box-shadow:0 8px 22px rgba(25,66,59,.07)}.student-identity{display:flex;align-items:center;gap:11px}.avatar-wrap{position:relative;flex:none;cursor:pointer}.avatar-wrap img,.avatar-wrap>span{display:grid;place-items:center;width:48px;height:48px;border-radius:50%;object-fit:cover;background:#dff4ef;color:#087f73;font-size:18px;font-weight:750}.avatar-wrap i{position:absolute;right:1px;bottom:1px;width:11px;height:11px;border:2px solid #fff;border-radius:50%;background:#22ad72}.avatar-wrap i.is-offline{background:#aab4b2}.identity-copy{min-width:0;cursor:pointer}.identity-copy h3{overflow:hidden;margin:0 0 3px;font-size:16px;text-overflow:ellipsis;white-space:nowrap}.identity-copy p{overflow:hidden;margin:0;color:#7b8885;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.status-pill{margin-left:auto;border-radius:999px;padding:5px 9px;background:#e7f6ed;color:#247b4f;font-size:11px;font-weight:700}.status-pill--disabled{background:#f0f1f1;color:#77807e}.status-pill--online{background:#e7f6ed;color:#247b4f}.status-pill--offline{background:#f0f1f1;color:#8a9491}.last-active{margin:13px 0 0;color:#7b8885;font-size:12px}.student-metrics{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:8px;margin:15px 0;padding:13px 0;border-block:1px solid #edf1ef}.student-metrics div{display:flex;flex-direction:column;gap:3px}.student-metrics div+div{border-left:1px solid #edf1ef;padding-left:10px}.student-metrics strong{font-size:18px}.student-metrics span,.pass-rate span{color:#778480;font-size:11px}.pass-rate>div{display:flex;justify-content:space-between;align-items:center}.pass-rate strong{font-size:12px}.rate-track{display:block;height:6px;margin-top:7px;border-radius:999px;background:#edf2f0;overflow:hidden}.rate-track i{display:block;height:100%;border-radius:inherit;background:#1aa493}.student-actions{display:flex;gap:8px;margin-top:14px}.student-action{flex:1;display:flex;align-items:center;justify-content:center;gap:4px;min-height:38px;padding:0 12px;border-radius:10px;background:#f1f8f6;color:#087f73;font-size:13px;font-weight:650;text-decoration:none;cursor:pointer;transition:background .15s ease,transform .1s ease;border:0}.student-action:hover{background:#e4f3f0}.student-action:active{transform:translateY(1px)}.student-action--view{background:#eef2ff;color:#4f46e5}.student-action--view:hover{background:#e0e7ff}
/* 邀请面板 */
.invite-panel{padding:18px 22px;border-bottom:1px solid var(--line);background:linear-gradient(180deg,#fafbfc 0%,#fff 100%)}.invite-panel__head h3{margin:0 0 4px;font-size:16px;color:var(--ink)}.invite-panel__head p{margin:0 0 14px;color:var(--muted);font-size:13px}.invite-panel__search{display:flex;gap:8px}.invite-panel__search input{flex:1;min-height:40px;padding:0 12px;border:1px solid #ccd8d5;border-radius:10px;outline:0;font-size:14px}.invite-panel__search input:focus{border-color:#45a99e;box-shadow:0 0 0 3px rgba(14,143,130,.1)}.invite-panel__hint{margin:12px 0 0;color:var(--muted);font-size:13px}.invite-list{list-style:none;margin:14px 0 0;padding:0;display:flex;flex-direction:column;gap:8px}.invite-item{display:flex;align-items:center;gap:12px;padding:10px 12px;border:1px solid var(--line);border-radius:12px;background:#fff}.invite-item__avatar{position:relative;cursor:pointer}.invite-item__avatar img,.invite-item__avatar>span{display:grid;place-items:center;width:40px;height:40px;border-radius:50%;object-fit:cover;background:#eef2ff;color:#4f46e5;font-size:15px;font-weight:750}.invite-item__avatar i{position:absolute;right:0;bottom:0;width:9px;height:9px;border:2px solid #fff;border-radius:50%;background:#22ad72}.invite-item__avatar i.is-offline{background:#aab4b2}.invite-item__info{flex:1;min-width:0;cursor:pointer}.invite-item__info strong{display:block;font-size:14px;color:var(--ink)}.invite-item__info span{color:#7b8885;font-size:12px}.invite-item__btn{min-height:34px;padding:0 16px;border:0;border-radius:8px;background:#4f46e5;color:#fff;font-size:13px;font-weight:650;cursor:pointer;transition:background .15s ease}.invite-item__btn:hover:not(:disabled){background:#4338ca}.invite-item__btn:disabled{opacity:.6;cursor:wait}
.primary-button--accent{background:linear-gradient(135deg,#4f46e5,#7c3aed)}
/* 待审批申请 */
.pending-card{margin-bottom:16px;padding:18px 20px;border:1px solid #ddd6fe;border-radius:16px;background:linear-gradient(135deg,#f5f3ff 0%,#fff 60%)}.pending-card__head h2{margin:0 0 4px;display:flex;align-items:center;gap:8px;font-size:17px;color:#5b21b6}.pending-card__head p{margin:0;color:#6d28d9;font-size:13px}.pending-badge{display:inline-grid;place-items:center;min-width:22px;height:22px;padding:0 6px;border-radius:999px;background:#7c3aed;color:#fff;font-size:12px;font-weight:800}.pending-list{list-style:none;margin:14px 0 0;padding:0;display:flex;flex-direction:column;gap:10px}.pending-item{display:flex;align-items:center;gap:12px;padding:12px;border:1px solid #ddd6fe;border-radius:12px;background:#fff}.pending-item__avatar{flex:none;cursor:pointer}.pending-item__avatar img,.pending-item__avatar>span{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;object-fit:cover;background:#ede9fe;color:#5b21b6;font-size:16px;font-weight:750}.pending-item__info{flex:1;min-width:0;cursor:pointer}.pending-item__info strong{display:block;font-size:14px;color:var(--ink)}.pending-item__info span{color:#6d28d9;font-size:12px}.pending-item__actions{display:flex;gap:8px}.btn-approve,.btn-reject{min-height:34px;padding:0 16px;border-radius:8px;font-size:13px;font-weight:650;cursor:pointer;border:0;transition:background .15s ease}.btn-approve{background:#16a34a;color:#fff}.btn-approve:hover:not(:disabled){background:#15803d}.btn-reject{background:#fee2e2;color:#dc2626}.btn-reject:hover:not(:disabled){background:#fecaca}.btn-approve:disabled,.btn-reject:disabled{opacity:.55;cursor:wait}
.empty-state{display:flex;flex-direction:column;align-items:center;padding:70px 20px;text-align:center}.empty-icon{display:grid;place-items:center;width:50px;height:50px;border-radius:15px;background:#e7f5f2;color:#168a7d;font-weight:800}.empty-state h3{margin:15px 0 6px}.empty-state p{margin:0 0 16px;color:var(--muted)}.student-card--skeleton{min-height:270px}.skeleton{display:block;border-radius:8px;background:linear-gradient(90deg,#edf1f0,#f7f8f8,#edf1f0);background-size:200% 100%;animation:skeleton 1.2s linear infinite}.skeleton--avatar{width:48px;height:48px;border-radius:50%}.skeleton--line{width:44%;height:14px;margin-top:18px}.skeleton--wide{width:100%;height:90px;margin-top:22px}@keyframes skeleton{to{background-position:-200% 0}}
@media(max-width:1100px){.summary-grid{grid-template-columns:repeat(2,1fr)}.student-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.directory-head{align-items:flex-start;flex-direction:column}.search-form{width:100%}.search-box{flex:1;width:auto}}
@media(max-width:680px){.teaching-page{padding:18px 14px 32px}.page-heading{align-items:flex-start;flex-direction:column}.page-heading h1{font-size:25px}.summary-grid{gap:8px}.summary-card{min-height:80px;padding:13px}.summary-icon{width:36px;height:36px}.summary-card strong{font-size:20px}.directory-head{padding:16px}.search-form{flex-wrap:wrap}.search-box{flex-basis:100%}.primary-button,.plain-button{flex:1}.student-grid{grid-template-columns:1fr;padding:12px}.scope-chip{align-self:flex-start}}
@media(prefers-reduced-motion:reduce){.student-card,.skeleton{transition:none;animation:none}}
</style>
