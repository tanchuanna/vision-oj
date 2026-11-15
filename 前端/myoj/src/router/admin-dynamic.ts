import type { Router, RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { adminRouteNames, adminRouteRecords } from '@/router/admin-routes'

let adminRoutesMounted = false

export function isAdminRoutesMounted() {
  return adminRoutesMounted
}

/** 判断当前用户是否可访问某条 admin 路由 */
function canAccessRoute(auth: ReturnType<typeof useAuthStore>, record: RouteRecordRaw): boolean {
  const meta = record.meta as Record<string, unknown> | undefined
  if (meta?.requiresAdmin) return auth.isAdmin
  const required =
    typeof meta?.requiresPermission === 'string' ? (meta.requiresPermission as string) : ''
  if (required) return auth.can(required)
  const anyRequired = Array.isArray(meta?.requiresAnyPermission)
    ? (meta.requiresAnyPermission as string[])
    : []
  if (anyRequired.length) return anyRequired.some((p) => auth.can(p))
  // 无特殊限制的 admin 路由默认仅管理员可见
  return auth.isAdmin
}

/** 将当前用户有权访问的 /admin/* 子路由挂到 layout 下（幂等）。
 *  管理员挂载全部；非管理员仅挂载其拥有对应权限的路由。 */
export function registerAdminRoutes(r: Router) {
  const auth = useAuthStore()
  if (adminRoutesMounted) return
  const accessible = adminRouteRecords.filter((record) => canAccessRoute(auth, record))
  for (const record of accessible) {
    r.addRoute('layout', record)
  }
  adminRoutesMounted = true
}

/** 退出时移除动态路由；若当前在 /admin 下则回首页 */
export function removeAdminRoutes(r: Router) {
  if (!adminRoutesMounted) return
  const path = r.currentRoute.value.path
  for (const name of adminRouteNames) {
    if (r.hasRoute(name)) {
      r.removeRoute(name)
    }
  }
  adminRoutesMounted = false
  if (path.startsWith('/admin')) {
    void r.replace('/')
  }
}
