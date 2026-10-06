import { useEffect, useState } from "react";
import { Link, Outlet, useLocation } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import { useActiveShop } from "../contexts/ActiveShopContext";
import MerchantShopMenu from "./MerchantShopMenu";
import { merchantApi, type StoreAccess } from "../services/api";

const SETTINGS_PATH = "/merchant/settings";
const STORE_PATH = "/merchant/store";
const APPEAL_PATH = "/merchant/reinstatement";

export default function MerchantStoreGuard() {
  const { user, loading, reload } = useAuth();
  const { shops, shopId, ready } = useActiveShop();
  const location = useLocation();
  const settingsRoute = location.pathname === SETTINGS_PATH || location.pathname.startsWith(`${SETTINGS_PATH}/`);
  const storeRoute = location.pathname === STORE_PATH || location.pathname.startsWith(`${STORE_PATH}/`);
  const profileRoute = settingsRoute || storeRoute;
  const appealRoute = location.pathname === APPEAL_PATH;
  const [access, setAccess] = useState<StoreAccess | null>(null);
  const [gate, setGate] = useState<"checking" | "none" | "no-shop" | "locked">("checking");

  useEffect(() => {
    if (loading || !user || !ready) return;
    if (shops.length === 0 || shopId == null) {
      setGate("no-shop");
      return;
    }
    let active = true;
    setGate("checking");
    merchantApi.storeAccess(shopId).then(async (result) => {
      if (!active) return;
      setAccess(result);
      if (!result.hasShop) {
        setGate("no-shop");
        return;
      }
      const canAppeal = appealRoute && result.status === "SUSPENDED";
      if (!result.approved && !profileRoute && !canAppeal) {
        setGate("locked");
        return;
      }
      if (result.approved && user.role !== "MERCHANT") await reload();
      if (active) setGate("none");
    }).catch(() => {
      if (active) setGate("no-shop");
    });
    return () => { active = false; };
  }, [loading, user, reload, profileRoute, appealRoute, ready, shopId, shops.length]);

  if (loading || !ready || (user && gate === "checking")) {
    return <main className="merchant-auth-gate">กำลังตรวจสอบสถานะร้านค้า...</main>;
  }
  if (!user) {
    window.location.replace("/login");
    return null;
  }
  if (gate === "no-shop") {
    return (
      <main className="merchant-auth-gate">
        <section role="alert">
          <h1>ยังไม่มีร้านค้า</h1>
          <p>กรุณาสมัครเปิดร้านค้าก่อนเข้าใช้งานส่วนนี้</p>
          <div>
            <Link className="primary" to="/merchant/register">สมัครเปิดร้าน</Link>
            <Link className="quiet" to="/">กลับหน้าแรก</Link>
          </div>
        </section>
      </main>
    );
  }
  if (gate === "locked") {
    const suspended = access?.status === "SUSPENDED";
    const rejected = access?.status === "REJECTED";
    const shopLabel = access?.shopName ? `ร้าน ${access.shopName} ` : "ร้านค้าของคุณ ";
    return (
      <main className="merchant-auth-gate has-shop-menu">
        <div className="merchant-gate-bar"><MerchantShopMenu /></div>
        <section role="alert">
          <h1>{suspended ? "ร้านค้าถูกระงับ" : rejected ? "ร้านค้าไม่ได้รับการอนุมัติ" : "ร้านค้ายังไม่ได้รับการอนุมัติ"}</h1>
          <p>
            {shopLabel}
            {suspended
              ? "ถูกระงับอยู่ จึงยังจัดการสินค้า คำสั่งซื้อ ลูกค้า โปรโมชั่น และรายงานไม่ได้"
              : rejected
                ? "ไม่สามารถใช้งานเมนูจัดการร้านได้ แต่ยังแก้ไขข้อมูลร้านและเวลาทำการได้"
                : "ยังไม่สามารถใช้งานเมนูจัดการร้านได้ จนกว่าผู้ดูแลระบบจะอนุมัติ แต่ยังแก้ไขข้อมูลร้านและเวลาทำการได้"}
          </p>
          <div>
            {suspended && <Link className="primary" to={APPEAL_PATH}>ยื่นคำร้องปลดระงับ</Link>}
            <Link className={suspended ? "quiet" : "primary"} to={STORE_PATH}>แก้ไขข้อมูลร้าน</Link>
            <Link className="quiet" to={SETTINGS_PATH}>ตั้งค่าเวลาทำการ</Link>
          </div>
        </section>
      </main>
    );
  }
  return <Outlet context={access} />;
}
