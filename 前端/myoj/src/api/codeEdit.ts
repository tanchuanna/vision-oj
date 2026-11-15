import { OpenAPI } from '@generated'

export type CodeEdit = {
  startOffset: number
  endOffset: number
  oldText: string
  newText: string
  reason: string
}
export type CodeEditProposal = { summary: string; edits: CodeEdit[] }
export type CodeEditRequest = {
  questionId?: string
  instruction: string
  code: string
  language: string
  questionTitle?: string
  questionContent?: string
  sampleCases?: string
  runResult?: string
  referenceAnswer?: string
  providerCode?: string
  modelId?: string
}

export async function requestCodeEdit(
  body: CodeEditRequest,
  signal: AbortSignal,
): Promise<CodeEditProposal> {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  const response = await fetch(`${OpenAPI.BASE.replace(/\/+$/, '')}/bailian_assist/code-edit`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token?.trim() ? { Authorization: `Bearer ${token.trim()}` } : {}),
    },
    body: JSON.stringify(body),
    signal,
    credentials: OpenAPI.WITH_CREDENTIALS ? 'include' : 'same-origin',
  })
  const result = (await response.json().catch(() => null)) as {
    code?: number
    message?: string
    data?: CodeEditProposal
  } | null
  if (!response.ok || result?.code !== 200 || !result.data)
    throw new Error(result?.message || `模型请求失败（HTTP ${response.status}）`)
  return result.data
}
