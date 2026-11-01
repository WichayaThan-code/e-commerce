// src/layouts/PublicLayout.tsx
import { Outlet, useMatch } from "react-router-dom";
import Header from "../components/Header";
import Footer from "../components/Footer";
import { useAuth } from "../contexts/AuthContext";
import { CartProvider } from "../contexts/CartContext";

export default function PublicLayout() {
  const { user } = useAuth();
  // Shop storefront pages (/shop/:slug and below) carry their own store chrome, so the site footer is hidden there.
  const shopPage = useMatch("/shop/:slug/*");

  return (
    <CartProvider>
      <div className={`app ${user ? "member-home" : ""}`}>
        <Header />
        <main>
          <Outlet />
        </main>
        {shopPage ? null : <Footer />}
      </div>
    </CartProvider>
  );
}