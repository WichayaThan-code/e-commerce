// src/components/Navbar.tsx
import React from "react";
import { NavLink } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Menu,
  // Gift, // FUTURE: ดีลพิเศษ
  Sparkles,
  Store, // ใช้ตัวนี้แทน StoreCheck
  Flame,
  Award,
  BookOpen,
  HelpCircle,
  PlusCircle,
  LucideIcon,
} from "lucide-react";

export interface NavItem {
  id: string;
  labelKey: string;
  path: string;
  iconName?: string;
}

const ICON_MAP: Record<string, LucideIcon> = {
  // Gift, // FUTURE: ดีลพิเศษ
  Menu, Sparkles, Store, Flame, Award, BookOpen, HelpCircle, PlusCircle,
};

export const MOCK_NAV_ITEMS: NavItem[] = [
  { id: "categories", labelKey: "nav.categories", path: "/#categories", iconName: "Menu" },
  { id: "store-opening", labelKey: "nav.openStore", path: "/merchant/register", iconName: "PlusCircle" },
  // FUTURE: ดีลพิเศษ — เปิดเมนูเมื่อพร้อมใช้งาน
  // { id: "deals", labelKey: "nav.deals", path: "/deals", iconName: "Gift" },
  { id: "recommendations", labelKey: "nav.recommended", path: "/recommendations", iconName: "Sparkles" },
  { id: "shops", labelKey: "nav.shops", path: "/shops", iconName: "Store" },
  { id: "best-sellers", labelKey: "nav.best", path: "/best-sellers", iconName: "Flame" },
  { id: "thai-brands", labelKey: "nav.brands", path: "/thai-brands", iconName: "Award" },
  { id: "articles", labelKey: "nav.articles", path: "/articles", iconName: "BookOpen" },
  { id: "help", labelKey: "nav.help", path: "/help", iconName: "HelpCircle" },
];

export function Navbar() {
  const { t } = useTranslation("common");
  return (
    <nav className="nav page-width" aria-label={t("nav.main")}>
      {MOCK_NAV_ITEMS.map(({ id, labelKey, path, iconName }) => {
        const IconComponent = iconName ? ICON_MAP[iconName] : null;
        const isAnchor = path.includes("#");

        if (isAnchor) {
          return (
            <a key={id} href={path}>
              {IconComponent && <IconComponent size={18} />}
              <span>{t(labelKey)}</span>
            </a>
          );
        }

        return (
          <NavLink
            key={id}
            to={path}
            className={({ isActive }) => (isActive ? "active" : "")}
          >
            {IconComponent && <IconComponent size={18} />}
            <span>{t(labelKey)}</span>
          </NavLink>
        );
      })}
    </nav>
  );
}

export default Navbar;