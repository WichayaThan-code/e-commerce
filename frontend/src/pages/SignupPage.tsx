import { useMemo, useRef, useState } from 'react'
import {
  ArrowLeft, ArrowRight, Check, CircleAlert, Eye, EyeOff, Gift, Heart, LoaderCircle, LockKeyhole,
  Mail, Phone, ShieldCheck, ShoppingBag, Store, Truck,
} from 'lucide-react'
<<<<<<< HEAD
import '../styles/login.css'
import '../styles/signup.css'
import SignupAgreement, { type AgreementStep } from '../components/SignupAgreement'
import { useAuth } from '../contexts/AuthContext'

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

// Field order determines which invalid field receives focus first.
const labels:Record<string,string>={firstName:'ชื่อ',lastName:'นามสกุล',email:'อีเมล',phone:'เบอร์โทรศัพท์',password:'รหัสผ่าน',confirmPassword:'ยืนยันรหัสผ่าน'}
const passwordRules=(value:string)=>({length:value.length>=8,number:/\d/.test(value),letter:/[A-Za-z]/.test(value)})

export default function SignupPage(){
  const [password,setPassword]=useState('')
  const [confirm,setConfirm]=useState('')
  const [showPassword,setShowPassword]=useState(false)
  const [showConfirm,setShowConfirm]=useState(false)
  const [message,setMessage]=useState('')
  const [submitting,setSubmitting]=useState(false)
  const [errors,setErrors]=useState<Record<string,string>>({})
  const formRef=useRef<HTMLFormElement>(null)
  const submitRef=useRef<HTMLButtonElement>(null)
  const registrationPending=useRef(false)
  const [agreement,setAgreement]=useState<AgreementStep|null>(null)
  const [agreementError,setAgreementError]=useState('')
  const {register}=useAuth()
  const rules=useMemo(()=>passwordRules(password),[password])

  const focusField=(name:string)=>(formRef.current?.elements.namedItem(name) as HTMLInputElement|null)?.focus()

  const validate=(data:FormData):Record<string,string>=>{
    const next:Record<string,string>={}
    for(const [name,label] of Object.entries(labels)) {
      if(!String(data.get(name)??'').trim()) next[name]=`กรุณากรอก${label}`
    }
    if(!next.email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(data.get('email')).trim())) next.email='กรุณากรอกอีเมลให้ถูกต้อง เช่น name@example.com'
    if(!next.phone && !/^0\d{8,9}$/.test(String(data.get('phone')).replace(/[\s-]/g,''))) next.phone='กรุณากรอกเบอร์โทรศัพท์ไทยให้ครบ เช่น 0812345678'
    if(!next.password && !Object.values(passwordRules(password)).every(Boolean)) next.password='ใช้รหัสผ่านอย่างน้อย 8 ตัว พร้อมตัวอักษรภาษาอังกฤษและตัวเลข'
    if(!next.confirmPassword && password!==confirm) next.confirmPassword='รหัสผ่านไม่ตรงกัน กรุณาตรวจสอบอีกครั้ง'
    return next
  }

  const createAccount=async(agreementAccepted:boolean)=>{
    if(registrationPending.current || !formRef.current) return
    const data=new FormData(formRef.current)
    const nextErrors=validate(data)
    setErrors(nextErrors)
    setMessage('')
    setAgreementError('')
    const firstError=Object.keys(labels).find(name=>nextErrors[name])
    if(firstError){
      // Invalid data never stays behind an open dialog: close it and send the user back to the field.
      setAgreement(null)
      // Restore field focus after the dialog has unmounted and restored its trigger.
      requestAnimationFrame(()=>focusField(firstError))
      return
    }
    if(!agreementAccepted){
      setAgreement('terms')
      return
    }
    registrationPending.current=true
    setSubmitting(true)
    try{
      await register({firstName:String(data.get('firstName')).trim(),lastName:String(data.get('lastName')).trim(),email:String(data.get('email')).trim(),phone:String(data.get('phone')).replace(/[\s-]/g,''),password})
      window.location.replace('/dashboard')
    }catch(error){
      const reason=error instanceof Error?error.message:'สมัครสมาชิกไม่สำเร็จ กรุณาลองใหม่'
      // Keep the dialog open so a retry does not mean reading both steps again.
      setAgreementError(reason)
      setMessage(reason)
    }
    finally{registrationPending.current=false;setSubmitting(false)}
  }

  const submit=(event:React.FormEvent<HTMLFormElement>)=>{
    event.preventDefault()
    if(registrationPending.current) return
    void createAccount(false)
  }
  // Registration is only authorized by the final agreement action.
  const acceptAgreement=()=>void createAccount(true)
  const closeAgreement=()=>{
    setAgreement(null)
    setAgreementError('')
    // Send keyboard users back to the button that opened the dialog.
    requestAnimationFrame(()=>submitRef.current?.focus())
  }

  const fieldProps=(name:string)=>({
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': [name==='password'?'signup-password-rules':'',errors[name]?`signup-error-${name}`:''].filter(Boolean).join(' ') || undefined,
  })
  const fieldError=(name:string)=>errors[name] ? <span className="signup-field-error" id={`signup-error-${name}`}><CircleAlert size={14} aria-hidden="true"/>{errors[name]}</span> : null

  return <main className="login-page signup-page">
    <section className="login-story">
      <div className="login-story-image"/>
      <div className="login-story-content">
        <SignupBrand/>
        <div className="story-copy">
          <span className="login-eyebrow"><span/> พื้นที่ของคนรักของไทย</span>
          <h1>เริ่มต้นความสุข<br/><em>กับของดีที่ใช่</em></h1>
          <p>สร้างบัญชี แล้วค้นพบร้านค้าไทยที่คุณรัก<br/>ช้อปง่าย ใกล้กันมากขึ้นทุกวัน</p>
        </div>
        <div className="story-note"><Heart size={18}/><span>ทุกการช้อปของคุณ<br/><b>ช่วยให้ร้านค้าไทยเติบโต</b></span></div>
        <div className="login-benefits">{perks.slice(0,3).map(({icon:Icon,title})=><div key={title}><Icon size={19}/><span>{title}</span></div>)}</div>
      </div>
    </section>
    <section className="login-form-side signup-form-side">
      <nav className="login-top-nav" aria-label="เมนูสมัครสมาชิก"><a href="/"><ArrowLeft size={16}/> กลับไปช้อปปิ้ง</a><a href="/help">ต้องการความช่วยเหลือ?</a></nav>
      <div className="login-card signup-card">
        <div className="login-title"><span className="login-welcome-icon"><ShoppingBag size={25}/></span><span className="login-kicker">ยินดีต้อนรับสู่ ThaiShopFun</span><h2>สมัครสมาชิก แล้วช้อปกัน</h2><p>กรอกข้อมูลด้านล่างเพื่อสร้างบัญชีของคุณ</p></div>
          <form ref={formRef} onSubmit={submit} noValidate aria-busy={submitting} onChange={event=>{
            if(!(event.target instanceof HTMLInputElement)) return
            const name=event.target.name
            setErrors(previous=>{const next={...previous};delete next[name];if(name==='password') delete next.confirmPassword;return next})
            setMessage('')
          }}>
            <div className="signup-two"><label>ชื่อ<input name="firstName" {...fieldProps('firstName')} required placeholder="ชื่อจริง" autoComplete="given-name"/>{fieldError('firstName')}</label><label>นามสกุล<input name="lastName" {...fieldProps('lastName')} required placeholder="นามสกุล" autoComplete="family-name"/>{fieldError('lastName')}</label></div>
            <label>อีเมล<div className="signup-input"><Mail/><input name="email" {...fieldProps('email')} required type="email" placeholder="ตัวอย่าง: name@example.com" autoComplete="email"/></div>{fieldError('email')}</label>
            <label>เบอร์โทรศัพท์<div className="signup-input"><Phone/><input name="phone" {...fieldProps('phone')} required type="tel" inputMode="tel" placeholder="ตัวอย่าง: 0812345678" autoComplete="tel"/></div>{fieldError('phone')}</label>
            <label>รหัสผ่าน <span id="signup-password-rules" className="password-rules"><i className={rules.length?'ok':''}><Check/>อย่างน้อย 8 ตัว</i><i className={rules.number?'ok':''}><Check/>มีตัวเลข</i><i className={rules.letter?'ok':''}><Check/>มีตัวอักษร</i></span><div className="signup-input"><LockKeyhole/><input name="password" {...fieldProps('password')} required value={password} onChange={e=>setPassword(e.target.value)} type={showPassword?'text':'password'} placeholder="ตั้งรหัสผ่าน (อย่างน้อย 8 ตัวอักษร)" autoComplete="new-password"/><button type="button" onClick={()=>setShowPassword(v=>!v)} aria-pressed={showPassword} aria-label={showPassword ? "ซ่อนรหัสผ่าน" : "แสดงรหัสผ่าน"}>{showPassword?<EyeOff/>:<Eye/>}</button></div>{fieldError('password')}</label>
            <label>ยืนยันรหัสผ่าน<div className="signup-input"><LockKeyhole/><input name="confirmPassword" {...fieldProps('confirmPassword')} required value={confirm} onChange={e=>setConfirm(e.target.value)} type={showConfirm?'text':'password'} placeholder="กรอกรหัสผ่านอีกครั้ง" autoComplete="new-password"/><button type="button" onClick={()=>setShowConfirm(v=>!v)} aria-pressed={showConfirm} aria-label={showConfirm ? "ซ่อนรหัสผ่านยืนยัน" : "แสดงรหัสผ่านยืนยัน"}>{showConfirm?<EyeOff/>:<Eye/>}</button></div>{fieldError('confirmPassword')}</label>
            <p className="signup-agreement-note"><ShieldCheck size={17} aria-hidden="true"/><span>ขั้นตอนถัดไป: อ่านข้อกำหนดการใช้งานและนโยบายความเป็นส่วนตัว ก่อนยืนยันสร้างบัญชี</span></p>
            <button ref={submitRef} className="login-submit signup-submit" type="submit" disabled={submitting}>{submitting ? <><LoaderCircle className="login-spinner" size={19}/> กำลังสมัครสมาชิก...</> : <>สร้างบัญชี <ArrowRight size={19}/></>}</button>
            {message&&<p className="login-message" role="alert"><CircleAlert size={18}/><span>{message}</span></p>}
          </form>
        <div className="login-divider"><span/>หรือสมัครสมาชิกด้วย<span/></div>
        <div className="social-logins">
          {['Google','Facebook','Apple'].map(provider=><button key={provider} type="button" disabled={submitting} onClick={()=>setMessage(`การสมัครด้วย ${provider} ยังไม่เปิดให้บริการ กรุณากรอกข้อมูลเพื่อสร้างบัญชี`)}>
            {provider==='Google'?<i className="google-g" aria-hidden="true">G</i>:provider==='Facebook'?<i className="facebook-f" aria-hidden="true">f</i>:<svg className="apple-mark" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path fill="currentColor" d="M17.1 12.5c0-2 1.6-3 1.7-3.1-1-1.4-2.5-1.6-3-1.6-1.3-.1-2.5.8-3.2.8-.6 0-1.6-.8-2.7-.7-1.4 0-2.7.8-3.4 2-1.5 2.5-.4 6.3 1 8.3.6 1 1.4 2 2.5 1.9 1-.1 1.4-.6 2.7-.6 1.2 0 1.6.6 2.7.6 1.1 0 1.8-1 2.4-1.9.8-1.1 1.1-2.2 1.2-2.3-.1 0-2.3-.9-2.3-3.4ZM15 6.4c.5-.7 1-1.7.9-2.7-.9 0-2 .6-2.6 1.3-.5.6-1 1.6-.9 2.5 1 .1 1.9-.5 2.6-1.1Z"/></svg>}{provider}
          </button>)}
        </div>
        <p className="signup-line">มีบัญชีอยู่แล้ว? <a href="/login">เข้าสู่ระบบ</a></p>
      </div>
      <div className="login-merchant"><Store size={20}/><span>อยากเติบโตไปกับเรา? <a href="/merchant/register">เปิดร้านค้าไทย <ArrowRight size={14}/></a></span></div>
      <footer className="login-footer"><p>© 2026 ThaiShopFun · ช้อปสนุก สนับสนุนของไทย</p></footer>
    </section>
    {agreement && <SignupAgreement initialStep={agreement} busy={submitting} error={agreementError} onClose={closeAgreement} onAccept={acceptAgreement}/>}
  </main>
