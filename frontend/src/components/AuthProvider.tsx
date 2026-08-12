import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { authApi } from '../api/auth'
import { getAuthToken, registerUnauthorizedHandler, setAuthToken } from '../api/client'
import type { UserSummary } from '../api/types'

interface AuthContextValue {
  user: UserSummary | null
  loading: boolean
  isAdmin: boolean
  login: (email: string, password: string) => Promise<void>
  signup: (email: string, password: string, displayName: string) => Promise<void>
  logout: () => void
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    registerUnauthorizedHandler(() => {
      setAuthToken(null)
      setUser(null)
    })
  }, [])

  useEffect(() => {
    const token = getAuthToken()
    if (!token) {
      setLoading(false)
      return
    }
    authApi
      .me()
      .then(setUser)
      .catch(() => setAuthToken(null))
      .finally(() => setLoading(false))
  }, [])

  const login = async (email: string, password: string) => {
    const res = await authApi.login({ email, password })
    setAuthToken(res.token)
    setUser(res.user)
  }

  const signup = async (email: string, password: string, displayName: string) => {
    const res = await authApi.signup({ email, password, displayName })
    setAuthToken(res.token)
    setUser(res.user)
  }

  const logout = () => {
    setAuthToken(null)
    setUser(null)
  }

  const changePassword = async (currentPassword: string, newPassword: string) => {
    await authApi.changePassword({ currentPassword, newPassword })
  }

  return (
    <AuthContext.Provider
      value={{ user, loading, isAdmin: user?.appRole === 'ADMIN', login, signup, logout, changePassword }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return ctx
}
