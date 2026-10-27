import { useMemo } from "react";
import { BadgeCheck, PackageCheck, RefreshCw, ShieldCheck, Sparkles } from "lucide-react";
import { usePublicTranslation } from "../../../hooks/usePublicTranslation";
import { mediaUrl } from "../../../services/api";
import { isLocalizedEmpty, resolveStepsContent, type StepIcon } from "../../../theme/blockContent";
import { BlockCtaButton, BlockHeading, panelClasses, useLocalized } from "./blockParts";
import type { ShopNewBlockProps } from "./types";

const ICONS = {
  AUTHENTIC: BadgeCheck,
  FAST_SHIP: PackageCheck,
  RETURNS: RefreshCw,
  SECURE_PAY: ShieldCheck,
  CUSTOM: Sparkles,
} as const;

/** AG4 STEPS: 2–6 numbered / icon steps with an optional call-to-action. Empty content renders nothing. */
export default function StepsSection({ block }: ShopNewBlockProps) {
  const content = useMemo(() => resolveStepsContent(block.content), [block.content]);
  const { tp } = usePublicTranslation();
  const l = useLocalized();

  const steps = content.steps.filter(
    (step) => !isLocalizedEmpty(step.title) || !isLocalizedEmpty(step.body) || Boolean(step.mediaUrl),
  );
  if (!steps.length && isLocalizedEmpty(content.title) && isLocalizedEmpty(content.subtitle)) return null;

  const renderMarker = (icon: StepIcon, index: number, imageUrl?: string) => {
    if (icon === "CUSTOM" && imageUrl) {
      return <img className="shop-steps-image" src={mediaUrl(imageUrl)} alt="" loading="lazy" />;
    }
    if (icon === "NUMBER") return <span className="shop-steps-number num" aria-hidden="true">{index + 1}</span>;
    const Icon = ICONS[icon];
    return <Icon className="shop-steps-icon" aria-hidden="true" />;
  };

  return (
    <section className={panelClasses(block, "shop-steps-section")} aria-label={l(content.title) || tp("shop.blocks.steps.label")}>
      <BlockHeading title={content.title} subtitle={content.subtitle} prefix="shop-steps" />
      {steps.length ? (
        <ol className="shop-steps-list">
          {steps.map((step, index) => (
            <li key={index} className="shop-steps-item">
              <span className="shop-steps-marker">{renderMarker(step.icon, index, step.mediaUrl)}</span>
              <div className="shop-steps-copy">
                <span className="shop-steps-order">{tp("shop.blocks.steps.stepOrder", { n: index + 1 })}</span>
                {!isLocalizedEmpty(step.title) ? <h3>{l(step.title)}</h3> : null}
                {!isLocalizedEmpty(step.body) ? <p>{l(step.body)}</p> : null}
              </div>
            </li>
          ))}
        </ol>
      ) : null}
      <BlockCtaButton cta={content.cta} className="shop-mall-btn is-primary shop-steps-cta" />
    </section>
  );
}
