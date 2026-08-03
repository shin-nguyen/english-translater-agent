const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  status: number
  details?: string[]

  constructor(status: number, message: string, details?: string[]) {
    super(message)
    this.status = status
    this.details = details
  }
}

interface BackendError {
  message?: string
  details?: string[]
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
  })

  if (res.status === 204) {
    return undefined as T
  }

  const isJson = res.headers.get('content-type')?.includes('application/json') ?? false
  const body = isJson ? ((await res.json()) as BackendError | T) : undefined

  if (!res.ok) {
    const err = body as BackendError | undefined
    throw new ApiError(res.status, err?.message ?? `Request failed with status ${res.status}`, err?.details)
  }

  return body as T
}

export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, payload?: unknown) =>
    request<T>(path, { method: 'POST', body: payload !== undefined ? JSON.stringify(payload) : undefined }),
  put: <T>(path: string, payload?: unknown) =>
    request<T>(path, { method: 'PUT', body: payload !== undefined ? JSON.stringify(payload) : undefined }),
  delete: <T = void>(path: string) => request<T>(path, { method: 'DELETE' }),
}
