import { useEffect, useRef, useState } from 'react'
<<<<<<< HEAD
=======
import { useTranslation } from 'react-i18next'
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
import { ArrowLeft, ArrowRight, Check, CircleAlert, FileText, LoaderCircle, RotateCcw, ShieldCheck, X } from 'lucide-react'

export type AgreementStep = 'terms' | 'privacy'

<<<<<<< HEAD
const terms = [
  ['บัญชีและการใช้บริการ', 'ThaiShopFun เป็นพื้นที่สำหรับค้นหาและซื้อสินค้าจากร้านค้า ผู้สมัครต้องให้ข้อมูลที่ถูกต้องและเป็นปัจจุบัน เก็บรหัสผ่านเป็นความลับ และแจ้งทีมช่วยเหลือเมื่อพบการใช้บัญชีโดยไม่ได้รับอนุญาต หากยังไม่สามารถทำนิติกรรมได้ด้วยตนเอง ต้องได้รับความยินยอมจากผู้แทนโดยชอบธรรมตามกฎหมายก่อนใช้บริการ'],
  ['สินค้า ราคา และคำสั่งซื้อ', 'โปรดตรวจสอบรายละเอียดสินค้า ตัวเลือก จำนวน ราคา ค่าจัดส่ง และที่อยู่ก่อนยืนยันคำสั่งซื้อ เงื่อนไขส่วนลดและจำนวนสินค้าขึ้นอยู่กับแต่ละรายการ หากพบข้อมูลคลาดเคลื่อนหรือไม่สามารถจัดหาสินค้าได้ เราหรือร้านค้าจะแจ้งเพื่อให้คุณเลือกแนวทางแก้ไขหรือยกเลิกตามสิทธิที่เกี่ยวข้อง โดยไม่เรียกเก็บค่าใช้จ่ายเพิ่มเติมโดยไม่ได้รับการยืนยัน'],
  ['การชำระเงินและการจัดส่ง', 'ชำระเงินผ่านช่องทางที่แสดงในระบบเท่านั้น ระยะเวลาจัดส่งเป็นประมาณการและอาจเปลี่ยนแปลงตามพื้นที่ ผู้ให้บริการขนส่ง หรือเหตุสุดวิสัย โปรดตรวจสอบสถานะคำสั่งซื้อและติดต่อทีมช่วยเหลือหากพบปัญหา'],
  ['การยกเลิก คืนสินค้า และคืนเงิน', 'ตรวจสอบเงื่อนไขที่แสดงในหน้าสินค้าและคำสั่งซื้อก่อนชำระเงิน หากได้รับสินค้าผิดรายการ ชำรุด หรือไม่ตรงตามรายละเอียด ให้ติดต่อทีมช่วยเหลือพร้อมหมายเลขคำสั่งซื้อและหลักฐาน การพิจารณาและระยะเวลาคืนเงินขึ้นอยู่กับกรณีและช่องทางชำระเงิน เงื่อนไขของร้านค้าจะไม่ตัดสิทธิของผู้บริโภคที่กฎหมายรับรอง'],
  ['การใช้งานอย่างเหมาะสม', 'ห้ามใช้บัญชีเพื่อฉ้อโกง สวมรอย ละเมิดสิทธิผู้อื่น เผยแพร่เนื้อหาผิดกฎหมาย หรือรบกวนการทำงานของระบบ รีวิวควรสะท้อนประสบการณ์จริง เราอาจจำกัดการใช้งานที่ฝ่าฝืนตามความจำเป็น พร้อมช่องทางให้ติดต่อชี้แจง โดยไม่กระทบสิทธิในคำสั่งซื้อหรือเงินที่ต้องคืนตามกฎหมาย'],
  ['สิทธิในเนื้อหาและความรับผิด', 'เครื่องหมายการค้า รูปภาพ และเนื้อหาบนแพลตฟอร์มเป็นของเจ้าของสิทธิที่เกี่ยวข้อง การนำไปใช้ต้องได้รับอนุญาตตามสมควร ผู้ขายรับผิดชอบรายละเอียดและคุณภาพสินค้าของตน ส่วน ThaiShopFun รับผิดชอบการให้บริการในส่วนของแพลตฟอร์มตามกฎหมาย ข้อกำหนดนี้ไม่ยกเว้นความรับผิดที่กฎหมายห้ามยกเว้น'],
  ['การเปลี่ยนแปลงและการติดต่อ', 'หากมีการเปลี่ยนแปลงสาระสำคัญ เราจะแจ้งผ่านช่องทางของบริการก่อนนำมาใช้ตามความเหมาะสม การเปลี่ยนแปลงจะไม่ลดสิทธิที่เกิดขึ้นแล้วจากคำสั่งซื้อเดิม หากมีข้อสงสัยหรือข้อพิพาท ติดต่อผ่านหน้าช่วยเหลือของ ThaiShopFun ข้อกำหนดนี้อยู่ภายใต้กฎหมายไทย โดยไม่จำกัดสิทธิร้องเรียนต่อหน่วยงานที่มีอำนาจ'],
]
const privacy = [
  ['ข้อมูลที่ใช้ในการให้บริการ', 'ข้อมูลบัญชีประกอบด้วยชื่อ นามสกุล อีเมล เบอร์โทรศัพท์ และข้อมูลสำหรับยืนยันตัวตน เมื่อใช้บริการซื้อสินค้า อาจมีข้อมูลที่อยู่จัดส่ง รายการสั่งซื้อ สถานะการชำระเงิน และการติดต่อทีมช่วยเหลือ รวมถึงข้อมูลทางเทคนิคที่จำเป็นต่อความปลอดภัยของระบบ'],
  ['วัตถุประสงค์และฐานการประมวลผล', 'เราใช้ข้อมูลเพื่อสร้างและดูแลบัญชี ดำเนินคำสั่งซื้อ จัดส่งสินค้า ประสานการชำระเงินและคืนเงิน และตอบคำถามตามการให้บริการที่คุณร้องขอ รวมถึงปฏิบัติหน้าที่ตามกฎหมาย และป้องกันการทุจริตภายใต้ประโยชน์โดยชอบด้วยกฎหมาย โดยคำนึงถึงสิทธิของคุณ หากไม่ให้ข้อมูลที่จำเป็น อาจไม่สามารถสมัครบัญชีหรือดำเนินคำสั่งซื้อได้'],
  ['ผู้ที่อาจได้รับข้อมูล', 'เราเปิดเผยข้อมูลเท่าที่จำเป็นต่อร้านค้าที่คุณสั่งซื้อ ผู้ให้บริการขนส่ง ผู้ให้บริการชำระเงิน และผู้ให้บริการระบบที่เกี่ยวข้อง ตามวัตถุประสงค์และข้อกำหนดการรักษาข้อมูล รวมถึงหน่วยงานที่มีอำนาจเมื่อมีหน้าที่ตามกฎหมาย หากมีการโอนข้อมูลไปต่างประเทศ จะต้องดำเนินการตามเงื่อนไขและมาตรการคุ้มครองที่กฎหมายกำหนด'],
  ['ระยะเวลาเก็บรักษาและความปลอดภัย', 'เก็บข้อมูลตามความจำเป็นสำหรับการดูแลบัญชีและการดำเนินธุรกรรม รวมถึงระยะเวลาที่ต้องเก็บเพื่อบัญชี ภาษี การจัดการข้อพิพาท หรือหน้าที่ตามกฎหมาย เมื่อหมดความจำเป็นจะลบ ทำลาย หรือทำให้ไม่สามารถระบุตัวบุคคลได้ โดยจำกัดการเข้าถึงข้อมูลตามหน้าที่และใช้มาตรการรักษาความปลอดภัยที่เหมาะสม'],
  ['สิทธิของคุณ', 'คุณสามารถขอเข้าถึงหรือรับสำเนา แก้ไข ลบ จำกัดการใช้ คัดค้านการประมวลผล หรือขอรับและโอนย้ายข้อมูลได้ตามเงื่อนไขของกฎหมาย หากการประมวลผลอาศัยความยินยอม คุณสามารถถอนความยินยอมได้โดยไม่กระทบการประมวลผลที่ชอบด้วยกฎหมายก่อนถอน และมีสิทธิร้องเรียนต่อสำนักงานคณะกรรมการคุ้มครองข้อมูลส่วนบุคคล'],
  ['คุกกี้และการตลาด', 'ระบบใช้คุกกี้หรือเทคโนโลยีที่จำเป็นสำหรับการเข้าสู่ระบบและความปลอดภัย หากมีการใช้เพื่อการตลาดหรือการวิเคราะห์ที่ต้องอาศัยความยินยอม จะขอแยกต่างหาก การสร้างบัญชีไม่ถือเป็นการยินยอมรับโฆษณาหรือการใช้ข้อมูลเพื่อวัตถุประสงค์อื่นที่ไม่จำเป็นต่อบริการ'],
  ['ติดต่อและปรับปรุงนโยบาย', 'หากต้องการใช้สิทธิหรือสอบถามเกี่ยวกับข้อมูลส่วนบุคคล ติดต่อทีมช่วยเหลือ ThaiShopFun ผ่านหน้าช่วยเหลือ โดยระบุเรื่อง “ข้อมูลส่วนบุคคล” เราอาจขอข้อมูลเท่าที่จำเป็นเพื่อยืนยันตัวตนก่อนดำเนินการ หากมีการเปลี่ยนแปลงสาระสำคัญ จะแจ้งให้ทราบผ่านช่องทางของบริการ'],
]
=======
type AgreementSection = { title: string; body: string }
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb

export default function SignupAgreement({ initialStep, busy, error, onClose, onAccept }: {
  initialStep: AgreementStep
  busy: boolean
  error: string
  onClose: () => void
  onAccept: () => void
}) {
<<<<<<< HEAD
=======
  const { t } = useTranslation('auth')
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  const [step, setStep] = useState<AgreementStep>(initialStep)
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const body = useRef<HTMLDivElement>(null)
  const alert = useRef<HTMLParagraphElement>(null)
<<<<<<< HEAD
  // Closing mid-request would hide a registration the user cannot see the result of.
  const close = () => { if (!busy) onClose() }

=======
  const close = () => { if (!busy) onClose() }

  const sectionsKey = step === 'terms' ? 'signupAgreement.termsSections' : 'signupAgreement.privacySections'
  const sections = t(sectionsKey, { returnObjects: true }) as AgreementSection[]

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null
    const overflow = document.body.style.overflow
    dialog.current?.showModal()
    document.body.style.overflow = 'hidden'
    return () => {
      dialog.current?.close()
      document.body.style.overflow = overflow
      previous?.focus()
    }
  }, [])
  useEffect(() => {
    body.current?.scrollTo(0, 0)
    heading.current?.focus()
  }, [step])
  useEffect(() => { if (error) alert.current?.focus() }, [error])

