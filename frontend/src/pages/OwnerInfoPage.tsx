import { useEffect, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Building2, Check, FileText, IdCard, Mail, Phone, UserRound,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useLanguage } from '../contexts/LanguageContext'
import MerchantOnboardingLayout from '../components/merchant/MerchantOnboardingLayout'
import OnboardingAside from '../components/merchant/OnboardingAside'
import RegisterStepper from '../components/merchant/RegisterStepper'
import MerchantHeader from '../components/MerchantHeader'
import SearchableSelect from '../components/SearchableSelect'
import ThaiAddressFields, { emptyThaiAddress, type ThaiAddressValue } from '../components/ThaiAddressFields'
import { merchantApi, type MerchantApplication, type MerchantOwner } from '../services/api'
import { showApiFailure } from '../services/apiErrors'
import DatePicker from '../components/form/DatePicker'
import FieldError from '../components/form/FieldError'
import FormField from '../components/form/FormField'
import FormGrid from '../components/form/FormGrid'
import { SHOP_IMAGE_ACCEPT } from '../services/shopImage'
import { readMerchantDraft, rememberDocumentName, writeMerchantDraft, type OwnerDraft } from '../services/merchantDraft'
import {
  digitsOnly,
  documentFileError,
  focusFirstInvalid,
  formatCitizenId,
  formatPhoneNumber,
  mergeFieldError,
  ownerPayload,
  validateOwner,
} from '../services/merchantApplicationValidation'
import { autofillSyncHandlers, readFormTextValue } from '../hooks/autofillSync'
import { useDocumentPreview } from '../hooks/useDocumentPreview'
import i18n from '../i18n'
import '../styles/owner-info.css'
import '../styles/form-fields.css'
import '../styles/form-invalid.css'
import '../styles/doc-confirm.css'

type OwnerDocType = 'IDENTITY' | 'PORTRAIT'

function docFieldKey(type: OwnerDocType) {
  return type === 'IDENTITY' ? 'identityDoc' : 'portraitDoc'
}

function shopAddress(userId?: string | null): ThaiAddressValue {
  const draft = readMerchantDraft(userId)
  return {
    province: draft.province || '',
    district: draft.district || '',
    subdistrict: draft.subdistrict || '',
    postalCode: draft.postalCode || '',
  }
}

function shopAddressLines(userId?: string | null) {
  const draft = readMerchantDraft(userId)
  const street = (draft.address || '').trim()
  const area = [draft.subdistrict, draft.district, draft.province, draft.postalCode].filter(Boolean).join(' ')
  return { street, area }
}

function AddressPreview({ userId, emptyLabel }: { userId?: string | null; emptyLabel: string }) {
  const { street, area } = shopAddressLines(userId)
  return (
    <div className="owner-address-display">
      {street || area ? (
        <>
          {street ? <b>{street}</b> : null}
          {area ? <span>{area}</span> : null}
        </>
      ) : <span>{emptyLabel}</span>}
    </div>
  )
}

