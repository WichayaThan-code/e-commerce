import { useCallback, useEffect, useMemo, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import {
  ArrowDown, ArrowUp, ChevronLeft, ChevronRight, CircleAlert, ExternalLink, ImagePlus, Newspaper, Pencil, Plus, Search, Trash2,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import AdminSelect from '../components/admin/AdminSelect'
import AdminSidePanel from '../components/admin/AdminSidePanel'
import ArticleMarkdown from '../components/articles/ArticleMarkdown'
import ConfirmDialog from '../components/form/ConfirmDialog'
import FormField from '../components/form/FormField'
import FormSelect from '../components/form/FormSelect'
import FormTextarea from '../components/form/FormTextarea'
import { SkeletonTable } from '../components/feedback/Skeleton'
import { useAuth } from '../contexts/AuthContext'
import { useLanguage } from '../contexts/LanguageContext'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { usePermissions } from '../hooks/usePermissions'
import {
  ApiError,
  articleAdminApi,
  mediaUrl,
  type AdminArticle,
  type AdminArticleCategory,
  type AdminArticleSummary,
  type ArticleCategoryRequest,
  type ArticleRequest,
  type ArticleStatus,
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
import '../styles/blog.css'
import '../styles/admin-articles.css'

type Tab = 'articles' | 'categories'
type Lang = 'Th' | 'En' | 'Zh'
type EditorTab = Lang | 'Settings'
type FieldErrors = Record<string, string>
type BodyMode = 'write' | 'preview'

type ArticleDraft = {
  slug: string
  categoryId: string
  audience: string
  tags: string
  readingMinutes: string
  titleTh: string; titleEn: string; titleZh: string
  excerptTh: string; excerptEn: string; excerptZh: string
  bodyMdTh: string; bodyMdEn: string; bodyMdZh: string
  metaTitle: string
  metaDescription: string
  relatedShopId: string
  relatedProductIds: string
  featured: boolean
  publishAt: string
}

type CategoryDraft = {
  slug: string
  active: boolean
  nameTh: string; nameEn: string; nameZh: string
  descriptionTh: string; descriptionEn: string; descriptionZh: string
}

const LANGS: Lang[] = ['Th', 'En', 'Zh']
const AUDIENCES = ['ALL', 'CUSTOMER', 'MERCHANT'] as const
const STATUSES: ArticleStatus[] = ['DRAFT', 'PUBLISHED', 'ARCHIVED']
const SLUG_PATTERN = /^[a-z0-9-]+$/
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const PAGE_SIZE = 10
const SEARCH_DEBOUNCE_MS = 300
const LIMITS = {
  title: 255, excerpt: 500, body: 200000, metaTitle: 120, metaDescription: 200, name: 150, description: 500,
  tags: 10, tag: 50, products: 10, minutes: 240, slug: 150,
}
const META_TITLE_HINT = 60
const META_DESCRIPTION_HINT = 160
const COVER_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const COVER_MAX_BYTES = 5 * 1024 * 1024

const emptyArticleDraft = (): ArticleDraft => ({
  slug: '', categoryId: '', audience: 'ALL', tags: '', readingMinutes: '',
  titleTh: '', titleEn: '', titleZh: '', excerptTh: '', excerptEn: '', excerptZh: '',
  bodyMdTh: '', bodyMdEn: '', bodyMdZh: '', metaTitle: '', metaDescription: '',
  relatedShopId: '', relatedProductIds: '', featured: false, publishAt: '',
})

const emptyCategoryDraft = (): CategoryDraft => ({
  slug: '', active: true, nameTh: '', nameEn: '', nameZh: '', descriptionTh: '', descriptionEn: '', descriptionZh: '',
})

const text = (value: string | null | undefined) => value ?? ''

function slugify(value: string): string {
  return value.toLowerCase().normalize('NFKD').replace(/[^a-z0-9\s-]/g, '').trim().replace(/\s+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')
}

const autoMinutes = (body: string) => Math.max(1, Math.ceil(body.length / 1000))

const parseTags = (value: string) => [...new Set(value.split(/[,\n]/).map(item => item.trim()).filter(Boolean))]
const parseIds = (value: string) => [...new Set(value.split(/[\s,]+/).map(item => item.trim()).filter(Boolean))]

function toLocalInput(value: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function draftFromArticle(article: AdminArticle): ArticleDraft {
  return {
    slug: article.slug, categoryId: text(article.categoryId), audience: article.audience || 'ALL',
    tags: (article.tags ?? []).join(', '), readingMinutes: String(article.readingMinutes ?? ''),
    titleTh: text(article.titleTh), titleEn: text(article.titleEn), titleZh: text(article.titleZh),
    excerptTh: text(article.excerptTh), excerptEn: text(article.excerptEn), excerptZh: text(article.excerptZh),
    bodyMdTh: text(article.bodyMdTh), bodyMdEn: text(article.bodyMdEn), bodyMdZh: text(article.bodyMdZh),
    metaTitle: text(article.metaTitle), metaDescription: text(article.metaDescription),
    relatedShopId: article.relatedShopId == null ? '' : String(article.relatedShopId),
    relatedProductIds: (article.relatedProductIds ?? []).join('\n'),
    featured: article.featured, publishAt: article.status === 'PUBLISHED' ? toLocalInput(article.publishedAt) : '',
  }
}

type EditorState = { id: string | null; status: ArticleStatus | null; slug: string; coverUrl: string | null }

export default function AdminArticlesPage() {
  const { t } = useTranslation('admin')
  const { localeTag } = useLanguage()
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canManage = can('PLATFORM_CONTENT_MANAGE')

  const [tab, setTab] = useState<Tab>('articles')
  const [notice, setNotice] = useState('')
  const [busyId, setBusyId] = useState<string | null>(null)

  // categories (needed by both tabs)
  const [categories, setCategories] = useState<AdminArticleCategory[]>([])
  const [categoriesLoaded, setCategoriesLoaded] = useState(false)
  const [categoriesError, setCategoriesError] = useState('')
  const [categoryReload, setCategoryReload] = useState(0)

  // articles list
  const [query, setQuery] = useState('')
  const debouncedQuery = useDebouncedValue(query.trim(), SEARCH_DEBOUNCE_MS)
  const [statusFilter, setStatusFilter] = useState('')
  const [categoryFilter, setCategoryFilter] = useState('')
  const [page, setPage] = useState(0)
  const [rows, setRows] = useState<AdminArticleSummary[]>([])
  const [total, setTotal] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [listReload, setListReload] = useState(0)

  // article editor
  const [editor, setEditor] = useState<EditorState | null>(null)
  const [editorLoading, setEditorLoading] = useState(false)
  const [draft, setDraft] = useState<ArticleDraft>(emptyArticleDraft)
  const [snapshot, setSnapshot] = useState('')
  const [editorTab, setEditorTab] = useState<EditorTab>('Th')
  const [bodyMode, setBodyMode] = useState<BodyMode>('write')
  const [slugEdited, setSlugEdited] = useState(false)
  const [minutesEdited, setMinutesEdited] = useState(false)
  const [fallbackSlug, setFallbackSlug] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState<'' | 'save' | 'publish' | 'archive'>('')
  const [coverFile, setCoverFile] = useState<File | null>(null)
  const [coverPreview, setCoverPreview] = useState('')
  const [coverRemoved, setCoverRemoved] = useState(false)
  const [discardAsk, setDiscardAsk] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  // category editor
  const [catEditing, setCatEditing] = useState<{ id: string | null } | null>(null)
  const [catDraft, setCatDraft] = useState<CategoryDraft>(emptyCategoryDraft)
  const [catLang, setCatLang] = useState<Lang>('Th')
  const [catErrors, setCatErrors] = useState<FieldErrors>({})
  const [catFormError, setCatFormError] = useState('')
  const [catSaving, setCatSaving] = useState(false)

  // delete
  const [deleting, setDeleting] = useState<{ kind: Tab; id: string; name: string } | null>(null)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteError, setDeleteError] = useState('')

  const notify = useCallback((message: string) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2400)
  }, [])
  const errorText = useCallback((error: unknown) => (error instanceof ApiError && error.message ? error.message : t('articlesCms.toast.actionFailed')), [t])

  const formatDate = useCallback((value: string | null) => {
    if (!value) return '-'
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return '-'
    return new Intl.DateTimeFormat(localeTag, { dateStyle: 'medium', timeStyle: 'short' }).format(date)
  }, [localeTag])

  /* ------------------------------------------------------------------ data */
  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setCategoriesError('')
    articleAdminApi.categories()
      .then(items => { if (!cancelled) { setCategories(items); setCategoriesLoaded(true) } })
      .catch(error => { if (!cancelled) { setCategoriesError(error instanceof ApiError && error.message ? error.message : t('articlesCms.loadFailed')); setCategoriesLoaded(true) } })
    return () => { cancelled = true }
  }, [allowed, categoryReload, t])

  useEffect(() => { setPage(0) }, [debouncedQuery, statusFilter, categoryFilter])

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setListLoading(true)
    setListError('')
    articleAdminApi.list({
      status: statusFilter || undefined, category: categoryFilter || undefined, q: debouncedQuery || undefined, page, size: PAGE_SIZE,
    })
      .then(result => {
        if (cancelled) return
        setRows(result.items)
        setTotal(result.totalElements)
        setTotalPages(result.totalPages)
      })
      .catch(error => { if (!cancelled) setListError(error instanceof ApiError && error.message ? error.message : t('articlesCms.loadFailed')) })
      .finally(() => { if (!cancelled) setListLoading(false) })
    return () => { cancelled = true }
  }, [allowed, statusFilter, categoryFilter, debouncedQuery, page, listReload, t])

  useEffect(() => () => { if (coverPreview) URL.revokeObjectURL(coverPreview) }, [coverPreview])

  const categoryName = useCallback((id: string | null, slug: string | null) => {
    const item = categories.find(row => (id ? row.id === id : row.slug === slug))
    return item ? item.nameTh : (slug ?? '-')
  }, [categories])

  const dirty = useMemo(() => editor !== null && !editorLoading && JSON.stringify(draft) !== snapshot || coverFile !== null || coverRemoved, [editor, editorLoading, draft, snapshot, coverFile, coverRemoved])

  if (loading) return <main className="auth-loading">{t('articlesCms.checkingAccess')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const filtering = query.trim() !== '' || statusFilter !== '' || categoryFilter !== ''
  const resetFilters = () => { setQuery(''); setStatusFilter(''); setCategoryFilter('') }
  const switchTab = (next: Tab) => { setTab(next); resetFilters() }

  /* --------------------------------------------------------------- editor */
  const resetCover = () => {
    setCoverFile(null)
    setCoverPreview('')
    setCoverRemoved(false)
    if (fileInput.current) fileInput.current.value = ''
  }
  const openEditor = (next: ArticleDraft, state: EditorState) => {
    setDraft(next)
    setSnapshot(JSON.stringify(next))
    setEditor(state)
    setEditorTab('Th')
    setBodyMode('write')
    setErrors({})
    setFormError('')
    setSlugEdited(state.id !== null)
    setMinutesEdited(state.id !== null)
    setFallbackSlug(`article-${Date.now().toString(36)}`)
    resetCover()
  }
  const openCreate = () => {
    setEditorLoading(false)
    openEditor(emptyArticleDraft(), { id: null, status: null, slug: '', coverUrl: null })
  }
  const openEdit = async (id: string) => {
    setEditorLoading(true)
    openEditor(emptyArticleDraft(), { id, status: null, slug: '', coverUrl: null })
    try {
      const article = await articleAdminApi.get(id)
      openEditor(draftFromArticle(article), { id: article.id, status: article.status, slug: article.slug, coverUrl: article.coverUrl })
    } catch (error) {
      setEditor(null)
      notify(errorText(error))
    } finally {
      setEditorLoading(false)
    }
  }
  const requestClose = () => {
    if (saving) return
    if (dirty && canManage) setDiscardAsk(true)
    else setEditor(null)
  }

  const setField = <K extends keyof ArticleDraft>(name: K, value: ArticleDraft[K]) => {
    setDraft(current => {
      const next = { ...current, [name]: value }
      if (name === 'bodyMdTh' && !minutesEdited) next.readingMinutes = String(autoMinutes(String(value)))
      if ((name === 'titleEn' || name === 'titleTh') && !slugEdited) {
        const base = slugify(name === 'titleEn' ? String(value) : next.titleEn) || slugify(name === 'titleTh' ? String(value) : next.titleTh)
        next.slug = base
      }
      return next
    })
    setErrors(current => {
      if (!current[name as string]) return current
      const { [name as string]: _removed, ...rest } = current
      return rest
    })
  }

  const pickCover = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return
    if (!COVER_TYPES.includes(file.type)) { setErrors(current => ({ ...current, cover: t('articlesCms.errors.coverType') })); return }
    if (file.size > COVER_MAX_BYTES) { setErrors(current => ({ ...current, cover: t('articlesCms.errors.coverSize') })); return }
    setErrors(current => { const { cover: _removed, ...rest } = current; return rest })
    setCoverFile(file)
    setCoverRemoved(false)
    setCoverPreview(URL.createObjectURL(file))
  }
  const removeCover = () => {
    if (fileInput.current) fileInput.current.value = ''
    setCoverFile(null)
    setCoverPreview('')
    setCoverRemoved(true)
  }

  const validate = (): FieldErrors => {
    const found: FieldErrors = {}
    const required = t('articlesCms.errors.required')
    const tooLong = (max: number) => t('articlesCms.errors.tooLong', { max })
    const slug = draft.slug.trim() || fallbackSlug
    if (!draft.titleTh.trim()) found.titleTh = required
    else if (draft.titleTh.length > LIMITS.title) found.titleTh = tooLong(LIMITS.title)
    if (!draft.bodyMdTh.trim()) found.bodyMdTh = required
    else if (draft.bodyMdTh.length > LIMITS.body) found.bodyMdTh = tooLong(LIMITS.body)
    if (!SLUG_PATTERN.test(slug) || slug.length > LIMITS.slug) found.slug = t('articlesCms.errors.slugInvalid')
    for (const lang of ['En', 'Zh'] as const) {
      if (draft[`title${lang}`].length > LIMITS.title) found[`title${lang}`] = tooLong(LIMITS.title)
      if (draft[`bodyMd${lang}`].length > LIMITS.body) found[`bodyMd${lang}`] = tooLong(LIMITS.body)
    }
    for (const lang of LANGS) {
      if (draft[`excerpt${lang}`].length > LIMITS.excerpt) found[`excerpt${lang}`] = tooLong(LIMITS.excerpt)
    }
    const tags = parseTags(draft.tags)
    if (tags.length > LIMITS.tags) found.tags = t('articlesCms.errors.tagsTooMany', { max: LIMITS.tags })
    else if (tags.some(tag => tag.length > LIMITS.tag)) found.tags = t('articlesCms.errors.tagTooLong', { max: LIMITS.tag })
    if (draft.readingMinutes.trim()) {
      const minutes = Number(draft.readingMinutes)
      if (!Number.isInteger(minutes) || minutes < 1 || minutes > LIMITS.minutes) found.readingMinutes = t('articlesCms.errors.minutesInvalid', { max: LIMITS.minutes })
    }
    if (draft.metaTitle.length > LIMITS.metaTitle) found.metaTitle = tooLong(LIMITS.metaTitle)
    if (draft.metaDescription.length > LIMITS.metaDescription) found.metaDescription = tooLong(LIMITS.metaDescription)
    if (draft.relatedShopId.trim() && !/^[1-9]\d{0,17}$/.test(draft.relatedShopId.trim())) found.relatedShopId = t('articlesCms.errors.shopInvalid')
    const ids = parseIds(draft.relatedProductIds)
    if (ids.length > LIMITS.products) found.relatedProductIds = t('articlesCms.errors.productsTooMany', { max: LIMITS.products })
    else if (ids.some(id => !UUID_PATTERN.test(id))) found.relatedProductIds = t('articlesCms.errors.productInvalid')
    if (draft.publishAt && Number.isNaN(new Date(draft.publishAt).getTime())) found.publishAt = t('articlesCms.errors.invalid')
    return found
  }

  const buildRequest = (): ArticleRequest => {
    const value = (name: keyof ArticleDraft) => String(draft[name]).trim()
    const minutes = draft.readingMinutes.trim()
    return {
      slug: draft.slug.trim() || fallbackSlug,
      categoryId: draft.categoryId || null,
      audience: draft.audience,
      featured: draft.featured,
      readingMinutes: minutes ? Number(minutes) : undefined,
      titleTh: value('titleTh'), titleEn: value('titleEn'), titleZh: value('titleZh'),
      excerptTh: value('excerptTh'), excerptEn: value('excerptEn'), excerptZh: value('excerptZh'),
      bodyMdTh: draft.bodyMdTh, bodyMdEn: draft.bodyMdEn, bodyMdZh: draft.bodyMdZh,
      tags: parseTags(draft.tags),
      metaTitle: value('metaTitle'), metaDescription: value('metaDescription'),
      relatedShopId: draft.relatedShopId.trim() ? Number(draft.relatedShopId.trim()) : null,
      relatedProductIds: parseIds(draft.relatedProductIds),
    }
  }

  const tabWithErrors = (found: FieldErrors): EditorTab | null => {
    const names = Object.keys(found)
    const lang = LANGS.find(code => names.some(name => name.endsWith(code)))
    if (lang) return lang
    return names.length > 0 ? 'Settings' : null
  }

  const applyApiErrors = (error: unknown) => {
    if (error instanceof ApiError && error.fieldErrors.length > 0) {
      const fromApi: FieldErrors = {}
      for (const item of error.fieldErrors) if (item.field && !fromApi[item.field]) fromApi[item.field] = item.message || t('articlesCms.errors.invalid')
      setErrors(fromApi)
      const target = tabWithErrors(fromApi)
      if (target) setEditorTab(target)
    } else if (error instanceof ApiError && error.code === 'ARTICLE_SLUG_TAKEN') {
      setErrors({ slug: error.message })
      setEditorTab('Settings')
    }
    setFormError(errorText(error))
  }

  const upsertRow = (article: AdminArticle) => {
    setRows(current => current.map(row => row.id === article.id ? {
      ...row, slug: article.slug, status: article.status, audience: article.audience, featured: article.featured,
      categoryId: article.categoryId, categorySlug: article.categorySlug, titleTh: article.titleTh, titleEn: article.titleEn,
      titleZh: article.titleZh, coverUrl: article.coverUrl, readingMinutes: article.readingMinutes, tags: article.tags,
      publishedAt: article.publishedAt, updatedAt: article.updatedAt,
    } : row))
  }

  /** Saves fields, then applies the staged cover change; returns the latest server copy. */
  const persist = async (): Promise<AdminArticle> => {
    const body = buildRequest()
    let saved = editor?.id ? await articleAdminApi.update(editor.id, body) : await articleAdminApi.create(body)
    if (coverFile) saved = await articleAdminApi.uploadCover(saved.id, coverFile)
    else if (coverRemoved && saved.coverUrl) saved = await articleAdminApi.removeCover(saved.id)
    return saved
  }

  const finish = (saved: AdminArticle, message: string, close = true) => {
    upsertRow(saved)
    setListReload(key => key + 1)
    if (close) setEditor(null)
    else {
      setEditor({ id: saved.id, status: saved.status, slug: saved.slug, coverUrl: saved.coverUrl })
      const next = draftFromArticle(saved)
      setDraft(next)
      setSnapshot(JSON.stringify(next))
      resetCover()
    }
    notify(message)
  }

  const submit = async (mode: 'save' | 'publish', event?: FormEvent) => {
    event?.preventDefault()
    if (!editor || saving || !canManage || editorLoading) return
    const found = validate()
    if (Object.keys(found).length > 0) {
      setErrors(found)
      setFormError('')
      const target = tabWithErrors(found)
      if (target) setEditorTab(target)
      return
    }
    setSaving(mode)
    setFormError('')
    try {
      let saved = await persist()
      if (mode === 'publish') {
        const publishedAt = draft.publishAt ? new Date(draft.publishAt).toISOString() : undefined
        saved = await articleAdminApi.setStatus(saved.id, 'PUBLISHED', publishedAt)
        finish(saved, t('articlesCms.toast.published'))
      } else {
        finish(saved, t(editor.id ? 'articlesCms.toast.updated' : 'articlesCms.toast.created'))
      }
    } catch (error) {
      applyApiErrors(error)
      setListReload(key => key + 1)
    } finally {
      setSaving('')
    }
  }

  const changeStatus = async (target: ArticleStatus) => {
    if (!editor?.id || saving || !canManage) return
    setSaving('archive')
    setFormError('')
    try {
      const saved = await articleAdminApi.setStatus(editor.id, target)
      finish(saved, t(target === 'ARCHIVED' ? 'articlesCms.toast.archived' : 'articlesCms.toast.restored'), false)
    } catch (error) {
      setFormError(errorText(error))
    } finally {
      setSaving('')
    }
  }

  const openPreview = () => {
    if (!editor?.id) return
    window.open(`/articles/${encodeURIComponent(editor.slug)}?preview=1&id=${encodeURIComponent(editor.id)}`, '_blank', 'noopener,noreferrer')
  }

  /* ------------------------------------------------------------ row actions */
  const toggleFeatured = async (row: AdminArticleSummary) => {
    if (!canManage || busyId) return
    setBusyId(row.id)
    try {
      const saved = await articleAdminApi.setFeatured(row.id, !row.featured)
      upsertRow(saved)
      notify(t(saved.featured ? 'articlesCms.toast.featured' : 'articlesCms.toast.unfeatured'))
    } catch (error) {
      notify(errorText(error))
    } finally {
      setBusyId(null)
    }
  }

  const toggleCategoryActive = async (item: AdminArticleCategory) => {
    if (!canManage || busyId) return
    setBusyId(item.id)
    try {
      const saved = await articleAdminApi.setCategoryActive(item.id, !item.active)
      setCategories(current => current.map(row => row.id === saved.id ? saved : row))
      notify(t(saved.active ? 'articlesCms.toast.activated' : 'articlesCms.toast.deactivated'))
    } catch (error) {
      notify(errorText(error))
    } finally {
      setBusyId(null)
    }
  }

  const moveCategory = async (index: number, direction: -1 | 1) => {
    if (!canManage || busyId) return
    const target = index + direction
    if (target < 0 || target >= categories.length) return
    const ids = categories.map(row => row.id)
    ;[ids[index], ids[target]] = [ids[target], ids[index]]
    setBusyId(categories[index].id)
    try {
      setCategories(await articleAdminApi.reorderCategories(ids))
      notify(t('articlesCms.toast.reordered'))
    } catch (error) {
      notify(errorText(error))
    } finally {
      setBusyId(null)
    }
  }

  const confirmDelete = async () => {
    if (!deleting || deleteBusy) return
    setDeleteBusy(true)
    setDeleteError('')
    try {
      if (deleting.kind === 'articles') {
        await articleAdminApi.remove(deleting.id)
        if (rows.length === 1 && page > 0) setPage(page - 1)
        else setListReload(key => key + 1)
        setCategoryReload(key => key + 1)
      } else {
        await articleAdminApi.deleteCategory(deleting.id)
        setCategories(current => current.filter(row => row.id !== deleting.id))
        if (categoryFilter && !categories.some(row => row.slug === categoryFilter && row.id !== deleting.id)) setCategoryFilter('')
      }
      setDeleting(null)
      notify(t('articlesCms.toast.deleted'))
    } catch (error) {
      setDeleteError(error instanceof ApiError && error.code === 'ARTICLE_CATEGORY_IN_USE' ? t('articlesCms.delete.categoryInUse') : errorText(error))
    } finally {
      setDeleteBusy(false)
    }
  }

  /* -------------------------------------------------------- category editor */
  const openCategoryCreate = () => {
    setCatDraft(emptyCategoryDraft())
    setCatEditing({ id: null })
    setCatLang('Th')
    setCatErrors({})
    setCatFormError('')
  }
  const openCategoryEdit = (item: AdminArticleCategory) => {
    setCatDraft({
      slug: item.slug, active: item.active,
      nameTh: text(item.nameTh), nameEn: text(item.nameEn), nameZh: text(item.nameZh),
      descriptionTh: text(item.descriptionTh), descriptionEn: text(item.descriptionEn), descriptionZh: text(item.descriptionZh),
    })
    setCatEditing({ id: item.id })
    setCatLang('Th')
    setCatErrors({})
    setCatFormError('')
  }
  const setCatField = <K extends keyof CategoryDraft>(name: K, value: CategoryDraft[K]) => {
    setCatDraft(current => {
      const next = { ...current, [name]: value }
      if ((name === 'nameEn') && catEditing?.id === null && (current.slug === '' || current.slug === slugify(current.nameEn))) next.slug = slugify(String(value))
      return next
    })
    setCatErrors(current => {
      if (!current[name as string]) return current
      const { [name as string]: _removed, ...rest } = current
      return rest
    })
  }
  const submitCategory = async (event: FormEvent) => {
    event.preventDefault()
    if (!catEditing || catSaving || !canManage) return
    const found: FieldErrors = {}
    const required = t('articlesCms.errors.required')
    const slug = catDraft.slug.trim()
    if (!catDraft.nameTh.trim()) found.nameTh = required
    if (!slug) found.slug = required
    else if (!SLUG_PATTERN.test(slug)) found.slug = t('articlesCms.errors.slugInvalid')
    for (const lang of LANGS) {
      if (catDraft[`name${lang}`].length > LIMITS.name) found[`name${lang}`] = t('articlesCms.errors.tooLong', { max: LIMITS.name })
      if (catDraft[`description${lang}`].length > LIMITS.description) found[`description${lang}`] = t('articlesCms.errors.tooLong', { max: LIMITS.description })
    }
    if (Object.keys(found).length > 0) {
      setCatErrors(found)
      const lang = LANGS.find(code => Object.keys(found).some(name => name.endsWith(code)))
      if (lang && Object.keys(found).every(name => LANGS.some(code => name.endsWith(code)))) setCatLang(lang)
      return
    }
    setCatSaving(true)
    setCatFormError('')
    const value = (name: keyof CategoryDraft) => String(catDraft[name]).trim()
    const body: ArticleCategoryRequest = {
      slug, active: catDraft.active,
      nameTh: value('nameTh'), nameEn: value('nameEn'), nameZh: value('nameZh'),
      descriptionTh: value('descriptionTh'), descriptionEn: value('descriptionEn'), descriptionZh: value('descriptionZh'),
    }
    try {
      const saved = catEditing.id ? await articleAdminApi.updateCategory(catEditing.id, body) : await articleAdminApi.createCategory(body)
      setCategories(current => catEditing.id ? current.map(row => row.id === saved.id ? saved : row) : [...current, saved])
      setCatEditing(null)
      notify(t(catEditing.id ? 'articlesCms.toast.updated' : 'articlesCms.toast.created'))
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors.length > 0) {
        const fromApi: FieldErrors = {}
        for (const item of error.fieldErrors) if (item.field && !fromApi[item.field]) fromApi[item.field] = item.message || t('articlesCms.errors.invalid')
        setCatErrors(fromApi)
      } else if (error instanceof ApiError && error.code === 'ARTICLE_CATEGORY_SLUG_TAKEN') {
        setCatErrors({ slug: error.message })
      }
      setCatFormError(errorText(error))
    } finally {
      setCatSaving(false)
    }
  }

  /* ----------------------------------------------------------------- render */
  const readOnly = !canManage
  const statusPill = (row: { status: ArticleStatus; publishedAt: string | null }) => {
    const scheduled = row.status === 'PUBLISHED' && row.publishedAt && new Date(row.publishedAt).getTime() > Date.now()
    if (scheduled) return <i className="ap-pill info">{t('articlesCms.status.SCHEDULED')}</i>
    const tone = row.status === 'PUBLISHED' ? 'ok' : row.status === 'ARCHIVED' ? 'warn' : 'muted'
    return <i className={`ap-pill ${tone}`}>{t(`articlesCms.status.${row.status}`)}</i>
  }
  const statusOptions = [{ value: '', label: t('articlesCms.filters.allStatuses') }, ...STATUSES.map(code => ({ value: code, label: t(`articlesCms.status.${code}`) }))]
  const categoryOptions = [{ value: '', label: t('articlesCms.filters.allCategories') }, ...categories.map(item => ({ value: item.slug, label: item.nameTh }))]
  const categoryEditorTabs = LANGS.map(code => ({
    id: code,
    label: `${code.toUpperCase()}${Object.keys(catErrors).some(name => name.endsWith(code)) ? ' •' : ''}`,
  }))
  const editorTabs = [
    ...LANGS.map(code => ({ id: code, label: `${code.toUpperCase()}${Object.keys(errors).some(name => name.endsWith(code)) ? ' •' : ''}` })),
    { id: 'Settings', label: `${t('articlesCms.editor.tabs.settings')}${Object.keys(errors).some(name => !LANGS.some(code => name.endsWith(code))) ? ' •' : ''}` },
  ]

  const counter = (value: string, hint: number, max: number) => (
    <span className={`ar-counter ${value.length > hint ? 'over' : ''}`.trim()}>{t('articlesCms.editor.counter', { count: value.length, max: hint })}{value.length > max ? ` · ${t('articlesCms.errors.tooLong', { max })}` : ''}</span>
  )

  const renderLangPanel = (lang: Lang) => {
    const isTh = lang === 'Th'
    const langLabel = t(`articlesCms.editor.lang.${lang.toLowerCase()}`)
    const titleName = `title${lang}` as keyof ArticleDraft
    const excerptName = `excerpt${lang}` as keyof ArticleDraft
    const bodyName = `bodyMd${lang}` as keyof ArticleDraft
    const bodyValue = String(draft[bodyName])
    return (
      <>
        <FormField
          label={<>{t('articlesCms.editor.fields.title')} ({langLabel}){isTh && <em className="hc-required"> *</em>}</>}
          error={errors[titleName]}
          errorId={`ar-${titleName}-error`}
        >
          <input
            value={String(draft[titleName])}
            maxLength={LIMITS.title}
            disabled={readOnly || !!saving}
            aria-invalid={Boolean(errors[titleName])}
            aria-describedby={errors[titleName] ? `ar-${titleName}-error` : undefined}
            onChange={event => setField(titleName, event.target.value)}
          />
        </FormField>
        <FormTextarea
          label={<>{t('articlesCms.editor.fields.excerpt')} ({langLabel})</>}
          rows={3}
          maxLength={LIMITS.excerpt}
          value={String(draft[excerptName])}
          error={errors[excerptName]}
          errorId={`ar-${excerptName}-error`}
          disabled={readOnly || !!saving}
          onChange={event => setField(excerptName, event.target.value)}
        />
        <div className="ar-body-tabs" role="group" aria-label={t('articlesCms.editor.fields.body')}>
          <button type="button" aria-pressed={bodyMode === 'write'} onClick={() => setBodyMode('write')}>{t('articlesCms.editor.write')}</button>
          <button type="button" aria-pressed={bodyMode === 'preview'} onClick={() => setBodyMode('preview')}>{t('articlesCms.editor.previewTab')}</button>
        </div>
        {bodyMode === 'write' ? (
          <FormTextarea
            label={<>{t('articlesCms.editor.fields.body')} ({langLabel}){isTh && <em className="hc-required"> *</em>}</>}
            rows={16}
            className="ar-mono"
            value={bodyValue}
            error={errors[bodyName]}
            errorId={`ar-${bodyName}-error`}
            disabled={readOnly || !!saving}
            spellCheck={false}
            onChange={event => setField(bodyName, event.target.value)}
          />
        ) : (
          <div className="ar-preview blog-page">
            {bodyValue.trim() ? <ArticleMarkdown markdown={bodyValue} /> : <p className="ar-preview-empty">{t('articlesCms.editor.previewEmpty')}</p>}
          </div>
        )}
        {!isTh && <p className="hc-hint">{t('articlesCms.editor.fallbackHint')}</p>}
      </>
    )
  }

  const coverSrc = coverPreview || (!coverRemoved && editor?.coverUrl ? mediaUrl(editor.coverUrl) : '')

  const renderSettings = () => {
    const minutesAuto = !minutesEdited
    return (
      <>
        <div className="ar-form-grid">
          <FormField
            label={<>{t('articlesCms.editor.fields.slug')}<em className="hc-required"> *</em></>}
            error={errors.slug}
            errorId="ar-slug-error"
            className="span-2"
          >
            <input
              value={draft.slug}
              placeholder={fallbackSlug}
              maxLength={LIMITS.slug}
              disabled={readOnly || !!saving}
              autoComplete="off"
              aria-invalid={Boolean(errors.slug)}
              aria-describedby={errors.slug ? 'ar-slug-error' : undefined}
              onChange={event => { setSlugEdited(true); setField('slug', event.target.value.toLowerCase()) }}
            />
          </FormField>
          <FormSelect label={t('articlesCms.editor.fields.category')} value={draft.categoryId} disabled={readOnly || !!saving} onChange={event => setField('categoryId', event.target.value)}>
            <option value="">{t('articlesCms.editor.fields.categoryNone')}</option>
            {categories.map(item => <option key={item.id} value={item.id}>{item.nameTh}{item.active ? '' : ` (${t('articlesCms.active.off')})`}</option>)}
          </FormSelect>
          <FormSelect label={t('articlesCms.editor.fields.audience')} value={draft.audience} disabled={readOnly || !!saving} onChange={event => setField('audience', event.target.value)}>
            {AUDIENCES.map(code => <option key={code} value={code}>{t(`articlesCms.audience.${code}`)}</option>)}
          </FormSelect>
          <FormField label={t('articlesCms.editor.fields.tags')} error={errors.tags} errorId="ar-tags-error" className="span-2" aside={<span className="ar-counter">{t('articlesCms.editor.fields.tagsHint', { count: parseTags(draft.tags).length, max: LIMITS.tags })}</span>}>
            <input
              value={draft.tags}
              disabled={readOnly || !!saving}
              placeholder="seo, tips"
              aria-invalid={Boolean(errors.tags)}
              aria-describedby={errors.tags ? 'ar-tags-error' : undefined}
              onChange={event => setField('tags', event.target.value)}
            />
          </FormField>
          <FormField
            label={t('articlesCms.editor.fields.readingMinutes')}
            error={errors.readingMinutes}
            errorId="ar-minutes-error"
            aside={<span className="ar-counter">{minutesAuto ? t('articlesCms.editor.fields.readingAuto') : t('articlesCms.editor.fields.readingManual')}</span>}
          >
            <input
              type="number"
              min={1}
              max={LIMITS.minutes}
              value={draft.readingMinutes}
              disabled={readOnly || !!saving}
              aria-invalid={Boolean(errors.readingMinutes)}
              aria-describedby={errors.readingMinutes ? 'ar-minutes-error' : undefined}
              onChange={event => { setMinutesEdited(event.target.value !== ''); setField('readingMinutes', event.target.value) }}
            />
          </FormField>
          <label className="hc-check ar-featured">
            <input type="checkbox" checked={draft.featured} disabled={readOnly || !!saving} onChange={event => setField('featured', event.target.checked)} />
            <span>{t('articlesCms.editor.fields.featured')}</span>
          </label>
        </div>

        <p className="ar-section">{t('articlesCms.editor.fields.cover')}</p>
        <div className="ar-cover-box">
          <div className="ar-cover-preview">
            {coverSrc ? <img src={coverSrc} alt={t('articlesCms.editor.fields.cover')} loading="lazy" /> : <span>{t('articlesCms.editor.cover.none')}</span>}
          </div>
          <div>
            <div className="ar-cover-actions">
              <input ref={fileInput} id="ar-cover-input" type="file" accept={COVER_TYPES.join(',')} onChange={pickCover} disabled={readOnly || !!saving} aria-label={t('articlesCms.editor.cover.choose')} />
              <button type="button" className="asp-secondary" disabled={readOnly || !!saving} onClick={() => fileInput.current?.click()}>
                <ImagePlus aria-hidden="true" /> {coverSrc ? t('articlesCms.editor.cover.replace') : t('articlesCms.editor.cover.choose')}
              </button>
              {coverSrc && <button type="button" className="asp-secondary" disabled={readOnly || !!saving} onClick={removeCover}>{t('articlesCms.editor.cover.remove')}</button>}
            </div>
            <p className="hc-hint">{t('articlesCms.editor.cover.hint')}</p>
            {coverFile && <p className="hc-hint">{t('articlesCms.editor.cover.pending')}</p>}
            {errors.cover && <p className="ap-error" role="alert">{errors.cover}</p>}
          </div>
        </div>

        <p className="ar-section">{t('articlesCms.editor.seo')}</p>
        <FormField
          label={t('articlesCms.editor.fields.metaTitle')}
          error={errors.metaTitle}
          errorId="ar-metatitle-error"
          aside={counter(draft.metaTitle, META_TITLE_HINT, LIMITS.metaTitle)}
        >
          <input
            value={draft.metaTitle}
            disabled={readOnly || !!saving}
            aria-invalid={Boolean(errors.metaTitle)}
            aria-describedby={errors.metaTitle ? 'ar-metatitle-error' : undefined}
            onChange={event => setField('metaTitle', event.target.value)}
          />
        </FormField>
        <FormTextarea
          label={t('articlesCms.editor.fields.metaDescription')}
          rows={3}
          value={draft.metaDescription}
          error={errors.metaDescription}
          errorId="ar-metadesc-error"
          disabled={readOnly || !!saving}
          onChange={event => setField('metaDescription', event.target.value)}
        />
        <p className="ar-draft-note">{counter(draft.metaDescription, META_DESCRIPTION_HINT, LIMITS.metaDescription)}</p>

        <p className="ar-section">{t('articlesCms.editor.related')}</p>
        <div className="ar-form-grid">
          <FormField label={t('articlesCms.editor.fields.relatedShopId')} error={errors.relatedShopId} errorId="ar-shop-error">
            <input
              inputMode="numeric"
              value={draft.relatedShopId}
              disabled={readOnly || !!saving}
              aria-invalid={Boolean(errors.relatedShopId)}
              aria-describedby={errors.relatedShopId ? 'ar-shop-error' : undefined}
              onChange={event => setField('relatedShopId', event.target.value)}
            />
          </FormField>
          <FormField label={t('articlesCms.editor.fields.publishAt')} error={errors.publishAt} errorId="ar-publishat-error" aside={<span className="ar-counter">{t('articlesCms.editor.fields.publishAtHint')}</span>}>
            <input
              type="datetime-local"
              value={draft.publishAt}
              disabled={readOnly || !!saving}
              aria-invalid={Boolean(errors.publishAt)}
              onChange={event => setField('publishAt', event.target.value)}
            />
          </FormField>
        </div>
        <FormTextarea
          label={t('articlesCms.editor.fields.relatedProductIds')}
          rows={3}
          value={draft.relatedProductIds}
          error={errors.relatedProductIds}
          errorId="ar-products-error"
          disabled={readOnly || !!saving}
          placeholder="00000000-0000-0000-0000-000000000000"
          onChange={event => setField('relatedProductIds', event.target.value)}
        />
        <p className="hc-hint">{t('articlesCms.editor.relatedHint')}</p>
      </>
    )
  }

  const renderArticleForm = () => (
    <form id="ar-form" className="ar-form" onSubmit={event => void submit('save', event)} noValidate>
      {formError && <p className="hc-form-error" role="alert"><CircleAlert aria-hidden="true" />{formError}</p>}
      {readOnly && <p className="ar-readonly-note">{t('articlesCms.readOnly')}</p>}
      {editorLoading ? <SkeletonTable rows={5} cols={2} /> : editorTab === 'Settings' ? renderSettings() : renderLangPanel(editorTab)}
    </form>
  )

  const renderCategoryForm = () => {
    const nameName = `name${catLang}` as keyof CategoryDraft
    const descName = `description${catLang}` as keyof CategoryDraft
    const isTh = catLang === 'Th'
    const langLabel = t(`articlesCms.editor.lang.${catLang.toLowerCase()}`)
    return (
      <form id="arc-form" className="ar-form" onSubmit={event => void submitCategory(event)} noValidate>
        {catFormError && <p className="hc-form-error" role="alert"><CircleAlert aria-hidden="true" />{catFormError}</p>}
        <FormField label={<>{t('articlesCms.editor.fields.slug')}<em className="hc-required"> *</em></>} error={catErrors.slug} errorId="arc-slug-error">
          <input
            value={catDraft.slug}
            maxLength={100}
            disabled={catSaving}
            autoComplete="off"
            aria-invalid={Boolean(catErrors.slug)}
            aria-describedby={catErrors.slug ? 'arc-slug-error' : undefined}
            onChange={event => setCatField('slug', event.target.value.toLowerCase())}
          />
        </FormField>
        <label className="hc-check">
          <input type="checkbox" checked={catDraft.active} disabled={catSaving} onChange={event => setCatField('active', event.target.checked)} />
          <span>{t('articlesCms.editor.fields.categoryActive')}</span>
        </label>
        <FormField label={<>{t('articlesCms.editor.fields.categoryName')} ({langLabel}){isTh && <em className="hc-required"> *</em>}</>} error={catErrors[nameName]} errorId={`arc-${nameName}-error`}>
          <input
            value={String(catDraft[nameName])}
            maxLength={LIMITS.name}
            disabled={catSaving}
            aria-invalid={Boolean(catErrors[nameName])}
            aria-describedby={catErrors[nameName] ? `arc-${nameName}-error` : undefined}
            onChange={event => setCatField(nameName, event.target.value)}
          />
        </FormField>
        <FormTextarea
          label={<>{t('articlesCms.editor.fields.categoryDescription')} ({langLabel})</>}
          rows={4}
          maxLength={LIMITS.description}
          value={String(catDraft[descName])}
          error={catErrors[descName]}
          errorId={`arc-${descName}-error`}
          disabled={catSaving}
          onChange={event => setCatField(descName, event.target.value)}
        />
        {!isTh && <p className="hc-hint">{t('articlesCms.editor.fallbackHint')}</p>}
      </form>
    )
  }

  const savedStatus = editor?.status ?? null
  const columnsCount = 8

  return <main className="admin-shell category-shell">
    <AdminHeader user={user} />
    <AdminSidebar active="articles" className="category-sidebar" />

    <div className="admin-content category-content hc-content">
      <section className="category-heading">
        <div className="category-title">
          <span><Newspaper /></span>
          <div>
            <h1>{t('articlesCms.title')}</h1>
            <p>{t('articlesCms.subtitle')}</p>
            {readOnly && <small>{t('articlesCms.readOnly')}</small>}
          </div>
        </div>
        <div className="hc-heading-actions">
          <a className="ap-ghost hc-preview" href="/articles" target="_blank" rel="noopener noreferrer"><ExternalLink />{t('articlesCms.openPublic')}</a>
          {canManage && (
            <button type="button" className="add-category" onClick={tab === 'articles' ? openCreate : openCategoryCreate}><Plus />{t(`articlesCms.add.${tab}`)}</button>
          )}
        </div>
      </section>

      <nav className="ap-tabs" aria-label={t('articlesCms.title')}>
        {(['articles', 'categories'] as const).map(kind => (
          <button key={kind} type="button" className={tab === kind ? 'active' : ''} aria-current={tab === kind ? 'page' : undefined} onClick={() => switchTab(kind)}>
            {t(`articlesCms.tabs.${kind}`)}
            {kind === 'articles' && !listError && !listLoading && <span className="hc-count">{total}</span>}
            {kind === 'categories' && categoriesLoaded && !categoriesError && <span className="hc-count">{categories.length}</span>}
          </button>
        ))}
      </nav>

      {tab === 'articles' ? (
        <>
          <form className="ap-filters category-filters" onSubmit={event => event.preventDefault()}>
            <div className="ap-filter-search wide">
              <span className="ap-filter-label">{t('articlesCms.search.label')}</span>
              <label className="category-search adm-search ap-search">
                <Search />
                <input value={query} onChange={event => setQuery(event.target.value)} placeholder={t('articlesCms.search.placeholder.articles')} aria-label={t('articlesCms.search.label')} />
              </label>
            </div>
            <AdminSelect label={t('articlesCms.filters.status')} value={statusFilter} onChange={setStatusFilter} options={statusOptions} />
            <AdminSelect label={t('articlesCms.filters.category')} value={categoryFilter} onChange={setCategoryFilter} options={categoryOptions} />
            <div className="ap-filter-actions">
              <button type="button" className="ap-ghost category-reset" onClick={resetFilters} disabled={!filtering}>{t('articlesCms.reset')}</button>
            </div>
          </form>

          <section className="ap-card hc-table-card">
            <header className="ap-card-head">
              <h2>{t('articlesCms.list.articles')} <span className="hc-muted">({t('articlesCms.count', { count: total })})</span></h2>
            </header>
            {listLoading && rows.length === 0 ? (
              <SkeletonTable rows={6} cols={6} />
            ) : listError ? (
              <div className="hc-state hc-state-error" role="alert">
                <CircleAlert aria-hidden="true" />
                <p>{listError}</p>
                <button type="button" className="ap-primary" onClick={() => setListReload(key => key + 1)}>{t('articlesCms.retry')}</button>
              </div>
            ) : (
              <>
                <div className="ap-table-scroll" aria-busy={listLoading}>
                  <table className="ap-table hc-table">
                    <thead>
                      <tr>
                        <th>{t('articlesCms.columns.cover')}</th>
                        <th>{t('articlesCms.columns.title')}</th>
                        <th>{t('articlesCms.columns.category')}</th>
                        <th>{t('articlesCms.columns.status')}</th>
                        <th>{t('articlesCms.columns.featured')}</th>
                        <th>{t('articlesCms.columns.publishedAt')}</th>
                        <th>{t('articlesCms.columns.updatedAt')}</th>
                        <th className="right">{t('articlesCms.columns.actions')}</th>
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map(row => {
                        const rowBusy = busyId === row.id
                        return (
                          <tr key={row.id} className={rowBusy ? 'is-busy' : undefined}>
                            <td>
                              <span className="ar-thumb">
                                {row.coverUrl ? <img src={mediaUrl(row.coverUrl)} alt={row.titleTh} loading="lazy" /> : <Newspaper aria-hidden="true" />}
                              </span>
                            </td>
                            <td className="ar-title">
                              <b>{row.titleTh}</b>
                              <small>{row.slug}</small>
                              {row.tags.length > 0 && <span className="ar-tags">{row.tags.slice(0, 4).map(tag => <i key={tag}>#{tag}</i>)}</span>}
                            </td>
                            <td>{row.categorySlug ? categoryName(row.categoryId, row.categorySlug) : '-'}</td>
                            <td>{statusPill(row)}</td>
                            <td>
                              {canManage ? (
                                <button type="button" role="switch" aria-checked={row.featured} aria-label={`${t('articlesCms.columns.featured')}: ${row.titleTh}`} className={`hc-switch ${row.featured ? 'on' : ''}`} disabled={!!busyId} onClick={() => void toggleFeatured(row)}>
                                  <span aria-hidden="true" />
                                  <em>{row.featured ? t('articlesCms.featured.on') : t('articlesCms.featured.off')}</em>
                                </button>
                              ) : (
                                <i className={`ap-pill ${row.featured ? 'ok' : 'muted'}`}>{row.featured ? t('articlesCms.featured.on') : t('articlesCms.featured.off')}</i>
                              )}
                            </td>
                            <td className="nowrap">{formatDate(row.publishedAt)}</td>
                            <td className="nowrap">{formatDate(row.updatedAt)}</td>
                            <td className="right nowrap">
                              <span className="hc-row-actions">
                                <button type="button" aria-label={`${t(canManage ? 'articlesCms.actions.edit' : 'articlesCms.actions.view')}: ${row.titleTh}`} title={t(canManage ? 'articlesCms.actions.edit' : 'articlesCms.actions.view')} onClick={() => void openEdit(row.id)}><Pencil /></button>
                                {canManage && (
                                  <button type="button" className="danger" aria-label={`${t('articlesCms.actions.delete')}: ${row.titleTh}`} title={t('articlesCms.actions.delete')} onClick={() => { setDeleteError(''); setDeleting({ kind: 'articles', id: row.id, name: row.titleTh }) }}><Trash2 /></button>
                                )}
                              </span>
                            </td>
                          </tr>
                        )
                      })}
                      {rows.length === 0 && (
                        <tr>
                          <td colSpan={columnsCount} className="ap-empty">
                            {filtering ? t('articlesCms.emptyFiltered') : t('articlesCms.empty.articles')}
                            {filtering && <> <button type="button" className="ap-link" onClick={resetFilters}>{t('articlesCms.reset')}</button></>}
                          </td>
                        </tr>
                      )}
                    </tbody>
                  </table>
                </div>
                {totalPages > 1 && (
                  <footer className="ar-pager">
                    <span>{t('articlesCms.pager.total', { count: total })}</span>
                    <nav aria-label={t('articlesCms.pager.label')}>
                      <button type="button" aria-label={t('articlesCms.pager.prev')} disabled={page <= 0 || listLoading} onClick={() => setPage(current => Math.max(0, current - 1))}><ChevronLeft /></button>
                      <span>{t('articlesCms.pager.page', { page: page + 1, pages: totalPages })}</span>
                      <button type="button" aria-label={t('articlesCms.pager.next')} disabled={page + 1 >= totalPages || listLoading} onClick={() => setPage(current => current + 1)}><ChevronRight /></button>
                    </nav>
                  </footer>
                )}
              </>
            )}
          </section>
        </>
      ) : (
        <section className="ap-card hc-table-card">
          <header className="ap-card-head">
            <h2>{t('articlesCms.list.categories')} <span className="hc-muted">({t('articlesCms.count', { count: categories.length })})</span></h2>
          </header>
          {!categoriesLoaded ? (
            <SkeletonTable rows={5} cols={5} />
          ) : categoriesError ? (
            <div className="hc-state hc-state-error" role="alert">
              <CircleAlert aria-hidden="true" />
              <p>{categoriesError}</p>
              <button type="button" className="ap-primary" onClick={() => { setCategoriesLoaded(false); setCategoryReload(key => key + 1) }}>{t('articlesCms.retry')}</button>
            </div>
          ) : (
            <div className="ap-table-scroll">
              <table className="ap-table hc-table">
                <thead>
                  <tr>
                    <th className="hc-col-order">{t('articlesCms.columns.order')}</th>
                    <th>{t('articlesCms.columns.name')}</th>
                    <th>{t('articlesCms.columns.articleCount')}</th>
                    <th>{t('articlesCms.columns.active')}</th>
                    <th>{t('articlesCms.columns.updatedAt')}</th>
                    {canManage && <th className="right">{t('articlesCms.columns.actions')}</th>}
                  </tr>
                </thead>
                <tbody>
                  {categories.map((item, index) => (
                    <tr key={item.id} className={busyId === item.id ? 'is-busy' : undefined}>
                      <td className="hc-col-order nowrap">
                        <span className="hc-order">{item.sortOrder}</span>
                        {canManage && (
                          <span className="hc-move">
                            <button type="button" aria-label={t('articlesCms.actions.moveUp')} title={t('articlesCms.actions.moveUp')} disabled={!!busyId || index <= 0} onClick={() => void moveCategory(index, -1)}><ArrowUp /></button>
                            <button type="button" aria-label={t('articlesCms.actions.moveDown')} title={t('articlesCms.actions.moveDown')} disabled={!!busyId || index >= categories.length - 1} onClick={() => void moveCategory(index, 1)}><ArrowDown /></button>
                          </span>
                        )}
                      </td>
                      <td className="hc-name"><b>{item.nameTh}</b><small>{item.slug}</small></td>
                      <td>{item.articleCount}</td>
                      <td>
                        {canManage ? (
                          <button type="button" role="switch" aria-checked={item.active} aria-label={`${t('articlesCms.columns.active')}: ${item.nameTh}`} className={`hc-switch ${item.active ? 'on' : ''}`} disabled={!!busyId} onClick={() => void toggleCategoryActive(item)}>
                            <span aria-hidden="true" />
                            <em>{item.active ? t('articlesCms.active.on') : t('articlesCms.active.off')}</em>
                          </button>
                        ) : (
                          <i className={`ap-pill ${item.active ? 'ok' : 'muted'}`}>{item.active ? t('articlesCms.active.on') : t('articlesCms.active.off')}</i>
                        )}
                      </td>
                      <td className="nowrap">{formatDate(item.updatedAt)}</td>
                      {canManage && (
                        <td className="right nowrap">
                          <span className="hc-row-actions">
                            <button type="button" aria-label={`${t('articlesCms.actions.edit')}: ${item.nameTh}`} title={t('articlesCms.actions.edit')} onClick={() => openCategoryEdit(item)}><Pencil /></button>
                            <button type="button" className="danger" aria-label={`${t('articlesCms.actions.delete')}: ${item.nameTh}`} title={t('articlesCms.actions.delete')} onClick={() => { setDeleteError(''); setDeleting({ kind: 'categories', id: item.id, name: item.nameTh }) }}><Trash2 /></button>
                          </span>
                        </td>
                      )}
                    </tr>
                  ))}
                  {categories.length === 0 && (
                    <tr><td colSpan={canManage ? 6 : 5} className="ap-empty">{t('articlesCms.empty.categories')}</td></tr>
                  )}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </div>

    {editor && (
      <AdminSidePanel
        variant="drawer"
        open
        title={t(`articlesCms.editor.title.${readOnly ? 'view' : editor.id ? 'edit' : 'create'}`)}
        headerExtra={savedStatus ? <span className="ar-status-pill">{statusPill({ status: savedStatus, publishedAt: null })}</span> : undefined}
        onClose={requestClose}
        tabs={editorTabs}
        activeTab={editorTab}
        onTabChange={id => setEditorTab(id as EditorTab)}
        className="ar-drawer hc-drawer"
        footer={<>
          <button type="button" className="asp-secondary" onClick={requestClose} disabled={!!saving}>{t('articlesCms.editor.buttons.close')}</button>
          {editor.id && <button type="button" className="asp-secondary" onClick={openPreview} disabled={!!saving} title={t('articlesCms.editor.buttons.previewHint')}><ExternalLink aria-hidden="true" /> {t('articlesCms.editor.buttons.preview')}</button>}
          <span className="ar-footer-spacer" />
          {canManage && editor.id && savedStatus !== 'ARCHIVED' && (
            <button type="button" className="asp-secondary" onClick={() => void changeStatus('ARCHIVED')} disabled={!!saving || editorLoading}>{t('articlesCms.editor.buttons.archive')}</button>
          )}
          {canManage && editor.id && savedStatus === 'ARCHIVED' && (
            <button type="button" className="asp-secondary" onClick={() => void changeStatus('DRAFT')} disabled={!!saving || editorLoading}>{t('articlesCms.editor.buttons.restore')}</button>
          )}
          {canManage && (
            <>
              <button type="submit" form="ar-form" className="asp-secondary" disabled={!!saving || editorLoading}>
                {saving === 'save' ? t('articlesCms.editor.buttons.saving') : t(savedStatus === 'PUBLISHED' ? 'articlesCms.editor.buttons.save' : 'articlesCms.editor.buttons.saveDraft')}
              </button>
              <button type="button" className="asp-primary ar-publish" onClick={() => void submit('publish')} disabled={!!saving || editorLoading}>
                {saving === 'publish' ? t('articlesCms.editor.buttons.publishing') : t(savedStatus === 'PUBLISHED' ? 'articlesCms.editor.buttons.republish' : 'articlesCms.editor.buttons.publish')}
              </button>
            </>
          )}
        </>}
      >
        {renderArticleForm()}
      </AdminSidePanel>
    )}

    {catEditing && (
      <AdminSidePanel
        variant="drawer"
        open
        title={t(`articlesCms.categoryEditor.title.${catEditing.id ? 'edit' : 'create'}`)}
        onClose={() => { if (!catSaving) setCatEditing(null) }}
        tabs={categoryEditorTabs}
        activeTab={catLang}
        onTabChange={id => setCatLang(id as Lang)}
        className="hc-drawer"
        footer={<>
          <button type="button" className="asp-secondary" onClick={() => setCatEditing(null)} disabled={catSaving}>{t('articlesCms.editor.buttons.close')}</button>
          <button type="submit" form="arc-form" className="asp-primary" disabled={catSaving || !canManage}>{catSaving ? t('articlesCms.editor.buttons.saving') : t('articlesCms.editor.buttons.save')}</button>
        </>}
      >
        {renderCategoryForm()}
      </AdminSidePanel>
    )}

    {discardAsk && (
      <ConfirmDialog
        title={t('articlesCms.unsaved.title')}
        message={t('articlesCms.unsaved.body')}
        cancelLabel={t('articlesCms.unsaved.keep')}
        confirmLabel={t('articlesCms.unsaved.discard')}
        danger
        onCancel={() => setDiscardAsk(false)}
        onConfirm={() => { setDiscardAsk(false); setEditor(null) }}
      />
    )}

    {deleting && (
      <ConfirmDialog
        title={t(`articlesCms.delete.title.${deleting.kind}`)}
        message={t('articlesCms.delete.body', { name: deleting.name })}
        cancelLabel={t('articlesCms.editor.buttons.close')}
        confirmLabel={deleteBusy ? t('articlesCms.delete.deleting') : t('articlesCms.delete.confirm')}
        danger
        confirmDisabled={deleteBusy}
        onCancel={() => { if (!deleteBusy) setDeleting(null) }}
        onConfirm={() => void confirmDelete()}
      >
        {deleteError && <p className="ap-error" role="alert">{deleteError}</p>}
      </ConfirmDialog>
    )}

    {notice && <div className="admin-toast" role="status">{notice}</div>}
  </main>
}
