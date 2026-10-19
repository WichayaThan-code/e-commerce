import type { ComponentType } from "react";
import type { ShopSectionKey } from "../../../theme/shopTheme";
import BannersSection from "./BannersSection";
import BestSellersSection from "./BestSellersSection";
import CatalogSection from "./CatalogSection";
import CategoriesSection from "./CategoriesSection";
import FeaturedSection from "./FeaturedSection";
import HeroSection from "./HeroSection";
import HoursSection from "./HoursSection";
import IntroSection from "./IntroSection";
import NewArrivalsSection from "./NewArrivalsSection";
import TrustSection from "./TrustSection";
import type { ShopSectionProps } from "./types";

export { resolveVariant, sectionRootClass } from "./sectionClasses";

export const SHOP_SECTION_COMPONENTS: Record<ShopSectionKey, ComponentType<ShopSectionProps>> = {
  HERO: HeroSection,
  BANNERS: BannersSection,
  CATEGORIES: CategoriesSection,
  TRUST: TrustSection,
  INTRO: IntroSection,
  FEATURED: FeaturedSection,
  BESTSELLERS: BestSellersSection,
  NEW_ARRIVALS: NewArrivalsSection,
  CATALOG: CatalogSection,
  HOURS: HoursSection,
};
