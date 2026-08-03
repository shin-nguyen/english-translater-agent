import { apiClient } from './client'
import type { Context, ContextRequest } from './types'

export const contextsApi = {
  list: () => apiClient.get<Context[]>('/api/contexts'),
  create: (payload: ContextRequest) => apiClient.post<Context>('/api/contexts', payload),
  update: (id: number, payload: ContextRequest) => apiClient.put<Context>(`/api/contexts/${id}`, payload),
  remove: (id: number) => apiClient.delete(`/api/contexts/${id}`),
}
