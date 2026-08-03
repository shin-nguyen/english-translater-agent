import { apiClient } from './client'
import type { Role, RoleRequest } from './types'

export const rolesApi = {
  list: () => apiClient.get<Role[]>('/api/roles'),
  create: (payload: RoleRequest) => apiClient.post<Role>('/api/roles', payload),
  update: (id: number, payload: RoleRequest) => apiClient.put<Role>(`/api/roles/${id}`, payload),
  remove: (id: number) => apiClient.delete(`/api/roles/${id}`),
}
