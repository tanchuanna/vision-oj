import { OpenAPI } from '@generated'
import { isResultSuccess } from '@/api/result'

export type ImageModel = {
  providerCode: string
  id: string
  name: string
  description: string
  apiKind: string
}

export type ImageProvider = {
  code: string
  name: string
  shortName: string
  description: string
  models: ImageModel[]
  configured?: boolean
}

export type SavedImageConfig = {
  providerCode: string
  modelId: string
  keyMask: string
  credentialSource: 'IMAGE' | 'TEXT_SHARED'
}

export type TestImageResult = { imageUrl: string; modelId: string; source: string }

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token =
    typeof OpenAPI.TOKEN === 'function' ? await OpenAPI.TOKEN({} as never) : OpenAPI.TOKEN
  const response = await fetch(`${OpenAPI.BASE.replace(/\/+$/, '')}/ai/image-models${path}`, {
    ...init,
    cache: 'no-store',
    credentials: OpenAPI.WITH_CREDENTIALS ? 'include' : 'same-origin',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...init.headers,
    },
  })
  const body = (await response.json().catch(() => null)) as {
    code?: number
    message?: string
    data?: T
  } | null
  if (!response.ok || !body || !isResultSuccess(body.code)) {
    throw new Error(body?.message || `请求失败（HTTP ${response.status}）`)
  }
  return body.data as T
}

export const aiImageModelsApi = {
  catalog: () => request<ImageProvider[]>('/catalog'),
  mine: () => request<SavedImageConfig[]>('/mine'),
  save: (provider: string, modelId: string, apiKey: string) =>
    request<void>(`/mine/${encodeURIComponent(provider)}`, {
      method: 'PUT',
      body: JSON.stringify({ modelId, apiKey }),
    }),
  test: (provider: string, modelId: string) =>
    request<TestImageResult>(`/mine/${encodeURIComponent(provider)}/test`, {
      method: 'POST',
      body: JSON.stringify({ modelId }),
    }),
  remove: (provider: string) =>
    request<void>(`/mine/${encodeURIComponent(provider)}`, { method: 'DELETE' }),
}