function UploadCard({
  title,
  icon: Icon,
  type,
  labels,
  fileName,
  uploading,
  error,
  onUploading,
  onUploaded,
  onError,
}: {
  title: string
  icon: typeof IdCard
  type: OwnerDocType
  labels: { drop: string; types: string; pick: string; change: string; uploading: string; fail: string }
  fileName: string
  uploading: boolean
  error?: string
  onUploading: (type: OwnerDocType, busy: boolean) => void
  onUploaded: (type: OwnerDocType, name: string) => void
  onError: (type: OwnerDocType, message: string) => void
}) {
  const { user } = useAuth()
  const field = docFieldKey(type)
  const [localFile, setLocalFile] = useState<File | null>(null)
  const preview = useDocumentPreview(type, localFile, fileName)
  const previewAlt = i18n.t('documents.previewAlt', { ns: 'merchant' })
  const upload = async (selected?: File) => {
    if (!selected) return
    const fileError = documentFileError(selected)
    if (fileError) {
      onError(type, fileError)
      return
    }
    onUploading(type, true)
    onError(type, '')
    try {
      await merchantApi.uploadDocument(type, selected)
      rememberDocumentName(user?.id, type, selected.name)
      setLocalFile(selected)
      onUploaded(type, selected.name)
    } catch (error) {
      showApiFailure(error, (message) => onError(type, message || labels.fail))
    } finally {
      onUploading(type, false)
    }
  }
  const invalid = Boolean(error)
  const busy = uploading || preview.loading
  const showLive = Boolean(preview.url) && !preview.failed
  return (
    <label className="owner-upload" data-field={field}>
      <span>{title} <em>*</em></span>
      <div
        className={[fileName ? 'has-file' : '', invalid ? 'is-invalid' : '', showLive ? 'has-preview' : ''].filter(Boolean).join(' ')}
        aria-invalid={invalid || undefined}
      >
        {busy ? (
          <div className="document-preview-skeleton owner-upload-preview" aria-busy="true" />
        ) : showLive && preview.isImage ? (
          <img className="owner-upload-preview-img" src={preview.url} alt={previewAlt} />
        ) : showLive && preview.isPdf ? (
          <span className="owner-upload-preview-named" aria-label={previewAlt}>
            <FileText aria-hidden="true" />
            <small>PDF</small>
          </span>
        ) : preview.failed && fileName ? (
          <span className="owner-upload-preview-named" aria-label={previewAlt}>
            <FileText aria-hidden="true" />
            <small>{i18n.t('documents.uploadedNamed', { ns: 'merchant', name: fileName })}</small>
          </span>
        ) : (
          <>
            <Icon />
            <p>{fileName || labels.drop}</p>
            <small>{labels.types}</small>
          </>
        )}
        <b>{busy ? labels.uploading : fileName ? labels.change : labels.pick}</b>
        <input
          disabled={uploading}
          type="file"
          accept={`${SHOP_IMAGE_ACCEPT},application/pdf`}
          aria-invalid={invalid || undefined}
          onChange={(e) => void upload(e.target.files?.[0])}
        />
      </div>
      <FieldError text={error} id={error ? `field-error-${field}` : undefined} />
    </label>
  )
}

