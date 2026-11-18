<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, provide, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { removeAdminRoutes } from '@/router/admin-dynamic'
import { Message } from '@arco-design/web-vue'
import { OpenAPI, Service } from '@generated'
import BrandLogo from '@/components/BrandLogo.vue'
import AppFooter from '@/components/AppFooter.vue'
import ModerationBanNotice from '@/components/moderation/ModerationBanNotice.vue'
import CheckInCalendarModal from '@/components/checkin/CheckInCalendarModal.vue'
import AuthModal from '@/components/auth/AuthModal.vue'
import { useAuthStore } from '@/stores/auth'
import { isResultSuccess } from '@/api/result'
import { fetchNotificationSummary } from '@/api/inAppNotifications'
import { LAYOUT_THEME_KEY } from '@/config/theme-injection'
import { HOME_INTRO_AUTH_VISIBLE_KEY } from '@/config/homeIntro'
import { useBanCountdownTick } from '@/composables/useBanCountdownTick'
import { useOnlineHeartbeat } from '@/composables/useOnlineHeartbeat'
import { useSessionStatusPush } from '@/composables/useSessionStatusPush'
import { formatBanCountdown, parseBanUntilMs } from '@/utils/formatBanCountdown'
import { normalizeRole, PERMISSIONS } from '@/config/roles'
import { blogSharedTransitionActive } from '@/composables/blogSharedTransition'
import { problemSharedTransitionActive } from '@/composables/problemSharedTransition'
import { localize as l, setSiteLocale, useSiteLocale } from '@/i18n/locale'

const route = useRoute()
const routerInstance = useRouter()
const pageTransition = ref('')
watch(
  () => route.path,
  (path, previousPath) => {
    if (!previousPath) return
    const from = routerInstance.resolve(previousPath).name
    const to = routerInstance.resolve(path).name
    // 白天模式的博客页保持实体白底，避免整页透明淡入时露出底层背景而显得发白。
    if ((to === 'blog' || to === 'blog-detail') && document.documentElement.dataset.theme !== 'dark') {
      pageTransition.value = ''
      return
    }
    // 编辑器有独立的布局与草稿生命周期，切换时直接呈现，避免动画期间重绘 Monaco。
    if (blogSharedTransitionActive || problemSharedTransitionActive || from === 'problem-solve' || to === 'problem-solve') {
      pageTransition.value = ''
      return
    }
    pageTransition.value = to === 'blog-detail' ? 'page-detail' : 'page-fade'
  },
  { flush: 'sync' },
)
const auth = useAuthStore()
const siteLocale = useSiteLocale()
function toggleSiteLocale() {
  setSiteLocale(siteLocale.value === 'zh-CN' ? 'en-US' : 'zh-CN')
}
const adminOpen = ref(false)
const adminMenuRef = ref<HTMLElement | null>(null)
const adminButtonRef = ref<HTMLButtonElement | null>(null)
const adminDropdownRef = ref<HTMLElement | null>(null)
const adminDropdownStyle = ref<Record<string, string>>({})
const userOpen = ref(false)
const userPinnedOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)
let userCloseTimer: ReturnType<typeof setTimeout> | null = null
const authVisible = ref(false)
provide(HOME_INTRO_AUTH_VISIBLE_KEY, authVisible)

/** 站内消息 + 未读公告角标 */
const notifUnreadTotal = ref(0)

const checkInPoints = ref(0)
const checkInTotal = ref(0)
const checkInCheckedToday = ref(false)
const checkInLoading = ref(false)
const checkInModalOpen = ref(false)

const THEME_STORAGE_KEY = 'myoj:theme'
type ThemeMode = 'light' | 'dark'

function readStoredTheme(): ThemeMode {
  if (typeof localStorage === 'undefined') return 'light'
  const saved = localStorage.getItem(THEME_STORAGE_KEY)
  if (saved === 'dark' || saved === 'light') return saved
  return 'light'
}

const themeMode = ref<ThemeMode>(readStoredTheme())
if (typeof document !== 'undefined') {
  document.documentElement.setAttribute('data-theme', themeMode.value)
}
provide(LAYOUT_THEME_KEY, themeMode)
const isDarkTheme = computed(() => themeMode.value === 'dark')
const themeButtonTitle = computed(() => (isDarkTheme.value ? l('切换到浅色模式', 'Switch to light mode') : l('切换到深色模式', 'Switch to dark mode')))
const themeButtonIcon = computed(() => (isDarkTheme.value ? '☀' : '☾'))
const userAvatarUrl = computed(() => auth.avatarUrl)
const showFooter = computed(() => route.meta.hideFooter !== true)

/** 顶栏角色展示：英文/空值统一成可读中文 */
const roleLabel = computed(() => {
  const role = normalizeRole(auth.userRole)
  return role === 'admin' ? l('管理员', 'Admin') : role === 'teacher' ? l('老师', 'Teacher') : l('学生', 'Student')
})

function applyTheme(mode: ThemeMode) {
  themeMode.value = mode
  document.documentElement.setAttribute('data-theme', mode)
  localStorage.setItem(THEME_STORAGE_KEY, mode)
}

function toggleTheme() {
  applyTheme(themeMode.value === 'dark' ? 'light' : 'dark')
}

onMounted(() => {
  const saved = localStorage.getItem(THEME_STORAGE_KEY)
  if (saved === 'dark' || saved === 'light') {
    applyTheme(saved)
    return
  }
  const preferDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches
  applyTheme(preferDark ? 'dark' : 'light')
})

