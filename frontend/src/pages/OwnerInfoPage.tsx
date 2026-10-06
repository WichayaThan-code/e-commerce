<<<<<<< HEAD
import { useState } from 'react'
import {
  ArrowLeft, ArrowRight, Bell, Building2, CalendarDays, Check, CheckCircle2,
  ChevronDown, CreditCard, Headphones, IdCard, Lightbulb, Mail, Phone,
  ShieldCheck, ShoppingBag, Upload, UserRound,
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import { ApiError, merchantApi, type DocumentType, type MerchantOwnerPayload } from '../services/api'
import '../styles/owner-info.css'

const flow=[
  {number:'✓',title:'ข้อมูลร้านค้า',text:'ชื่อร้าน ประเภทสินค้า',state:'done'},
  {number:'2',title:'ข้อมูลเจ้าของร้าน',text:'ข้อมูลส่วนตัว / ข้อมูลบริษัท',state:'active'},
  {number:'3',title:'เอกสารยืนยัน',text:'ยืนยันตัวตน และเอกสารธุรกิจ',state:''},
  {number:'4',title:'ตรวจสอบและส่ง',text:'ตรวจสอบข้อมูลก่อนส่ง',state:''},
]

function OwnerBrand(){return <a className="brand owner-brand" href="/"><span className="brand-mark"><ShoppingBag/><i>✓</i></span><span><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></span></a>}

function UploadCard({title,icon:Icon,type}:{title:string;icon:typeof IdCard;type:DocumentType}){
  const [file,setFile]=useState('')
  const [uploading,setUploading]=useState(false)
  const upload=async(selected?:File)=>{if(!selected)return;setUploading(true);setFile('กำลังอัปโหลด...');try{await merchantApi.uploadDocument(type,selected);setFile(selected.name)}catch(error){setFile(error instanceof ApiError?error.message:'อัปโหลดไม่สำเร็จ')}finally{setUploading(false)}}
  return <label className="owner-upload"><span>{title} <em>*</em></span><div className={file?'has-file':''}><Icon/><p>{file||'ลากไฟล์มาวาง หรือ คลิกเพื่ออัปโหลด'}</p><small>JPG, PNG, PDF ไม่เกิน 5 MB</small><b>{uploading?'กำลังอัปโหลด':file?'เปลี่ยนไฟล์':'เลือกไฟล์'}</b><input disabled={uploading} type="file" accept="image/jpeg,image/png,application/pdf" onChange={e=>void upload(e.target.files?.[0])}/></div></label>
}

export default function OwnerInfoPage(){
  const {user}=useAuth()
  const [ownerType,setOwnerType]=useState<'person'|'company'>('person')
  const [sameAddress,setSameAddress]=useState(true)
  const [message,setMessage]=useState('')
  const [saving,setSaving]=useState(false)
  const submit=async(event:React.FormEvent<HTMLFormElement>)=>{
    event.preventDefault()
    if(!user){window.location.assign('/login?next=/merchant/owner-info');return}
    const form=new FormData(event.currentTarget)
    const value=(name:string)=>String(form.get(name)||'')
    const payload:MerchantOwnerPayload={ownerType:ownerType==='person'?'PERSON':'COMPANY',title:value('title'),firstName:value('firstName'),lastName:value('lastName'),citizenId:value('citizenId'),birthDate:value('birthDate'),nationality:value('nationality'),phone:value('phone'),email:value('email'),houseNo:value('houseNo'),villageNo:value('villageNo'),building:value('building'),alley:value('alley'),road:value('road'),province:value('province'),district:value('district'),subdistrict:value('subdistrict'),postalCode:value('postalCode'),bankName:value('bankName'),bankAccountNumber:value('bankAccountNumber'),bankAccountName:value('bankAccountName')}
    setSaving(true);setMessage('กำลังบันทึกข้อมูลเจ้าของร้าน...')
    try{await merchantApi.saveOwner(payload);window.location.assign('/merchant/documents')}
    catch(error){setMessage(error instanceof ApiError?error.message:'ไม่สามารถบันทึกข้อมูลเจ้าของร้านได้');setSaving(false)}
  }
  return <main className="owner-page">
    <header className="owner-header"><OwnerBrand/><div className="owner-head-actions"><label><span className="mini-flag"/><select defaultValue="th" aria-label="เลือกภาษา"><option value="th">ไทย</option><option value="en">English</option></select><ChevronDown/></label><button aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><a href="/dashboard"><span>{(user?.firstName||'S').charAt(0)}</span>{user?`${user.firstName} ${user.lastName}`:'Sample Store'}<ChevronDown/></a></div></header>
    <div className="owner-layout">
      <aside className="owner-sidebar">
        <a className="back-home" href="/"><ArrowLeft/>กลับสู่หน้าหลัก</a><h2>สมัครเป็นร้านค้า</h2><p>สร้างร้านค้าของคุณกับ ThaiShopFun<br/>ง่ายๆ ในไม่กี่ขั้นตอน</p>
        <div className="owner-flow">{flow.map(step=><div className={step.state} key={step.number}><span>{step.number}</span><p><b>{step.title}</b><small>{step.text}</small></p></div>)}</div>
        <div className="owner-help"><Headphones/><p><b>ต้องการความช่วยเหลือ?</b><span>ทีมงานพร้อมดูแลคุณ</span><a href="#contact">ติดต่อทีมงาน</a></p></div>
        <blockquote>ร้านค้าไทย<br/>เติบโตไปด้วยกัน<i/></blockquote>
      </aside>

      <section className="owner-content">
        <div className="owner-card"><div className="owner-title"><h1>ข้อมูลเจ้าของร้าน</h1><p>กรุณากรอกข้อมูลของเจ้าของร้านให้ครบถ้วน เพื่อยืนยันตัวตนและติดต่อได้สะดวก</p></div>
          <form onSubmit={submit} noValidate>
            <fieldset className="owner-type"><legend>ประเภทเจ้าของร้าน</legend><div><button type="button" className={ownerType==='person'?'active':''} onClick={()=>setOwnerType('person')}><i>{ownerType==='person'?<Check/>:null}</i><UserRound/><span><b>บุคคลธรรมดา</b><small>เจ้าของกิจการรายบุคคล</small></span></button><button type="button" className={ownerType==='company'?'active':''} onClick={()=>setOwnerType('company')}><i>{ownerType==='company'?<Check/>:null}</i><Building2/><span><b>นิติบุคคล</b><small>บริษัท / ห้างหุ้นส่วน / อื่นๆ</small></span></button></div></fieldset>
            <section className="owner-form-section"><h3>ข้อมูลส่วนตัว</h3><div className="owner-person-grid"><label>คำนำหน้า <em>*</em><select name="title" defaultValue="นาย"><option>นาย</option><option>นาง</option><option>นางสาว</option></select></label><label>ชื่อ <em>*</em><input name="firstName" defaultValue={user?.firstName||'สมชาย'}/></label><label>นามสกุล <em>*</em><input name="lastName" defaultValue={user?.lastName||'ใจดี'}/></label><label>เลขบัตรประชาชน <em>*</em><input name="citizenId" inputMode="numeric" placeholder="1 2345 67890 12 3"/></label><label>วันเดือนปีเกิด <em>*</em><div className="owner-icon-field"><input name="birthDate" type="date"/><CalendarDays/></div></label><label>สัญชาติ <em>*</em><select name="nationality" defaultValue="ไทย"><option>ไทย</option><option>อื่นๆ</option></select></label></div><div className="owner-contact-grid"><label>เบอร์โทรศัพท์ <em>*</em><div className="owner-icon-field left"><Phone/><input name="phone" defaultValue={user?.phone||'081 234 5678'}/></div></label><label>อีเมล <em>*</em><div className="owner-icon-field left"><Mail/><input name="email" type="email" defaultValue={user?.email==='admin'?'admin@thaishopfun.local':user?.email||'somchai@example.com'}/></div></label></div></section>
            <section className="owner-form-section address"><div className="section-row"><h3>ที่อยู่ตามบัตรประชาชน</h3><label className="same-address"><input type="checkbox" checked={sameAddress} onChange={e=>setSameAddress(e.target.checked)}/><span><Check/></span>ใช้ที่อยู่เดียวกับที่อยู่ร้านค้า</label></div><div className="owner-address-grid"><label>เลขที่ <em>*</em><input name="houseNo" placeholder="123/45"/></label><label>หมู่ที่<input name="villageNo" placeholder="5"/></label><label className="wide">หมู่บ้าน / อาคาร<input name="building" placeholder="หมู่บ้านสุขสันต์"/></label><label>ซอย<input name="alley" placeholder="สุขุมวิท 101"/></label><label className="wide">ถนน<input name="road" placeholder="สุขุมวิท"/></label><label>จังหวัด <em>*</em><select name="province" defaultValue=""><option value="" disabled>เลือกจังหวัด</option><option>กรุงเทพมหานคร</option><option>เชียงใหม่</option><option>ขอนแก่น</option></select></label><label>อำเภอ / เขต <em>*</em><select name="district" defaultValue=""><option value="" disabled>เลือกอำเภอ</option><option>บางนา</option><option>บางรัก</option><option>เมือง</option></select></label><label>ตำบล / แขวง <em>*</em><input name="subdistrict" placeholder="บางนา"/></label><label>รหัสไปรษณีย์ <em>*</em><input name="postalCode" inputMode="numeric" maxLength={5} placeholder="10260"/></label></div></section>
            <section className="owner-form-section"><h3>ข้อมูลรับเงิน</h3><div className="owner-person-grid"><label>ธนาคาร <em>*</em><select name="bankName" defaultValue=""><option value="" disabled>เลือกธนาคาร</option><option value="KBANK - ธนาคารกสิกรไทย">KBANK - ธนาคารกสิกรไทย</option><option value="SCB - ธนาคารไทยพาณิชย์">SCB - ธนาคารไทยพาณิชย์</option><option value="KTB - ธนาคารกรุงไทย">KTB - ธนาคารกรุงไทย</option></select></label><label>เลขบัญชี <em>*</em><input name="bankAccountNumber" inputMode="numeric" placeholder="123-4-56789-0"/></label><label>ชื่อบัญชี <em>*</em><input name="bankAccountName" placeholder="ชื่อ–นามสกุลเจ้าของบัญชี"/></label></div></section>
            <section className="owner-form-section documents"><h3>เอกสารยืนยันตัวตน</h3><p>อัปโหลดเอกสารเพื่อยืนยันตัวตน (รองรับไฟล์ JPG, PNG, PDF ขนาดไม่เกิน 5 MB)</p><div><UploadCard type="IDENTITY" title="บัตรประชาชน" icon={IdCard}/><UploadCard type="PORTRAIT" title="รูปถ่ายหน้าตรงคู่บัตรประชาชน" icon={UserRound}/></div></section>
            <div className="owner-form-actions"><a href="/merchant/register"><ArrowLeft/>ย้อนกลับ</a><button type="submit" disabled={saving}>{saving?'กำลังบันทึก...':'ถัดไป'} <ArrowRight/></button></div>{message&&<p className="owner-message" role="status">{message}</p>}
          </form>
        </div>
      </section>

      <aside className="owner-notes"><section className="safe-note"><div><IdCard/><CheckCircle2/></div><h3>ข้อมูลของคุณจะปลอดภัย</h3><ul><li>เข้ารหัสข้อมูล (SSL)</li><li>ใช้เฉพาะสำหรับยืนยันตัวตน</li><li>ไม่มีเปิดเผยข้อมูลต่อสาธารณะ</li><li>เป็นไปตาม PDPA</li></ul></section><section className="tips-note"><h3><Lightbulb/>คำแนะนำ</h3><ul><li>กรอกข้อมูลให้ตรงกับเอกสารจริง</li><li>อัปโหลดภาพที่ชัดเจน อ่านได้</li><li>ไฟล์ไม่ควรเกิน 5 MB</li><li>รองรับไฟล์ JPG, PNG, PDF</li></ul></section><section className="question-note"><Headphones/><div><h3>มีคำถาม?</h3><p>ทีมงานพร้อมช่วยเหลือคุณ<br/>ทุกขั้นตอน</p></div><a href="#contact">ติดต่อทีมงาน</a></section><blockquote>Good Shops<br/>Brighter Thailand<i/></blockquote></aside>
    </div>
  </main>
=======
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
                  <label className="owner-field">{o.firstName} <em>*</em><input value={firstName} onChange={(e) => setFirstName(e.target.value)} /></label>
                  <label className="owner-field">{o.lastName} <em>*</em><input value={lastName} onChange={(e) => setLastName(e.target.value)} /></label>
                  <label className="owner-field">{o.citizenId} <em>*</em><input inputMode="numeric" placeholder={o.citizenPh} value={citizenId} onChange={(e) => setCitizenId(e.target.value)} /></label>
                  <label className="owner-field">{o.birthDate} <em>*</em><span className="owner-icon-field"><input type="date" value={birthDate} onChange={(e) => setBirthDate(e.target.value)} /><CalendarDays /></span></label>
                  <SearchableSelect label={o.nationality} required options={[...o.nationalities]} value={nationality} onChange={setNationality} />
                </div>
                <div className="owner-contact-grid">
                  <label className="owner-field">{o.phone} <em>*</em><span className="owner-icon-field left"><Phone /><input value={phone} onChange={(e) => setPhone(e.target.value)} /></span></label>
                  <label className="owner-field">{o.email} <em>*</em><span className="owner-icon-field left"><Mail /><input type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></span></label>
                </div>
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
                  <label className="owner-field">{o.houseNo} <em>*</em><input placeholder="123/45" value={houseNo} onChange={(e) => setHouseNo(e.target.value)} /></label>
                  <label className="owner-field">{o.villageNo}<input placeholder="5" value={villageNo} onChange={(e) => setVillageNo(e.target.value)} /></label>
                  <label className="owner-field wide">{o.building}<input placeholder="หมู่บ้านสุขสันต์" value={building} onChange={(e) => setBuilding(e.target.value)} /></label>
                  <label className="owner-field">{o.alley}<input placeholder="สุขุมวิท 101" value={alley} onChange={(e) => setAlley(e.target.value)} /></label>
                  <label className="owner-field">{o.road}<input placeholder="สุขุมวิท" value={road} onChange={(e) => setRoad(e.target.value)} /></label>
                  <ThaiAddressFields value={thaiAddress} onChange={setThaiAddress} />
                </div>
                )}
              </section>
              <section className="owner-form-section">
                <h3>{o.payout}</h3>
                <div className="owner-payout-grid">
                  <SearchableSelect label={o.bank} required placeholder={o.bankPh} options={[...o.banks]} value={bankName} onChange={setBankName} />
                  <label className="owner-field">{o.accountNo} <em>*</em><input inputMode="numeric" placeholder={o.accountNoPh} value={bankAccountNumber} onChange={(e) => setBankAccountNumber(e.target.value)} /></label>
                  <label className="owner-field wide">{o.accountName} <em>*</em><input placeholder={o.accountNamePh} value={bankAccountName} onChange={(e) => setBankAccountName(e.target.value)} /></label>
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
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
}
