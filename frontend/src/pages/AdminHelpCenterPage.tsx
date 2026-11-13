import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowDown, ArrowUp, CircleAlert, ExternalLink, Headphones, Pencil, Plus, Search, ShieldCheck, Trash2 } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import AdminSelect from '../components/admin/AdminSelect'
import AdminSidePanel from '../components/admin/AdminSidePanel'
import ConfirmDialog from '../components/form/ConfirmDialog'
import FormField from '../components/form/FormField'
import FormSelect from '../components/form/FormSelect'
import FormTextarea from '../components/form/FormTextarea'
import HelpRichTextEditor from '../components/help/HelpRichTextEditor'
import { helpPlainText } from '../components/help/helpHtml'
import { SkeletonTable } from '../components/feedback/Skeleton'
import { useAuth } from '../contexts/AuthContext'
import { useLanguage } from '../contexts/LanguageContext'
import { usePermissions } from '../hooks/usePermissions'
import {
  ApiError,
  helpAdminApi,
  type AdminHelpFaq,
  type AdminHelpGuide,
  type AdminHelpTopic,
  type HelpFaqRequest,
  type HelpGuideRequest,
  type HelpTopicRequest,
} from '../services/api'
import { hasPlatformAccess } from '../services/permissions'
import '../styles/admin-dashboard.css'
import '../styles/admin-categories.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/admin-side-panel.css'
import '../styles/form-fields.css'
import '../styles/form-invalid.css'
import '../styles/admin-help-center.css'

type Kind = 'topics' | 'faqs' | 'guides'
type Lang = 'Th' | 'En' | 'Zh'
type Draft = Record<string, string>
type Row = {
  id: string
  name: string
  sub: string
  topic: string
  audience: string
  active: boolean
  sortOrder: number
  updatedAt: string
  helpfulYes: number
  helpfulNo: number
  lastReviewedAt: string | null
  search: string
}
type Editing = { kind: Kind; id: string | null }
type FieldErrors = Record<string, string>

const KINDS: Kind[] = ['topics', 'faqs', 'guides']
const LANGS: Lang[] = ['Th', 'En', 'Zh']
const AUDIENCES = ['ALL', 'CUSTOMER', 'MERCHANT'] as const
const ICON_NAMES = ['shopping-cart', 'credit-card', 'truck', 'box', 'store', 'user-round', 'shield-check', 'settings'] as const
const SLUG_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/
const TITLE_FIELD: Record<Kind, string> = { topics: 'title', faqs: 'question', guides: 'title' }
const BODY_FIELD: Record<Kind, string> = { topics: 'description', faqs: 'answer', guides: 'summary' }
const BODY_REQUIRED: Record<Kind, boolean> = { topics: true, faqs: true, guides: true }
const TITLE_MAX = 200
const BODY_MAX = 10_000

const isBlankBody = (value: string) => {
  if (!value.trim()) return true
  if (/<img\b/i.test(value)) return false
  return !helpPlainText(value)
}

const emptyDraft = (kind: Kind): Draft => {
  const draft: Draft = { slug: '', icon: ICON_NAMES[0], topicId: '', audience: 'ALL', url: '' }
  for (const lang of LANGS) {
    draft[`${TITLE_FIELD[kind]}${lang}`] = ''
    draft[`${BODY_FIELD[kind]}${lang}`] = ''
  }
  return draft
}

const text = (value: string | null | undefined) => value ?? ''