watch(
  () => route.query.openAuth,
  (v) => {
    if (v === '1') {
      authVisible.value = true
      const q = { ...route.query } as Record<string, string | string[] | undefined>
      delete q.openAuth
      routerInstance.replace({ path: route.path, query: q })
    }
  },
  { immediate: true },
)

function logout() {
  closeUserMenu()
  const onProtectedPage =
    route.matched.some((r) => r.meta.requiresAuth) || route.path.startsWith('/admin')
  removeAdminRoutes(routerInstance)
  auth.logout()
  Message.success(l('已退出登录', 'Signed out'))
  if (onProtectedPage) {
    void routerInstance.replace('/')
  }
}

type NavItem = {
  to: string
  label: string
  exact?: boolean
  accent?: boolean
}

const mainNav = computed<NavItem[]>(() => [
  { to: '/', label: l('首页', 'Home'), exact: true },
  { to: '/problems', label: l('题库', 'Problems') },
  ...(auth.isStudent
    ? [{ to: '/my-practice', label: l('我的练习', 'My practice') }]
    : []),
  { to: '/contests', label: l('比赛', 'Contests') },
  { to: '/rankings', label: l('排行榜', 'Leaderboard') },
  { to: '/teaching/teachers', label: l('任课老师', 'Teachers') },
  { to: '/submissions', label: l('提交记录', 'Submissions') },
  { to: '/notifications', label: l('消息', 'Messages') },
])

const adminMenus = computed(
  () =>
    [
      auth.can(PERMISSIONS.QUESTION_AUTHOR)
        ? {
            to: '/manage/problems',
            title: l('题目管理', 'Manage problems'),
            desc: l('编辑题目、标签与判题配置', 'Edit problems, tags and judging settings'),
            icon: '🧩',
          }
        : null,
      auth.can(PERMISSIONS.STUDENT_VIEW_ASSIGNED) || auth.can(PERMISSIONS.STUDENT_VIEW_ALL)
        ? {
            to: '/teaching/students',
            title: l('学生管理', 'Manage students'),
            desc: l('查看有权管理学生的学习数据', 'View assigned student progress'),
            icon: '🎓',
          }
        : null,
      auth.can(PERMISSIONS.USER_ROLE_MANAGE)
        ? { to: '/admin/users', title: l('用户管理', 'Manage users'), desc: l('角色、分配与账号状态', 'Roles, assignments and account status'), icon: '👥' }
        : null,
      auth.can(PERMISSIONS.CONTEST_MANAGE)
        ? { to: '/admin/contests', title: l('比赛管理', 'Manage contests'), desc: l('创建限时赛与办赛配置', 'Create contests and settings'), icon: '🏁' }
        : null,
      auth.can(PERMISSIONS.ANNOUNCEMENT_MANAGE)
        ? { to: '/admin/announcements', title: l('发布公告', 'Announcements'), desc: l('全站公告与未读提醒', 'Site notices and unread alerts'), icon: '📢' }
        : null,
    ].filter(Boolean) as Array<{ to: string; title: string; desc: string; icon: string }>,
)

function isNavActive(path: string, exact?: boolean) {
  if (exact) return route.path === '/' || route.path === ''
  return route.path === path || route.path.startsWith(path + '/')
}

function isAdminNavActive() {
  return (
    route.path.startsWith('/admin') ||
    route.path.startsWith('/manage') ||
    route.path.startsWith('/teaching')
  )
}

function toggleAdminMenu() {
  adminOpen.value = !adminOpen.value
  if (adminOpen.value) {
    updateAdminDropdownPosition()
    void nextTick(() => adminDropdownRef.value?.querySelector<HTMLElement>('a')?.focus())
  }
}

function updateAdminDropdownPosition() {
  if (!adminOpen.value || !adminButtonRef.value) return
  const bounds = adminButtonRef.value.getBoundingClientRect()
  const width = Math.min(320, window.innerWidth - 24)
  const left = Math.max(12, Math.min(bounds.right - width, window.innerWidth - width - 12))
  const top = bounds.bottom + 8
  adminDropdownStyle.value = {
    left: `${left}px`,
    top: `${top}px`,
    maxHeight: `${Math.max(120, window.innerHeight - top - 12)}px`,
  }
}

function closeAdminMenu() {
  adminOpen.value = false
}

function openUserMenu() {
  if (userCloseTimer) {
    clearTimeout(userCloseTimer)
    userCloseTimer = null
  }
  userOpen.value = true
}

function closeUserMenu() {
  if (userCloseTimer) {
    clearTimeout(userCloseTimer)
    userCloseTimer = null
  }
  userPinnedOpen.value = false
  userOpen.value = false
}

function goAccountSettings() {
  closeUserMenu()
  void routerInstance.push('/account/settings')
}

async function getBearerHeaders(): Promise<Record<string, string>> {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token?.trim() ? { Authorization: `Bearer ${token.trim()}` } : {}
}

async function loadCheckInStatus() {
  if (!auth.isLoggedIn) return
  checkInLoading.value = true
  try {
    const headers = await getBearerHeaders()
    if (!headers.Authorization) return
    const data = await Service.checkInStatus()
    if (!isResultSuccess(data?.code) || !data?.data) return
    const d = data.data
    checkInPoints.value = typeof d.points === 'number' ? d.points : 0
    checkInTotal.value = typeof d.checkInCount === 'number' ? d.checkInCount : 0
    checkInCheckedToday.value = !!d.checkedToday
  } catch {
    /* 静默失败：下拉仍可正常使用（如后端未跑迁移） */
  } finally {
    checkInLoading.value = false
  }
}

