import { useEffect, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Building2, CalendarDays, Check, CheckCircle2,
  Headphones, IdCard, Lightbulb, Mail, Phone, UserRound,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../contexts/AuthContext'
import { useLanguage } from '../contexts/LanguageContext'
import MerchantOnboardingSidebar from '../components/merchant/MerchantOnboardingSidebar'
import MerchantHeader from '../components/MerchantHeader'
import SearchableSelect from '../components/SearchableSelect'
import ThaiAddressFields, { emptyThaiAddress, type ThaiAddressValue } from '../components/ThaiAddressFields'
import { ApiError, merchantApi, type DocumentType, type MerchantOwner } from '../services/api'
import FormField from '../components/form/FormField'
import FormGrid from '../components/form/FormGrid'
import { SHOP_IMAGE_ACCEPT } from '../services/shopImage'
import { readMerchantDraft, rememberDocumentName, writeMerchantDraft, type OwnerDraft } from '../services/merchantDraft'
import '../styles/owner-info.css'
import '../styles/form-fields.css'

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

function UploadCard({ title, icon: Icon, type, labels }: { title: string; icon: typeof IdCard; type: DocumentType; labels: { drop: string; types: string; pick: string; change: string; uploading: string; fail: string } }) {
  const { user } = useAuth()
  const [file, setFile] = useState(readMerchantDraft(user?.id).documentNames?.[type] || '')
  const [uploading, setUploading] = useState(false)
  const upload = async (selected?: File) => {
    if (!selected) return
    setUploading(true)
    setFile(labels.uploading)
    try {
      await merchantApi.uploadDocument(type, selected)
      setFile(selected.name)
      rememberDocumentName(user?.id, type, selected.name)
    } catch (error) {
      setFile(error instanceof ApiError ? error.message : labels.fail)
    } finally {
      setUploading(false)
    }
  }
  return (
    <label className="owner-upload">
      <span>{title} <em>*</em></span>
      <div className={file ? 'has-file' : ''}>
        <Icon />
        <p>{file && file !== labels.uploading && file !== labels.fail ? file : labels.drop}</p>
        <small>{labels.types}</small>
        <b>{uploading ? labels.uploading : file && file !== labels.fail ? labels.change : labels.pick}</b>
        <input disabled={uploading} type="file" accept={`${SHOP_IMAGE_ACCEPT},application/pdf`} onChange={(e) => void upload(e.target.files?.[0])} />
      </div>
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
  const [ownerType, setOwnerType] = useState<OwnerDraft['ownerType']>(saved?.ownerType || 'person')
  const [title, setTitle] = useState(saved?.title || o.titles[0])
  const [firstName, setFirstName] = useState(saved?.firstName || user?.firstName || '')
  const [lastName, setLastName] = useState(saved?.lastName || user?.lastName || '')
  const [citizenId, setCitizenId] = useState(saved?.citizenId || '')
  const [birthDate, setBirthDate] = useState(saved?.birthDate || '')
  const [nationality, setNationality] = useState(saved?.nationality || o.nationalities[0])
  const [phone, setPhone] = useState(saved?.phone || user?.phone || '')
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
  const [bankAccountNumber, setBankAccountNumber] = useState(saved?.bankAccountNumber || '')
  const [bankAccountName, setBankAccountName] = useState(saved?.bankAccountName || '')
  const [message, setMessage] = useState('')
  const [owners, setOwners] = useState<MerchantOwner[]>([])
  const [adding, setAdding] = useState(false)
  const [picking, setPicking] = useState(false)

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
    setHouseNo(draft.address || '')
    setVillageNo('')
    setBuilding('')
    setAlley('')
    setRoad('')
    setThaiAddress(shopAddress(user.id))
  }, [sameAddress, user])

  useEffect(() => {
    if (!user || saved?.firstName) return
    setFirstName((current) => current || user.firstName || '')
    setLastName((current) => current || user.lastName || '')
    setPhone((current) => current || user.phone || '')
    setEmail((current) => current || (user.email === 'admin' ? '' : user.email || ''))
  }, [user])

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (!user) {
      window.location.assign('/login?next=/merchant/owner-info')
      return
    }
    if (!firstName.trim() || !lastName.trim() || !citizenId.trim() || !birthDate || !phone.trim() || !email.trim()) {
      setMessage(o.errPersonal)
      return
    }
    if (citizenId.replace(/\D/g, '').length < 13) {
      setMessage(o.errCitizen)
      return
    }
    if (!houseNo.trim() || !thaiAddress.province || !thaiAddress.district || !thaiAddress.subdistrict || !thaiAddress.postalCode) {
      setMessage(o.errAddress)
      return
    }
    if (!bankName || !bankAccountNumber.trim() || !bankAccountName.trim()) {
      setMessage(o.errBank)
      return
    }
    const owner: OwnerDraft = {
      ownerType, title, firstName: firstName.trim(), lastName: lastName.trim(), citizenId: citizenId.trim(),
      birthDate, nationality, phone: phone.trim(), email: email.trim(), houseNo: houseNo.trim(),
      villageNo: villageNo.trim(), building: building.trim(), alley: alley.trim(), road: road.trim(),
      province: thaiAddress.province, district: thaiAddress.district, subdistrict: thaiAddress.subdistrict,
      postalCode: thaiAddress.postalCode, sameAddress, bankName, bankAccountNumber: bankAccountNumber.trim(),
      bankAccountName: bankAccountName.trim(),
    }
    writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), owner, ownerId: undefined })
    navigate('/merchant/documents')
  }

  const reuseOwner = async (ownerId: string) => {
    if (!user || picking) return
    setPicking(true)
    setMessage('')
    try {
      await merchantApi.attachOwner(ownerId)
      writeMerchantDraft(user.id, { ...readMerchantDraft(user.id), ownerId, documentsConfirmed: true })
      navigate('/merchant/review')
    } catch (error) {
      setMessage(error instanceof ApiError ? (error.code === 'CITIZEN_ID_TAKEN' ? ob.review.errCitizenTaken : error.message) : o.errPersonal)
    } finally {
      setPicking(false)
    }
  }

  return (
    <main className="owner-page">
      <MerchantHeader />
      <div className="owner-layout">
        <MerchantOnboardingSidebar copy={ob} activeStep={1} className="owner-sidebar" />

        <section className="owner-content">
          <div className="owner-card">
            <div className="owner-title"><h1>{o.pageTitle}</h1><p>{o.pageLead}</p></div>
            {owners.length > 0 && !adding ? (
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
            {owners.length > 0 && adding ? <button type="button" className="owner-choice-back" onClick={() => setAdding(false)}>{o.reuseLabel}</button> : null}
            <form onSubmit={submit} hidden={owners.length > 0 && !adding} noValidate>
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
                  <SearchableSelect label={o.title} required options={[...o.titles]} value={title} onChange={setTitle} />
                  <FormField className="flush" label={<>{o.firstName} <em>*</em></>} icon={UserRound}>
                    <input value={firstName} autoComplete="given-name" onChange={(e) => setFirstName(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={<>{o.lastName} <em>*</em></>}>
                    <input value={lastName} autoComplete="family-name" onChange={(e) => setLastName(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={<>{o.citizenId} <em>*</em></>} icon={IdCard}>
                    <input inputMode="numeric" placeholder={o.citizenPh} value={citizenId} onChange={(e) => setCitizenId(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={<>{o.birthDate} <em>*</em></>} icon={CalendarDays}>
                    <input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} />
                  </FormField>
                  <SearchableSelect label={o.nationality} required options={[...o.nationalities]} value={nationality} onChange={setNationality} />
                </div>
                <FormGrid>
                  <FormField className="flush" label={<>{o.phone} <em>*</em></>} icon={Phone}>
                    <input type="tel" inputMode="tel" autoComplete="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={<>{o.email} <em>*</em></>} icon={Mail}>
                    <input type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} />
                  </FormField>
                </FormGrid>
              </section>
              <section className="owner-form-section">
                <div className="section-row">
                  <h3>{o.idAddress}</h3>
                  <label className="same-address">
                    <input type="checkbox" checked={sameAddress} onChange={(e) => setSameAddress(e.target.checked)} />
                    <span><Check /></span>{o.sameShopAddress}
                  </label>
                </div>
                {sameAddress ? (
                  <AddressPreview userId={user?.id} emptyLabel={o.sameAddressEmpty} />
                ) : (
                <div className="owner-address-grid">
                  <FormField className="flush" label={<>{o.houseNo} <em>*</em></>}>
                    <input placeholder="123/45" value={houseNo} onChange={(e) => setHouseNo(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={o.villageNo}>
                    <input placeholder="5" value={villageNo} onChange={(e) => setVillageNo(e.target.value)} />
                  </FormField>
                  <FormField className="flush wide" label={o.building}>
                    <input placeholder="หมู่บ้านสุขสันต์" value={building} onChange={(e) => setBuilding(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={o.alley}>
                    <input placeholder="สุขุมวิท 101" value={alley} onChange={(e) => setAlley(e.target.value)} />
                  </FormField>
                  <FormField className="flush" label={o.road}>
                    <input placeholder="สุขุมวิท" value={road} onChange={(e) => setRoad(e.target.value)} />
                  </FormField>
                  <ThaiAddressFields value={thaiAddress} onChange={setThaiAddress} />
                </div>
                )}
              </section>
              <section className="owner-form-section">
                <h3>{o.payout}</h3>
                <div className="owner-payout-grid">
                  <SearchableSelect label={o.bank} required placeholder={o.bankPh} options={[...o.banks]} value={bankName} onChange={setBankName} />
                  <FormField className="flush" label={<>{o.accountNo} <em>*</em></>}>
                    <input inputMode="numeric" placeholder={o.accountNoPh} value={bankAccountNumber} onChange={(e) => setBankAccountNumber(e.target.value)} />
                  </FormField>
                  <FormField className="flush wide" label={<>{o.accountName} <em>*</em></>}>
                    <input placeholder={o.accountNamePh} value={bankAccountName} onChange={(e) => setBankAccountName(e.target.value)} />
                  </FormField>
                </div>
              </section>
              <section className="owner-form-section documents">
                <h3>{o.docsSection}</h3>
                <p>{o.docsHint}</p>
                <div>
                  <UploadCard type="IDENTITY" title={o.idCard} icon={IdCard} labels={uploadLabels} />
                  <UploadCard type="PORTRAIT" title={o.portrait} icon={UserRound} labels={uploadLabels} />
                </div>
              </section>
              <div className="owner-form-actions">
                <a href="/merchant/register"><ArrowLeft />{ob.common.back}</a>
                <button className="login-submit" type="submit">{ob.common.next} <ArrowRight /></button>
              </div>
              {message && <p className="owner-message" role="alert">{message}</p>}
            </form>
          </div>
        </section>

        <aside className="owner-notes">
          <section className="safe-note"><div><IdCard /><CheckCircle2 /></div><h3>{o.safeTitle}</h3><ul>{o.safeBullets.map((line) => <li key={line}>{line}</li>)}</ul></section>
          <section className="tips-note"><h3><Lightbulb />{o.tipsTitle}</h3><ul>{o.tipsBullets.map((line) => <li key={line}>{line}</li>)}</ul></section>
          <section className="question-note"><Headphones /><div><h3>{o.questionTitle}</h3><p>{o.questionLead.split('\n').map((line, i) => <span key={i}>{line}{i === 0 && <br />}</span>)}</p></div><a href="#contact">{ob.common.contact}</a></section>
          <blockquote>{ob.common.quoteEn.split('\n').map((line, i) => <span key={i}>{line}{i === 0 && <br />}</span>)}<i /></blockquote>
        </aside>
      </div>
    </main>
  )
}
