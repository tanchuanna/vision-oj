import { OpenAPI } from '@generated'

type ApiResult<T> = { code?: number; message?: string; data?: T }
export type PageData<T> = { records: T[]; total: number; current: number; size: number }

export type SolutionEligibility = {
  accepted: boolean
  canWrite: boolean
  canViewFull: boolean
  admin: boolean
}
export type SolutionListItem = {
  id: string
  questionId: string
  userId: string
  authorName?: string
  authorAvatar?: string
  title: string
  summary: string
  hintContent: string
  reviewStatus: number
  official: boolean
  viewCount: number
  likeCount: number
  commentCount: number
  publishedAt?: string
  createTime?: string
}
export type SolutionDetail = SolutionListItem & {
  accessLevel: 'HINT' | 'FULL'
  content?: string | null
  reviewReason?: string | null
  liked?: boolean | null
  mine?: boolean
  updateTime?: string
}
export type SolutionSavePayload = {
  id?: string
  questionId: string
  title: string
  summary: string
  hintContent: string
  content: string
}
export type SolutionComment = {
  id: string
  solutionId: string
  userId: string
  parentId?: string
  authorName?: string
  authorAvatar?: string
  content: string
  createTime?: string
  mine?: boolean
}

async function authHeaders(json = false): Promise<Record<string, string>> {
  const resolver = OpenAPI.TOKEN
  const token = typeof resolver === 'function' ? await resolver({} as never) : resolver
  const headers: Record<string, string> = {}
  if (json) headers['Content-Type'] = 'application/json'
  if (token?.trim()) headers.Authorization = `Bearer ${token.trim()}`
  return headers
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${OpenAPI.BASE.replace(/\/+$/, '')}${path}`, {
    ...init,
    headers: { ...(await authHeaders(!!init?.body)), ...(init?.headers ?? {}) },
    credentials: OpenAPI.WITH_CREDENTIALS ? 'include' : 'same-origin',
  })
  const body = (await response.json().catch(() => ({}))) as ApiResult<T>
  if (!response.ok || body.code !== 200)
    throw new Error(body.message || `请求失败（HTTP ${response.status}）`)
  return body.data as T
}

const post = <T>(path: string, body: unknown) =>
  request<T>(path, { method: 'POST', body: JSON.stringify(body) })

export const questionSolutionApi = {
  eligibility: (questionId: string) =>
    request<SolutionEligibility>(
      `/question-solution/public/eligibility?questionId=${encodeURIComponent(questionId)}`,
    ),
  page: (questionId: string, keyword = '', current = 1) =>
    post<PageData<SolutionListItem>>('/question-solution/public/page', {
      questionId,
      keyword,
      current,
      pageSize: 20,
    }),
  detail: (id: string) =>
    request<SolutionDetail>(`/question-solution/public/get?id=${encodeURIComponent(id)}`),
  mine: (questionId: string) =>
    request<SolutionDetail | null>(
      `/question-solution/mine?questionId=${encodeURIComponent(questionId)}`,
    ),
  save: (payload: SolutionSavePayload) => post<string>('/question-solution/save', payload),
  submitReview: (id: string) => post<boolean>('/question-solution/submit-review', { id }),
  delete: (id: string) => post<boolean>('/question-solution/delete', { id }),
  toggleLike: (id: string) =>
    post<{ liked: boolean; likeCount: number }>('/question-solution/like/toggle', { id }),
  comments: (solutionId: string, current = 1) =>
    request<PageData<SolutionComment>>(
      `/question-solution/comment/page?solutionId=${encodeURIComponent(solutionId)}&current=${current}&pageSize=30`,
    ),
  addComment: (solutionId: string, content: string, parentId?: string) =>
    post<string>('/question-solution/comment/add', { solutionId, content, parentId }),
  deleteComment: (id: string) => post<boolean>('/question-solution/comment/delete', { id }),
  adminPage: (questionId: string, reviewStatus = 1) =>
    post<PageData<SolutionListItem>>('/question-solution/admin/page', {
      questionId,
      reviewStatus,
      current: 1,
      pageSize: 50,
    }),
  adminDetail: (id: string) =>
    request<SolutionDetail>(`/question-solution/admin/get?id=${encodeURIComponent(id)}`),
  review: (id: string, approved: boolean, reason?: string) =>
    post<boolean>('/question-solution/admin/review', { id, approved, reason }),
  official: (id: string, official: boolean) =>
    post<boolean>('/question-solution/admin/official', { id, official }),
}