function openCheckInModal() {
  if (auth.isAccountDisabled) {
    Message.warning('账号已被禁用，暂不可签到')
    return
  }
  userOpen.value = false
  checkInModalOpen.value = true
}

function onCheckInStatusFromModal(payload: {
  points: number
  checkInCount: number
  checkedToday: boolean
}) {
  checkInPoints.value = payload.points
  checkInTotal.value = payload.checkInCount
  checkInCheckedToday.value = payload.checkedToday
}

function scheduleCloseUserMenu() {
  if (userPinnedOpen.value) return
  if (userCloseTimer) {
    clearTimeout(userCloseTimer)
  }
  userCloseTimer = setTimeout(() => {
    userOpen.value = false
    userCloseTimer = null
  }, 220)
}

function toggleUserMenuPin() {
  if (userPinnedOpen.value) {
    closeUserMenu()
    return
  }
  openUserMenu()
  userPinnedOpen.value = true
}

function onGlobalPointerDown(e: Event) {
  const target = e.target as Node | null
  if (adminOpen.value && target && !adminMenuRef.value?.contains(target) && !adminDropdownRef.value?.contains(target)) {
    adminOpen.value = false
  }
  if (userOpen.value && userMenuRef.value && target && !userMenuRef.value.contains(target)) {
    userPinnedOpen.value = false
    userOpen.value = false
  }
}

function onGlobalKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (adminOpen.value) adminButtonRef.value?.focus()
    adminOpen.value = false
    closeUserMenu()
  }
}

watch(
  () => route.fullPath,
  () => {
    adminOpen.value = false
    closeUserMenu()
  },
)

watch(
  () => userOpen.value,
  (open) => {
    if (open && auth.isLoggedIn) {
      void loadCheckInStatus()
    }
  },
)

let notifPollTimer: number | null = null

const { nowMs: banCountdownNow } = useBanCountdownTick()
useSessionStatusPush()
useOnlineHeartbeat()

const adminBanCountdown = computed(() =>
  formatBanCountdown(parseBanUntilMs(auth.banUntil), banCountdownNow.value),
)

const commentBanCountdown = computed(() =>
  formatBanCountdown(parseBanUntilMs(auth.commentBanUntil ?? auth.banUntil), banCountdownNow.value),
)

const aiAssistBanCountdown = computed(() =>
  formatBanCountdown(parseBanUntilMs(auth.aiBanUntil ?? auth.banUntil), banCountdownNow.value),
)

const accountBanNotice = computed(() => {
  if (!auth.isAccountDisabled) return null
  const timed = auth.banType === 'admin' && !!auth.banUntil?.trim()
  return {
    title: timed ? '账号限时限制中' : '账号已被管理员禁用',
    description: timed
      ? '当前无法使用做题、博客、签到等互动功能，可浏览公开内容与基本资料。'
      : '可浏览公开内容与基本资料，但无法使用做题、写博客、签到等互动功能。',
    countdown: timed ? adminBanCountdown.value || '计算中…' : '',
    reason: (auth.banReason ?? '').trim(),
    footer: timed ? '到期后将自动恢复，请遵守平台规范。' : '如需解禁请联系管理员。',
  }
})

const commentBanNotice = computed(() => {
  if (!auth.isCommentBanned || auth.isAccountDisabled) return null
  return {
    title: '评论功能已限制',
    description: '检测到不当评论内容，系统已暂停您的评论权限。',
    countdown: commentBanCountdown.value || '计算中…',
    reason: (auth.commentBanReason ?? auth.banReason ?? '').trim(),
    footer: '做题、签到等其他功能不受影响，到期后自动恢复。',
  }
})

const aiBanNotice = computed(() => {
  if (!auth.isAiAssistBanned || auth.isAccountDisabled) return null
  return {
    title: 'AI 学习助手已限制',
    description: '向智能助手发送了不当内容，学习助手入口已临时锁定。',
    countdown: aiAssistBanCountdown.value || '计算中…',
    reason: (auth.aiBanReason ?? auth.banReason ?? '').trim(),
    footer: '做题、评论等其他功能不受影响，到期后自动恢复。',
  }
})

async function refreshNotifBadge() {
  if (!auth.isLoggedIn) {
    notifUnreadTotal.value = 0
    return
  }
  try {
    const s = await fetchNotificationSummary()
    if (!s) return
    notifUnreadTotal.value = Math.min(999, (s.inAppUnread || 0) + (s.announcementUnread || 0))
  } catch {
    /* 静默 */
  }
}

function onNotifHubEvent() {
  void refreshNotifBadge()
}

function onSessionExpired() {
  Message.warning('登录已过期，请重新登录')
  authVisible.value = true
}

function onAccountRevoked() {
  Message.warning('账号已被管理员禁用，当前会话已结束，请重新登录')
  authVisible.value = true
}

function onLoginElsewhere() {
  Message.warning('您的账号已在其他设备登录，当前设备已退出，请重新登录')
  authVisible.value = true
}

function onAccountDisabled() {
  Message.warning('账号已被禁用，做题、博客等功能暂不可用')
}

function onAccountEnabled() {
  /* 详细文案由 useSessionStatusPush 提示 */
}

let sessionPollTimer: number | null = null

async function pollSessionStatus() {
  if (!auth.isLoggedIn) return
  await auth.syncSessionStatus()
}

