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
}
