// src/layouts/MainLayout.tsx
import React, { Suspense } from "react";
import { Outlet } from "react-router-dom";
import Header from "../components/Header";

export default function MainLayout() {
  return (
    <div className="app">
      {/* Header และ Navbar อยู่ตรงนี้ทีเดียว ครอบทุกหน้า */}
      <Header />

      <main>
        <Suspense fallback={<div className="page-width" style={{ padding: "2rem" }}>กำลังโหลด...</div>}>
          <Outlet />
        </Suspense>
      </main>

      <footer>
        <div className="page-width">
          <p>© 2026 ThaiShopFun. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}