watch(
  () => auth.isAccountDisabled,
  (disabled) => {
    if (!disabled) return
    if (route.matched.some((r) => r.meta.requiresActiveAccount)) {
      Message.warning('账号已被禁用，已退出当前功能页')
      routerInstance.replace('/')
    }
  },
)

watch(
  () => auth.isLoggedIn,
  (loggedIn) => {
    void refreshNotifBadge()
    if (loggedIn) {
      void pollSessionStatus()
    }
  },
)

watch(
  () => auth.isAdmin,
  (isAdmin, wasAdmin) => {
    if (wasAdmin && !isAdmin) {
      removeAdminRoutes(routerInstance)
      adminOpen.value = false
    }
  },
)

onMounted(() => {
  document.addEventListener('pointerdown', onGlobalPointerDown)
  document.addEventListener('keydown', onGlobalKeydown)
  window.addEventListener('resize', updateAdminDropdownPosition)
  window.addEventListener('scroll', updateAdminDropdownPosition, true)
  window.addEventListener('myoj:notifications-changed', onNotifHubEvent)
  window.addEventListener('myoj:session-expired', onSessionExpired)
  window.addEventListener('myoj:account-revoked', onAccountRevoked)
  window.addEventListener('myoj:login-elsewhere', onLoginElsewhere)
  window.addEventListener('myoj:account-disabled', onAccountDisabled)
  window.addEventListener('myoj:account-enabled', onAccountEnabled)
  void refreshNotifBadge()
  void pollSessionStatus()
  notifPollTimer = window.setInterval(() => void refreshNotifBadge(), 60_000)
  sessionPollTimer = window.setInterval(() => void pollSessionStatus(), 90_000)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateAdminDropdownPosition)
  window.removeEventListener('scroll', updateAdminDropdownPosition, true)
  if (userCloseTimer) {
    clearTimeout(userCloseTimer)
    userCloseTimer = null
  }
  if (notifPollTimer != null) {
    clearInterval(notifPollTimer)
    notifPollTimer = null
  }
  if (sessionPollTimer != null) {
    clearInterval(sessionPollTimer)
    sessionPollTimer = null
  }
  window.removeEventListener('myoj:notifications-changed', onNotifHubEvent)
  window.removeEventListener('myoj:session-expired', onSessionExpired)
  window.removeEventListener('myoj:account-revoked', onAccountRevoked)
  window.removeEventListener('myoj:login-elsewhere', onLoginElsewhere)
  window.removeEventListener('myoj:account-disabled', onAccountDisabled)
  window.removeEventListener('myoj:account-enabled', onAccountEnabled)
  document.removeEventListener('pointerdown', onGlobalPointerDown)
  document.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<template>
  <div class="layout">
    <ModerationBanNotice
      v-if="auth.isLoggedIn && accountBanNotice"
      variant="account"
      :title="accountBanNotice.title"
      :description="accountBanNotice.description"
      :countdown="accountBanNotice.countdown"
      :reason="accountBanNotice.reason"
      :footer="accountBanNotice.footer"
    />
    <ModerationBanNotice
      v-if="auth.isLoggedIn && commentBanNotice"
      variant="comment"
      :title="commentBanNotice.title"
      :description="commentBanNotice.description"
      :countdown="commentBanNotice.countdown"
      :reason="commentBanNotice.reason"
      :footer="commentBanNotice.footer"
    />
    <ModerationBanNotice
      v-if="auth.isLoggedIn && aiBanNotice"
      variant="ai"
      :title="aiBanNotice.title"
      :description="aiBanNotice.description"
      :countdown="aiBanNotice.countdown"
      :reason="aiBanNotice.reason"
      :footer="aiBanNotice.footer"
    />
    <header class="header">
      <div class="header-inner">
        <RouterLink to="/" class="brand">
          <BrandLogo :size="36" />
          <span class="brand-text">码跃OJ</span>
        </RouterLink>

        <nav class="nav">
          <template v-for="item in mainNav" :key="item.to">
            <RouterLink
              :to="item.to"
              class="nav-link nav-item-wrap"
              :class="{
                active: isNavActive(item.to, item.exact),
                accent: item.accent,
              }"
            >
              {{ item.label }}
            </RouterLink>
          </template>

          <div v-if="adminMenus.length" ref="adminMenuRef" class="nav-item-wrap admin-wrap">
            <button
              ref="adminButtonRef"
              type="button"
              class="nav-link admin-nav"
              :class="{ active: isAdminNavActive() || adminOpen }"
              :aria-expanded="adminOpen"
              aria-haspopup="menu"
              @click="toggleAdminMenu"
            >
              {{ l('工作台', 'Workspace') }}
              <span class="caret" aria-hidden="true">▾</span>
            </button>
            <Teleport to="body">
            <div v-if="adminOpen" ref="adminDropdownRef" class="dropdown admin-dropdown" role="menu" :style="adminDropdownStyle">
              <RouterLink
                v-for="item in adminMenus"
                :key="item.to"
                :to="item.to"
                class="dropdown-item admin-item"
                :class="{ active: route.path === item.to || route.path.startsWith(item.to + '/') }"
                @click="closeAdminMenu"
              >
                <span class="admin-item-icon" aria-hidden="true">{{ item.icon }}</span>
                <span class="admin-item-text">
                  <span class="admin-item-title">{{ item.title }}</span>
                  <span class="admin-item-desc">{{ item.desc }}</span>
                </span>
              </RouterLink>
            </div>
            </Teleport>
          </div>
        </nav>

        <div class="actions">
          <RouterLink to="/publish" class="btn-publish">
            <span class="icon-plane" aria-hidden="true">✈</span>
            {{ l('博客', 'Blog') }}
          </RouterLink>
          <button
            type="button"
            class="icon-btn locale-btn"
            :aria-label="l('切换到英语', 'Switch to Chinese')"
            :title="l('切换到英语', 'Switch to Chinese')"
            @click="toggleSiteLocale"
          >{{ siteLocale === 'zh-CN' ? 'EN' : '中' }}</button>
          <button
            type="button"
            class="icon-btn"
            :aria-label="themeButtonTitle"
            :title="themeButtonTitle"
            @click="toggleTheme"
          >
            {{ themeButtonIcon }}
          </button>
          <RouterLink to="/notifications" class="icon-btn notif-bell" :aria-label="l('通知', 'Notifications')">
            🔔
            <span v-if="notifUnreadTotal > 0" class="notif-badge" aria-hidden="true">{{
              notifUnreadTotal > 99 ? '99+' : notifUnreadTotal
            }}</span>
          </RouterLink>
          <template v-if="!auth.isLoggedIn">
            <button type="button" class="btn-auth" @click="authVisible = true">{{ l('登录/注册', 'Sign in') }}</button>
          </template>
          <template v-else>
            <div
              ref="userMenuRef"
              class="user-menu"
              @mouseenter="openUserMenu"
              @mouseleave="scheduleCloseUserMenu"
            >
              <button
                type="button"
                class="user-avatar-btn"
                :class="{ 'user-avatar-btn--open': userOpen }"
                :aria-expanded="userOpen"
                aria-haspopup="menu"
                :title="auth.displayName"
                @click="toggleUserMenuPin"
              >
                <span class="user-avatar-ring" aria-hidden="true">
                  <span class="user-avatar-inner">
                    <img v-if="userAvatarUrl" :src="userAvatarUrl" alt="" class="user-avatar-img" />
                    <span v-else class="user-avatar-letter">{{
                      (auth.displayName || 'U').slice(0, 1).toUpperCase()
                    }}</span>
                  </span>
                </span>
              </button>
              <Transition name="user-pop">
                <div
                  v-show="userOpen"
                  class="user-popover"
                  role="menu"
                  @mouseenter="openUserMenu"
                  @mouseleave="scheduleCloseUserMenu"
                >
                  <div class="user-popover-head">
                    <div class="user-popover-head-bg" aria-hidden="true" />
                    <div class="user-popover-head-inner">
                      <div class="user-popover-avatar" aria-hidden="true">
                        <img
                          v-if="userAvatarUrl"
                          :src="userAvatarUrl"
                          alt=""
                          class="user-popover-avatar-img"
                        />
                        <span v-else>{{
                          (auth.displayName || 'U').slice(0, 1).toUpperCase()
                        }}</span>
                      </div>
                      <div class="user-popover-meta">
                        <p class="user-popover-name">{{ auth.displayName }}</p>
                        <p class="user-popover-id">{{ roleLabel }}</p>
                        <p class="user-popover-stats" aria-live="polite">
                          <span class="user-popover-stat"
                            >{{ l('积分', 'Points') }} {{ checkInLoading ? '…' : checkInPoints }}</span
                          >
                          <span class="user-popover-stat-sep">·</span>
                          <span class="user-popover-stat"
                            >{{ l('累计签到', 'Check-ins') }} {{ checkInLoading ? '…' : checkInTotal }} {{ l('天', 'days') }}</span
                          >
                        </p>
                      </div>
                    </div>
                  </div>
                  <div class="user-popover-actions">
                    <button
                      type="button"
                      class="user-popover-btn user-popover-btn--checkin"
                      :disabled="checkInLoading || auth.isAccountDisabled"
                      @click="openCheckInModal"
                    >
                      <span class="user-popover-btn-ico" aria-hidden="true">📅</span>
                      {{ checkInCheckedToday ? l('学习打卡 · 今日已签', 'Checked in today') : l('学习打卡', 'Daily check-in') }}
                    </button>
                    <button
                      type="button"
                      class="user-popover-btn user-popover-btn--primary"
                      @click="goAccountSettings"
                    >
                      <span class="user-popover-btn-ico" aria-hidden="true">⚙</span>
                      {{ l('账户设置', 'Account settings') }}
                    </button>
                    <button
                      type="button"
                      class="user-popover-btn user-popover-btn--ghost"
                      @click="logout"
                    >
                      <span class="user-popover-btn-ico" aria-hidden="true">↪</span>
                      {{ l('退出登录', 'Sign out') }}
                    </button>
                  </div>
                </div>
              </Transition>
            </div>
          </template>
        </div>
      </div>
    </header>

    <AuthModal v-model:visible="authVisible" />
    <CheckInCalendarModal
      v-model:visible="checkInModalOpen"
      :account-disabled="auth.isAccountDisabled"
      @status-change="onCheckInStatusFromModal"
    />

    <main class="main">
      <RouterView v-slot="{ Component, route: currentRoute }">
        <Transition :name="pageTransition" mode="out-in">
          <component :is="Component" :key="currentRoute.path" />
        </Transition>
      </RouterView>
    </main>

    <AppFooter v-if="showFooter" />
  </div>
