import { OpenAPI } from '@generated'
import { isResultSuccess } from '@/api/result'

export type PracticeItem = {
  questionId: string
  title: string
  questionType?: string
  difficulty?: string
  focusTag?: string
  reason?: string
  sortOrder?: number
  solved?: boolean
}

export type PracticeAssignment = {
  id: string
  teacherId: string
  studentId: string
  studentName?: string
  teacherName?: string
  title: string
  remark?: string
  dueTime?: string
  createTime?: string
  itemCount?: number
  solvedCount?: number
  progress?: number
  status?: number
  items?: PracticeItem[]
}

export type PracticeAssignResult = {
  title: string
  created: number
  skipped: number
  totalQuestions: number
  skippedStudentNames?: string[]
}

export type PracticeAssignPayload = {
  studentIds: string[]
  questionCount?: number
  title?: string
  remark?: string
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token =
    typeof OpenAPI.TOKEN === 'function' ? await OpenAPI.TOKEN({} as never) : OpenAPI.TOKEN
  const res = await fetch(`${OpenAPI.BASE.replace(/\/+$/, '')}/teaching/practice${path}`, {
    ...init,
    cache: 'no-store',
    credentials: OpenAPI.WITH_CREDENTIALS ? 'include' : 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  const body = (await res.json().catch(() => null)) as {
    code?: number
    message?: string
    data?: T
  } | null
  if (!res.ok || !body || !isResultSuccess(body.code)) {
    throw new Error(body?.message || `请求失败（HTTP ${res.status}）`)
  }
  return body.data as T
}

export const practiceAssignmentsApi = {
  assign: (payload: PracticeAssignPayload) =>
    request<PracticeAssignResult>('/assign', {
      method: 'POST',
      body: JSON.stringify(payload),
    }),
  listForTeacher: (studentId?: string) =>
    request<PracticeAssignment[]>(
      `/assignments${studentId ? `?studentId=${encodeURIComponent(studentId)}` : ''}`,
    ),
  mine: () => request<PracticeAssignment[]>('/my'),
  remove: (id: string) =>
    request<void>(`/assignments/${encodeURIComponent(id)}`, { method: 'DELETE' }),
}
