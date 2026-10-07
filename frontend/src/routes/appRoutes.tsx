// src/routes/appRoutes.ts
import { RouteObject } from "react-router-dom";
import PublicLayout from "../layouts/PublicLayout";
import RouteProgress from "../components/RouteProgress";
import MerchantSignupGuard from "../components/MerchantSignupGuard";
import MerchantStoreGuard from "../components/MerchantStoreGuard";
import ActiveShopProvider from "../contexts/ActiveShopContext";

import MarketplacePage from "../pages/MarketplacePage";
import LoginPage from "../pages/LoginPage";
import SignupPage from "../pages/SignupPage";
import DashboardPage from "../pages/DashboardPage";

import AdminDashboardPage from "../pages/AdminDashboardPage";
import AdminStoresPage from "../pages/AdminStoresPage";
import AdminApplicationsPage from "../pages/AdminApplicationsPage";
import AdminCategoriesPage from "../pages/AdminCategoriesPage";
import AdminPromotionsPage from "../pages/AdminPromotionsPage";
import AdminUsersPage from "../pages/AdminUsersPage";
import AdminRolesPage from "../pages/AdminRolesPage";

import MerchantDashboardPage from "../pages/MerchantDashboardPage";
import ProductManagementPage from "../pages/ProductManagementPage";
import ProductFormPage from "../pages/ProductFormPage";
import ProductDetailPage from "../pages/ProductDetailPage";
import OrderManagementPage from "../pages/OrderManagementPage";
import CustomerManagementPage from "../pages/CustomerManagementPage";
import PromotionManagementPage from "../pages/PromotionManagementPage";
import ReportsPage from "../pages/ReportsPage";
import FinancePage from "../pages/FinancePage";
import ShippingPage from "../pages/ShippingPage";
import MyStorePage from "../pages/MyStorePage";
import SettingsPage from "../pages/SettingsPage";
import ReinstatementPage from "../pages/ReinstatementPage";

import DealsPage from "../pages/DealsPage";
import RecommendedProductsPage from "../pages/RecommendedProductsPage";
import BestSellerPage from "../pages/BestSellerPage";
import ThaiBrandsPage from "../pages/ThaiBrandsPage";
import BlogPage from "../pages/BlogPage";
import HelpPage from "../pages/HelpPage";
import CartPage from "../pages/CartPage";

import MerchantSignupPage from "../pages/MerchantSignupPage";
import OwnerInfoPage from "../pages/OwnerInfoPage";
import DocumentConfirmPage from "../pages/DocumentConfirmPage";
import VerifySendPage from "../pages/VerifySendPage";
import ShopPage from "../pages/ShopPage";
import ShopProductPage from "../pages/ShopProductPage";
import ShopsPage from "../pages/ShopsPage";

export const routes: RouteObject[] = [
  {
    element: <RouteProgress />,
    children: [
  // 1. กลุ่มหน้า Public / Customer (ทุกหน้าในกลุ่มนี้จะมี Header & Navbar ครอบให้อัตโนมัติ)
  {
    element: <PublicLayout />,
    children: [
      { path: "/", element: <MarketplacePage /> },
      { path: "/deals", element: <DealsPage /> },
      { path: "/recommendations", element: <RecommendedProductsPage /> },
      { path: "/best-sellers", element: <BestSellerPage /> },
      { path: "/thai-brands", element: <ThaiBrandsPage /> },
      { path: "/articles", element: <BlogPage /> },
      { path: "/help", element: <HelpPage /> },
      { path: "/cart", element: <CartPage /> },
      { path: "/shops", element: <ShopsPage /> },
      { path: "/shop/:slug", element: <ShopPage /> },
      { path: "/shop/:slug/product/:productSlug", element: <ShopProductPage /> },
    ],
  },

  // 2. กลุ่มหน้า Auth (ไม่มี Header หน้าร้าน)
  { path: "/login", element: <LoginPage /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/dashboard", element: <DashboardPage /> },

  // 3. กลุ่มหน้า Admin
  { path: "/admin", element: <AdminDashboardPage /> },
  { path: "/admin/stores", element: <AdminStoresPage /> },
  { path: "/admin/store-applications", element: <AdminApplicationsPage /> },
  { path: "/admin/categories", element: <AdminCategoriesPage /> },
  { path: "/admin/promotions", element: <AdminPromotionsPage /> },
  { path: "/admin/users", element: <AdminUsersPage /> },
  { path: "/admin/roles", element: <AdminRolesPage /> },

  // 4. กลุ่มหน้า Merchant
  {
    element: <ActiveShopProvider />,
    children: [
  {
    element: <MerchantStoreGuard />,
    children: [
      { path: "/merchant/dashboard", element: <MerchantDashboardPage /> },
      { path: "/merchant/products", element: <ProductManagementPage /> },
      { path: "/merchant/products/new", element: <ProductFormPage /> },
      { path: "/merchant/products/:productId", element: <ProductDetailPage /> },
      { path: "/merchant/products/:productId/edit", element: <ProductFormPage /> },
      { path: "/merchant/orders", element: <OrderManagementPage /> },
      { path: "/merchant/customers", element: <CustomerManagementPage /> },
      { path: "/merchant/promotions", element: <PromotionManagementPage /> },
      { path: "/merchant/reports", element: <ReportsPage /> },
      { path: "/merchant/finance", element: <FinancePage /> },
      { path: "/merchant/shipping", element: <ShippingPage /> },
      { path: "/merchant/store", element: <MyStorePage /> },
      { path: "/merchant/settings", element: <SettingsPage /> },
      { path: "/merchant/reinstatement", element: <ReinstatementPage /> },
    ],
  },
    ],
  },
  {
    element: <MerchantSignupGuard />,
    children: [
      { path: "/merchant/register", element: <MerchantSignupPage /> },
      { path: "/merchant/owner-info", element: <OwnerInfoPage /> },
      { path: "/merchant/documents", element: <DocumentConfirmPage /> },
      { path: "/merchant/review", element: <VerifySendPage /> },
    ],
  },
    ],
  },
];