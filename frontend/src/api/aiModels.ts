import { apiClient } from './client'
import type { AiModelConfigAdmin, AiModelConfigCreateRequest, AiModelConfigUpdateRequest, AiModelOption } from './types'

export const aiModelsApi = {
  list: () => apiClient.get<AiModelConfigAdmin[]>('/api/ai-models'),
  create: (payload: AiModelConfigCreateRequest) => apiClient.post<AiModelConfigAdmin>('/api/ai-models', payload),
  update: (id: number, payload: AiModelConfigUpdateRequest) =>
    apiClient.put<AiModelConfigAdmin>(`/api/ai-models/${id}`, payload),
  remove: (id: number) => apiClient.delete(`/api/ai-models/${id}`),
  listEnabled: () => apiClient.get<AiModelOption[]>('/api/ai-models/enabled'),
}
