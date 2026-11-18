import axios from 'axios'
import { OpenAPI } from '@generated'
import { isResultSuccess } from '@/api/result'

export type RelationVO = {
  id: string
  peerId: string
  peerUsername?: string
  peerNickname?: string
  peerAvatar?: string
  /** 1=已建立, 2=待审批 */
  status: number
  initiatedBy?: string
  mine?: boolean
  /** 等待谁审批: teacher / student */
  pendingRole?: string
  createTime?: string
  assignedAt?: string
}

export type PublicProfileVO = {
  id: string
  nickname?: string
  username?: string
  avatar?: string
  introduction?: string
  website?: string[]
  locationCodes?: { province?: string; city?: string; district?: string }
  sex?: number
  country?: string
  birthday?: string
  status?: number
  userRole?: string
  points?: number
  checkInCount?: number
  createTime?: string
  solvedCount?: number
  submitCount?: number
  teacherNames?: string[]
  studentCount?: number
}

async function getAuthHeaders() {
  const tokenResolver = OpenAPI.TOKEN
  const token =
    typeof tokenResolver === 'function' ? await tokenResolver({} as never) : tokenResolver
  return token ? { Authorization: `Bearer ${token}` } : undefined
}

function base() {
  return OpenAPI.BASE.replace(/\/+$/, '')
}

export async function fetchPublicProfile(userId: string): Promise<PublicProfileVO | null> {
  try {
    const { data } = await axios.get(`${base()}/user/public/${userId}`, {
      headers: await getAuthHeaders(),
    })
    if (!isResultSuccess(data?.code) || !data?.data) return null
    return data.data
  } catch {
    return null
  }
}

export async function fetchRelationWith(userId: string): Promise<RelationVO | null> {
  try {
    const { data } = await axios.get(`${base()}/teaching/relations/with/${userId}`, {
      headers: await getAuthHeaders(),
    })
    if (!isResultSuccess(data?.code)) return null
    return data?.data ?? null
  } catch {
    return null
  }
}

export async function fetchPendingRelations(): Promise<RelationVO[]> {
  try {
    const { data } = await axios.get(`${base()}/teaching/relations/pending`, {
      headers: await getAuthHeaders(),
    })
    if (!isResultSuccess(data?.code)) return []
    return Array.isArray(data?.data) ? data.data : []
  } catch {
    return []
  }
}

async function postRelation(path: string, body?: Record<string, unknown>): Promise<boolean> {
  try {
    const { data } = body
      ? await axios.post(`${base()}${path}`, body, { headers: await getAuthHeaders() })
      : await axios.post(`${base()}${path}`, {}, { headers: await getAuthHeaders() })
    return isResultSuccess(data?.code)
  } catch {
    return false
  }
}

export const applyToTeacher = (teacherId: string) =>
  postRelation('/teaching/relations/apply', { teacherId })

export const inviteStudent = (studentId: string) =>
  postRelation('/teaching/relations/invite', { studentId })

export const approveRelation = (relationId: string) =>
  postRelation(`/teaching/relations/${relationId}/approve`)

export const rejectRelation = (relationId: string) =>
  postRelation(`/teaching/relations/${relationId}/reject`)

export const cancelRelation = (relationId: string) =>
  postRelation(`/teaching/relations/${relationId}/cancel`)

export const leaveRelation = (relationId: string) =>
  postRelation(`/teaching/relations/${relationId}/leave`)
