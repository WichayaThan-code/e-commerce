// src/layouts/PublicLayout.tsx
import { Outlet, useMatch } from "react-router-dom";
import Header from "../components/Header";
import Footer from "../components/Footer";
import { useAuth } from "../contexts/AuthContext";
import { CartProvider } from "../contexts/CartContext";

export default function PublicLayout() {
  const { user } = useAuth();
  // Shop storefront pages (/shop/:slug and below) use their own chrome — hide marketplace header + footer.
  const shopExact = useMatch("/shop/:slug");
  const shopNested = useMatch("/shop/:slug/*");
  const shopPage = Boolean(shopNested ?? shopExact);

  return (
    <CartProvider>
      <div className={`app ${user ? "member-home" : ""}`}>
        {shopPage ? null : <Header />}
        <main>
          <Outlet />
        </main>
        {shopPage ? null : <Footer />}
      </div>
    </CartProvider>
  );
}