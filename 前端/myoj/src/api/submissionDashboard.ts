import axios from 'axios'
import { OpenAPI } from '@generated'
import { API_BASE_URL } from '@/config/api'

export type SubmissionScope = 'SELF' | 'MANAGED'

export type SubmissionFilters = {
  scope: SubmissionScope
  userId?: string
  questionKeyword?: string
  userKeyword?: string
  verdict?: string
  language?: string
  startTime?: string
  endTime?: string
  sort?: 'ASC' | 'DESC'
  current?: number
  pageSize?: number
}

export type SubmissionListItem = {
  submissionId: string
  globalSubmitNo?: number
  questionId: string
  questionTitle: string
  userId?: string
  username?: string
  nickname?: string
  avatar?: string
  language?: string
  status?: number
  verdict?: string
  runtimeMs?: number
  memoryKb?: number
  codeLength?: number
  contestId?: string
  createTime?: string
}

export type SubmissionOverview = {
  totalSubmissions: number
  acceptedSubmissions: number
  solvedProblems: number
  activeUsers: number
  pendingSubmissions: number
  acceptedRate: number
  availableScopes: SubmissionScope[]
  trend: Array<{ date: string; total: number; accepted: number }>
  verdictDistribution: Array<{ name: string; value: number }>
  languageDistribution: Array<{ name: string; value: number }>
}

export type SubmissionDetail = SubmissionListItem & {
  code?: string
  errorDetail?: string
  ownSubmission?: boolean
}

export type SubmissionDiagnosis = {
  verdict?: string
  headline?: string
  rootCause?: string
  evidence?: string[]
  hints?: string[]
  nextStep?: string
  snippet?: string
}

type ApiResult<T> = { code?: number; message?: string; data?: T }
type ApiPage<T> = { records?: T[]; total?: number; current?: number; size?: number }

async function getAuthHeaders() {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

function cleanParams(filters: SubmissionFilters) {
  return Object.fromEntries(
    Object.entries(filters).filter(
      ([, value]) => value !== undefined && value !== '' && value !== null,
    ),
  )
}

export async function getSubmissionPage(filters: SubmissionFilters) {
  const { data } = await axios.get<ApiResult<ApiPage<SubmissionListItem>>>(
    `${API_BASE_URL}/question_submit/page`,
    { params: cleanParams(filters), headers: await getAuthHeaders() },
  )
  if (data.code !== 200 || !data.data) throw new Error(data.message || '提交记录加载失败')
  return data.data
}

export async function getSubmissionOverview(filters: SubmissionFilters, days: 7 | 30 | 90) {
  const { data } = await axios.get<ApiResult<SubmissionOverview>>(
    `${API_BASE_URL}/question_submit/overview`,
    { params: { ...cleanParams(filters), days }, headers: await getAuthHeaders() },
  )
  if (data.code !== 200 || !data.data) throw new Error(data.message || '统计数据加载失败')
  return data.data
}

export async function getSubmissionDetail(submissionId: string) {
  const { data } = await axios.get<ApiResult<SubmissionDetail>>(
    `${API_BASE_URL}/question_submit/${encodeURIComponent(submissionId)}/detail`,
    { headers: await getAuthHeaders() },
  )
  if (data.code !== 200 || !data.data) throw new Error(data.message || '提交详情加载失败')
  return data.data
}

export async function diagnoseSubmission(
  submissionId: string,
  options?: { providerCode?: string; modelId?: string },
) {
  const { data } = await axios.post<ApiResult<SubmissionDiagnosis>>(
    `${API_BASE_URL}/question_submit/${encodeURIComponent(submissionId)}/ai-diagnose`,
    { providerCode: options?.providerCode, modelId: options?.modelId },
    { headers: await getAuthHeaders() },
  )
  if (data.code !== 200 || !data.data) throw new Error(data.message || 'AI 诊断失败，请稍后重试')
  return data.data
}
