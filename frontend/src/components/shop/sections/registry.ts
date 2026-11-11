import type { ComponentType } from "react";
import type { NewBlockType } from "../../../theme/blockContent";
import type { ShopSectionKey } from "../../../theme/shopTheme";
import BannersSection from "./BannersSection";
import BestSellersSection from "./BestSellersSection";
import CatalogSection from "./CatalogSection";
import CategoriesSection from "./CategoriesSection";
import CouponsSection from "./CouponsSection";
import FeaturedSection from "./FeaturedSection";
import HeroSection from "./HeroSection";
import HoursSection from "./HoursSection";
import IntroSection from "./IntroSection";
import NewArrivalsSection from "./NewArrivalsSection";
import TrustSection from "./TrustSection";
import StepsSection from "./StepsSection";
import FeatureSection from "./FeatureSection";
import GallerySection from "./GallerySection";
import SocialSection from "./SocialSection";
import RichTextSection from "./RichTextSection";
import CollectionsSection from "./CollectionsSection";
import type { ShopNewBlockProps, ShopSectionProps } from "./types";

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
  COUPONS: CouponsSection,
};

/**
 * AG4 block-only content types (CUSTOM `layout.blocks`). They are not sections: the legacy section path and
 * SHOP_SECTION_COMPONENTS above never see them.
 */
export const SHOP_BLOCK_COMPONENTS: Record<NewBlockType, ComponentType<ShopNewBlockProps>> = {
  STEPS: StepsSection,
  FEATURE: FeatureSection,
  GALLERY: GallerySection,
  SOCIAL: SocialSection,
  RICH_TEXT: RichTextSection,
  COLLECTIONS: CollectionsSection,
};
