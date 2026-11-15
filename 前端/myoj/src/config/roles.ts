export type AppRole = 'user' | 'teacher' | 'admin' | 'unknown'

export const PERMISSIONS = {
  QUESTION_AUTHOR: 'QUESTION_AUTHOR',
  STUDENT_VIEW_ASSIGNED: 'STUDENT_VIEW_ASSIGNED',
  STUDENT_VIEW_ALL: 'STUDENT_VIEW_ALL',
  USER_ROLE_MANAGE: 'USER_ROLE_MANAGE',
  CONTEST_MANAGE: 'CONTEST_MANAGE',
  ANNOUNCEMENT_MANAGE: 'ANNOUNCEMENT_MANAGE',
} as const

export function normalizeRole(raw?: string | null): AppRole {
  const role = raw?.trim().toLowerCase()
  if (role === 'user' || role === 'student' || raw === '学生' || raw === '普通用户') return 'user'
  if (role === 'teacher' || raw === '教师' || raw === '老师') return 'teacher'
  if (role === 'admin' || raw === '管理员') return 'admin'
  return 'unknown'
}

export const isAdminRole = (role?: string | null) => normalizeRole(role) === 'admin'
export const isTeacherRole = (role?: string | null) => normalizeRole(role) === 'teacher'
export const isStudentRole = (role?: string | null) => normalizeRole(role) === 'user'
