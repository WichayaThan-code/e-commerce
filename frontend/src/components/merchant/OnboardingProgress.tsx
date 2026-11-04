import { useEffect, useState } from 'react'
import { ArrowLeft, Clock3, Home, IdCard, Landmark, Store } from 'lucide-react'
import { Link } from 'react-router-dom'
import { useLanguage } from '../../contexts/LanguageContext'
import { mediaUrl, shopApi, type PublicShop } from '../../services/api'

const PREPARE_ICONS = [IdCard, Landmark, Home] as const
const AVATAR_TONES = ['tone-a', 'tone-b', 'tone-c', 'tone-d', 'tone-a'] as const
const SHOP_LOGO_CACHE_KEY = 'tsf_onb_shop_logos_v1'
const SHOP_LOGO_CACHE_MS = 10 * 60 * 1000
const MAX_LOGOS = 5

type ShopLogoItem = { name: string; logoUrl: string }

type LogoCache = {
  at: number
  shops: ShopLogoItem[]
  totalWithLogo: number
}

function readLogoCache(): LogoCache | null {
  try {
    const raw = sessionStorage.getItem(SHOP_LOGO_CACHE_KEY)
    if (!raw) return null
    const parsed = JSON.parse(raw) as LogoCache
    if (!parsed?.at || !Array.isArray(parsed.shops)) return null
    if (Date.now() - parsed.at > SHOP_LOGO_CACHE_MS) return null
    return parsed
  } catch {
    return null
  }
}

function writeLogoCache(data: LogoCache) {
  try {
    sessionStorage.setItem(SHOP_LOGO_CACHE_KEY, JSON.stringify(data))
  } catch {
    /* ignore quota */
  }
}

function shopDisplayName(shop: PublicShop) {
  return (shop.name || shop.nameEn || shop.slug || '').trim()
}

function initialOf(name: string) {
  return (name.trim().charAt(0) || '?').toUpperCase()
}

function ShopAvatar({ shop, tone }: { shop: ShopLogoItem; tone: string }) {
  const [broken, setBroken] = useState(false)
  const src = mediaUrl(shop.logoUrl)
  const showImage = Boolean(src) && !broken

  return (
    <span className={tone} title={shop.name}>
      {showImage ? (
        <img src={src} alt={shop.name} title={shop.name} onError={() => setBroken(true)} />
      ) : (
        <em aria-hidden="true">{initialOf(shop.name)}</em>
      )}
    </span>
  )
}

/** Left rail for merchant onboarding — no form/card styling. */
export default function OnboardingProgress() {
  const { onboarding: ob } = useLanguage()
  const c = ob.common
  const lead = c.applyLeadShort || c.applyLead.replace(/\n/g, ' ')
  const prepareSteps = c.prepareSteps ?? []
  const [logoLoading, setLogoLoading] = useState(true)
  const [logoShops, setLogoShops] = useState<ShopLogoItem[]>([])
  const [logoTotal, setLogoTotal] = useState(0)

  useEffect(() => {
    let active = true
    const cached = readLogoCache()
    if (cached) {
      setLogoShops(cached.shops)
      setLogoTotal(cached.totalWithLogo)
      setLogoLoading(false)
      return
    }

    setLogoLoading(true)
    shopApi.list()
      .then((rows) => {
        if (!active) return
        const withLogo = rows
          .map((shop) => ({ name: shopDisplayName(shop), logoUrl: (shop.logoUrl || '').trim() }))
          .filter((shop) => shop.logoUrl && shop.name)
        const payload: LogoCache = {
          at: Date.now(),
          shops: withLogo.slice(0, MAX_LOGOS),
          totalWithLogo: withLogo.length,
        }
        writeLogoCache(payload)
        setLogoShops(payload.shops)
        setLogoTotal(payload.totalWithLogo)
      })
      .catch(() => {
        if (!active) return
        setLogoShops([])
        setLogoTotal(0)
      })
      .finally(() => {
        if (active) setLogoLoading(false)
      })

    return () => { active = false }
  }, [])

  const extraCount = Math.max(0, logoTotal - logoShops.length)
  const showAvatars = logoLoading || logoShops.length > 0

  return (
    <div className="onb-left">
      <Link className="onb-back" to="/">
        <ArrowLeft aria-hidden="true" />
        {c.backHome}
      </Link>

      <span className="onb-badge">
        <Store aria-hidden="true" />
        {c.badgeOnboarding}
      </span>

      <h2 className="onb-title">
        {c.applyTitleBefore}
        <span className="onb-title-accent">{c.applyTitleAccent}</span>
        {c.applyTitleAfter}
      </h2>
      <p className="onb-lead">{lead}</p>

      <div className="onb-eta">
        <span className="onb-eta-icon" aria-hidden="true">
          <Clock3 />
        </span>
        <span>{c.progressEta}</span>
      </div>

      <h3 className="onb-prepare-title">{c.prepareTitle}</h3>
      <ol className="onb-prepare">
        {prepareSteps.map((step, index) => {
          const Icon = PREPARE_ICONS[index % PREPARE_ICONS.length]
          return (
            <li key={step.title}>
              <span className="onb-prepare-icon" aria-hidden="true">
                <Icon />
              </span>
              <div className="onb-prepare-body">
                <b>{step.title}</b>
                <small>{step.hint}</small>
              </div>
            </li>
          )
        })}
      </ol>

      <div className="onb-community">
        {showAvatars ? (
          <div className="onb-avatars" aria-label={c.communityTitle}>
            {logoLoading
              ? Array.from({ length: 4 }, (_, index) => (
                  <span key={index} className="onb-avatar-skel" aria-hidden="true" />
                ))
              : (
                <>
                  {logoShops.map((shop, index) => (
                    <ShopAvatar key={`${shop.name}-${index}`} shop={shop} tone={AVATAR_TONES[index % AVATAR_TONES.length]} />
                  ))}
                  {extraCount > 0 ? (
                    <span className="onb-avatar-more" title={c.communityMore.replace('{{count}}', String(extraCount))}>
                      +{extraCount}
                    </span>
                  ) : null}
                </>
              )}
          </div>
        ) : null}
        <div className="onb-community-copy">
          <b>{c.communityTitle}</b>
          <p>{c.communityLead}</p>
        </div>
      </div>
    </div>
  )
}
