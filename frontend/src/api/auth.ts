import { apiClient } from './client'
import type { AuthResponse, ChangePasswordRequest, LoginRequest, SignupRequest, UserSummary } from './types'

export const authApi = {
  signup: (payload: SignupRequest) => apiClient.post<AuthResponse>('/api/auth/signup', payload),
  login: (payload: LoginRequest) => apiClient.post<AuthResponse>('/api/auth/login', payload),
  me: () => apiClient.get<UserSummary>('/api/auth/me'),
  changePassword: (payload: ChangePasswordRequest) => apiClient.post<void>('/api/auth/change-password', payload),
}
