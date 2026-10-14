// src/layouts/PublicLayout.tsx
import { Outlet } from "react-router-dom";
import Header from "../components/Header";
import Footer from "../components/Footer";
import { useAuth } from "../contexts/AuthContext";
import { CartProvider } from "../contexts/CartContext";

export default function PublicLayout() {
  const { user } = useAuth();

  return (
    <CartProvider>
      <div className={`app ${user ? "member-home" : ""}`}>
        <Header />
        <main>
          <Outlet />
        </main>
        <Footer />
      </div>
    </CartProvider>
  );
}