<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Message } from '@arco-design/web-vue'
import { useAuthStore } from '@/stores/auth'
import { resolveMediaUrl } from '@/utils/resolveMediaUrl'
import {
  applyToTeacher,
  approveRelation,
  cancelRelation,
  fetchPublicProfile,
  fetchRelationWith,
  inviteStudent,
  leaveRelation,
  type PublicProfileVO,
  rejectRelation,
  type RelationVO,
} from '@/api/teachingRelations'

const props = defineProps<{
  visible: boolean
  userId: string
  /** 预设在线状态（来自列表，避免重复查） */
  online?: boolean
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'changed'): void
}>()

const auth = useAuthStore()

const loading = ref(false)
const profile = ref<PublicProfileVO | null>(null)
const relation = ref<RelationVO | null>(null)
const acting = ref(false)
const avatarFailed = ref(false)
const loadError = ref('')

const displayName = computed(
  () => profile.value?.nickname?.trim() || profile.value?.username?.trim() || '该用户',
)

const avatarSrc = computed(() => {
  if (avatarFailed.value) return ''
  return resolveMediaUrl(profile.value?.avatar)
})

const roleLabel = computed(() => {
  const r = (profile.value?.userRole || '').toLowerCase()
  if (r === 'admin') return '管理员'
  if (r === 'teacher') return '老师'
  if (r === 'user' || r === 'student') return '学生'
  return ''
})

const roleClass = computed(() => {
  const r = (profile.value?.userRole || '').toLowerCase()
  if (r === 'admin') return 'role-admin'
  if (r === 'teacher') return 'role-teacher'
  return 'role-student'
})

const sexText = computed(() => {
  if (profile.value?.sex === 1) return '男'
  if (profile.value?.sex === 2) return '女'
  return ''
})

const locationText = computed(() => {
  const l = profile.value?.locationCodes
  if (!l) return ''
  return [l.province, l.city, l.district].filter(Boolean).join(' · ')
})

const websiteList = computed(() => profile.value?.website?.filter(Boolean) ?? [])

// 关系状态派生
const isActive = computed(() => relation.value?.status === 1)
const isPending = computed(() => relation.value?.status === 2)
const isMine = computed(() => relation.value?.mine === true)
// 我是不是被审批的一方：pendingRole 表示等待谁审批
const pendingForMe = computed(() => {
  if (!isPending.value) return false
  const pr = relation.value?.pendingRole
  if (pr === 'teacher') return auth.isTeacher
  if (pr === 'student') return auth.isStudent
  return false
})

// 操作可用性
const canApply = computed(
  () => !relation.value && auth.isStudent && profile.value?.userRole?.toLowerCase() === 'teacher',
)
const canInvite = computed(
  () =>
    !relation.value &&
    auth.isTeacher &&
    (profile.value?.userRole?.toLowerCase() === 'user' ||
      profile.value?.userRole?.toLowerCase() === 'student'),
)
const canApprove = computed(() => isPending.value && pendingForMe.value)
const canCancel = computed(() => isPending.value && isMine.value)
const canLeave = computed(() => isActive.value)

async function load() {
  if (!props.userId) return
  loading.value = true
  loadError.value = ''
  avatarFailed.value = false
  try {
    const [p, r] = await Promise.all([
      fetchPublicProfile(props.userId),
      fetchRelationWith(props.userId),
    ])
    profile.value = p
    relation.value = r
    if (!p) {
      loadError.value = '资料加载失败，请检查网络后重试'
    }
  } catch (e) {
    console.error('[UserProfileModal] load failed:', e)
    loadError.value = '资料加载失败，请重试'
  } finally {
    loading.value = false
  }
}

async function doAction(
  fn: () => Promise<boolean>,
  okMsg: string,
  failMsg = '操作失败，请稍后重试',
) {
  if (acting.value) return
  acting.value = true
  try {
    const ok = await fn()
    if (ok) {
      Message.success(okMsg)
      emit('changed')
      relation.value = await fetchRelationWith(props.userId)
    } else {
      Message.error(failMsg)
    }
  } finally {
    acting.value = false
  }
}

function onApply() {
  doAction(() => applyToTeacher(props.userId), '申请已提交，等待老师同意')
}
function onInvite() {
  doAction(() => inviteStudent(props.userId), '邀请已发送，等待学生同意')
}
function onApprove() {
  if (!relation.value) return
  doAction(() => approveRelation(relation.value!.id), '已同意')
}
function onReject() {
  if (!relation.value) return
  doAction(() => rejectRelation(relation.value!.id), '已拒绝')
}
function onCancel() {
  if (!relation.value) return
  doAction(() => cancelRelation(relation.value!.id), '已撤回')
}
function onLeave() {
  if (!relation.value) return
  doAction(() => leaveRelation(relation.value!.id), '已解除师生关系')
}

