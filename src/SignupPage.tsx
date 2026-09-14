import { useMemo, useState } from 'react'
import {
  Check, CheckCircle2, Eye, EyeOff, Gift, Headphones, Heart, LockKeyhole,
  Mail, PackageCheck, Phone, ShieldCheck, ShoppingBag, Store, Truck, UsersRound,
} from 'lucide-react'
import './signup.css'
import { useAuth } from './AuthContext'

function SignupBrand() {
  return <a className="brand signup-brand" href="/" aria-label="กลับหน้าแรก">
    <span className="brand-mark"><ShoppingBag size={24}/><i>✓</i></span>
    <span><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></span>
  </a>
}

const perks = [
  {icon: Gift, title:'สินค้าหลากหลาย', text:'จากร้านค้าไทยทั่วประเทศ'},
  {icon: ShieldCheck, title:'ชำระเงินปลอดภัย', text:'รองรับหลายช่องทาง'},
  {icon: Truck, title:'จัดส่งทั่วไทย', text:'ติดตามสถานะได้ตลอด'},
  {icon: Heart, title:'สนับสนุนผู้ประกอบการไทย', text:'ช้อปง่าย ได้ของดี มีความสุข'},
]

export default function SignupPage(){
  const [password,setPassword]=useState('')
  const [confirm,setConfirm]=useState('')
  const [showPassword,setShowPassword]=useState(false)
  const [showConfirm,setShowConfirm]=useState(false)
  const [message,setMessage]=useState('')
  const [submitting,setSubmitting]=useState(false)
  const {register}=useAuth()
  const rules=useMemo(()=>({length:password.length>=8,number:/\d/.test(password),letter:/[A-Za-z]/.test(password)}),[password])

  const submit=async(event:React.FormEvent<HTMLFormElement>)=>{
    event.preventDefault()
    const data=new FormData(event.currentTarget)
    const required=['firstName','lastName','email','phone']
    if(required.some(key=>!String(data.get(key)??'').trim())||!password||!confirm){setMessage('กรุณากรอกข้อมูลให้ครบทุกช่อง');return}
    if(!Object.values(rules).every(Boolean)){setMessage('รหัสผ่านยังไม่ตรงตามเงื่อนไข');return}
    if(password!==confirm){setMessage('รหัสผ่านทั้งสองช่องไม่ตรงกัน');return}
    if(!data.get('terms')){setMessage('กรุณายอมรับข้อกำหนดการใช้งาน');return}
    setSubmitting(true)
    setMessage('กำลังสร้างบัญชี...')
    try{
      await register({firstName:String(data.get('firstName')).trim(),lastName:String(data.get('lastName')).trim(),email:String(data.get('email')).trim(),phone:String(data.get('phone')).trim(),password})
      window.location.replace('/dashboard')
    }catch(error){setMessage(error instanceof Error?error.message:'สมัครสมาชิกไม่สำเร็จ กรุณาลองใหม่')}
    finally{setSubmitting(false)}
  }

  return <main className="signup-page">
    <header className="signup-header">
      <SignupBrand/>
      <div className="signup-header-actions"><span>มีบัญชีอยู่แล้ว?</span><a href="/login">เข้าสู่ระบบ</a><label><span className="mini-flag"/><select aria-label="เลือกภาษา" defaultValue="th"><option value="th">ไทย</option><option value="en">English</option></select></label></div>
    </header>

    <div className="signup-main">
      <section className="signup-story">
        <div className="signup-photo"/>
        <div className="signup-story-content">
          <h1>สมัครสมาชิก<br/>เริ่มช้อปของดีไทยได้เลย</h1><span className="signup-swipe"/>
          <p>สินค้าคุณภาพ จากร้านค้าไทย<br/>ส่งตรงถึงบ้านคุณ</p>
          <div className="signup-perks">{perks.map(({icon:Icon,title,text})=><div key={title}><Icon/><p><b>{title}</b><span>{text}</span></p></div>)}</div>
          <p className="signup-handwriting">ช้อปไทย<br/>ให้ไทยไปไกลกว่าเดิม <i>♡</i></p>
          <aside className="support-card"><span className="tiny-store">🏪</span><div><b>ร่วมเป็นส่วนหนึ่ง<br/>ในการสนับสนุนสินค้าไทย</b><p>ช้อปง่าย ปลอดภัย มั่นใจ ได้ของดี 🇹🇭</p></div></aside>
        </div>
      </section>

      <section className="signup-form-side">
        <div className="signup-card">
          <div className="signup-card-head"><div><h2>สมัครสมาชิก</h2><p>สร้างบัญชีเพื่อเริ่มต้นการช้อปกับ ThaiShopFun</p></div><div className="signup-steps">{[['1','ข้อมูลบัญชี'],['2','ข้อมูลเพิ่มเติม'],['3','เสร็จสิ้น']].map(([n,label],i)=><div className={i===0?'active':''} key={n}><span>{n}</span><small>{label}</small></div>)}</div></div>
          <form onSubmit={submit} noValidate>
            <div className="signup-two"><label>ชื่อ<input name="firstName" placeholder="ชื่อจริง" autoComplete="given-name"/></label><label>นามสกุล<input name="lastName" placeholder="นามสกุล" autoComplete="family-name"/></label></div>
            <label>อีเมล<div className="signup-input"><Mail/><input name="email" type="email" placeholder="ตัวอย่าง: name@example.com" autoComplete="email"/></div></label>
            <label>เบอร์โทรศัพท์<div className="signup-input"><Phone/><input name="phone" inputMode="tel" placeholder="ตัวอย่าง: 0812345678" autoComplete="tel"/></div></label>
            <label>รหัสผ่าน <span className="password-rules"><i className={rules.length?'ok':''}><Check/>อย่างน้อย 8 ตัว</i><i className={rules.number?'ok':''}><Check/>มีตัวเลข</i><i className={rules.letter?'ok':''}><Check/>มีตัวอักษร</i></span><div className="signup-input"><LockKeyhole/><input value={password} onChange={e=>setPassword(e.target.value)} type={showPassword?'text':'password'} placeholder="ตั้งรหัสผ่าน (อย่างน้อย 8 ตัวอักษร)" autoComplete="new-password"/><button type="button" onClick={()=>setShowPassword(v=>!v)} aria-label="แสดงหรือซ่อนรหัสผ่าน">{showPassword?<EyeOff/>:<Eye/>}</button></div></label>
            <label>ยืนยันรหัสผ่าน<div className="signup-input"><LockKeyhole/><input value={confirm} onChange={e=>setConfirm(e.target.value)} type={showConfirm?'text':'password'} placeholder="กรอกรหัสผ่านอีกครั้ง" autoComplete="new-password"/><button type="button" onClick={()=>setShowConfirm(v=>!v)} aria-label="แสดงหรือซ่อนรหัสผ่าน">{showConfirm?<EyeOff/>:<Eye/>}</button></div></label>
            <label className="terms-check"><input name="terms" type="checkbox"/><span>ฉันได้อ่านและยอมรับ <a href="#terms">ข้อกำหนดการใช้งาน</a> และ <a href="#privacy">นโยบายความเป็นส่วนตัว</a></span></label>
            <button className="signup-submit" type="submit" disabled={submitting}>{submitting?'กำลังสมัครสมาชิก...':'สมัครสมาชิก'}</button>
            {message&&<p className="signup-message" role="status">{message}</p>}
          </form>
          <div className="signup-divider"><span/>หรือสมัครผ่าน<span/></div>
          <div className="signup-social"><button><i className="google-g">G</i><span>สมัครด้วย Google</span></button><button><i className="facebook-f">f</i><span>สมัครด้วย Facebook</span></button><button><i className="apple-mark">●</i><span>สมัครด้วย Apple</span></button></div>
          <div className="privacy-card"><CheckCircle2/><div><b>ข้อมูลของคุณปลอดภัย</b><p>เราให้ความสำคัญกับการปกป้องข้อมูลส่วนบุคคลของคุณ</p></div></div>
        </div>
      </section>
    </div>

    <footer className="signup-stats"><div><UsersRound/><p><b>100,000+</b><span>ลูกค้าที่ไว้วางใจ</span></p></div><div><Store/><p><b>5,000+</b><span>ร้านค้าไทย</span></p></div><div><PackageCheck/><p><b>1,000,000+</b><span>คำสั่งซื้อที่จัดส่งแล้ว</span></p></div><div><Headphones/><p><b>บริการลูกค้า</b><span>พร้อมดูแลคุณเสมอ</span></p></div></footer>
  </main>
}