</template>

<style scoped>
.layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  background: var(--app-bg);
  color: var(--app-text);
  transition:
    background-color 0.2s ease,
    color 0.2s ease;
}

.header {
  position: sticky;
  top: 0;
  z-index: 100;
  background: color-mix(in srgb, var(--app-bg) 86%, transparent);
  border-bottom: 1px solid var(--app-border);
  box-shadow: 0 8px 26px rgba(70, 41, 14, 0.055);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  transition:
    background-color 0.2s ease,
    border-color 0.2s ease,
    box-shadow 0.2s ease;
}

html[data-theme='light'] .header {
  background: #fffdf9;
  backdrop-filter: none;
  -webkit-backdrop-filter: none;
  box-shadow: 0 1px 2px rgba(32, 25, 20, 0.06);
}

.header-inner {
  max-width: 1440px;
  margin: 0 auto;
  padding: 0 28px;
  height: var(--app-header-height);
  display: flex;
  align-items: center;
  gap: 32px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 46px;
  border-radius: 13px;
  padding: 5px 10px 5px 6px;
  text-decoration: none;
  color: inherit;
  flex-shrink: 0;
  transition:
    background-color 0.16s ease,
    transform 0.16s ease;
}

.brand:hover {
  background: color-mix(in srgb, var(--app-accent) 7%, transparent);
  transform: translateY(-1px);
}

