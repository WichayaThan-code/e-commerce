import { createContext, useCallback, useContext, useEffect, useState } from 'react'
import { authApi, type AuthUser, type RegisterPayload } from '../services/api'

type AuthContextValue = {
  user: AuthUser | null
  loading: boolean
  login(identity:string, password:string): Promise<AuthUser>
  register(payload:RegisterPayload): Promise<void>
  logout(): Promise<void>
  reload(): Promise<AuthUser>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({children}:{children:React.ReactNode}) {
  const [user,setUser] = useState<AuthUser|null>(null)
  const [loading,setLoading] = useState(true)

  useEffect(() => {
    authApi.refresh().then(setUser).catch(() => setUser(null)).finally(() => setLoading(false))
  }, [])

  const reload = useCallback(async () => {
    const next = await authApi.refresh()
    setUser(next)
    return next
  }, [])

  const value:AuthContextValue = {
    user, loading,
    login: async (identity,password) => { const authenticated=await authApi.login(identity,password); setUser(authenticated); return authenticated },
    register: async payload => setUser(await authApi.register(payload)),
    logout: async () => { await authApi.logout(); setUser(null) },
    reload,
  }
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const value = useContext(AuthContext)
  if (!value) throw new Error('useAuth must be used inside AuthProvider')
  return value
}
