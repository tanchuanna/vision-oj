<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { Message } from '@arco-design/web-vue'
import { OpenAPI } from '@generated'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import UserProfileModal from '@/components/teaching/UserProfileModal.vue'
import { approveRelation, fetchPendingRelations, rejectRelation, type RelationVO } from '@/api/teachingRelations'

type Teacher = {
  id: string
  username: string
  nickname?: string
  avatar?: string
  online?: boolean
}

const teachers = ref<Teacher[]>([])
const keyword = ref('')
const loading = ref(false)
const failedAvatars = ref<string[]>([])

const pendingList = ref<RelationVO[]>([])
const pendingLoading = ref(false)
const pendingActingId = ref('')

const modalVisible = ref(false)
const modalTeacherId = ref('')
const modalOnline = ref(false)

const totalTeachers = computed(() => teachers.value.length)
const onlineTeachers = computed(() => teachers.value.filter((t) => t.online).length)

function displayName(t: Teacher) { return t.nickname?.trim() || t.username?.trim() || '未命名老师' }
function avatarUrl(t: Teacher) {
  if (failedAvatars.value.includes(String(t.id))) return ''
  return resolveMediaUrl(t.avatar)
}
function onAvatarError(t: Teacher) {
  const id = String(t.id)
  if (!failedAvatars.value.includes(id)) failedAvatars.value.push(id)
}

function peerDisplayName(r: RelationVO) { return r.peerNickname?.trim() || r.peerUsername?.trim() || '该用户' }
function peerAvatarUrl(r: RelationVO) {
  if (failedAvatars.value.includes(String(r.peerId))) return ''
  return resolveMediaUrl(r.peerAvatar)
}
function onPeerAvatarError(r: RelationVO) {
  const id = String(r.peerId)
  if (!failedAvatars.value.includes(id)) failedAvatars.value.push(id)
}

function openProfile(t: Teacher) {
  modalTeacherId.value = String(t.id)
  modalOnline.value = !!t.online
  modalVisible.value = true
}
function openProfileByRelation(r: RelationVO) {
  modalTeacherId.value = String(r.peerId)
  modalOnline.value = false
  modalVisible.value = true
}

