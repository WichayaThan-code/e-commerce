import { useState } from 'react'
import {
  Eye, EyeOff, Heart, LockKeyhole, Mail, PackageCheck, ShieldCheck,
  ShoppingBag, Store, Truck, UserRound, UsersRound,
} from 'lucide-react'
import './login.css'
import { useAuth } from './AuthContext'

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
    if (!identity || !password) { setMessage('กรุณากรอกอีเมลและรหัสผ่านให้ครบ'); return }
    setSubmitting(true)
    setMessage('กำลังตรวจสอบข้อมูลเข้าสู่ระบบ...')
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

  const socialLogin = (provider: string) => setMessage(`กำลังเชื่อมต่อกับ ${provider}...`)

  return <main className="login-page">
    <section className="login-story">
      <div className="login-story-image" />
      <div className="login-story-content">
        <LoginBrand />
        <div className="story-copy">
          <h1>ช้อปของดี<br/>สนับสนุนของไทย</h1>
          <span className="orange-swipe" />
          <p>สินค้าคุณภาพ จากร้านค้าไทย<br/>ส่งตรงถึงบ้านคุณ</p>
          <div className="selling-points">
            {sellingPoints.map(({icon: Icon, title, text}) => <div className="selling-point" key={title}>
              <Icon /><div><b>{title}</b><span>{text}</span></div>
            </div>)}
          </div>
        </div>
        <p className="story-handwriting">ช้อปไทย<br/>ให้ไทยไปไกลกว่าเดิม <i>♡</i></p>
        <div className="login-stats">
          <div><UsersRound/><p><b>100,000+</b><span>ลูกค้าที่ไว้วางใจ</span></p></div>
          <div><Store/><p><b>5,000+</b><span>ร้านค้าไทย</span></p></div>
          <div><PackageCheck/><p><b>1,000,000+</b><span>คำสั่งซื้อที่จัดส่งแล้ว</span></p></div>
        </div>
      </div>
    </section>

    <section className="login-form-side">
      <label className="language-picker" aria-label="เลือกภาษา"><span className="mini-flag"/><select defaultValue="th"><option value="th">ไทย</option><option value="en">English</option></select></label>
      <div className="login-card">
        <div className="login-title"><h2>เข้าสู่ระบบ</h2><p>ยินดีต้อนรับกลับมา<br/>เข้าสู่ระบบเพื่อเริ่มต้นช้อปปิ้งกับ ThaiShopFun</p></div>
        <form onSubmit={submit} noValidate>
          <label className="field-label" htmlFor="identity">ชื่อผู้ใช้ อีเมล หรือ เบอร์โทรศัพท์</label>
          <div className="login-field"><UserRound/><input id="identity" name="identity" autoComplete="username" placeholder="กรอกชื่อผู้ใช้ อีเมล หรือ เบอร์โทรศัพท์" /></div>
          <label className="field-label" htmlFor="password">รหัสผ่าน</label>
          <div className="login-field"><LockKeyhole/><input id="password" name="password" type={passwordVisible ? 'text' : 'password'} autoComplete="current-password" placeholder="กรอกรหัสผ่าน"/><button type="button" onClick={() => setPasswordVisible(v => !v)} aria-label={passwordVisible ? 'ซ่อนรหัสผ่าน' : 'แสดงรหัสผ่าน'}>{passwordVisible ? <EyeOff/> : <Eye/>}</button></div>
          <a className="forgot" href="#forgot">ลืมรหัสผ่าน?</a>
          <button className="login-submit" type="submit" disabled={submitting}>{submitting ? 'กำลังเข้าสู่ระบบ...' : 'เข้าสู่ระบบ'}</button>
          {message && <p className="login-message" role="status">{message}</p>}
        </form>
        <div className="login-divider"><span/>หรือ<span/></div>
        <div className="social-logins">
          <button onClick={() => socialLogin('Google')}><i className="google-g">G</i>เข้าสู่ระบบด้วย Google</button>
          <button onClick={() => socialLogin('Facebook')}><i className="facebook-f">f</i>เข้าสู่ระบบด้วย Facebook</button>
          <button onClick={() => socialLogin('Apple')}><i className="apple-mark">●</i>เข้าสู่ระบบด้วย Apple</button>
        </div>
        <p className="signup-line">ยังไม่มีบัญชีใช่ไหม? <a href="/signup">สมัครสมาชิก</a></p>
      </div>
      <footer className="login-footer"><a href="#terms">ข้อกำหนดการใช้งาน</a><i/> <a href="#privacy">นโยบายความเป็นส่วนตัว</a><i/> <a href="#contact">ติดต่อเรา</a><p>© 2026 ThaiShopFun. สงวนลิขสิทธิ์ทุกประการ</p></footer>
    </section>
  </main>
}
