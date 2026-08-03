import { apiClient } from './client'
import type { NoteCreateRequest, NoteDetail, NoteSummary, NoteUpdateRequest, Page } from './types'

export interface NoteSearchParams {
  roleId?: number | null
  contextId?: number | null
  keyword?: string
  page?: number
  size?: number
}

function buildQuery(params: NoteSearchParams): string {
  const search = new URLSearchParams()
  if (params.roleId) search.set('roleId', String(params.roleId))
  if (params.contextId) search.set('contextId', String(params.contextId))
  if (params.keyword) search.set('keyword', params.keyword)
  search.set('page', String(params.page ?? 0))
  search.set('size', String(params.size ?? 20))
  return search.toString()
}

export const notesApi = {
  search: (params: NoteSearchParams) => apiClient.get<Page<NoteSummary>>(`/api/notes?${buildQuery(params)}`),
  get: (id: number) => apiClient.get<NoteDetail>(`/api/notes/${id}`),
  create: (payload: NoteCreateRequest) => apiClient.post<NoteDetail>('/api/notes', payload),
  update: (id: number, payload: NoteUpdateRequest) => apiClient.put<NoteDetail>(`/api/notes/${id}`, payload),
  remove: (id: number) => apiClient.delete(`/api/notes/${id}`),
}