=======
import { useTranslation } from 'react-i18next'
import '../styles/login.css'
import '../styles/signup.css'
import '../styles/form-fields.css'
import FormField from '../components/form/FormField'
import FormGrid from '../components/form/FormGrid'
import SignupAgreement, { type AgreementStep } from '../components/SignupAgreement'
import { useAuth } from '../contexts/AuthContext'
import LanguageSwitcher from '../components/LanguageSwitcher'

const fieldNames = ['firstName', 'lastName', 'email', 'phone', 'password', 'confirmPassword'] as const
type FieldName = (typeof fieldNames)[number]
const perkKeys = ['variety', 'payment', 'delivery'] as const
const perkIcons = { variety: Gift, payment: ShieldCheck, delivery: Truck }

function SignupBrand({ ariaLabel, tagline }: { ariaLabel: string; tagline: string }) {
  return (
    <a className="brand signup-brand" href="/" aria-label={ariaLabel}>
      <span className="brand-mark"><ShoppingBag size={24} /><i>✓</i></span>
      <span><b>ThaiShop<span>Fun</span></b><small>{tagline}</small></span>
    </a>
  )
}

const passwordRules = (value: string) => ({ length: value.length >= 8, number: /\d/.test(value), letter: /[A-Za-z]/.test(value) })

