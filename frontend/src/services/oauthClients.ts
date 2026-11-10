export type OAuthProviderId = 'google' | 'facebook'

export type OAuthCredential = {
  idToken: string
  fullName?: string
}

type FacebookLoginStatus = {
  status: string
  authResponse?: { accessToken?: string } | null
}

declare global {
  interface Window {
    FB?: {
      init: (config: { appId: string; cookie?: boolean; xfbml?: boolean; version: string }) => void
      login: (callback: (response: FacebookLoginStatus) => void, options?: { scope?: string }) => void
      getLoginStatus?: (callback: (response: FacebookLoginStatus) => void) => void
    }
    fbAsyncInit?: () => void
  }
}

const GOOGLE_CLIENT_ID = (import.meta.env.VITE_OAUTH_GOOGLE_CLIENT_ID || '').trim()
const FACEBOOK_APP_ID = (import.meta.env.VITE_OAUTH_FACEBOOK_APP_ID || '').trim()

export function frontendOAuthConfigured(provider: OAuthProviderId): boolean {
  if (provider === 'google') return Boolean(GOOGLE_CLIENT_ID)
  return Boolean(FACEBOOK_APP_ID)
}

function loadScript(src: string, id: string): Promise<void> {
  const existing = document.getElementById(id) as HTMLScriptElement | null
  if (existing?.dataset.loaded === 'true') return Promise.resolve()
  if (existing) {
    return new Promise((resolve, reject) => {
      existing.addEventListener('load', () => resolve(), { once: true })
      existing.addEventListener('error', () => reject(new Error(`Failed to load ${src}`)), { once: true })
    })
  }
  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.id = id
    script.src = src
    script.async = true
    script.onload = () => {
      script.dataset.loaded = 'true'
      resolve()
    }
    script.onerror = () => reject(new Error(`Failed to load ${src}`))
    document.head.appendChild(script)
  })
}

function randomNonce(): string {
  const bytes = new Uint8Array(16)
  crypto.getRandomValues(bytes)
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
}

/**
 * Google Sign-In via OAuth popup (id_token). Avoids One Tap / FedCM, which often fails on
 * localhost when third-party sign-in is blocked or Authorized JavaScript origins are incomplete.
 */
async function requestGoogleCredential(): Promise<OAuthCredential> {
  if (!GOOGLE_CLIENT_ID) throw new Error('GOOGLE_NOT_CONFIGURED')

  const redirectUri = `${window.location.origin}/oauth/google/callback`
  const nonce = randomNonce()
  const params = new URLSearchParams({
    client_id: GOOGLE_CLIENT_ID,
    redirect_uri: redirectUri,
    response_type: 'id_token',
    scope: 'openid email profile',
    nonce,
    prompt: 'select_account',
  })
  const authUrl = `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`

  return new Promise((resolve, reject) => {
    const width = 500
    const height = 650
    const left = Math.max(0, Math.round(window.screenX + (window.outerWidth - width) / 2))
    const top = Math.max(0, Math.round(window.screenY + (window.outerHeight - height) / 2))
    const popup = window.open(
      authUrl,
      'tsf-google-oauth',
      `width=${width},height=${height},left=${left},top=${top},menubar=no,toolbar=no,status=no`,
    )
    if (!popup) {
      reject(new Error('GOOGLE_POPUP_BLOCKED'))
      return
    }

    let settled = false
    const finish = (error?: Error, credential?: OAuthCredential) => {
      if (settled) return
      settled = true
      window.clearInterval(closedTimer)
      window.removeEventListener('message', onMessage)
      try { popup.close() } catch { /* ignore */ }
      if (error) reject(error)
      else if (credential) resolve(credential)
      else reject(new Error('GOOGLE_CANCELLED'))
    }

    const onMessage = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return
      const data = event.data
      if (!data || data.type !== 'tsf-google-oauth') return
      if (data.error) {
        finish(new Error(String(data.error).includes('access_denied') ? 'GOOGLE_CANCELLED' : `GOOGLE_${data.error}`))
        return
      }
      if (!data.idToken || typeof data.idToken !== 'string') {
        finish(new Error('GOOGLE_NO_CREDENTIAL'))
        return
      }
      finish(undefined, { idToken: data.idToken })
    }

    window.addEventListener('message', onMessage)
    const closedTimer = window.setInterval(() => {
      if (popup.closed) finish(new Error('GOOGLE_CANCELLED'))
    }, 400)
  })
}

async function requestFacebookCredential(): Promise<OAuthCredential> {
  if (!FACEBOOK_APP_ID) throw new Error('FACEBOOK_NOT_CONFIGURED')
  await new Promise<void>((resolve, reject) => {
    window.fbAsyncInit = () => {
      window.FB?.init({ appId: FACEBOOK_APP_ID, cookie: true, xfbml: false, version: 'v21.0' })
      resolve()
    }
    loadScript('https://connect.facebook.net/en_US/sdk.js', 'tsf-facebook-sdk').catch(reject)
    if (window.FB) {
      window.FB.init({ appId: FACEBOOK_APP_ID, cookie: true, xfbml: false, version: 'v21.0' })
      resolve()
    }
  })
  if (!window.FB) throw new Error('FACEBOOK_SDK_UNAVAILABLE')

  return new Promise((resolve, reject) => {
    window.FB!.login((response) => {
      const token = response.authResponse?.accessToken
      if (!token) {
        reject(new Error(response.status === 'connected' ? 'FACEBOOK_NO_TOKEN' : 'FACEBOOK_CANCELLED'))
        return
      }
      resolve({ idToken: token })
    }, { scope: 'email,public_profile' })
  })
}

export async function requestOAuthCredential(provider: OAuthProviderId): Promise<OAuthCredential> {
  if (provider === 'google') return requestGoogleCredential()
  return requestFacebookCredential()
}