function onClose() {
  emit('close')
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') onClose()
}

watch(
  () => [props.visible, props.userId],
  ([v]) => {
    if (v) {
      void load()
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  },
  { immediate: true },
)

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal-fade">
      <div v-if="visible" class="profile-modal-mask" @click.self="onClose">
        <Transition name="modal-pop" appear>
          <div
            v-if="visible"
            class="profile-modal"
            role="dialog"
            aria-modal="true"
            :aria-label="`${displayName}的资料`"
          >
            <!-- 顶部渐变条 -->
            <div class="profile-modal__banner" :class="roleClass" />

            <!-- 关闭按钮 -->
            <button class="profile-modal__close" aria-label="关闭" @click="onClose">
              <svg
                viewBox="0 0 24 24"
                width="18"
                height="18"
                fill="none"
                stroke="currentColor"
                stroke-width="2.2"
                stroke-linecap="round"
              >
                <path d="M6 6l12 12M18 6L6 18" />
              </svg>
            </button>

            <!-- 头像 -->
            <div class="profile-modal__avatar-wrap">
              <div class="profile-modal__avatar-ring" :class="roleClass">
                <img
                  v-if="avatarSrc"
                  :src="avatarSrc"
                  :alt="`${displayName}的头像`"
                  @error="avatarFailed = true"
                />
                <span v-else>{{ displayName.slice(0, 1).toUpperCase() }}</span>
              </div>
              <span class="profile-modal__online" :class="online ? 'is-online' : 'is-offline'">
                {{ online ? '在线' : '离线' }}
              </span>
            </div>

            <div class="profile-modal__body">
              <div v-if="loading" class="profile-modal__loading">加载中…</div>

              <template v-else-if="profile">
                <!-- 名字与角色 -->
                <div class="profile-modal__name-row">
                  <h2 class="profile-modal__name">{{ displayName }}</h2>
                  <span v-if="roleLabel" class="profile-modal__role" :class="roleClass">{{
                    roleLabel
                  }}</span>
                </div>
                <p class="profile-modal__username">@{{ profile.username }}</p>

                <!-- 关系状态提示 -->
                <div
                  v-if="isActive"
                  class="profile-modal__relation profile-modal__relation--active"
                >
                  <span class="dot" /> 师生关系已建立
                </div>
                <div
                  v-else-if="isPending && isMine"
                  class="profile-modal__relation profile-modal__relation--pending"
                >
                  <span class="dot" /> 等待对方同意中…
                </div>
                <div
                  v-else-if="isPending && !isMine"
                  class="profile-modal__relation profile-modal__relation--incoming"
                >
                  <span class="dot" /> 对方向你发出{{ auth.isTeacher ? '加入申请' : '管理邀请' }}
                </div>

                <!-- 个人介绍 -->
                <div v-if="profile.introduction?.trim()" class="profile-modal__intro">
                  <div class="profile-modal__intro-label">个人介绍</div>
                  <p>{{ profile.introduction }}</p>
                </div>

                <!-- 信息网格 -->
                <div
                  v-if="locationText || sexText || websiteList.length || profile.country"
                  class="profile-modal__info"
                >
                  <div v-if="locationText" class="info-item">
                    <span class="info-icon" aria-hidden="true">📍</span>
                    <span>{{ locationText }}</span>
                  </div>
                  <div v-if="sexText" class="info-item">
                    <span class="info-icon" aria-hidden="true">👤</span>
                    <span>{{ sexText }}</span>
                  </div>
                  <div v-if="profile.country" class="info-item">
                    <span class="info-icon" aria-hidden="true">🌐</span>
                    <span>{{ profile.country }}</span>
                  </div>
                  <div v-for="site in websiteList" :key="site" class="info-item">
                    <span class="info-icon" aria-hidden="true">🔗</span>
                    <a
                      :href="site.startsWith('http') ? site : `https://${site}`"
                      target="_blank"
                      rel="noopener noreferrer"
                      >{{ site }}</a
                    >
                  </div>
                </div>

                <!-- 空状态 -->
                <div
                  v-if="
                    !profile.introduction?.trim() &&
                    !locationText &&
                    !sexText &&
                    !websiteList.length &&
                    !profile.country
                  "
                  class="profile-modal__empty"
                >
                  这个人很神秘，还没有留下任何介绍。
                </div>
              </template>

              <div v-else class="profile-modal__error">
                <p>{{ loadError || '无法加载该用户资料' }}</p>
                <button class="btn btn--primary" @click="load">重试</button>
              </div>
            </div>

            <!-- 操作按钮区 -->
            <div v-if="!loading && profile" class="profile-modal__actions">
              <template v-if="canApply">
                <button class="btn btn--primary" :disabled="acting" @click="onApply">
                  申请加入该老师
                </button>
              </template>
              <template v-else-if="canInvite">
                <button class="btn btn--primary" :disabled="acting" @click="onInvite">
                  邀请该学生加入管理
                </button>
              </template>
              <template v-else-if="canApprove">
                <button class="btn btn--primary" :disabled="acting" @click="onApprove">同意</button>
                <button class="btn btn--ghost" :disabled="acting" @click="onReject">拒绝</button>
              </template>
              <template v-else-if="canCancel">
                <span class="hint">等待对方处理…</span>
                <button class="btn btn--ghost" :disabled="acting" @click="onCancel">撤回</button>
              </template>
              <template v-else-if="canLeave">
                <button class="btn btn--danger" :disabled="acting" @click="onLeave">
                  {{ auth.isTeacher ? '移除该学生' : '退出师门' }}
                </button>
              </template>
              <template v-else>
                <span class="hint">—</span>
              </template>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.profile-modal-mask {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(15, 23, 42, 0.55);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
}

.profile-modal {
  position: relative;
  width: 100%;
  max-width: 480px;
  max-height: 88vh;
  overflow-y: auto;
  border-radius: 22px;
  background: var(--color-bg-2, #fff);
  box-shadow:
    0 30px 80px rgba(0, 0, 0, 0.35),
    0 8px 24px rgba(0, 0, 0, 0.18);
  padding-bottom: 76px;
}

/* 顶部渐变 banner */
.profile-modal__banner {
  height: 110px;
  border-radius: 22px 22px 0 0;
  background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 50%, #a855f7 100%);
}
.profile-modal__banner.role-teacher {
  background: linear-gradient(135deg, #0ea5e9 0%, #06b6d4 50%, #14b8a6 100%);
}
.profile-modal__banner.role-student {
  background: linear-gradient(135deg, #f59e0b 0%, #f97316 50%, #ef4444 100%);
}
.profile-modal__banner.role-admin {
  background: linear-gradient(135deg, #475569 0%, #334155 50%, #1e293b 100%);
}

.profile-modal__close {
  position: absolute;
  top: 12px;
  right: 12px;
  z-index: 2;
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 0;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  cursor: pointer;
  backdrop-filter: blur(4px);
  transition: background 0.15s ease;
}
.profile-modal__close:hover {
  background: rgba(255, 255, 255, 0.4);
}

/* 头像 */
.profile-modal__avatar-wrap {
  position: relative;
  margin: -48px auto 0;
  width: 96px;
}
.profile-modal__avatar-ring {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  padding: 4px;
  background: linear-gradient(135deg, #6366f1, #a855f7);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.4);
}
.profile-modal__avatar-ring.role-teacher {
  background: linear-gradient(135deg, #0ea5e9, #14b8a6);
  box-shadow: 0 8px 24px rgba(14, 165, 233, 0.4);
}
.profile-modal__avatar-ring.role-student {
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  box-shadow: 0 8px 24px rgba(245, 158, 11, 0.4);
}
.profile-modal__avatar-ring.role-admin {
  background: linear-gradient(135deg, #475569, #1e293b);
}
.profile-modal__avatar-ring img,
.profile-modal__avatar-ring > span {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  border-radius: 50%;
  background: #fff;
  object-fit: cover;
  font-size: 36px;
  font-weight: 800;
  color: #6366f1;
}
.profile-modal__avatar-ring.role-teacher > span {
  color: #0ea5e9;
}
.profile-modal__avatar-ring.role-student > span {
  color: #f59e0b;
}
.profile-modal__avatar-ring.role-admin > span {
  color: #475569;
}

.profile-modal__online {
  position: absolute;
  bottom: 4px;
  right: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
  border: 2px solid var(--color-bg-2, #fff);
}
.profile-modal__online.is-online {
  background: #16a34a;
  color: #fff;
}
.profile-modal__online.is-offline {
  background: #94a3b8;
  color: #fff;
}

/* 主体内容 */
.profile-modal__body {
  padding: 16px 24px 20px;
  text-align: center;
}

.profile-modal__loading {
  padding: 40px 0;
  color: var(--app-text-muted, #94a3b8);
  font-size: 14px;
}

.profile-modal__error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14px;
  padding: 30px 20px;
}
.profile-modal__error p {
  margin: 0;
  color: var(--app-text-muted, #94a3b8);
  font-size: 14px;
}

.profile-modal__name-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 8px;
}
.profile-modal__name {
  margin: 0;
  font-size: 22px;
  font-weight: 800;
  color: var(--color-text-1, #0f172a);
}
.profile-modal__role {
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  color: #fff;
}
.profile-modal__role.role-teacher {
  background: #0ea5e9;
}
.profile-modal__role.role-student {
  background: #f59e0b;
}
.profile-modal__role.role-admin {
  background: #475569;
}

.profile-modal__username {
  margin: 4px 0 0;
  color: var(--app-text-muted, #94a3b8);
  font-size: 13px;
}

/* 关系状态 */
.profile-modal__relation {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 14px auto 0;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
}
.profile-modal__relation .dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
}
.profile-modal__relation--active {
  background: rgba(22, 163, 74, 0.12);
  color: #15803d;
}
.profile-modal__relation--active .dot {
  background: #16a34a;
}
.profile-modal__relation--pending {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}
.profile-modal__relation--pending .dot {
  background: #f59e0b;
  animation: pulse 1.4s infinite;
}
.profile-modal__relation--incoming {
  background: rgba(99, 102, 241, 0.12);
  color: #4f46e5;
}
.profile-modal__relation--incoming .dot {
  background: #6366f1;
  animation: pulse 1.4s infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(1.3);
  }
}

/* 个人介绍 */
.profile-modal__intro {
  margin: 18px 0 0;
  padding: 14px 16px;
  border-radius: 14px;
  background: var(--color-fill-1, #f8fafc);
  text-align: left;
}
.profile-modal__intro-label {
  margin-bottom: 6px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--app-text-muted, #94a3b8);
  text-transform: uppercase;
}
.profile-modal__intro p {
  margin: 0;
  font-size: 14px;
  line-height: 1.65;
  color: var(--color-text-2, #334155);
  white-space: pre-wrap;
  word-break: break-word;
}

/* 信息网格 */
.profile-modal__info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin: 16px 0 0;
  text-align: left;
}
.info-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  color: var(--color-text-2, #334155);
}
.info-item a {
  color: #0ea5e9;
  text-decoration: none;
  word-break: break-all;
}
.info-item a:hover {
  text-decoration: underline;
}

.profile-modal__empty {
  margin: 22px 0 0;
  padding: 18px;
  color: var(--app-text-muted, #94a3b8);
  font-size: 13px;
  font-style: italic;
}

/* 底部操作区 */
.profile-modal__actions {
  position: sticky;
  bottom: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 14px 24px;
  background: inherit;
  border-top: 1px solid var(--color-border-1, #e2e8f0);
  margin-top: 4px;
}

.btn {
  min-height: 38px;
  padding: 0 20px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 650;
  cursor: pointer;
  border: 1px solid transparent;
  transition:
    transform 0.1s ease,
    box-shadow 0.15s ease,
    background 0.15s ease;
}
.btn:active:not(:disabled) {
  transform: translateY(1px);
}
.btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.btn--primary {
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  box-shadow: 0 4px 14px rgba(99, 102, 241, 0.35);
}
.btn--primary:hover:not(:disabled) {
  box-shadow: 0 6px 20px rgba(99, 102, 241, 0.45);
}

.btn--ghost {
  background: transparent;
  color: var(--color-text-2, #475569);
  border-color: var(--color-border-2, #cbd5e1);
}
.btn--ghost:hover:not(:disabled) {
  background: var(--color-fill-1, #f1f5f9);
}

.btn--danger {
  background: #fff;
  color: #dc2626;
  border-color: #fecaca;
}
.btn--danger:hover:not(:disabled) {
  background: #fef2f2;
}

.hint {
  font-size: 13px;
  color: var(--app-text-muted, #94a3b8);
}

/* 动画 */
.modal-fade-enter-active,
.modal-fade-leave-active {
  transition: opacity 0.2s ease;
}
.modal-fade-enter-from,
.modal-fade-leave-to {
  opacity: 0;
}

.modal-pop-enter-active {
  transition:
    transform 0.28s cubic-bezier(0.34, 1.56, 0.64, 1),
    opacity 0.2s ease;
}
.modal-pop-leave-active {
  transition:
    transform 0.18s ease,
    opacity 0.15s ease;
}
.modal-pop-enter-from {
  opacity: 0;
  transform: translateY(24px) scale(0.94);
}
.modal-pop-leave-to {
  opacity: 0;
  transform: translateY(12px) scale(0.97);
}

/* 滚动条美化 */
.profile-modal::-webkit-scrollbar {
  width: 6px;
}
.profile-modal::-webkit-scrollbar-thumb {
  background: rgba(148, 163, 184, 0.4);
  border-radius: 3px;
}

@media (max-width: 520px) {
  .profile-modal {
    border-radius: 18px;
  }
  .profile-modal__banner {
    height: 90px;
  }
  .profile-modal__avatar-wrap {
    margin-top: -40px;
    width: 80px;
  }
  .profile-modal__avatar-ring {
    width: 80px;
    height: 80px;
  }
  .profile-modal__name {
    font-size: 19px;
  }
}
</style>