.brand-text {
  font-size: 19px;
  font-weight: 850;
  letter-spacing: -0.025em;
}

.nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.nav-item-wrap {
  position: relative;
}

.nav-link {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 42px;
  padding: 0 13px;
  font-size: 13px;
  font-weight: 650;
  color: var(--app-text-muted);
  text-decoration: none;
  border-radius: 11px;
  transition:
    color 0.15s,
    background 0.15s,
    transform 0.15s ease;
}

.nav-link:hover {
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 7%, transparent);
  transform: translateY(-1px);
}

.nav-link.active {
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 10%, var(--app-surface-strong));
  font-weight: 800;
}

.nav-link.active::after {
  content: '';
  position: absolute;
  right: 13px;
  bottom: 4px;
  left: 13px;
  height: 2px;
  border-radius: 999px;
  background: var(--app-accent);
}

.nav-link.accent {
  color: #ea580c;
}

.caret {
  font-size: 10px;
  opacity: 0.7;
}

.dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 4px;
  min-width: 120px;
  padding: 6px;
  background: var(--app-surface);
  border: 1px solid var(--app-border);
  border-radius: 8px;
  box-shadow: var(--app-dropdown-shadow);
}

.admin-dropdown {
  position: fixed;
  z-index: 1000;
  top: auto;
  left: auto;
  right: auto;
  width: min(320px, calc(100vw - 24px));
  min-width: 0;
  max-height: calc(100dvh - var(--app-header-height) - 20px);
  margin: 0;
  padding: 9px;
  overflow-y: auto;
  overscroll-behavior: contain;
  border-radius: 16px;
  background: var(--color-bg-1);
  box-shadow: 0 18px 48px rgba(28, 35, 35, 0.18);
}

.dropdown-item {
  display: block;
  padding: 8px 12px;
  font-size: 14px;
  color: var(--app-text-muted);
  text-decoration: none;
  border-radius: 6px;
}

.admin-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  min-height: 58px;
  padding: 11px 12px;
  border-radius: 10px;
}

.admin-item-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: rgba(13, 148, 136, 0.12);
  font-size: 14px;
  flex-shrink: 0;
}

.admin-item-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.admin-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--app-text);
}

.admin-item-desc {
  margin-top: 2px;
  font-size: 12px;
  line-height: 1.4;
  color: var(--app-text-subtle);
}

.admin-item.active {
  background: rgba(13, 148, 136, 0.12);
}

.dropdown-item:hover {
  background: var(--app-hover);
  color: #0d9488;
}

.actions {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-shrink: 0;
}

.btn-publish {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 40px;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 800;
  color: #fff;
  background: var(--app-accent);
  border-radius: 11px;
  text-decoration: none;
  box-shadow: 0 7px 18px color-mix(in srgb, var(--app-accent) 20%, transparent);
  transition:
    filter 0.15s,
    transform 0.15s ease,
    box-shadow 0.15s ease;
}

.btn-publish:hover {
  filter: brightness(1.05);
  transform: translateY(-1px);
  box-shadow: 0 10px 22px color-mix(in srgb, var(--app-accent) 25%, transparent);
}

.icon-plane {
  font-size: 12px;
  transform: rotate(-45deg);
  display: inline-block;
}

.icon-btn {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border: 1px solid transparent;
  background: transparent;
  border-radius: 11px;
  font-size: 16px;
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  transition:
    background-color 0.15s ease,
    border-color 0.15s ease,
    transform 0.15s ease;
}

.icon-btn:hover:not(:disabled) {
  border-color: var(--app-border);
  background: var(--app-surface-strong);
  transform: translateY(-1px);
}

.icon-btn:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.locale-btn {
  width: 42px;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.02em;
  border-color: var(--app-border);
}

.notif-bell {
  position: relative;
}

.notif-badge {
  position: absolute;
  top: 2px;
  right: 2px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 800;
  line-height: 16px;
  text-align: center;
  color: #fff;
  background: #ef4444;
  box-shadow: 0 0 0 2px var(--color-bg-1);
}

.btn-auth {
  min-height: 40px;
  border: 0;
  padding: 0 16px;
  font-size: 13px;
  font-weight: 800;
  color: #fff;
  background: var(--app-accent);
  border-radius: 11px;
  text-decoration: none;
  margin-left: 4px;
}

