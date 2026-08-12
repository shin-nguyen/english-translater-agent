export interface Role {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface RoleRequest {
  name: string
  description: string | null
}

export interface Context {
  id: number
  name: string
  description: string | null
  createdAt: string
}

export interface ContextRequest {
  name: string
  description: string | null
}

export type DetectedLanguage = 'vi' | 'en' | 'mixed'

export interface AnalysisPoint {
  original: string
  improved: string
  reason: string
}

export interface Alternative {
  text: string
  style: string
  reason: string
}

export interface TranslateRequest {
  text: string
  roleId: number | null
  contextId: number | null
  modelConfigId: number | null
}

export interface TranslateResponse {
  detectedLanguage: DetectedLanguage
  suggestedTitle: string
  mainResult: string
  alternatives: Alternative[]
  analysis: AnalysisPoint[]
}

export interface NoteCreateRequest {
  title: string
  originalText: string
  detectedLanguage: string
  englishResult: string
  alternatives: Alternative[]
  analysis: AnalysisPoint[]
  roleId: number | null
  contextId: number | null
}

export type NoteUpdateRequest = NoteCreateRequest

export interface NoteSummary {
  id: number
  title: string
  originalTextExcerpt: string
  englishResultExcerpt: string
  role: Role | null
  context: Context | null
  createdAt: string
}

export interface NoteDetail {
  id: number
  title: string
  originalText: string
  detectedLanguage: string
  englishResult: string
  alternatives: Alternative[]
  analysis: AnalysisPoint[]
  role: Role | null
  context: Context | null
  createdAt: string
  updatedAt: string
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
  first: boolean
  last: boolean
}

export type AppRole = 'ADMIN' | 'USER'

export interface UserSummary {
  id: number
  email: string
  displayName: string
  appRole: AppRole
  enabled: boolean
  createdAt: string
}

export interface AuthResponse {
  token: string
  user: UserSummary
}

export interface SignupRequest {
  email: string
  password: string
  displayName: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface AdminCreateUserRequest {
  email: string
  password: string
  displayName: string
  appRole: AppRole
}

export type AiProviderType = 'ANTHROPIC' | 'OPENAI_COMPATIBLE'

export interface AiModelConfigAdmin {
  id: number
  label: string
  provider: AiProviderType
  baseUrl: string
  modelIdentifier: string
  apiVersion: string | null
  maxTokens: number
  timeoutSeconds: number
  enabled: boolean
  isDefault: boolean
  createdAt: string
  updatedAt: string
}

export interface AiModelConfigCreateRequest {
  label: string
  provider: AiProviderType
  baseUrl: string
  apiKey: string
  modelIdentifier: string
  apiVersion: string | null
  maxTokens: number
  timeoutSeconds: number
  enabled: boolean
  isDefault: boolean
}

// apiKey blank/omitted means "keep the existing stored key unchanged"
export type AiModelConfigUpdateRequest = AiModelConfigCreateRequest

export interface AiModelOption {
  id: number
  label: string
}
