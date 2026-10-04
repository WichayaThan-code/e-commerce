// src/layouts/PublicLayout.tsx
import { Outlet } from "react-router-dom";
import Header from "../components/Header";
import Footer from "../components/Footer";
import { useAuth } from "../contexts/AuthContext";

export default function PublicLayout() {
  const { user } = useAuth();

  return (
    <div className={`app ${user ? "member-home" : ""}`}>
      {/* Header + Navbar ตรงนี้จะแสดงผลทุกหน้าฝั่งลูกค้าโดยอัตโนมัติ */}
      <Header />

      <main>
        {/* Outlet คือจุดที่ MarketplacePage, RecommendedProductsPage ฯลฯ จะมาแสดง */}
        <Outlet />
      </main>

    <Footer />
    </div>
  );
}