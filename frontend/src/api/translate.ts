import { apiClient } from './client'
import type { TranslateRequest, TranslateResponse } from './types'

export const translateApi = {
  translate: (payload: TranslateRequest) => apiClient.post<TranslateResponse>('/api/translate', payload),
}