.btn-auth:hover {
  filter: brightness(1.05);
}

.user-menu {
  position: relative;
}

.user-menu::after {
  content: '';
  position: absolute;
  top: 100%;
  right: 0;
  width: 100%;
  height: 12px;
}

/* 右上角头像：青色渐变环 + 外发光（与深色顶栏/账户页视觉一致） */
.user-avatar-btn {
  position: relative;
  padding: 0;
  border: none;
  background: transparent;
  border-radius: 999px;
  cursor: pointer;
  line-height: 0;
  filter: drop-shadow(0 0 6px rgba(0, 194, 160, 0.45)) drop-shadow(0 0 16px rgba(0, 194, 160, 0.28));
  transition:
    transform 0.2s cubic-bezier(0.34, 1.2, 0.64, 1),
    filter 0.22s ease;
}

.user-avatar-ring {
  display: block;
  width: 40px;
  height: 40px;
  border-radius: 999px;
  padding: 2px;
  background: linear-gradient(145deg, #5eead4 0%, #00c2a0 42%, #00a896 100%);
  box-shadow:
    0 0 0 1px rgba(0, 194, 160, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.22);
}

.user-avatar-inner {
  width: 100%;
  height: 100%;
  border-radius: 999px;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(155deg, #0f3d3a 0%, #0f766e 48%, #14b8a6 100%);
  color: #fff;
}

.user-avatar-letter {
  font-size: 15px;
  font-weight: 700;
  letter-spacing: -0.02em;
  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.35);
}

.user-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-avatar-btn:hover {
  transform: translateY(-1px) scale(1.04);
  filter: drop-shadow(0 0 10px rgba(0, 194, 160, 0.55)) drop-shadow(0 0 24px rgba(0, 194, 160, 0.4));
}

.user-avatar-btn:focus-visible {
  outline: 2px solid #14b8a6;
  outline-offset: 3px;
}

.user-avatar-btn--open .user-avatar-ring {
  background: linear-gradient(145deg, #99f6e4 0%, #5eead4 35%, #00c2a0 100%);
  box-shadow:
    0 0 0 2px rgba(0, 194, 160, 0.35),
    0 0 20px rgba(0, 194, 160, 0.55),
    inset 0 1px 0 rgba(255, 255, 255, 0.28);
}

.user-avatar-btn--open {
  filter: drop-shadow(0 0 8px rgba(0, 194, 160, 0.55)) drop-shadow(0 0 28px rgba(0, 194, 160, 0.42));
}

.user-pop-enter-active,
.user-pop-leave-active {
  transition:
    opacity 0.2s cubic-bezier(0.34, 1.2, 0.64, 1),
    transform 0.22s cubic-bezier(0.34, 1.2, 0.64, 1);
}

.user-pop-enter-from,
.user-pop-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.98);
}

.user-popover {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  width: 268px;
  border: 1px solid rgba(13, 148, 136, 0.14);
  border-radius: 14px;
  background: var(--app-surface);
  box-shadow:
    0 20px 40px rgba(15, 23, 42, 0.12),
    0 8px 16px rgba(15, 23, 42, 0.06),
    0 0 0 1px rgba(255, 255, 255, 0.06) inset;
  overflow: hidden;
  z-index: 120;
}

.user-popover-head {
  position: relative;
  padding: 0;
  border-bottom: 1px solid var(--app-border);
}

.user-popover-head-bg {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    135deg,
    rgba(20, 184, 166, 0.12) 0%,
    rgba(15, 118, 110, 0.06) 50%,
    transparent 100%
  );
  pointer-events: none;
}

.user-popover-head-inner {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 16px 14px;
}

.user-popover-avatar {
  width: 48px;
  height: 48px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(145deg, #0f766e 0%, #14b8a6 60%, #2dd4bf 120%);
  box-shadow:
    0 4px 12px rgba(13, 148, 136, 0.35),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  flex-shrink: 0;
  overflow: hidden;
}

.user-popover-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.user-popover-meta {
  min-width: 0;
}

.user-popover-name {
  margin: 0;
  font-size: 15px;
  font-weight: 700;
  letter-spacing: 0.02em;
  color: var(--app-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-popover-id {
  margin: 5px 0 0;
  font-size: 12px;
  color: var(--app-text-subtle);
  line-height: 1.35;
}

.user-popover-stats {
  margin: 8px 0 0;
  font-size: 11px;
  color: var(--app-text-muted);
  line-height: 1.45;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.user-popover-stat-sep {
  opacity: 0.55;
}

.user-popover-actions {
  padding: 12px 14px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.5) 0%, transparent 100%);
}

.user-popover-btn {
  width: 100%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 10px;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  transition:
    transform 0.15s ease,
    box-shadow 0.2s ease,
    border-color 0.2s ease,
    background 0.2s ease,
    color 0.2s ease;
}

.user-popover-btn:focus-visible {
  outline: 2px solid #14b8a6;
  outline-offset: 2px;
}

.user-popover-btn-ico {
  font-size: 14px;
  opacity: 0.9;
}

.user-popover-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  transform: none !important;
  box-shadow: none !important;
}

.user-popover-btn--checkin {
  border: 1px solid rgba(52, 211, 153, 0.4);
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.98) 0%, rgba(209, 250, 229, 0.7) 100%);
  color: #047857;
  box-shadow: 0 2px 10px rgba(16, 185, 129, 0.14);
}

