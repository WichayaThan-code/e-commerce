import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { ApiError, merchantApi, type ReinstatementRequest } from "../services/api";
import "../styles/styles.css";

export default function ReinstatementPage() {
  const { shopId, shops, ready } = useActiveShop();
  const shop = shops.find((item) => item.shopId === shopId);
  const [current, setCurrent] = useState<ReinstatementRequest | null>(null);
  const [message, setMessage] = useState("");
  const [notice, setNotice] = useState("");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!ready || shopId == null) return;
    let active = true;
    setLoading(true);
    setNotice("");
    merchantApi.reinstatement(shopId).then((data) => {
      if (!active) return;
      setCurrent(data);
      setMessage(data.message || "");
    }).catch((error) => {
      if (!active) return;
      setCurrent(null);
      setNotice(error instanceof ApiError ? error.message : "โหลดคำร้องไม่สำเร็จ");
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [ready, shopId]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (shopId == null || busy) return;
    const text = message.trim();
    if (text.length < 10) {
      setNotice("กรุณาอธิบายเหตุผลอย่างน้อย 10 ตัวอักษร");
      return;
    }
    setBusy(true);
    try {
      const saved = await merchantApi.submitReinstatement(shopId, text);
      setCurrent(saved);
      setNotice("ส่งคำร้องแล้ว รอผู้ดูแลระบบตรวจสอบ");
    } catch (error) {
      setNotice(error instanceof ApiError ? error.message : "ส่งคำร้องไม่สำเร็จ");
    } finally {
      setBusy(false);
    }
  };

  const pending = current?.status === "PENDING";
  return (
    <main className="merchant-auth-gate has-shop-menu">
      <div className="merchant-gate-bar"><MerchantShopMenu /></div>
      <section className="reinstatement-card">
        <h1>ยื่นคำร้องปลดระงับ</h1>
        <p>ร้าน {shop?.shopName || "นี้"} ถูกระงับอยู่ ส่งเหตุผลให้ผู้ดูแลระบบพิจารณาเปิดใช้งานอีกครั้ง</p>
        {loading ? <p>กำลังโหลดคำร้อง...</p> : (
          <form onSubmit={(event) => void submit(event)}>
            {pending && <p className="reinstatement-status">มีคำร้องรอตรวจสอบอยู่ สามารถแก้ไขข้อความแล้วส่งใหม่ได้</p>}
            {current?.status === "REJECTED" && <p className="reinstatement-status">คำร้องก่อนหน้าไม่ผ่าน สามารถส่งคำร้องใหม่ได้</p>}
            <label>
              เหตุผลที่ขอปลดระงับ
              <textarea value={message} maxLength={1000} rows={6} onChange={(event) => setMessage(event.target.value)} placeholder="อธิบายสิ่งที่แก้ไขแล้ว และเหตุผลที่ควรเปิดร้านอีกครั้ง" />
            </label>
            <small>{message.trim().length}/1000</small>
            <div>
              <button type="submit" disabled={busy || message.trim().length < 10}>{busy ? "กำลังส่ง..." : pending ? "อัปเดตคำร้อง" : "ส่งคำร้อง"}</button>
              <Link className="quiet" to="/merchant/store">กลับไปข้อมูลร้าน</Link>
            </div>
          </form>
        )}
        {notice && <p role="status">{notice}</p>}
      </section>
    </main>
  );
}