async function onApprove(r: RelationVO) {
  if (pendingActingId.value) return
  pendingActingId.value = String(r.id)
  try {
    const ok = await approveRelation(String(r.id))
    if (ok) {
      Message.success(`已同意 ${peerDisplayName(r)} 的邀请`)
      void loadPending()
    } else {
      Message.error('操作失败')
    }
  } finally {
    pendingActingId.value = ''
  }
}
async function onReject(r: RelationVO) {
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

async function getAuthHeaders() {
  const tokenResolver = OpenAPI.TOKEN
  const token = typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

async function load() {
  loading.value = true
  try {
    const base = OpenAPI.BASE.replace(/\/+$/, '')
    const { data } = await axios.get(`${base}/teaching/teachers/page`, {
      params: { current: 1, size: 50, keyword: keyword.value.trim() || undefined },
      headers: await getAuthHeaders(),
    })
    if (data?.code !== 0 && data?.code !== 200) throw new Error(data?.message || '加载失败')
    teachers.value = Array.isArray(data?.data?.records) ? data.data.records : []
  } catch (error) {
    Message.error(error instanceof Error ? error.message : '老师列表加载失败，请稍后重试')
  } finally { loading.value = false }
}

async function loadPending() {
  pendingLoading.value = true
  try {
    pendingList.value = await fetchPendingRelations()
  } finally {
    pendingLoading.value = false
  }
}

function resetSearch() { keyword.value = ''; void load() }
onMounted(() => { void load(); void loadPending() })
</script>

<template>
  <main class="teaching-page">
    <section class="page-heading">
      <div>
        <div class="heading-kicker"><span class="kicker-dot" />我的老师</div>
        <h1>任课老师</h1>
        <p>查看平台任课老师列表及其在线状态。</p>
      </div>
    </section>

    <section class="summary-grid" aria-label="老师概览">
      <article class="summary-card"><span class="summary-icon summary-icon--teal">师</span><div><strong>{{ totalTeachers }}</strong><span>任课老师</span></div></article>
      <article class="summary-card"><span class="summary-icon summary-icon--green">●</span><div><strong>{{ onlineTeachers }}</strong><span>在线</span></div></article>
    </section>

    <!-- 待我处理的老师邀请 -->
    <section v-if="pendingList.length" class="pending-card">
      <div class="pending-card__head">
        <h2><span class="pending-badge">{{ pendingList.length }}</span> 待处理的邀请</h2>
        <p>有老师邀请你加入管理，同意后即可建立师生关系。</p>
      </div>
      <ul class="pending-list">
        <li v-for="r in pendingList" :key="r.id" class="pending-item">
          <div class="pending-item__avatar" @click="openProfileByRelation(r)">
            <img v-if="peerAvatarUrl(r)" :src="peerAvatarUrl(r)" :alt="`${peerDisplayName(r)}的头像`" @error="onPeerAvatarError(r)" />
            <span v-else>{{ peerDisplayName(r).slice(0, 1).toUpperCase() }}</span>
          </div>
          <div class="pending-item__info" @click="openProfileByRelation(r)">
            <strong>{{ peerDisplayName(r) }}</strong>
            <span>邀请你加入其管理</span>
          </div>
          <div class="pending-item__actions">
            <button class="btn-approve" :disabled="pendingActingId === String(r.id)" @click="onApprove(r)">同意</button>
            <button class="btn-reject" :disabled="pendingActingId === String(r.id)" @click="onReject(r)">拒绝</button>
          </div>
        </li>
      </ul>
    </section>

    <section class="directory-card">
      <header class="directory-head">
        <div><h2>老师目录</h2><p>点击可查看老师在线状态，绿色表示当前在线。</p></div>
        <form class="search-form" role="search" @submit.prevent="load">
          <label class="search-box"><span aria-hidden="true">⌕</span><input v-model="keyword" placeholder="搜索昵称或用户名" aria-label="搜索老师" /></label>
          <button type="submit" class="primary-button" :disabled="loading">查询</button>
          <button v-if="keyword" type="button" class="plain-button" @click="resetSearch">清除</button>
        </form>
      </header>

      <div v-if="loading" class="teacher-grid" aria-label="正在加载老师">
        <article v-for="index in 6" :key="index" class="teacher-card teacher-card--skeleton"><span class="skeleton skeleton--avatar" /><span class="skeleton skeleton--line" /><span class="skeleton skeleton--wide" /></article>
      </div>
      <div v-else-if="teachers.length" class="teacher-grid">
        <article v-for="teacher in teachers" :key="teacher.id" class="teacher-card">
          <header class="teacher-identity">
            <div class="avatar-wrap" @click="openProfile(teacher)">
              <img v-if="avatarUrl(teacher)" :src="avatarUrl(teacher)" :alt="`${displayName(teacher)}的头像`" @error="onAvatarError(teacher)" />
              <span v-else>{{ displayName(teacher).slice(0, 1).toUpperCase() }}</span>
              <i :class="{ 'is-offline': !teacher.online }" aria-hidden="true" />
            </div>
            <div class="identity-copy" @click="openProfile(teacher)">
              <h3>{{ displayName(teacher) }}</h3>
              <p>@{{ teacher.username }}</p>
            </div>
            <span class="status-pill" :class="teacher.online ? 'status-pill--online' : 'status-pill--offline'">{{ teacher.online ? '在线' : '离线' }}</span>
          </header>
          <button class="teacher-card__action" @click="openProfile(teacher)">查看资料 · 申请加入 →</button>
        </article>
      </div>
      <div v-else class="empty-state">
        <span class="empty-icon">师</span><h3>{{ keyword ? '没有找到匹配的老师' : '暂无任课老师' }}</h3>
        <p>{{ keyword ? '请尝试更换昵称或用户名关键词。' : '管理员添加老师后会显示在这里。' }}</p>
      </div>
    </section>

    <UserProfileModal
      :visible="modalVisible"
      :user-id="modalTeacherId"
      :online="modalOnline"
      @close="modalVisible = false"
      @changed="load()"
    />
  </main>
</template>

<style scoped>
.teaching-page{--ink:#172326;--muted:#667673;--line:#dfe7e4;--surface:#fff;--teal:#0e8f82;max-width:1100px;margin:0 auto;padding:26px 28px 44px;color:var(--ink)}
.page-heading{margin-bottom:20px}.heading-kicker{display:flex;align-items:center;gap:8px;color:var(--teal);font-size:12px;font-weight:700;letter-spacing:.12em}.kicker-dot{width:7px;height:7px;border-radius:50%;background:#1bb9a6}.page-heading h1{margin:7px 0 5px;font-size:30px;line-height:1.2;letter-spacing:-.03em}.page-heading p{margin:0;color:var(--muted);font-size:14px}
.summary-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-bottom:16px}.summary-card{display:flex;align-items:center;gap:13px;min-height:92px;padding:17px 18px;border:1px solid var(--line);border-radius:15px;background:var(--surface)}.summary-icon{display:grid;place-items:center;width:42px;height:42px;border-radius:12px;font-size:15px;font-weight:800}.summary-icon--teal{color:#087f73;background:#ddf4ef}.summary-icon--green{color:#198754;background:#e5f5ea}.summary-card div{display:flex;flex-direction:column}.summary-card strong{font-size:23px;line-height:1.15}.summary-card span:last-child{margin-top:4px;color:var(--muted);font-size:12px}
.directory-card{border:1px solid var(--line);border-radius:18px;background:var(--surface);overflow:hidden}.directory-head{display:flex;align-items:center;justify-content:space-between;gap:20px;padding:20px 22px;border-bottom:1px solid var(--line)}.directory-head h2{margin:0 0 4px;font-size:19px}.directory-head p{margin:0;color:var(--muted);font-size:14px}.search-form{display:flex;align-items:center;gap:8px}.search-box{display:flex;align-items:center;gap:8px;width:260px;min-height:42px;padding:0 12px;border:1px solid #ccd8d5;border-radius:10px;background:#fff;color:#84918e}.search-box:focus-within{border-color:#45a99e;box-shadow:0 0 0 3px rgba(14,143,130,.1)}.search-box input{width:100%;border:0;outline:0;background:transparent;color:var(--ink)}.primary-button,.plain-button{min-height:42px;padding:0 15px;border-radius:10px;font-weight:650;cursor:pointer}.primary-button{border:1px solid var(--teal);background:var(--teal);color:#fff}.plain-button{border:1px solid #ccd8d5;background:#fff;color:#42524f}.primary-button:disabled{opacity:.6;cursor:wait}
.teacher-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:14px;padding:18px}.teacher-card{display:flex;flex-direction:column;min-width:0;padding:17px;border:1px solid var(--line);border-radius:15px;background:#fff;transition:border-color .16s ease,box-shadow .16s ease}.teacher-card:hover{border-color:#a8d4cd;box-shadow:0 8px 22px rgba(25,66,59,.07)}.teacher-card__action{margin-top:14px;min-height:38px;padding:0 14px;border:0;border-radius:10px;background:#f1f8f6;color:#087f73;font-size:13px;font-weight:650;cursor:pointer;transition:background .15s ease,transform .1s ease}.teacher-card__action:hover{background:#e4f3f0}.teacher-card__action:active{transform:translateY(1px)}.teacher-identity{display:flex;align-items:center;gap:11px}.avatar-wrap{position:relative;flex:none;cursor:pointer}.identity-copy{min-width:0;flex:1;cursor:pointer}.avatar-wrap img,.avatar-wrap>span{display:grid;place-items:center;width:48px;height:48px;border-radius:50%;object-fit:cover;background:#dff4ef;color:#087f73;font-size:18px;font-weight:750}.avatar-wrap i{position:absolute;right:1px;bottom:1px;width:11px;height:11px;border:2px solid #fff;border-radius:50%;background:#22ad72}.avatar-wrap i.is-offline{background:#aab4b2}.identity-copy h3{overflow:hidden;margin:0 0 3px;font-size:16px;text-overflow:ellipsis;white-space:nowrap}.identity-copy p{overflow:hidden;margin:0;color:#7b8885;font-size:12px;text-overflow:ellipsis;white-space:nowrap}.status-pill{flex:none;border-radius:999px;padding:5px 9px;font-size:11px;font-weight:700}.status-pill--online{background:#e7f6ed;color:#247b4f}.status-pill--offline{background:#f0f1f1;color:#8a9491}
.empty-state{display:flex;flex-direction:column;align-items:center;padding:70px 20px;text-align:center}.empty-icon{display:grid;place-items:center;width:50px;height:50px;border-radius:15px;background:#e7f5f2;color:#168a7d;font-weight:800}.empty-state h3{margin:15px 0 6px}.empty-state p{margin:0;color:var(--muted)}
/* 待处理邀请 */
.pending-card{margin-bottom:16px;padding:18px 20px;border:1px solid #fde68a;border-radius:16px;background:linear-gradient(135deg,#fffbeb 0%,#fff 60%)}.pending-card__head h2{margin:0 0 4px;display:flex;align-items:center;gap:8px;font-size:17px;color:#92400e}.pending-card__head p{margin:0;color:#a16207;font-size:13px}.pending-badge{display:inline-grid;place-items:center;min-width:22px;height:22px;padding:0 6px;border-radius:999px;background:#f59e0b;color:#fff;font-size:12px;font-weight:800}.pending-list{list-style:none;margin:14px 0 0;padding:0;display:flex;flex-direction:column;gap:10px}.pending-item{display:flex;align-items:center;gap:12px;padding:12px;border:1px solid #fde68a;border-radius:12px;background:#fff}.pending-item__avatar{flex:none;cursor:pointer}.pending-item__avatar img,.pending-item__avatar>span{display:grid;place-items:center;width:42px;height:42px;border-radius:50%;object-fit:cover;background:#fef3c7;color:#92400e;font-size:16px;font-weight:750}.pending-item__info{flex:1;min-width:0;cursor:pointer}.pending-item__info strong{display:block;font-size:14px;color:var(--ink)}.pending-item__info span{color:#a16207;font-size:12px}.pending-item__actions{display:flex;gap:8px}.btn-approve,.btn-reject{min-height:34px;padding:0 16px;border-radius:8px;font-size:13px;font-weight:650;cursor:pointer;border:0;transition:background .15s ease}.btn-approve{background:#16a34a;color:#fff}.btn-approve:hover:not(:disabled){background:#15803d}.btn-reject{background:#fee2e2;color:#dc2626}.btn-reject:hover:not(:disabled){background:#fecaca}.btn-approve:disabled,.btn-reject:disabled{opacity:.55;cursor:wait}
.teacher-card--skeleton{min-height:80px}.skeleton{display:block;border-radius:8px;background:linear-gradient(90deg,#edf1f0,#f7f8f8,#edf1f0);background-size:200% 100%;animation:skeleton 1.2s linear infinite}.skeleton--avatar{width:48px;height:48px;border-radius:50%}.skeleton--line{width:44%;height:14px;margin-top:18px}.skeleton--wide{width:100%;height:90px;margin-top:22px}@keyframes skeleton{to{background-position:-200% 0}}
@media(max-width:680px){.teaching-page{padding:18px 14px 32px}.page-heading h1{font-size:25px}.summary-grid{grid-template-columns:1fr}.directory-head{flex-direction:column;align-items:flex-start}.search-form{width:100%}.search-box{flex:1;width:auto}}
</style>
