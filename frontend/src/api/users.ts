import { apiClient } from './client'
import type { AdminCreateUserRequest, AppRole, UserSummary } from './types'

export const usersApi = {
  list: () => apiClient.get<UserSummary[]>('/api/users'),
  create: (payload: AdminCreateUserRequest) => apiClient.post<UserSummary>('/api/users', payload),
  setRole: (id: number, appRole: AppRole) => apiClient.patch<UserSummary>(`/api/users/${id}/role`, { appRole }),
  setEnabled: (id: number, enabled: boolean) => apiClient.patch<UserSummary>(`/api/users/${id}/status`, { enabled }),
  remove: (id: number) => apiClient.delete(`/api/users/${id}`),
}
