import { OpenAPI } from '@generated'

type ApiResult<T> = { code?: number; message?: string; data?: T }

export type QuestionRankingItem = {
  rankNo: number
  userName: string
  userAvatar?: string
  runtimeMs?: number
  memoryKb?: number
  language?: string
  codeLength?: number
  createTime?: string
}

export type QuestionStatistics = {
  totalSubmissions: number
  acceptedSubmissions: number
  failedSubmissions: number
  acceptedUsers: number
  passRate: number
  beatRuntimePercent?: number | null
  beatMemoryPercent?: number | null
  acceptedByCurrentUser: boolean
  fastestRankings: QuestionRankingItem[]
  memoryRankings: QuestionRankingItem[]
}

async function resolveToken(): Promise<string> {
  const resolver = OpenAPI.TOKEN
  const token = typeof resolver === 'function' ? await resolver({} as never) : resolver
  return token?.trim() || ''
}

export async function getQuestionStatistics(questionId: string): Promise<QuestionStatistics> {
  const token = await resolveToken()
  const request = async (path: string, bearer = '') => {
    const response = await fetch(
      `${OpenAPI.BASE.replace(/\/+$/, '')}${path}?questionId=${encodeURIComponent(questionId)}`,
      {
        headers: bearer ? { Authorization: `Bearer ${bearer}` } : {},
        credentials: OpenAPI.WITH_CREDENTIALS ? 'include' : 'same-origin',
      },
    )
    const body = (await response.json().catch(() => ({}))) as ApiResult<QuestionStatistics>
    return { response, body }
  }

  let result = await request(
    token ? '/question_submit/statistics/my' : '/question_submit/public/statistics',
    token,
  )
  // 登录状态过期时仍展示公开统计，不让整块页面因为个人数据失败而空白。
  if (token && (result.response.status === 401 || result.body.code === 401)) {
    result = await request('/question_submit/public/statistics')
  }
  if (!result.response.ok || result.body.code !== 200 || !result.body.data) {
    throw new Error(result.body.message || `统计数据加载失败（HTTP ${result.response.status}）`)
  }
  return result.body.data
}
