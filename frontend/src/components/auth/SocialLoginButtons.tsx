import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuth } from '../../contexts/AuthContext'
import { ApiError, authApi, type OAuthProviders } from '../../services/api'
import { frontendOAuthConfigured, requestOAuthCredential, type OAuthProviderId } from '../../services/oauthClients'
import { homePath } from '../../services/permissions'

type Props = {
  disabled?: boolean
  onMessage: (message: string) => void
  onBusyChange?: (busy: boolean) => void
  /** i18n key prefix under auth namespace, e.g. loginPage or signupPage */
  i18nPrefix: 'loginPage' | 'signupPage'
}

const PROVIDERS: OAuthProviderId[] = ['google', 'facebook']

function labelKey(provider: OAuthProviderId) {
  return provider === 'google' ? 'socialGoogle' : 'socialFacebook'
}

function isEnabled(providers: OAuthProviders | null, provider: OAuthProviderId) {
  if (!providers) return false
  return Boolean(providers[provider] && frontendOAuthConfigured(provider))
}

function redirectAfterAuth(authenticated: { emailVerified?: boolean } & Parameters<typeof homePath>[0]) {
  if (authenticated.emailVerified === false) {
    const next = new URLSearchParams(window.location.search).get('next')
    const suffix = next?.startsWith('/') && !next.startsWith('//') ? `?next=${encodeURIComponent(next)}` : ''
    window.location.replace(`/verify-email${suffix}`)
    return
  }
  const next = new URLSearchParams(window.location.search).get('next')
  const destination = next?.startsWith('/') && !next.startsWith('//') ? next : homePath(authenticated)
  window.location.replace(destination)
}

export default function SocialLoginButtons({ disabled, onMessage, onBusyChange, i18nPrefix }: Props) {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`${i18nPrefix}.${key}`, options)
  const { loginWithOAuth } = useAuth()
  const [providers, setProviders] = useState<OAuthProviders | null>(null)
  const [busyProvider, setBusyProvider] = useState<OAuthProviderId | null>(null)

  useEffect(() => {
    let cancelled = false
    authApi.oauthProviders()
      .then((next) => { if (!cancelled) setProviders(next) })
      .catch(() => { if (!cancelled) setProviders({ google: false, facebook: false }) })
    return () => { cancelled = true }
  }, [])

  const socialLogin = async (provider: OAuthProviderId) => {
    const displayName = p(labelKey(provider))
    if (!isEnabled(providers, provider)) {
      onMessage(p('form.socialUnavailable', { provider: displayName }))
      return
    }
    if (disabled || busyProvider) return
    setBusyProvider(provider)
    onBusyChange?.(true)
    onMessage('')
    try {
      const credential = await requestOAuthCredential(provider)
      const authenticated = await loginWithOAuth(provider, credential.idToken, credential.fullName)
      redirectAfterAuth(authenticated)
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'OAUTH_EMAIL_REQUIRED') onMessage(p('form.socialEmailRequired'))
        else onMessage(error.message || p('form.socialFailed', { provider: displayName }))
      } else if (error instanceof Error && /POPUP_BLOCKED/i.test(error.message)) {
        onMessage(p('form.socialPopupBlocked', { provider: displayName }))
      } else if (error instanceof Error && /CANCELLED|access_denied|user_cancel/i.test(error.message)) {
        onMessage('')
      } else {
        onMessage(p('form.socialFailed', { provider: displayName }))
      }
    } finally {
      setBusyProvider(null)
      onBusyChange?.(false)
    }
  }

  return (
    <div className="social-logins">
      {PROVIDERS.map((provider) => {
        const enabled = isEnabled(providers, provider)
        const label = p(labelKey(provider))
        const busy = busyProvider === provider
        return (
          <button
            key={provider}
            type="button"
            disabled={disabled || Boolean(busyProvider) || !enabled}
            aria-busy={busy}
            title={enabled ? undefined : p('form.socialUnavailable', { provider: label })}
            onClick={() => void socialLogin(provider)}
          >
            {provider === 'google'
              ? <i className="google-g" aria-hidden="true">G</i>
              : <i className="facebook-f" aria-hidden="true">f</i>}
            {label}
          </button>
        )
      })}
    </div>
  )
}