export default function OwnerInfoPage() {
  const { user } = useAuth()
  const { onboarding: ob } = useLanguage()
  const o = ob.owner
  const uploadLabels = { drop: o.uploadDrop, types: o.uploadTypes, pick: o.uploadPick, change: o.uploadChange, uploading: o.uploading, fail: o.uploadFail }
  const navigate = useNavigate()
  const saved = readMerchantDraft(user?.id).owner
  // Draft linked to an owner the user already has (second shop): owner details are prefilled and editable,
  // the citizen id stays that owner's (shown masked, not editable).
  const [linkedOwnerId, setLinkedOwnerId] = useState(() => readMerchantDraft(user?.id).ownerId || '')
  const linked = Boolean(linkedOwnerId)
  const [ownerType, setOwnerType] = useState<OwnerDraft['ownerType']>(saved?.ownerType || 'person')
  const [title, setTitle] = useState(saved?.title || o.titles[0])
  const [firstName, setFirstName] = useState(saved?.firstName || user?.firstName || '')
  const [lastName, setLastName] = useState(saved?.lastName || user?.lastName || '')
  const [citizenId, setCitizenId] = useState(() => linkedOwnerId ? (saved?.citizenId || '') : formatCitizenId(saved?.citizenId || ''))
  const [birthDate, setBirthDate] = useState(saved?.birthDate || '')
  const [nationality, setNationality] = useState(saved?.nationality || o.nationalities[0])
  const [phone, setPhone] = useState(formatPhoneNumber(saved?.phone || user?.phone || ''))
  const [email, setEmail] = useState(saved?.email || (user?.email === 'admin' ? '' : user?.email || ''))
  const [houseNo, setHouseNo] = useState(saved?.houseNo || '')
  const [villageNo, setVillageNo] = useState(saved?.villageNo || '')
  const [building, setBuilding] = useState(saved?.building || '')
  const [alley, setAlley] = useState(saved?.alley || '')
  const [road, setRoad] = useState(saved?.road || '')
  const [sameAddress, setSameAddress] = useState(saved?.sameAddress ?? true)
  const [thaiAddress, setThaiAddress] = useState<ThaiAddressValue>(() => saved?.province
    ? { province: saved.province, district: saved.district, subdistrict: saved.subdistrict, postalCode: saved.postalCode }
    : sameAddress ? shopAddress(user?.id) : emptyThaiAddress)
  const [bankName, setBankName] = useState(saved?.bankName || '')
  const [bankAccountNumber, setBankAccountNumber] = useState(digitsOnly(saved?.bankAccountNumber || '', 20))
  const [bankAccountName, setBankAccountName] = useState(saved?.bankAccountName || '')
  const [message, setMessage] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [owners, setOwners] = useState<MerchantOwner[]>([])
  const [adding, setAdding] = useState(false)
  const [picking, setPicking] = useState(false)
  const savedDocNames = readMerchantDraft(user?.id).documentNames
  const [identityFile, setIdentityFile] = useState(savedDocNames?.IDENTITY || '')
  const [portraitFile, setPortraitFile] = useState(savedDocNames?.PORTRAIT || '')
  const [identityUploading, setIdentityUploading] = useState(false)
  const [portraitUploading, setPortraitUploading] = useState(false)

  const setDocUploading = (type: OwnerDocType, busy: boolean) => {
    if (type === 'IDENTITY') setIdentityUploading(busy)
    else setPortraitUploading(busy)
  }
  const onDocUploaded = (type: OwnerDocType, name: string) => {
    if (type === 'IDENTITY') setIdentityFile(name)
    else setPortraitFile(name)
    setFieldErrors((prev) => mergeFieldError(prev, docFieldKey(type)))
  }
  const onDocError = (type: OwnerDocType, message: string) => {
    setFieldErrors((prev) => mergeFieldError(prev, docFieldKey(type), message || undefined))
  }

  /**
   * Prefill from the server draft linked to an existing owner: owner/personal details and the owner documents the
   * server copied into this draft (ID card, portrait). Payout details come from the owner profile, like the server-side owner prefill.
   */
  const applyPrefill = (app: MerchantApplication, ownerId: string, profile?: MerchantOwner) => {
    if (!user) return
    setOwnerType(app.ownerType === 'COMPANY' ? 'company' : 'person')
    if (app.title) setTitle(app.title)
    setFirstName(app.firstName || '')
    setLastName(app.lastName || '')
    setCitizenId(app.citizenIdMasked || '')
    setBirthDate(app.birthDate || '')
    if (app.nationality) setNationality(app.nationality)
    setPhone(formatPhoneNumber(app.phone || ''))
    setEmail(app.email || '')
    setSameAddress(false)
    setHouseNo(app.houseNo || '')
    setVillageNo(app.villageNo || '')
    setBuilding(app.building || '')
    setAlley(app.alley || '')
    setRoad(app.road || '')
    setThaiAddress({ province: app.province || '', district: app.district || '', subdistrict: app.subdistrict || '', postalCode: app.postalCode || '' })
    // The application only carries a masked account number; the full one comes from the owner profile.
    setBankName(profile?.bankName || app.bankName || '')
    setBankAccountNumber(digitsOnly(profile?.bankAccountNumber || '', 20))
    setBankAccountName(profile?.bankAccountName || app.bankAccountName || '')
    const identity = app.documents.find((doc) => doc.type === 'IDENTITY')
    const portrait = app.documents.find((doc) => doc.type === 'PORTRAIT')
    if (identity) rememberDocumentName(user.id, 'IDENTITY', identity.originalFilename)
    if (portrait) rememberDocumentName(user.id, 'PORTRAIT', portrait.originalFilename)
    setIdentityFile(identity?.originalFilename || '')
    setPortraitFile(portrait?.originalFilename || '')
    setLinkedOwnerId(ownerId)
    setAdding(true)
    setFieldErrors({})
    writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), ownerId, ownerPrefilledFor: ownerId })
  }

  const unlinkOwner = () => {
    if (!user) return
    setLinkedOwnerId('')
    setCitizenId('')
    setFieldErrors({})
    setAdding(true)
    writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), ownerId: undefined, ownerPrefilledFor: undefined })
  }

  useEffect(() => {
    if (!user || !linkedOwnerId) return
    const local = readMerchantDraft(user.id)
    if (local.ownerPrefilledFor === linkedOwnerId && local.owner) return
    let active = true
    Promise.all([merchantApi.getDraft(), merchantApi.owners().catch(() => [] as MerchantOwner[])]).then(([app, rows]) => {
      if (!active) return
      if (app.ownerId !== linkedOwnerId) {
        unlinkOwner()
        return
      }
      applyPrefill(app, linkedOwnerId, rows.find((row) => row.id === linkedOwnerId))
    }).catch(() => undefined)
    return () => { active = false }
  }, [user])

  useEffect(() => {
    if (!user) return
    let active = true
    merchantApi.owners().then((rows) => { if (active) setOwners(rows) }).catch(() => { if (active) setOwners([]) })
    return () => { active = false }
  }, [user])

  useEffect(() => {
    const owner: OwnerDraft = {
      ownerType, title, firstName, lastName, citizenId, birthDate, nationality, phone, email,
      houseNo, villageNo, building, alley, road, province: thaiAddress.province, district: thaiAddress.district,
      subdistrict: thaiAddress.subdistrict, postalCode: thaiAddress.postalCode, sameAddress, bankName,
      bankAccountNumber, bankAccountName,
    }
    if (!user) return
    writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), owner })
  }, [ownerType, title, firstName, lastName, citizenId, birthDate, nationality, phone, email, houseNo, villageNo, building, alley, road, thaiAddress, sameAddress, bankName, bankAccountNumber, bankAccountName])

  useEffect(() => {
    if (!sameAddress || !user) return
    const draft = readMerchantDraft(user.id)
    const street = (draft.address || '').trim()
    // Never dump the full shop address line into houseNo (varchar 50) — that caused SQLState 22001.
    setHouseNo((prev) => {
      if (prev && prev.length <= 50 && prev !== street) return prev
      return o.sameAddressHouseNo || ''
    })
    setVillageNo('')
    setBuilding('')
    setAlley('')
    setRoad(street.slice(0, 255))
    setThaiAddress(shopAddress(user.id))
  }, [sameAddress, user, o.sameAddressHouseNo])

  useEffect(() => {
    if (!user || saved?.firstName) return
    setFirstName((current) => current || user.firstName || '')
    setLastName((current) => current || user.lastName || '')
    setPhone((current) => current || formatPhoneNumber(user.phone || ''))
    setEmail((current) => current || (user.email === 'admin' ? '' : user.email || ''))
  }, [user])

  const currentOwner = (): OwnerDraft => ({
    ownerType, title, firstName, lastName, citizenId, birthDate, nationality, phone, email,
    houseNo, villageNo, building, alley, road, province: thaiAddress.province, district: thaiAddress.district,
    subdistrict: thaiAddress.subdistrict, postalCode: thaiAddress.postalCode, sameAddress, bankName,
    bankAccountNumber, bankAccountName,
  })

  const blurOwner = (field: string, overrides?: Partial<OwnerDraft>) => {
    setFieldErrors((prev) => mergeFieldError(prev, field, validateOwner({ ...currentOwner(), ...overrides }, { linkedOwner: linked })[field]))
  }

  const patchThaiAddress = (next: ThaiAddressValue) => {
    setThaiAddress(next)
    setFieldErrors((prev) => {
      const updated = { ...prev }
      for (const key of ['province', 'district', 'subdistrict', 'postalCode'] as const) {
        if (next[key]?.trim() || !next[key]) delete updated[key]
      }
      return updated
    })
  }

  const pickOwnerField = (field: string, setter: (value: string) => void) => (value: string) => {
    setter(value)
    setFieldErrors((prev) => mergeFieldError(prev, field))
  }

  const clearOwnerError = (field: string) => setFieldErrors((prev) => mergeFieldError(prev, field))

  const firstNameHandlers = autofillSyncHandlers({ onValue: setFirstName, onClearError: () => clearOwnerError('firstName') })
  const lastNameHandlers = autofillSyncHandlers({ onValue: setLastName, onClearError: () => clearOwnerError('lastName') })
  const citizenIdHandlers = autofillSyncHandlers({
    onValue: setCitizenId,
    onClearError: () => clearOwnerError('citizenId'),
    transform: formatCitizenId,
  })
  const phoneHandlers = autofillSyncHandlers({
    onValue: setPhone,
    onClearError: () => clearOwnerError('phone'),
    transform: formatPhoneNumber,
  })
  const emailHandlers = autofillSyncHandlers({ onValue: setEmail, onClearError: () => clearOwnerError('email') })
  const houseNoHandlers = autofillSyncHandlers({ onValue: setHouseNo, onClearError: () => clearOwnerError('houseNo') })
  const bankAccountNumberHandlers = autofillSyncHandlers({
    onValue: setBankAccountNumber,
    onClearError: () => clearOwnerError('bankAccountNumber'),
    transform: (value) => digitsOnly(value, 20),
  })
  const bankAccountNameHandlers = autofillSyncHandlers({
    onValue: setBankAccountName,
    onClearError: () => clearOwnerError('bankAccountName'),
  })

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    if (!user) {
      window.location.assign('/login?next=/merchant/owner-info')
      return
    }
    const form = event.currentTarget
    const syncedFirstName = readFormTextValue(form, 'firstName', firstName)
    const syncedLastName = readFormTextValue(form, 'lastName', lastName)
    const syncedCitizenId = linked ? citizenId : formatCitizenId(readFormTextValue(form, 'citizenId', citizenId))
    const syncedPhone = formatPhoneNumber(readFormTextValue(form, 'phone', phone))
    const syncedEmail = readFormTextValue(form, 'email', email)
    const syncedHouseNo = readFormTextValue(form, 'houseNo', houseNo)
    const syncedBankAccountNumber = digitsOnly(readFormTextValue(form, 'bankAccountNumber', bankAccountNumber), 20)
    const syncedBankAccountName = readFormTextValue(form, 'bankAccountName', bankAccountName)
    setFirstName(syncedFirstName)
    setLastName(syncedLastName)
    setCitizenId(syncedCitizenId)
    setPhone(syncedPhone)
    setEmail(syncedEmail)
    setHouseNo(syncedHouseNo)
    setBankAccountNumber(syncedBankAccountNumber)
    setBankAccountName(syncedBankAccountName)
    const owner: OwnerDraft = {
      ...currentOwner(),
      firstName: syncedFirstName,
      lastName: syncedLastName,
      citizenId: syncedCitizenId,
      phone: syncedPhone,
      email: syncedEmail,
      houseNo: syncedHouseNo,
      bankAccountNumber: syncedBankAccountNumber,
      bankAccountName: syncedBankAccountName,
    }
    const nextErrors = validateOwner(owner, { linkedOwner: linked })
    if (!identityFile) nextErrors.identityDoc = i18n.t('validation.identityDoc', { ns: 'merchant' })
    if (!portraitFile) nextErrors.portraitDoc = i18n.t('validation.portraitDoc', { ns: 'merchant' })
    if (identityUploading || portraitUploading) {
      setFieldErrors(nextErrors)
      setMessage(i18n.t('validation.uploadWait', { ns: 'merchant' }))
      if (Object.keys(nextErrors).length > 0) focusFirstInvalid(nextErrors)
      return
    }
    setFieldErrors(nextErrors)
    setMessage('')
    if (Object.keys(nextErrors).length > 0) {
      focusFirstInvalid(nextErrors)
      return
    }
    setSubmitting(true)
    try {
      // A linked owner keeps its citizen id (already checked); its details are saved to this application on submit.
      if (!linked) await merchantApi.validateOwner(ownerPayload(owner))
      writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), owner, ownerId: linked ? linkedOwnerId : undefined })
      navigate('/merchant/documents')
    } catch (error) {
      showApiFailure(error, setMessage, setFieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  const reuseOwner = async (ownerId: string) => {
    if (!user || picking) return
    setPicking(true)
    setMessage('')
    try {
      // Prefill only: the form (and the document step) stay required, like a first application.
      applyPrefill(await merchantApi.attachOwner(ownerId), ownerId, owners.find((row) => row.id === ownerId))
    } catch (error) {
      showApiFailure(error, setMessage)
    } finally {
      setPicking(false)
    }
  }

  return (
    <main className="owner-page onboard-page">
      <MerchantHeader />
      <MerchantOnboardingLayout
        aside={(
          <OnboardingAside
            safeSummaryLabel={ob.common.asideSafeSummary}
            safe={{ title: o.safeTitle, items: [...o.safeBullets] }}
            tips={{ title: o.tipsTitle, items: [...o.tipsBullets] }}
            help={{
              title: ob.common.helpTitle,
              lead: o.questionLead.replace(/\n/g, ' '),
              contactLabel: ob.common.contact,
            }}
            trustLabel={ob.common.trustSslPdpa}
          />
        )}
      >
          <div className="owner-card">
            <div className="owner-title"><h1>{o.pageTitle}</h1><p>{o.pageLead}</p></div>
            <RegisterStepper currentStep={2} />
            {owners.length > 0 && !adding && !linked ? (
              <div className="owner-choice">
                <p>{o.reuseHint}</p>
                {owners.map((owner) => (
                  <button type="button" key={owner.id} disabled={picking} onClick={() => void reuseOwner(owner.id)}>
                    {o.reuseCard}: {owner.firstName} {owner.lastName}
                  </button>
                ))}
                <button type="button" className="new" onClick={() => setAdding(true)}>{o.addOwner}</button>
                {message && <p className="owner-message" role="alert">{message}</p>}
              </div>
            ) : null}
            {owners.length > 0 && adding && !linked ? <button type="button" className="owner-choice-back" onClick={() => setAdding(false)}>{o.reuseLabel}</button> : null}
            <form onSubmit={(event) => void submit(event)} hidden={owners.length > 0 && !adding && !linked} noValidate>
              {message && <p className="form-alert" role="alert">{message}</p>}
              <fieldset className="owner-type">
                <legend>{o.ownerType}</legend>
                <div>
                  <button type="button" className={ownerType === 'person' ? 'active' : ''} onClick={() => setOwnerType('person')}><i>{ownerType === 'person' ? <Check /> : null}</i><UserRound /><span><b>{o.person}</b><small>{o.personHint}</small></span></button>
                  <button type="button" className={ownerType === 'company' ? 'active' : ''} onClick={() => setOwnerType('company')}><i>{ownerType === 'company' ? <Check /> : null}</i><Building2 /><span><b>{o.company}</b><small>{o.companyHint}</small></span></button>
                </div>
              </fieldset>
              <section className="owner-form-section">
                <h3>{o.personal}</h3>
                <div className="owner-person-grid">
                  <SearchableSelect field="title" error={fieldErrors.title} onBlur={() => blurOwner('title')} label={o.title} required options={[...o.titles]} value={title} onChange={pickOwnerField('title', setTitle)} />
                  <FormField className="flush" label={<>{o.firstName} <em>*</em></>} icon={UserRound} error={fieldErrors.firstName}>
                    <input name="firstName" maxLength={100} value={firstName} autoComplete="given-name" aria-invalid={Boolean(fieldErrors.firstName)} {...firstNameHandlers} onBlur={() => blurOwner('firstName')} />
                  </FormField>
                  <FormField className="flush" label={<>{o.lastName} <em>*</em></>} error={fieldErrors.lastName}>
                    <input name="lastName" maxLength={100} value={lastName} autoComplete="family-name" aria-invalid={Boolean(fieldErrors.lastName)} {...lastNameHandlers} onBlur={() => blurOwner('lastName')} />
                  </FormField>
                  <FormField className="flush" label={<>{o.citizenId} <em>*</em></>} icon={IdCard} error={fieldErrors.citizenId}>
                    <input
                      name="citizenId"
                      inputMode="numeric"
                      autoComplete="off"
                      maxLength={17}
                      placeholder={o.citizenPh}
                      value={citizenId}
                      aria-invalid={Boolean(fieldErrors.citizenId)}
                      {...(linked ? { readOnly: true, 'aria-readonly': true, title: o.citizenLocked } : citizenIdHandlers)}
                      onBlur={() => blurOwner('citizenId')}
                    />
                  </FormField>
                  <DatePicker
                    className="flush"
                    field="birthDate"
                    name="birthDate"
                    label={o.birthDate}
                    required
                    value={birthDate}
                    max={new Date().toISOString().slice(0, 10)}
                    error={fieldErrors.birthDate}
                    placeholder={o.birthDatePh}
                    onChange={pickOwnerField('birthDate', setBirthDate)}
                    onBlur={() => blurOwner('birthDate')}
                  />
                  <SearchableSelect field="nationality" error={fieldErrors.nationality} onBlur={() => blurOwner('nationality')} label={o.nationality} required options={[...o.nationalities]} value={nationality} onChange={pickOwnerField('nationality', setNationality)} />
                </div>
                <FormGrid>
                  <FormField className="flush" label={<>{o.phone} <em>*</em></>} icon={Phone} error={fieldErrors.phone}>
                    <input
                      name="phone"
                      type="tel"
                      inputMode="numeric"
                      autoComplete="tel"
                      maxLength={12}
                      placeholder="0xx-xxx-xxxx"
                      value={phone}
                      aria-invalid={Boolean(fieldErrors.phone)}
                      {...phoneHandlers}
                      onBlur={() => blurOwner('phone')}
                    />
                  </FormField>
                  <FormField className="flush" label={<>{o.email} <em>*</em></>} icon={Mail} error={fieldErrors.email}>
                    <input name="email" type="email" maxLength={255} autoComplete="email" value={email} aria-invalid={Boolean(fieldErrors.email)} {...emailHandlers} onBlur={() => blurOwner('email')} />
                  </FormField>
                </FormGrid>
              </section>
              <section className="owner-form-section">
                <div className="section-row">
                  <h3>{o.idAddress}</h3>
                  <label
                    className="same-address"
                    data-focus-fields="houseNo villageNo building alley road province district subdistrict postalCode"
                  >
                    <input type="checkbox" checked={sameAddress} onChange={(e) => setSameAddress(e.target.checked)} />
                    <span><Check /></span>{o.sameShopAddress}
                  </label>
                </div>
                {sameAddress ? (
                  <>
                    <AddressPreview userId={user?.id} emptyLabel={o.sameAddressEmpty} />
                    {(fieldErrors.houseNo || fieldErrors.province || fieldErrors.postalCode) && (
                      <p className="form-alert" role="alert">{fieldErrors.houseNo || fieldErrors.province || fieldErrors.district || fieldErrors.subdistrict || fieldErrors.postalCode}</p>
                    )}
                  </>
                ) : (
                <div className="owner-address-grid">
                  <FormField className="flush" label={<>{o.houseNo} <em>*</em></>} error={fieldErrors.houseNo}>
                    <input
                      name="houseNo"
                      maxLength={50}
                      placeholder={o.houseNoPh}
                      value={houseNo}
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.houseNo)}
                      {...houseNoHandlers}
                      onBlur={() => blurOwner('houseNo')}
                    />
                  </FormField>
                  <FormField className="flush" label={o.villageNo} error={fieldErrors.villageNo}>
                    <input
                      name="villageNo"
                      maxLength={50}
                      placeholder={o.villageNoPh}
                      value={villageNo}
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.villageNo)}
                      onChange={(e) => {
                        setVillageNo(e.target.value)
                        clearOwnerError('villageNo')
                      }}
                      onBlur={() => blurOwner('villageNo')}
                    />
                  </FormField>
                  <FormField className="flush wide" label={o.building} error={fieldErrors.building}>
                    <input
                      name="building"
                      maxLength={255}
                      placeholder={o.buildingPh}
                      value={building}
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.building)}
                      onChange={(e) => {
                        setBuilding(e.target.value)
                        clearOwnerError('building')
                      }}
                      onBlur={() => blurOwner('building')}
                    />
                  </FormField>
                  <FormField className="flush" label={o.alley} error={fieldErrors.alley}>
                    <input
                      name="alley"
                      maxLength={255}
                      placeholder={o.alleyPh}
                      value={alley}
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.alley)}
                      onChange={(e) => {
                        setAlley(e.target.value)
                        clearOwnerError('alley')
                      }}
                      onBlur={() => blurOwner('alley')}
                    />
                  </FormField>
                  <FormField className="flush" label={o.road} error={fieldErrors.road}>
                    <input
                      name="road"
                      maxLength={255}
                      placeholder={o.roadPh}
                      value={road}
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.road)}
                      onChange={(e) => {
                        setRoad(e.target.value)
                        clearOwnerError('road')
                      }}
                      onBlur={() => blurOwner('road')}
                    />
                  </FormField>
                  <ThaiAddressFields value={thaiAddress} onChange={patchThaiAddress} errors={fieldErrors} onBlurField={blurOwner} />
                </div>
                )}
              </section>
              <section className="owner-form-section">
                <h3>{o.payout}</h3>
                <div className="owner-payout-grid">
                  <SearchableSelect field="bankName" error={fieldErrors.bankName} onBlur={() => blurOwner('bankName')} label={o.bank} required placeholder={o.bankPh} options={[...o.banks]} value={bankName} onChange={pickOwnerField('bankName', setBankName)} />
                  <FormField className="flush" label={<>{o.accountNo} <em>*</em></>} error={fieldErrors.bankAccountNumber}>
                    <input
                      name="bankAccountNumber"
                      inputMode="numeric"
                      autoComplete="off"
                      maxLength={20}
                      placeholder={o.accountNoPh}
                      value={bankAccountNumber}
                      aria-invalid={Boolean(fieldErrors.bankAccountNumber)}
                      {...bankAccountNumberHandlers}
                      onBlur={() => blurOwner('bankAccountNumber')}
                    />
                  </FormField>
                  <FormField className="flush wide" label={<>{o.accountName} <em>*</em></>} error={fieldErrors.bankAccountName}>
                    <input name="bankAccountName" maxLength={255} placeholder={o.accountNamePh} value={bankAccountName} autoComplete="off" aria-invalid={Boolean(fieldErrors.bankAccountName)} {...bankAccountNameHandlers} onBlur={() => blurOwner('bankAccountName')} />
                  </FormField>
                </div>
              </section>
              <section className="owner-form-section documents">
                <h3>{o.docsSection}</h3>
                <p>{o.docsHint}</p>
                <div>
                  <UploadCard
                    type="IDENTITY"
                    title={o.idCard}
                    icon={IdCard}
                    labels={uploadLabels}
                    fileName={identityFile}
                    uploading={identityUploading}
                    error={fieldErrors.identityDoc}
                    onUploading={setDocUploading}
                    onUploaded={onDocUploaded}
                    onError={onDocError}
                  />
                  <UploadCard
                    type="PORTRAIT"
                    title={o.portrait}
                    icon={UserRound}
                    labels={uploadLabels}
                    fileName={portraitFile}
                    uploading={portraitUploading}
                    error={fieldErrors.portraitDoc}
                    onUploading={setDocUploading}
                    onUploaded={onDocUploaded}
                    onError={onDocError}
                  />
                </div>
              </section>
              <div className="owner-form-actions">
                <a href="/merchant/register"><ArrowLeft />{ob.common.back}</a>
                <button className="login-submit" type="submit" disabled={submitting}>{submitting ? i18n.t('validation.checking', { ns: 'merchant' }) : ob.common.next} <ArrowRight /></button>
              </div>
            </form>
          </div>
      </MerchantOnboardingLayout>
    </main>
  )
}