export default function SignupPage() {
  const { t } = useTranslation('auth')
  const p = (key: string, options?: Record<string, unknown>) => t(`signupPage.${key}`, options)
  const label = (name: FieldName) => t(`signupPage.fieldLabels.${name}`)
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [showConfirm, setShowConfirm] = useState(false)
  const [message, setMessage] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [errors, setErrors] = useState<Partial<Record<FieldName, string>>>({})
  const formRef = useRef<HTMLFormElement>(null)
  const submitRef = useRef<HTMLButtonElement>(null)
  const registrationPending = useRef(false)
  const [agreement, setAgreement] = useState<AgreementStep | null>(null)
  const [agreementError, setAgreementError] = useState('')
  const { register } = useAuth()
  const rules = useMemo(() => passwordRules(password), [password])

  const focusField = (name: string) => (formRef.current?.elements.namedItem(name) as HTMLInputElement | null)?.focus()

  const validate = (data: FormData): Partial<Record<FieldName, string>> => {
    const next: Partial<Record<FieldName, string>> = {}
    for (const name of fieldNames) {
      if (!String(data.get(name) ?? '').trim()) next[name] = p('form.fieldRequired', { label: label(name) })
    }
    if (!next.email && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(String(data.get('email')).trim())) next.email = p('form.errorEmailInvalid')
    if (!next.phone && !/^0\d{8,9}$/.test(String(data.get('phone')).replace(/[\s-]/g, ''))) next.phone = p('form.errorPhoneInvalid')
    if (!next.password && !Object.values(passwordRules(password)).every(Boolean)) next.password = p('form.errorPasswordRules')
    if (!next.confirmPassword && password !== confirm) next.confirmPassword = p('form.errorPasswordMismatch')
    return next
  }

  const createAccount = async (agreementAccepted: boolean) => {
    if (registrationPending.current || !formRef.current) return
    const data = new FormData(formRef.current)
    const nextErrors = validate(data)
    setErrors(nextErrors)
    setMessage('')
    setAgreementError('')
    const firstError = fieldNames.find((name) => nextErrors[name])
    if (firstError) {
      setAgreement(null)
      requestAnimationFrame(() => focusField(firstError))
      return
    }
    if (!agreementAccepted) {
      setAgreement('terms')
      return
    }
    registrationPending.current = true
    setSubmitting(true)
    try {
      await register({
        firstName: String(data.get('firstName')).trim(),
        lastName: String(data.get('lastName')).trim(),
        email: String(data.get('email')).trim(),
        phone: String(data.get('phone')).replace(/[\s-]/g, ''),
        password,
      })
      window.location.replace('/dashboard')
    } catch (error) {
      const reason = error instanceof Error ? error.message : p('form.errorFailed')
      setAgreementError(reason)
      setMessage(reason)
    } finally {
      registrationPending.current = false
      setSubmitting(false)
    }
  }

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (registrationPending.current) return
    void createAccount(false)
  }

  const acceptAgreement = () => void createAccount(true)
  const closeAgreement = () => {
    setAgreement(null)
    setAgreementError('')
    requestAnimationFrame(() => submitRef.current?.focus())
  }

  const fieldProps = (name: FieldName) => ({
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': [name === 'password' ? 'signup-password-rules' : '', errors[name] ? `signup-error-${name}` : ''].filter(Boolean).join(' ') || undefined,
  })
  const fieldError = (name: FieldName) =>
    errors[name] ? (
      <span className="signup-field-error" id={`signup-error-${name}`}><CircleAlert size={14} aria-hidden="true" />{errors[name]}</span>
    ) : null

  return (
    <main className="login-page signup-page">
      <section className="login-story">
        <div className="login-story-image" />
        <div className="login-story-content">
          <SignupBrand ariaLabel={p('brandAriaHome')} tagline={p('brandTagline')} />
          <div className="story-copy">
            <span className="login-eyebrow"><span /> {p('story.eyebrow')}</span>
            <h1>{p('story.titleLine1')}<br /><em>{p('story.titleEmphasis')}</em></h1>
            <p>{p('story.descriptionLine1')}<br />{p('story.descriptionLine2')}</p>
          </div>
          <div className="story-note">
            <Heart size={18} />
            <span>{p('story.noteLine1')}<br /><b>{p('story.noteEmphasis')}</b></span>
          </div>
          <div className="login-benefits">
            {perkKeys.map((key) => {
              const Icon = perkIcons[key]
              return (
                <div key={key}>
                  <Icon size={19} />
                  <span>{p(`benefits.${key}.title`)}</span>
                </div>
              )
            })}
          </div>
        </div>
      </section>
      <section className="login-form-side signup-form-side">
        <nav className="login-top-nav" aria-label={p('navAria')}>
          <a href="/"><ArrowLeft size={16} /> {t('back')}</a>
          <div className="login-nav-tools"><LanguageSwitcher /><a href="/help">{t('help')}</a></div>
        </nav>
        <div className="login-card signup-card">
          <div className="login-title">
            <span className="login-welcome-icon"><ShoppingBag size={25} /></span>
            <span className="login-kicker">{p('form.kicker')}</span>
            <h2>{p('form.title')}</h2>
            <p>{p('form.subtitle')}</p>
          </div>
          <form
            ref={formRef}
            onSubmit={submit}
            noValidate
            aria-busy={submitting}
            onChange={(event) => {
              if (!(event.target instanceof HTMLInputElement)) return
              const name = event.target.name as FieldName
              setErrors((previous) => {
                const next = { ...previous }
                delete next[name]
                if (name === 'password') delete next.confirmPassword
                return next
              })
              setMessage('')
            }}
          >
            <FormGrid>
              <FormField label={p('form.firstNameLabel')} error={errors.firstName} errorId="signup-error-firstName">
                <input name="firstName" {...fieldProps('firstName')} required placeholder={p('form.firstNamePlaceholder')} autoComplete="given-name" />
              </FormField>
              <FormField label={p('form.lastNameLabel')} error={errors.lastName} errorId="signup-error-lastName">
                <input name="lastName" {...fieldProps('lastName')} required placeholder={p('form.lastNamePlaceholder')} autoComplete="family-name" />
              </FormField>
            </FormGrid>
            <FormField label={p('form.emailLabel')} icon={Mail} error={errors.email} errorId="signup-error-email">
              <input name="email" {...fieldProps('email')} required type="email" placeholder={p('form.emailPlaceholder')} autoComplete="email" />
            </FormField>
            <FormField label={p('form.phoneLabel')} icon={Phone} error={errors.phone} errorId="signup-error-phone">
              <input name="phone" {...fieldProps('phone')} required type="tel" inputMode="tel" placeholder={p('form.phonePlaceholder')} autoComplete="tel" />
            </FormField>
            <label>
              {p('form.passwordLabel')}{' '}
              <span id="signup-password-rules" className="password-rules">
                <i className={rules.length ? 'ok' : ''}><Check />{p('form.passwordRuleLength')}</i>
                <i className={rules.number ? 'ok' : ''}><Check />{p('form.passwordRuleNumber')}</i>
                <i className={rules.letter ? 'ok' : ''}><Check />{p('form.passwordRuleLetter')}</i>
              </span>
              <div className="signup-input">
                <LockKeyhole />
                <input name="password" {...fieldProps('password')} required value={password} onChange={(e) => setPassword(e.target.value)} type={showPassword ? 'text' : 'password'} placeholder={p('form.passwordPlaceholder')} autoComplete="new-password" />
                <button type="button" onClick={() => setShowPassword((v) => !v)} aria-pressed={showPassword} aria-label={showPassword ? p('form.hidePassword') : p('form.showPassword')}>{showPassword ? <EyeOff /> : <Eye />}</button>
              </div>
              {fieldError('password')}
            </label>
            <label>
              {p('form.confirmPasswordLabel')}
              <div className="signup-input">
                <LockKeyhole />
                <input name="confirmPassword" {...fieldProps('confirmPassword')} required value={confirm} onChange={(e) => setConfirm(e.target.value)} type={showConfirm ? 'text' : 'password'} placeholder={p('form.confirmPasswordPlaceholder')} autoComplete="new-password" />
                <button type="button" onClick={() => setShowConfirm((v) => !v)} aria-pressed={showConfirm} aria-label={showConfirm ? p('form.hideConfirmPassword') : p('form.showConfirmPassword')}>{showConfirm ? <EyeOff /> : <Eye />}</button>
              </div>
              {fieldError('confirmPassword')}
            </label>
            <p className="signup-agreement-note"><ShieldCheck size={17} aria-hidden="true" /><span>{p('form.agreementNote')}</span></p>
            <button ref={submitRef} className="login-submit signup-submit" type="submit" disabled={submitting}>
              {submitting ? <><LoaderCircle className="login-spinner" size={19} /> {p('form.submitting')}</> : <>{p('form.submit')} <ArrowRight size={19} /></>}
            </button>
            {message && <p className="login-message" role="alert"><CircleAlert size={18} /><span>{message}</span></p>}
          </form>
          <div className="login-divider"><span />{p('dividerSocial')}<span /></div>
          <div className="social-logins">
            {[p('socialGoogle'), p('socialFacebook'), p('socialApple')].map((provider) => (
              <button key={provider} type="button" disabled={submitting} onClick={() => setMessage(p('form.socialUnavailable', { provider }))}>
                {provider === p('socialGoogle') ? <i className="google-g" aria-hidden="true">G</i> : provider === p('socialFacebook') ? <i className="facebook-f" aria-hidden="true">f</i> : (
                  <svg className="apple-mark" viewBox="0 0 24 24" width="22" height="22" aria-hidden="true"><path fill="currentColor" d="M17.1 12.5c0-2 1.6-3 1.7-3.1-1-1.4-2.5-1.6-3-1.6-1.3-.1-2.5.8-3.2.8-.6 0-1.6-.8-2.7-.7-1.4 0-2.7.8-3.4 2-1.5 2.5-.4 6.3 1 8.3.6 1 1.4 2 2.5 1.9 1-.1 1.4-.6 2.7-.6 1.2 0 1.6.6 2.7.6 1.1 0 1.8-1 2.4-1.9.8-1.1 1.1-2.2 1.2-2.3-.1 0-2.3-.9-2.3-3.4ZM15 6.4c.5-.7 1-1.7.9-2.7-.9 0-2 .6-2.6 1.3-.5.6-1 1.6-.9 2.5 1 .1 1.9-.5 2.6-1.1Z" /></svg>
                )}
                {provider}
              </button>
            ))}
          </div>
          <p className="signup-line">{p('hasAccountPrompt')} <a href="/login">{p('loginLink')}</a></p>
        </div>
        <div className="login-merchant">
          <Store size={20} />
          <span>{p('merchantPrompt')} <a href="/merchant/register">{p('merchantLink')} <ArrowRight size={14} /></a></span>
        </div>
        <footer className="login-footer"><p>{p('footer')}</p></footer>
      </section>
      {agreement && <SignupAgreement initialStep={agreement} busy={submitting} error={agreementError} onClose={closeAgreement} onAccept={acceptAgreement} />}
    </main>
  )
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
}
