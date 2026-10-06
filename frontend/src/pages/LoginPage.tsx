import { useState } from 'react'
import {
  ArrowLeft, ArrowRight, CircleAlert, Eye, EyeOff, Heart, LoaderCircle, LockKeyhole, ShieldCheck,
  ShoppingBag, Store, Truck, UserRound,
} from 'lucide-react'
import '../styles/login.css'
import { useAuth } from '../contexts/AuthContext'

function LoginBrand() {
  return <a className="brand login-brand" href="/" aria-label="กลับหน้าแรก">
    <span className="brand-mark"><ShoppingBag size={24} /><i>✓</i></span>
    <span><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></span>
  </a>
}

const sellingPoints = [
  { icon: Truck, title: 'สินค้าหลากหลาย', text: 'จากร้านค้าไทยทั่วประเทศ' },
  { icon: ShieldCheck, title: 'ชำระเงินปลอดภัย', text: 'รองรับ QR Payment' },
  { icon: Heart, title: 'ช้อปง่าย ได้ของชัวร์', text: 'มีทีมดูแลลูกค้าเสมอ' },
]

export default function LoginPage() {
  const [passwordVisible, setPasswordVisible] = useState(false)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const { login } = useAuth()

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const identity = String(form.get('identity') ?? '').trim()
    const password = String(form.get('password') ?? '')
    if (submitting) return
    if (!identity || !password) {
      setMessage('กรุณากรอกข้อมูลบัญชีและรหัสผ่านให้ครบ')
      event.currentTarget.querySelector<HTMLInputElement>(!identity ? '#identity' : '#password')?.focus()
      return
    }
    setSubmitting(true)
    setMessage('')
    try {
      const authenticated=await login(identity, password)
      const next=new URLSearchParams(window.location.search).get('next')
      const roleDestination=authenticated.role==='ADMIN'?'/admin':authenticated.role==='MERCHANT'?'/merchant/dashboard':'/'
      const destination=next?.startsWith('/')&&!next.startsWith('//')?next:roleDestination
      window.location.replace(destination)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'เข้าสู่ระบบไม่สำเร็จ กรุณาลองใหม่')
    } finally { setSubmitting(false) }
  }

  const socialLogin = (provider: string) => setMessage(`การเข้าสู่ระบบด้วย ${provider} ยังไม่เปิดให้บริการ กรุณาใช้ข้อมูลบัญชีของคุณ`)

  return <main className="login-page">
    <section className="login-story">
      <div className="login-story-image" />
      <div className="login-story-content">
        <LoginBrand />
        <div className="story-copy">
          <span className="login-eyebrow"><span/> พื้นที่ของคนรักของไทย</span>
          <h1>ช้อปที่ใช่<br/><em>ใกล้กันกว่าเดิม</em></h1>
          <p>ค้นพบของดีจากร้านค้าไทย<br/>แล้วเติมความสุขให้ทุกวันของคุณ</p>
        </div>
        <div className="story-note"><Heart size={18}/><span>ทุกการช้อปของคุณ<br/><b>ช่วยให้ร้านค้าไทยเติบโต</b></span></div>
        <div className="login-benefits">
          {sellingPoints.map(({icon: Icon, title}) => <div key={title}><Icon size={19}/><span>{title}</span></div>)}
        </div>
      </div>
    </section>

    <section className="login-form-side">
      <nav className="login-top-nav" aria-label="เมนูหน้าเข้าสู่ระบบ"><a href="/"><ArrowLeft size={16}/> กลับไปช้อปปิ้ง</a><a href="/help">ต้องการความช่วยเหลือ?</a></nav>
      <div className="login-card">
        <div className="login-title"><span className="login-welcome-icon"><ShoppingBag size={25}/></span><span className="login-kicker">ยินดีต้อนรับกลับมา</span><h2>เข้าสู่ระบบ แล้วช้อปกัน</h2><p>ของที่ถูกใจและร้านโปรด กำลังรอคุณอยู่</p></div>
        <form onSubmit={submit} noValidate aria-busy={submitting}>
          <label className="field-label" htmlFor="identity">อีเมล</label>
          <div className="login-field"><UserRound/><input id="identity" name="identity" autoComplete="username" required aria-describedby={message ? "login-message" : undefined} placeholder="กรอกอีเมล" /></div>
          <label className="field-label" htmlFor="password">รหัสผ่าน</label>
          <div className="login-field"><LockKeyhole/><input id="password" name="password" type={passwordVisible ? 'text' : 'password'} autoComplete="current-password" required aria-describedby={message ? "login-message" : undefined} placeholder="กรอกรหัสผ่าน"/><button type="button" onClick={() => setPasswordVisible(v => !v)} aria-pressed={passwordVisible} aria-label={passwordVisible ? 'ซ่อนรหัสผ่าน' : 'แสดงรหัสผ่าน'}>{passwordVisible ? <EyeOff/> : <Eye/>}</button></div>
          <button className="forgot" type="button" onClick={() => setMessage("หากลืมรหัสผ่าน กรุณาติดต่อทีมช่วยเหลือผ่านหน้าช่วยเหลือ")}>ลืมรหัสผ่าน?</button>
          <button className="login-submit" type="submit" disabled={submitting}>{submitting ? <><LoaderCircle className="login-spinner" size={19}/> กำลังเข้าสู่ระบบ...</> : <>เข้าสู่ระบบ <ArrowRight size={19}/></>}</button>
          {message && <p id="login-message" className="login-message" role="alert"><CircleAlert size={18}/><span>{message}</span></p>}
        </form>
        <div className="login-divider"><span/>หรือเข้าสู่ระบบด้วย<span/></div>
        <div className="social-logins">
          <button type="button" disabled={submitting} onClick={() => socialLogin('Google')}><i className="google-g">G</i>Google</button>
          <button type="button" disabled={submitting} onClick={() => socialLogin('Facebook')}><i className="facebook-f">f</i>Facebook</button>
          <button type="button" disabled={submitting} onClick={() => socialLogin('Apple')}><svg className="apple-mark" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path fill="currentColor" d="M17.1 12.5c0-2 1.6-3 1.7-3.1-1-1.4-2.5-1.6-3-1.6-1.3-.1-2.5.8-3.2.8-.6 0-1.6-.8-2.7-.7-1.4 0-2.7.8-3.4 2-1.5 2.5-.4 6.3 1 8.3.6 1 1.4 2 2.5 1.9 1-.1 1.4-.6 2.7-.6 1.2 0 1.6.6 2.7.6 1.1 0 1.8-1 2.4-1.9.8-1.1 1.1-2.2 1.2-2.3-.1 0-2.3-.9-2.3-3.4ZM15 6.4c.5-.7 1-1.7.9-2.7-.9 0-2 .6-2.6 1.3-.5.6-1 1.6-.9 2.5 1 .1 1.9-.5 2.6-1.1Z"/></svg>Apple</button>
        </div>
        <p className="login-security"><ShieldCheck size={15}/> เข้าสู่ระบบเพื่อจัดการบัญชีและคำสั่งซื้อของคุณ</p><p className="signup-line">ยังไม่มีบัญชีใช่ไหม? <a href="/signup">สมัครสมาชิก</a></p>
      </div>
      <div className="login-merchant"><Store size={20}/><span>อยากเติบโตไปกับเรา? <a href="/merchant/register">เปิดร้านค้าไทย <ArrowRight size={14}/></a></span></div>
      <footer className="login-footer"><p>© 2026 ThaiShopFun · ช้อปสนุก สนับสนุนของไทย</p></footer>
    </section>
  </main>
}