<<<<<<< HEAD
  return <dialog ref={dialog} className="agreement-dialog" aria-busy={busy} aria-labelledby="agreement-title" aria-describedby="agreement-intro" onCancel={event => { event.preventDefault(); close() }}>
    <div className="agreement-header">
      <span className="agreement-icon">{step === 'terms' ? <FileText/> : <ShieldCheck/>}</span>
      <div><span className="agreement-eyebrow">THAISHOPFUN · ก่อนเริ่มช้อปด้วยกัน</span><h2 ref={heading} tabIndex={-1} id="agreement-title">{step === 'terms' ? 'ข้อกำหนดการใช้งาน' : 'นโยบายความเป็นส่วนตัว'}</h2></div>
      <button type="button" className="agreement-close" aria-label="ปิดและกลับไปกรอกข้อมูล" disabled={busy} onClick={close}><X size={20}/></button>
    </div>
    <ol className="agreement-steps" aria-label="ขั้นตอนการอ่านข้อตกลง">
      <li className={step === 'terms' ? 'current' : 'done'} aria-current={step === 'terms' ? 'step' : undefined}><span>{step === 'privacy' ? <Check size={14}/> : '1'}</span>ข้อกำหนดการใช้งาน</li>
      <li className={step === 'privacy' ? 'current' : ''} aria-current={step === 'privacy' ? 'step' : undefined}><span>2</span>ความเป็นส่วนตัว</li>
    </ol>
    <div className="agreement-body" ref={body} tabIndex={0}>
      <p id="agreement-intro" className="agreement-intro">{step === 'terms' ? 'โปรดอ่านเงื่อนไขการใช้บริการ เพื่อให้ทุกการช้อปของคุณชัดเจนและมั่นใจ' : 'ทำความเข้าใจว่าเราใช้ข้อมูลใด เพื่ออะไร และคุณจัดการสิทธิของตัวเองได้อย่างไร'}</p>
      {(step === 'terms' ? terms : privacy).map(([title, text], index) => <section key={title}><h3><span>{String(index + 1).padStart(2, '0')}</span>{title}</h3><p>{text}</p></section>)}
      <a className="agreement-help" href="/help" target="_blank" rel="noopener noreferrer">ติดต่อทีมช่วยเหลือ ThaiShopFun (เปิดแท็บใหม่) <ArrowRight size={14}/></a>
    </div>
    <div className="agreement-footer">
      {error && <p className="agreement-alert" ref={alert} tabIndex={-1} role="alert"><CircleAlert size={16} aria-hidden="true"/><span>{error} ข้อมูลที่กรอกไว้ยังอยู่ครบ กดลองอีกครั้ง หรือกลับไปแก้ไขข้อมูลได้</span></p>}
      <p>{step === 'terms' ? 'ขั้นตอนถัดไป: อ่านนโยบายความเป็นส่วนตัว' : 'เมื่อกดยอมรับและสร้างบัญชี คุณยอมรับข้อกำหนดและรับทราบนโยบายความเป็นส่วนตัว ระบบจะตรวจข้อมูลและส่งคำขอสมัครทันที'}</p>
      <div><button className="agreement-secondary" type="button" disabled={busy} onClick={() => step === 'privacy' && !error ? setStep('terms') : close()}><ArrowLeft size={16}/>{error ? 'แก้ไขข้อมูล' : step === 'privacy' ? 'ย้อนกลับ' : 'ไว้ก่อน'}</button>
        <button className="login-submit" type="button" disabled={busy} onClick={() => step === 'terms' ? setStep('privacy') : onAccept()}>
          {step === 'terms' ? <>ถัดไป <ArrowRight size={17}/></>
            : busy ? <><LoaderCircle className="login-spinner" size={18}/> กำลังสร้างบัญชี...</>
            : error ? <><RotateCcw size={17}/> ลองอีกครั้ง</>
            : <><Check size={18}/> ยอมรับและสร้างบัญชี</>}
