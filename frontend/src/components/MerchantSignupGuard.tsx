import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";

export default function MerchantSignupGuard() {
  const { user, loading } = useAuth();
  const location = useLocation();
  const next = `${location.pathname}${location.search}`;

  if (loading) return <main className="merchant-auth-gate">กำลังตรวจสอบการเข้าสู่ระบบ...</main>;
  if (user) return <Outlet />;

  return (
    <main className="merchant-auth-gate">
      <section role="alert">
        <h1>กรุณาเข้าสู่ระบบหรือสมัครสมาชิกก่อน</h1>
        <p>หน้าสร้างร้านค้าเปิดได้หลังจากเข้าสู่ระบบแล้ว</p>
        <div>
          <Link to={`/login?next=${encodeURIComponent(next)}`}>เข้าสู่ระบบ</Link>
          <Link to="/signup">สมัครสมาชิก</Link>
        </div>
      </section>
    </main>
  );
}
