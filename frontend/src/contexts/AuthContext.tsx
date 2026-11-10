import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { authApi, PERMISSIONS_STALE_EVENT, type AuthUser, type OAuthProviderId, type RegisterPayload } from '../services/api'
import { permissionFingerprint } from '../services/permissions'

type AuthContextValue = {
  user: AuthUser | null
  loading: boolean
  login(identity:string, password:string): Promise<AuthUser>
  loginWithOAuth(provider: OAuthProviderId, idToken: string, fullName?: string): Promise<AuthUser>
  register(payload:RegisterPayload): Promise<AuthUser>
  logout(): Promise<void>
  reload(): Promise<AuthUser>
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({children}:{children:React.ReactNode}) {
  const [user,setUser] = useState<AuthUser|null>(null)
  const [loading,setLoading] = useState(true)
  const signedIn = useRef(false)
  signedIn.current = user !== null

  useEffect(() => {
    authApi.refresh().then(setUser).catch(() => setUser(null)).finally(() => setLoading(false))
  }, [])

  // Roles can change while the user is signed in (the backend applies them on the very next request).
  // Re-read the effective permissions after a denial and when the tab regains focus, keeping the same
  // object when nothing changed so pages do not re-render or reload their data.
  useEffect(() => {
    let lastSync = 0
    const sync = (minGapMs:number) => {
      const now = Date.now()
      if (!signedIn.current || now - lastSync < minGapMs) return
      lastSync = now
      authApi.me().then(next => setUser(current => current && permissionFingerprint(current) !== permissionFingerprint(next) ? next : current)).catch(() => {})
    }
    const onStale = () => sync(3000)
    const onVisible = () => { if (document.visibilityState === 'visible') sync(30000) }
    window.addEventListener(PERMISSIONS_STALE_EVENT, onStale)
    window.addEventListener('focus', onVisible)
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.removeEventListener(PERMISSIONS_STALE_EVENT, onStale)
      window.removeEventListener('focus', onVisible)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [])

  const reload = useCallback(async () => {
    const next = await authApi.refresh()
    setUser(next)
    return next
  }, [])

  const value:AuthContextValue = {
    user, loading,
    login: async (identity,password) => { const authenticated=await authApi.login(identity,password); setUser(authenticated); return authenticated },
    loginWithOAuth: async (provider, idToken, fullName) => {
      const authenticated = await authApi.oauthLogin(provider, idToken, fullName)
      setUser(authenticated)
      return authenticated
    },
    register: async payload => { const next = await authApi.register(payload); setUser(next); return next },
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