=======
  const title = step === 'terms' ? t('signupAgreement.termsTitle') : t('signupAgreement.privacyTitle')
  const intro = step === 'terms' ? t('signupAgreement.introTerms') : t('signupAgreement.introPrivacy')
  const footerHint = step === 'terms' ? t('signupAgreement.footerNextTerms') : t('signupAgreement.footerAccept')

  const secondaryLabel = error
    ? t('signupAgreement.btnEdit')
    : step === 'privacy'
      ? t('signupAgreement.btnBack')
      : t('signupAgreement.btnLater')

  return <dialog ref={dialog} className="agreement-dialog" aria-busy={busy} aria-labelledby="agreement-title" aria-describedby="agreement-intro" onCancel={event => { event.preventDefault(); close() }}>
    <div className="agreement-header">
      <span className="agreement-icon">{step === 'terms' ? <FileText/> : <ShieldCheck/>}</span>
      <div><span className="agreement-eyebrow">{t('signupAgreement.eyebrow')}</span><h2 ref={heading} tabIndex={-1} id="agreement-title">{title}</h2></div>
      <button type="button" className="agreement-close" aria-label={t('signupAgreement.closeAria')} disabled={busy} onClick={close}><X size={20}/></button>
    </div>
    <ol className="agreement-steps" aria-label={t('signupAgreement.stepsAria')}>
      <li className={step === 'terms' ? 'current' : 'done'} aria-current={step === 'terms' ? 'step' : undefined}><span>{step === 'privacy' ? <Check size={14}/> : '1'}</span>{t('signupAgreement.stepTerms')}</li>
      <li className={step === 'privacy' ? 'current' : ''} aria-current={step === 'privacy' ? 'step' : undefined}><span>2</span>{t('signupAgreement.stepPrivacy')}</li>
    </ol>
    <div className="agreement-body" ref={body} tabIndex={0}>
      <p id="agreement-intro" className="agreement-intro">{intro}</p>
      {sections.map(({ title: sectionTitle, body: sectionBody }, index) => (
        <section key={sectionTitle}><h3><span>{String(index + 1).padStart(2, '0')}</span>{sectionTitle}</h3><p>{sectionBody}</p></section>
      ))}
      <a className="agreement-help" href="/help" target="_blank" rel="noopener noreferrer">{t('signupAgreement.helpLink')} <ArrowRight size={14}/></a>
    </div>
    <div className="agreement-footer">
      {error && <p className="agreement-alert" ref={alert} tabIndex={-1} role="alert"><CircleAlert size={16} aria-hidden="true"/><span>{error}{t('signupAgreement.errorSuffix')}</span></p>}
      <p>{footerHint}</p>
      <div><button className="agreement-secondary" type="button" disabled={busy} onClick={() => step === 'privacy' && !error ? setStep('terms') : close()}><ArrowLeft size={16}/>{secondaryLabel}</button>
        <button className="login-submit" type="button" disabled={busy} onClick={() => step === 'terms' ? setStep('privacy') : onAccept()}>
          {step === 'terms' ? <>{t('signupAgreement.btnNext')} <ArrowRight size={17}/></>
            : busy ? <><LoaderCircle className="login-spinner" size={18}/> {t('signupAgreement.btnCreating')}</>
            : error ? <><RotateCcw size={17}/> {t('signupAgreement.btnRetry')}</>
            : <><Check size={18}/> {t('signupAgreement.btnAcceptCreate')}</>}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
        </button></div>
    </div>
  </dialog>
}