export default function AdminHelpCenterPage() {
  const { t } = useTranslation('admin')
  const { localeTag } = useLanguage()
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canManage = can('PLATFORM_HELP_MANAGE')

  const [tab, setTab] = useState<Kind>('topics')
  const [topics, setTopics] = useState<AdminHelpTopic[]>([])
  const [faqs, setFaqs] = useState<AdminHelpFaq[]>([])
  const [guides, setGuides] = useState<AdminHelpGuide[]>([])
  const [loaded, setLoaded] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)

  const [editing, setEditing] = useState<Editing | null>(null)
  const [draft, setDraft] = useState<Draft>(() => emptyDraft('topics'))
  const [draftActive, setDraftActive] = useState(true)
  const [lang, setLang] = useState<Lang>('Th')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [deleting, setDeleting] = useState<{ kind: Kind; row: Row } | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  const notify = useCallback((message: string) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2400)
  }, [])
  const errorText = useCallback((error: unknown) => (error instanceof ApiError && error.message ? error.message : t('helpCenter.toast.actionFailed')), [t])

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setLoadError('')
    Promise.all([helpAdminApi.topics(), helpAdminApi.faqs(), helpAdminApi.guides()])
      .then(([topicRows, faqRows, guideRows]) => {
        if (cancelled) return
        setTopics(topicRows)
        setFaqs(faqRows)
        setGuides(guideRows)
        setLoaded(true)
      })
      .catch(error => {
        if (cancelled) return
        setLoadError(error instanceof ApiError && error.message ? error.message : t('helpCenter.loadFailed'))
        setLoaded(true)
      })
    return () => { cancelled = true }
  }, [allowed, reloadKey, t])

  const topicName = useCallback((slug: string, id?: string) => {
    const topic = topics.find(item => (id ? item.id === id : item.slug === slug))
    return topic ? topic.titleTh : slug
  }, [topics])

  const rowsByKind = useMemo<Record<Kind, Row[]>>(() => {
    const byOrder = (a: Row, b: Row) => a.sortOrder - b.sortOrder
    return {
      topics: topics.map(item => ({
        id: item.id, name: item.titleTh, sub: item.slug, topic: '', audience: '', active: item.active,
        sortOrder: item.sortOrder, updatedAt: item.updatedAt, helpfulYes: 0, helpfulNo: 0, lastReviewedAt: null,
        search: [item.slug, item.titleTh, item.titleEn, item.titleZh, item.descriptionTh].map(text).join(' ').toLowerCase(),
      })).sort(byOrder),
      faqs: faqs.map(item => ({
        id: item.id, name: item.questionTh, sub: '', topic: topicName(item.topicSlug, item.topicId), audience: item.audience,
        active: item.active, sortOrder: item.sortOrder, updatedAt: item.updatedAt,
        helpfulYes: item.helpfulYes ?? 0, helpfulNo: item.helpfulNo ?? 0, lastReviewedAt: item.lastReviewedAt ?? null,
        search: [item.questionTh, item.questionEn, item.questionZh, item.answerTh, item.topicSlug].map(text).join(' ').toLowerCase(),
      })).sort(byOrder),
      guides: guides.map(item => ({
        id: item.id, name: item.titleTh, sub: item.slug, topic: '', audience: '', active: item.active,
        sortOrder: item.sortOrder, updatedAt: item.updatedAt, helpfulYes: 0, helpfulNo: 0, lastReviewedAt: null,
        search: [item.slug, item.titleTh, item.titleEn, item.titleZh, item.summaryTh].map(text).join(' ').toLowerCase(),
      })).sort(byOrder),
    }
  }, [topics, faqs, guides, topicName])

  const allRows = rowsByKind[tab]
  const filtering = query.trim() !== '' || status !== ''
  const rows = useMemo(() => {
    const term = query.trim().toLowerCase()
    return allRows.filter(row => (!term || row.search.includes(term)) && (!status || (status === 'active' ? row.active : !row.active)))
  }, [allRows, query, status])

  const formatDate = useCallback((value: string) => {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return new Intl.DateTimeFormat(localeTag, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
  }, [localeTag])

  if (loading) return <main className="auth-loading">{t('helpCenter.checkingAccess')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const switchTab = (next: Kind) => {
    setTab(next)
    setQuery('')
    setStatus('')
  }
  const resetFilters = () => { setQuery(''); setStatus('') }

  /* ------------------------------------------------------------------ form */
  const openCreate = () => {
    const next = emptyDraft(tab)
    if (tab === 'faqs') next.topicId = topics[0]?.id ?? ''
    setDraft(next)
    setDraftActive(true)
    setEditing({ kind: tab, id: null })
    setLang('Th')
    setErrors({})
    setFormError('')
  }
  const openEdit = (kind: Kind, id: string) => {
    const next = emptyDraft(kind)
    let active = true
    if (kind === 'topics') {
      const item = topics.find(row => row.id === id)
      if (!item) return
      Object.assign(next, {
        slug: item.slug, icon: item.icon || ICON_NAMES[0],
        titleTh: text(item.titleTh), titleEn: text(item.titleEn), titleZh: text(item.titleZh),
        descriptionTh: text(item.descriptionTh), descriptionEn: text(item.descriptionEn), descriptionZh: text(item.descriptionZh),
      })
      active = item.active
    } else if (kind === 'faqs') {
      const item = faqs.find(row => row.id === id)
      if (!item) return
      Object.assign(next, {
        topicId: item.topicId, audience: item.audience || 'ALL',
        questionTh: text(item.questionTh), questionEn: text(item.questionEn), questionZh: text(item.questionZh),
        answerTh: text(item.answerTh), answerEn: text(item.answerEn), answerZh: text(item.answerZh),
      })
      active = item.active
    } else {
      const item = guides.find(row => row.id === id)
      if (!item) return
      Object.assign(next, {
        slug: item.slug, url: text(item.url),
        titleTh: text(item.titleTh), titleEn: text(item.titleEn), titleZh: text(item.titleZh),
        summaryTh: text(item.summaryTh), summaryEn: text(item.summaryEn), summaryZh: text(item.summaryZh),
      })
      active = item.active
    }
    setDraft(next)
    setDraftActive(active)
    setEditing({ kind, id })
    setLang('Th')
    setErrors({})
    setFormError('')
  }
  const closeForm = () => { if (!saving) setEditing(null) }
  const setField = (name: string, value: string) => {
    setDraft(current => ({ ...current, [name]: value }))
    setErrors(current => {
      if (!current[name]) return current
      const { [name]: _removed, ...rest } = current
      return rest
    })
  }

  const validate = (kind: Kind): FieldErrors => {
    const found: FieldErrors = {}
    const required = t('helpCenter.errors.required')
    const titleTh = `${TITLE_FIELD[kind]}Th`
    const bodyTh = `${BODY_FIELD[kind]}Th`
    if (!draft[titleTh].trim()) found[titleTh] = required
    if (BODY_REQUIRED[kind] && isBlankBody(draft[bodyTh])) found[bodyTh] = required
    for (const lang of LANGS) {
      const bodyName = `${BODY_FIELD[kind]}${lang}`
      if (draft[bodyName].length > BODY_MAX) found[bodyName] = t('helpCenter.errors.tooLong', { max: BODY_MAX })
    }
    if (kind !== 'faqs') {
      const slug = draft.slug.trim()
      if (!slug) found.slug = required
      else if (!SLUG_PATTERN.test(slug)) found.slug = t('helpCenter.errors.slugInvalid')
    }
    if (kind === 'faqs' && !draft.topicId) found.topicId = t('helpCenter.errors.topicRequired')
    if (kind === 'guides') {
      const url = draft.url.trim()
      if (!url) found.url = required
      else if (!/^(https?:\/\/|\/)\S+$/i.test(url)) found.url = t('helpCenter.errors.urlInvalid')
    }
    return found
  }

  const buildRequest = (kind: Kind): HelpTopicRequest | HelpFaqRequest | HelpGuideRequest => {
    const value = (name: string) => draft[name].trim()
    if (kind === 'topics') {
      return {
        slug: value('slug'), icon: draft.icon, active: draftActive,
        titleTh: value('titleTh'), titleEn: value('titleEn'), titleZh: value('titleZh'),
        descriptionTh: value('descriptionTh'), descriptionEn: value('descriptionEn'), descriptionZh: value('descriptionZh'),
      }
    }
    if (kind === 'faqs') {
      return {
        topicId: draft.topicId, audience: draft.audience, active: draftActive,
        questionTh: value('questionTh'), questionEn: value('questionEn'), questionZh: value('questionZh'),
        answerTh: value('answerTh'), answerEn: value('answerEn'), answerZh: value('answerZh'),
      }
    }
    return {
      slug: value('slug'), url: value('url'), active: draftActive,
      titleTh: value('titleTh'), titleEn: value('titleEn'), titleZh: value('titleZh'),
      summaryTh: value('summaryTh'), summaryEn: value('summaryEn'), summaryZh: value('summaryZh'),
    }
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    if (!editing || saving || !canManage) return
    const { kind, id } = editing
    const found = validate(kind)
    if (Object.keys(found).length > 0) {
      setErrors(found)
      setFormError('')
      const firstLang = LANGS.find(code => Object.keys(found).some(name => name.endsWith(code)))
      const onlyLangFields = Object.keys(found).every(name => LANGS.some(code => name.endsWith(code)))
      if (firstLang && onlyLangFields) setLang(firstLang)
      return
    }
    setSaving(true)
    setFormError('')
    try {
      const body = buildRequest(kind)
      if (kind === 'topics') {
        const saved = id ? await helpAdminApi.updateTopic(id, body as HelpTopicRequest) : await helpAdminApi.createTopic(body as HelpTopicRequest)
        setTopics(current => id ? current.map(row => row.id === saved.id ? saved : row) : [...current, saved])
      } else if (kind === 'faqs') {
        const saved = id ? await helpAdminApi.updateFaq(id, body as HelpFaqRequest) : await helpAdminApi.createFaq(body as HelpFaqRequest)
        setFaqs(current => id ? current.map(row => row.id === saved.id ? saved : row) : [...current, saved])
        // topic FAQ counts changed
        helpAdminApi.topics().then(setTopics).catch(() => undefined)
      } else {
        const saved = id ? await helpAdminApi.updateGuide(id, body as HelpGuideRequest) : await helpAdminApi.createGuide(body as HelpGuideRequest)
        setGuides(current => id ? current.map(row => row.id === saved.id ? saved : row) : [...current, saved])
      }
      setEditing(null)
      notify(t(id ? 'helpCenter.toast.updated' : 'helpCenter.toast.created'))
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        const fromApi: FieldErrors = {}
        for (const item of error.fieldErrors) if (item.field && !fromApi[item.field]) fromApi[item.field] = item.message || t('helpCenter.errors.invalid')
        setErrors(fromApi)
        const firstLang = LANGS.find(code => Object.keys(fromApi).some(name => name.endsWith(code)))
        if (firstLang) setLang(firstLang)
      }
      setFormError(errorText(error))
    } finally {
      setSaving(false)
    }
  }

  /* ------------------------------------------------------------ row actions */
  const toggleActive = async (row: Row) => {
    if (!canManage || busyId) return
    setBusyId(row.id)
    try {
      const next = !row.active
      if (tab === 'topics') {
        const saved = await helpAdminApi.setTopicActive(row.id, next)
        setTopics(current => current.map(item => item.id === saved.id ? saved : item))
      } else if (tab === 'faqs') {
        const saved = await helpAdminApi.setFaqActive(row.id, next)
        setFaqs(current => current.map(item => item.id === saved.id ? saved : item))
      } else {
        const saved = await helpAdminApi.setGuideActive(row.id, next)
        setGuides(current => current.map(item => item.id === saved.id ? saved : item))
      }
      notify(t(next ? 'helpCenter.toast.activated' : 'helpCenter.toast.deactivated'))
    } catch (error) {
      notify(errorText(error))
    } finally {
      setBusyId(null)
    }
  }

  const markReviewed = async (row: Row) => {
    if (!canManage || busyId) return
    setBusyId(row.id)
    try {
      const saved = await helpAdminApi.markFaqReviewed(row.id)
      setFaqs(current => current.map(item => item.id === saved.id ? saved : item))
      notify(t('helpCenter.toast.reviewed'))
    } catch (error) {
      notify(errorText(error))
    } finally {
      setBusyId(null)
    }
  }

  const move = async (index: number, direction: -1 | 1) => {
    if (!canManage || busyId || filtering) return
    const target = index + direction
    if (target < 0 || target >= allRows.length) return
    const ids = allRows.map(row => row.id)
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    setBusyId(allRows[index].id)
    try {
      if (tab === 'topics') setTopics(await helpAdminApi.reorderTopics(ids))
      else if (tab === 'faqs') setFaqs(await helpAdminApi.reorderFaqs(ids))
      else setGuides(await helpAdminApi.reorderGuides(ids))
      notify(t('helpCenter.toast.reordered'))
    } catch (error) {
      notify(errorText(error))
    } finally {
      setBusyId(null)
    }
  }

  const confirmDelete = async () => {
    if (!deleting || deleteBusy) return
    const { kind, row } = deleting
    setDeleteBusy(true)
    setDeleteError('')
    try {
      if (kind === 'topics') {
        await helpAdminApi.deleteTopic(row.id)
        setTopics(current => current.filter(item => item.id !== row.id))
        helpAdminApi.faqs().then(setFaqs).catch(() => undefined)
      } else if (kind === 'faqs') {
        await helpAdminApi.deleteFaq(row.id)
        setFaqs(current => current.filter(item => item.id !== row.id))
        helpAdminApi.topics().then(setTopics).catch(() => undefined)
      } else {
        await helpAdminApi.deleteGuide(row.id)
        setGuides(current => current.filter(item => item.id !== row.id))
      }
      setDeleting(null)
      notify(t('helpCenter.toast.deleted'))
    } catch (error) {
      setDeleteError(errorText(error))
    } finally {
      setDeleteBusy(false)
    }
  }

  /* ----------------------------------------------------------------- render */
  const columnsCount = (tab === 'faqs' ? 9 : 5) + (canManage ? 1 : 0)
  const topicFaqCount = (id: string) => topics.find(item => item.id === id)?.faqCount ?? 0
  const deletingTopicFaqs = deleting?.kind === 'topics' ? topicFaqCount(deleting.row.id) : 0
  const statusOptions = [
    { value: '', label: t('helpCenter.status.all') },
    { value: 'active', label: t('helpCenter.status.active') },
    { value: 'inactive', label: t('helpCenter.status.inactive') },
  ]

  const renderLangFields = (kind: Kind) => {
    const titleName = `${TITLE_FIELD[kind]}${lang}`
    const bodyName = `${BODY_FIELD[kind]}${lang}`
    const isTh = lang === 'Th'
    const langLabel = t(`helpCenter.form.lang.${lang.toLowerCase()}`)
    const titleLabel = `${t(`helpCenter.form.fields.${TITLE_FIELD[kind]}`)} (${langLabel})`
    const bodyLabel = `${t(`helpCenter.form.fields.${BODY_FIELD[kind]}`)} (${langLabel})`
    return (
      <>
        {kind === 'faqs' ? (
          <FormTextarea
            key={titleName}
            label={<>{titleLabel}{isTh && <em className="hc-required"> *</em>}</>}
            rows={3}
            maxLength={TITLE_MAX}
            value={draft[titleName]}
            error={errors[titleName]}
            errorId={`hc-${titleName}-error`}
            disabled={saving}
            onChange={event => setField(titleName, event.target.value)}
          />
        ) : (
          <FormField
            key={titleName}
            label={<>{titleLabel}{isTh && <em className="hc-required"> *</em>}</>}
            error={errors[titleName]}
            errorId={`hc-${titleName}-error`}
          >
            <input
              value={draft[titleName]}
              maxLength={TITLE_MAX}
              disabled={saving}
              aria-invalid={Boolean(errors[titleName])}
              aria-describedby={errors[titleName] ? `hc-${titleName}-error` : undefined}
              onChange={event => setField(titleName, event.target.value)}
            />
          </FormField>
        )}
        <HelpRichTextEditor
          key={bodyName}
          label={<>{bodyLabel}{isTh && BODY_REQUIRED[kind] && <em className="hc-required"> *</em>}</>}
          value={draft[bodyName]}
          error={errors[bodyName]}
          errorId={`hc-${bodyName}-error`}
          disabled={saving}
          maxLength={BODY_MAX}
          onChange={html => setField(bodyName, html)}
        />
        {!isTh && <p className="hc-hint">{t('helpCenter.form.fallbackHint')}</p>}
      </>
    )
  }

  const renderForm = () => {
    if (!editing) return null
    const { kind, id } = editing
    return (
      <form id="hc-form" className="hc-form" onSubmit={submit} noValidate>
        {formError && <p className="hc-form-error" role="alert"><CircleAlert aria-hidden="true" />{formError}</p>}
        {kind !== 'faqs' && (
          <FormField
            label={<>{t('helpCenter.form.fields.slug')}<em className="hc-required"> *</em></>}
            error={errors.slug}
            errorId="hc-slug-error"
          >
            <input
              value={draft.slug}
              placeholder="shipping-tracking"
              disabled={saving}
              maxLength={80}
              autoComplete="off"
              aria-invalid={Boolean(errors.slug)}
              aria-describedby={errors.slug ? 'hc-slug-error' : undefined}
              onChange={event => setField('slug', event.target.value)}
            />
          </FormField>
        )}
        {kind === 'topics' && (
          <FormSelect label={t('helpCenter.form.fields.icon')} value={draft.icon} disabled={saving} onChange={event => setField('icon', event.target.value)}>
            {ICON_NAMES.map(name => <option key={name} value={name}>{t(`helpCenter.icons.${name}`)}</option>)}
          </FormSelect>
        )}
        {kind === 'faqs' && (
          <>
            <FormSelect
              label={<>{t('helpCenter.form.fields.topic')}<em className="hc-required"> *</em></>}
              value={draft.topicId}
              disabled={saving}
              error={errors.topicId}
              errorId="hc-topic-error"
              onChange={event => setField('topicId', event.target.value)}
            >
              <option value="">{t('helpCenter.form.topicPlaceholder')}</option>
              {topics.map(topic => <option key={topic.id} value={topic.id}>{topic.titleTh}</option>)}
            </FormSelect>
            <FormSelect label={t('helpCenter.form.fields.audience')} value={draft.audience} disabled={saving} onChange={event => setField('audience', event.target.value)}>
              {AUDIENCES.map(code => <option key={code} value={code}>{t(`helpCenter.audience.${code}`)}</option>)}
            </FormSelect>
          </>
        )}
        {kind === 'guides' && (
          <FormField
            label={<>{t('helpCenter.form.fields.url')}<em className="hc-required"> *</em></>}
            error={errors.url}
            errorId="hc-url-error"
          >
            <input
              value={draft.url}
              placeholder="https://… / /help/guide"
              disabled={saving}
              inputMode="url"
              autoComplete="off"
              aria-invalid={Boolean(errors.url)}
              aria-describedby={errors.url ? 'hc-url-error' : undefined}
              onChange={event => setField('url', event.target.value)}
            />
          </FormField>
        )}
        <label className="hc-check">
          <input type="checkbox" checked={draftActive} disabled={saving} onChange={event => setDraftActive(event.target.checked)} />
          <span>{t('helpCenter.form.fields.active')}</span>
        </label>
        <fieldset className="hc-lang-fields">
          <legend className="visually-hidden">{t('helpCenter.form.languages')}</legend>
          {renderLangFields(kind)}
        </fieldset>
        {id === null && kind === 'faqs' && topics.length === 0 && <p className="hc-hint">{t('helpCenter.form.noTopics')}</p>}
      </form>
    )
  }

  const langTabs = LANGS.map(code => ({
    id: code,
    label: `${code.toUpperCase()}${Object.keys(errors).some(name => name.endsWith(code)) ? ' •' : ''}`,
  }))

  return <main className="admin-shell category-shell">
    <AdminHeader user={user} />
    <AdminSidebar active="help-center" className="category-sidebar" />

    <div className="admin-content category-content hc-content">
      <section className="category-heading">
        <div className="category-title">
          <span><Headphones /></span>
          <div>
            <h1>{t('helpCenter.title')}</h1>
            <p>{t('helpCenter.subtitle')}</p>
            {!canManage && <small>{t('helpCenter.readOnly')}</small>}
          </div>
        </div>
        <div className="hc-heading-actions">
          <a className="ap-ghost hc-preview" href="/help" target="_blank" rel="noopener noreferrer"><ExternalLink />{t('helpCenter.preview')}</a>
          {canManage && <button type="button" className="add-category" onClick={openCreate} disabled={tab === 'faqs' && topics.length === 0}><Plus />{t(`helpCenter.add.${tab}`)}</button>}
        </div>
      </section>

      <nav className="ap-tabs" aria-label={t('helpCenter.title')}>
        {KINDS.map(kind => (
          <button key={kind} type="button" className={tab === kind ? 'active' : ''} aria-current={tab === kind ? 'page' : undefined} onClick={() => switchTab(kind)}>
            {t(`helpCenter.tabs.${kind}`)}
            {loaded && !loadError && <span className="hc-count">{rowsByKind[kind].length}</span>}
          </button>
        ))}
      </nav>

      <form className="ap-filters category-filters" onSubmit={event => event.preventDefault()}>
        <div className="ap-filter-search wide">
          <span className="ap-filter-label">{t('helpCenter.search.label')}</span>
          <label className="category-search adm-search ap-search">
            <Search />
            <input value={query} onChange={event => setQuery(event.target.value)} placeholder={t(`helpCenter.search.placeholder.${tab}`)} aria-label={t('helpCenter.search.label')} />
          </label>
        </div>
        <AdminSelect label={t('helpCenter.status.label')} value={status} onChange={setStatus} options={statusOptions} />
        <div className="ap-filter-actions">
          <button type="button" className="ap-ghost category-reset" onClick={resetFilters} disabled={!filtering}>{t('helpCenter.reset')}</button>
        </div>
      </form>

      <section className="ap-card hc-table-card">
        <header className="ap-card-head">
          <h2>{t(`helpCenter.list.${tab}`)} <span className="hc-muted">({t('helpCenter.count', { count: rows.length })})</span></h2>
          {canManage && filtering && rows.length > 0 && <small className="hc-muted">{t('helpCenter.reorderDisabled')}</small>}
        </header>
        {!loaded ? (
          <SkeletonTable rows={6} cols={5} />
        ) : loadError ? (
          <div className="hc-state hc-state-error" role="alert">
            <CircleAlert aria-hidden="true" />
            <p>{loadError}</p>
            <button type="button" className="ap-primary" onClick={() => { setLoaded(false); setReloadKey(key => key + 1) }}>{t('helpCenter.retry')}</button>
          </div>
        ) : (
          <div className="ap-table-scroll">
            <table className="ap-table hc-table">
              <thead>
                <tr>
                  <th className="hc-col-order">{t('helpCenter.columns.order')}</th>
                  <th>{t(`helpCenter.columns.name.${tab}`)}</th>
                  {tab === 'faqs' && <th>{t('helpCenter.columns.topic')}</th>}
                  {tab === 'faqs' && <th>{t('helpCenter.columns.audience')}</th>}
                  {tab === 'faqs' && <th className="right">{t('helpCenter.columns.helpfulYes')}</th>}
                  {tab === 'faqs' && <th className="right">{t('helpCenter.columns.helpfulNo')}</th>}
                  {tab === 'faqs' && <th>{t('helpCenter.columns.lastReviewed')}</th>}
                  <th>{t('helpCenter.columns.status')}</th>
                  <th>{t('helpCenter.columns.updatedAt')}</th>
                  {canManage && <th className="right">{t('helpCenter.columns.actions')}</th>}
                </tr>
              </thead>
              <tbody>
                {rows.map(row => {
                  const index = allRows.findIndex(item => item.id === row.id)
                  const rowBusy = busyId === row.id
                  return (
                    <tr key={row.id} className={rowBusy ? 'is-busy' : undefined}>
                      <td className="hc-col-order nowrap">
                        <span className="hc-order">{row.sortOrder}</span>
                        {canManage && (
                          <span className="hc-move">
                            <button type="button" aria-label={t('helpCenter.actions.moveUp')} title={filtering ? t('helpCenter.reorderDisabled') : t('helpCenter.actions.moveUp')} disabled={filtering || !!busyId || index <= 0} onClick={() => void move(index, -1)}><ArrowUp /></button>
                            <button type="button" aria-label={t('helpCenter.actions.moveDown')} title={filtering ? t('helpCenter.reorderDisabled') : t('helpCenter.actions.moveDown')} disabled={filtering || !!busyId || index >= allRows.length - 1} onClick={() => void move(index, 1)}><ArrowDown /></button>
                          </span>
                        )}
                      </td>
                      <td className="hc-name"><b>{row.name}</b>{row.sub && <small>{row.sub}</small>}</td>
                      {tab === 'faqs' && <td>{row.topic || '-'}</td>}
                      {tab === 'faqs' && <td><i className={`ap-pill ${row.audience === 'MERCHANT' ? 'warn' : row.audience === 'CUSTOMER' ? 'info' : 'muted'}`}>{t(`helpCenter.audience.${row.audience}`, { defaultValue: row.audience })}</i></td>}
                      {tab === 'faqs' && <td className="right hc-num">{row.helpfulYes}</td>}
                      {tab === 'faqs' && <td className="right hc-num">{row.helpfulNo}</td>}
                      {tab === 'faqs' && <td className="nowrap">{row.lastReviewedAt ? formatDate(row.lastReviewedAt) : <span className="hc-muted">{t('helpCenter.neverReviewed')}</span>}</td>}
                      <td>
                        {canManage ? (
                          <button type="button" role="switch" aria-checked={row.active} aria-label={`${t('helpCenter.columns.status')}: ${row.name}`} className={`hc-switch ${row.active ? 'on' : ''}`} disabled={!!busyId} onClick={() => void toggleActive(row)}>
                            <span aria-hidden="true" />
                            <em>{row.active ? t('helpCenter.status.active') : t('helpCenter.status.inactive')}</em>
                          </button>
                        ) : (
                          <i className={`ap-pill ${row.active ? 'ok' : 'muted'}`}>{row.active ? t('helpCenter.status.active') : t('helpCenter.status.inactive')}</i>
                        )}
                      </td>
                      <td className="nowrap">{formatDate(row.updatedAt)}</td>
                      {canManage && (
                        <td className="right nowrap">
                          <span className="hc-row-actions">
                            {tab === 'faqs' && <button type="button" aria-label={`${t('helpCenter.actions.markReviewed')}: ${row.name}`} title={t('helpCenter.actions.markReviewed')} disabled={!!busyId} onClick={() => void markReviewed(row)}><ShieldCheck /></button>}
                            <button type="button" aria-label={`${t('helpCenter.actions.edit')}: ${row.name}`} title={t('helpCenter.actions.edit')} onClick={() => openEdit(tab, row.id)}><Pencil /></button>
                            <button type="button" className="danger" aria-label={`${t('helpCenter.actions.delete')}: ${row.name}`} title={t('helpCenter.actions.delete')} onClick={() => { setDeleteError(''); setDeleting({ kind: tab, row }) }}><Trash2 /></button>
                          </span>
                        </td>
                      )}
                    </tr>
                  )
                })}
                {rows.length === 0 && (
                  <tr>
                    <td colSpan={columnsCount} className="ap-empty">
                      {filtering ? t('helpCenter.emptyFiltered') : t(`helpCenter.empty.${tab}`)}
                      {filtering && <> <button type="button" className="ap-link" onClick={resetFilters}>{t('helpCenter.reset')}</button></>}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        )}
      </section>
    </div>

    {editing && (
      <AdminSidePanel
        variant="drawer"
        open
        title={t(`helpCenter.form.title.${editing.id ? 'edit' : 'create'}.${editing.kind}`)}
        onClose={closeForm}
        tabs={langTabs}
        activeTab={lang}
        onTabChange={id => setLang(id as Lang)}
        className="hc-drawer"
        footer={<>
          <button type="button" className="asp-secondary" onClick={closeForm} disabled={saving}>{t('helpCenter.form.cancel')}</button>
          <button type="submit" form="hc-form" className="asp-primary" disabled={saving || !canManage}>{saving ? t('helpCenter.form.saving') : t('helpCenter.form.save')}</button>
        </>}
      >
        {renderForm()}
      </AdminSidePanel>
    )}

    {deleting && (
      <ConfirmDialog
        title={t(`helpCenter.delete.title.${deleting.kind}`)}
        message={t('helpCenter.delete.body', { name: deleting.row.name })}
        cancelLabel={t('helpCenter.form.cancel')}
        confirmLabel={deleteBusy ? t('helpCenter.delete.deleting') : t('helpCenter.delete.confirm')}
        danger
        confirmDisabled={deleteBusy}
        onCancel={() => { if (!deleteBusy) setDeleting(null) }}
        onConfirm={() => void confirmDelete()}
      >
        {deletingTopicFaqs > 0 && <p className="hc-delete-warning">{t('helpCenter.delete.topicWarning', { count: deletingTopicFaqs })}</p>}
        {deleteError && <p className="ap-error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    )}

    {notice && <div className="admin-toast" role="status">{notice}</div>}
  </main>
}
