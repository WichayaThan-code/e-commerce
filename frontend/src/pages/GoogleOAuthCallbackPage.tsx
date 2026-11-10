import { useEffect } from 'react'

/** Popup landing page for Google implicit id_token flow. Posts result to opener then closes. */
export default function GoogleOAuthCallbackPage() {
  useEffect(() => {
    const params = new URLSearchParams(window.location.hash.replace(/^#/, ''))
    const idToken = params.get('id_token')
    const error = params.get('error') || params.get('error_description')
    const payload = {
      type: 'tsf-google-oauth',
      idToken,
      error,
    } as const
    if (window.opener && !window.opener.closed) {
      window.opener.postMessage(payload, window.location.origin)
    }
    window.close()
  }, [])

  return (
    <main style={{ fontFamily: 'system-ui,sans-serif', padding: 24, textAlign: 'center' }}>
      <p>กำลังกลับไปหน้าเข้าสู่ระบบ…</p>
      <p style={{ color: '#667', fontSize: 13 }}>ถ้าหน้าต่างนี้ไม่ปิดเอง ให้ปิดแล้วลองอีกครั้ง</p>
    </main>
  )
}