.user-popover-btn--checkin:hover:not(:disabled) {
  border-color: #34d399;
  color: #065f46;
  background: linear-gradient(180deg, #ecfdf5 0%, #a7f3d0 100%);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.24);
  transform: translateY(-1px);
}

.user-popover-btn--checkin:active:not(:disabled) {
  transform: translateY(0);
}

.user-popover-btn--primary {
  border: 1px solid rgba(13, 148, 136, 0.45);
  background: linear-gradient(180deg, rgba(236, 253, 245, 0.95) 0%, rgba(240, 253, 250, 0.75) 100%);
  color: #0f766e;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.12);
}

.user-popover-btn--primary:hover {
  border-color: #14b8a6;
  color: #0d9488;
  background: linear-gradient(180deg, #ecfdf5 0%, #d1fae5 100%);
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.2);
  transform: translateY(-1px);
}

.user-popover-btn--primary:active {
  transform: translateY(0);
}

.user-popover-btn--ghost {
  border: 1px solid var(--app-border);
  background: var(--app-surface);
  color: var(--app-text-muted);
}

.user-popover-btn--ghost:hover {
  border-color: #cbd5e1;
  color: var(--app-text);
  background: var(--app-hover);
}

.user-popover-btn--ghost:active {
  transform: scale(0.99);
}

.admin-nav {
  border: none;
  cursor: pointer;
  background: transparent;
}

.admin-nav .caret {
  transition: transform 0.16s ease;
}

.admin-nav[aria-expanded='true'] .caret {
  transform: rotate(180deg);
}

.main {
  flex: 1;
}

.page-fade-enter-active,
.page-fade-leave-active,
.page-detail-enter-active,
.page-detail-leave-active {
  transition: opacity 150ms ease, transform 150ms ease;
}

.page-fade-enter-from,
.page-fade-leave-to {
  opacity: 0;
}

.page-detail-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-detail-leave-to {
  opacity: 0;
}

@media (prefers-reduced-motion: reduce) {
  .page-fade-enter-active,
  .page-fade-leave-active,
  .page-detail-enter-active,
  .page-detail-leave-active {
    transition: none;
  }
}

:global(html[data-theme='dark']) .nav-link:hover {
  color: var(--app-accent);
  background: color-mix(in srgb, var(--app-accent) 12%, transparent);
}
:global(html[data-theme='dark']) .dropdown-item:hover {
  background: #334155;
  color: #2dd4bf;
}

:global(html[data-theme='dark']) .user-popover {
  border-color: rgba(51, 65, 85, 0.9);
  box-shadow:
    0 24px 48px rgba(0, 0, 0, 0.45),
    0 0 0 1px rgba(255, 255, 255, 0.04) inset;
}

:global(html[data-theme='dark']) .user-popover-actions {
  background: linear-gradient(180deg, rgba(30, 41, 59, 0.6) 0%, transparent 100%);
}

:global(html[data-theme='dark']) .user-popover-btn--primary {
  border-color: rgba(45, 212, 191, 0.35);
  background: linear-gradient(180deg, rgba(15, 118, 110, 0.35) 0%, rgba(15, 118, 110, 0.15) 100%);
  color: #5eead4;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.25);
}

:global(html[data-theme='dark']) .user-popover-btn--primary:hover {
  border-color: #2dd4bf;
  color: #99f6e4;
  background: linear-gradient(180deg, rgba(15, 118, 110, 0.5) 0%, rgba(15, 118, 110, 0.25) 100%);
}

:global(html[data-theme='dark']) .user-popover-btn--checkin {
  border-color: rgba(251, 191, 36, 0.4);
  background: linear-gradient(180deg, rgba(120, 53, 15, 0.45) 0%, rgba(69, 26, 3, 0.35) 100%);
  color: #fde68a;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

:global(html[data-theme='dark']) .user-popover-btn--checkin:hover:not(:disabled) {
  border-color: #fbbf24;
  color: #fffbeb;
  background: linear-gradient(180deg, rgba(146, 64, 14, 0.55) 0%, rgba(120, 53, 15, 0.4) 100%);
}

.brand:focus-visible,
.nav-link:focus-visible,
.btn-publish:focus-visible,
.icon-btn:focus-visible,
.btn-auth:focus-visible {
  outline: 2px solid var(--app-accent);
  outline-offset: 2px;
}

@media (max-width: 1180px) {
  .header-inner {
    gap: 16px;
    padding: 0 18px;
  }

  .nav {
    gap: 2px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .nav::-webkit-scrollbar {
    display: none;
  }

  .nav-link {
    flex: 0 0 auto;
    padding: 0 10px;
  }
}

@media (max-width: 860px) {
  .header-inner {
    height: var(--app-header-height);
    padding: 0 12px;
  }

  .brand {
    padding-right: 6px;
  }

  .brand-text {
    display: none;
  }

  .btn-publish {
    width: 40px;
    padding: 0;
    justify-content: center;
    font-size: 0;
  }

  .btn-publish .icon-plane {
    font-size: 13px;
  }
}

@media (max-width: 480px) {
  .header-inner { gap: 6px; padding: 0 8px; }
  .brand { padding-right: 0; }
  .nav { min-width: 52px; }
  .nav-link { padding: 0 7px; }
  .actions { gap: 2px; }
  .btn-publish, .icon-btn { width: 32px; height: 36px; min-height: 36px; }
  .locale-btn { width: 32px; font-size: 11px; }
  .btn-auth { min-height: 36px; padding: 0 9px; margin-left: 0; font-size: 12px; }
}
</style>
