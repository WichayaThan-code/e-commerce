// src/routes/appRoutes.ts
import type { ReactElement } from "react";
import { RouteObject } from "react-router-dom";
import PublicLayout from "../layouts/PublicLayout";
import RouteProgress from "../components/RouteProgress";
import MerchantSignupGuard from "../components/MerchantSignupGuard";
import MerchantStoreGuard from "../components/MerchantStoreGuard";
import ActiveShopProvider from "../contexts/ActiveShopContext";
import PermissionRoute from "../components/PermissionRoute";
import { ROUTE_PERMISSIONS } from "../services/permissions";

import MarketplacePage from "../pages/MarketplacePage";
import LoginPage from "../pages/LoginPage";
import PasswordResetPages from "../pages/PasswordResetPages";
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

/** Wraps a page with the permission declared for its path in services/permissions.ts. */
function guarded(path: string, page: ReactElement) {
  const rule = ROUTE_PERMISSIONS[path];
  if (!rule) throw new Error(`No permission declared for route ${path}`);
  return <PermissionRoute permission={rule.permission} scope={rule.scope}>{page}</PermissionRoute>;
}

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
  { path: "/forgot-password", element: <PasswordResetPages /> },
  { path: "/reset-password", element: <PasswordResetPages /> },
  { path: "/signup", element: <SignupPage /> },
  { path: "/dashboard", element: <DashboardPage /> },

  // 3. กลุ่มหน้า Admin
  { path: "/admin", element: guarded("/admin", <AdminDashboardPage />) },
  { path: "/admin/stores", element: guarded("/admin/stores", <AdminStoresPage />) },
  { path: "/admin/store-applications", element: guarded("/admin/store-applications", <AdminApplicationsPage />) },
  { path: "/admin/categories", element: guarded("/admin/categories", <AdminCategoriesPage />) },
  { path: "/admin/promotions", element: guarded("/admin/promotions", <AdminPromotionsPage />) },
  { path: "/admin/users", element: guarded("/admin/users", <AdminUsersPage />) },
  { path: "/admin/roles", element: guarded("/admin/roles", <AdminRolesPage />) },

  // 4. กลุ่มหน้า Merchant
  {
    element: <ActiveShopProvider />,
    children: [
  {
    element: <MerchantStoreGuard />,
    children: [
      { path: "/merchant/dashboard", element: guarded("/merchant/dashboard", <MerchantDashboardPage />) },
      { path: "/merchant/products", element: guarded("/merchant/products", <ProductManagementPage />) },
      { path: "/merchant/products/new", element: guarded("/merchant/products", <ProductFormPage />) },
      { path: "/merchant/products/:productId/edit", element: guarded("/merchant/products", <ProductFormPage />) },
      { path: "/merchant/products/:productId", element: guarded("/merchant/products", <ProductDetailPage />) },
      { path: "/merchant/orders", element: guarded("/merchant/orders", <OrderManagementPage />) },
      { path: "/merchant/customers", element: guarded("/merchant/customers", <CustomerManagementPage />) },
      { path: "/merchant/promotions", element: guarded("/merchant/promotions", <PromotionManagementPage />) },
      { path: "/merchant/reports", element: guarded("/merchant/reports", <ReportsPage />) },
      { path: "/merchant/finance", element: guarded("/merchant/finance", <FinancePage />) },
      { path: "/merchant/shipping", element: guarded("/merchant/shipping", <ShippingPage />) },
      { path: "/merchant/store", element: guarded("/merchant/store", <MyStorePage />) },
      { path: "/merchant/settings", element: guarded("/merchant/settings", <SettingsPage />) },
      { path: "/merchant/reinstatement", element: guarded("/merchant/reinstatement", <ReinstatementPage />) },
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