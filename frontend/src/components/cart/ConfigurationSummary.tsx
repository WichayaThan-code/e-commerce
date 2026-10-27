import "./configurationSummary.css";
import { formatBaht } from "./cartFormat";
import { moneyValue, type ConfigurationSnapshot } from "../../services/api";

type Label = Record<string, string | null | undefined> | string | null | undefined;

/** Picks the label for the active language (th fallback). Labels are plain text: React escapes them, never HTML. */
function pick(label: Label, lang: string) {
  if (!label) return "";
  if (typeof label === "string") return label;
  const key = lang.startsWith("en") ? "en" : lang.startsWith("zh") ? "zh" : "th";
  return label[key] || label.th || label.en || "";
}

/**
 * V43 product-configurator summary for a cart line / checkout line / order item ("สี: ทอง +฿120").
 * Renders nothing for normal (non-configured) lines, so existing layouts stay unchanged.
 */
export default function ConfigurationSummary({ configuration, localeTag, className }: { configuration?: ConfigurationSnapshot | null; localeTag: string; className?: string }) {
  const lines = configuration?.lines ?? [];
  if (!lines.length) return null;
  return (
    <ul className={className ?? "cart-config-summary"} data-testid="configuration-summary">
      {lines.map((line, index) => {
        const value = line.optionId == null ? (line.text ?? pick(line.label, localeTag)) : pick(line.label, localeTag);
        const delta = moneyValue(line.priceDelta);
        return (
          <li key={`${line.stepId}-${line.optionId ?? index}`}>
            <span>{pick(line.stepLabel, localeTag)}: </span><span>{value}</span>
            {delta !== 0 ? <span className="num"> {delta > 0 ? "+" : ""}{formatBaht(delta, localeTag)}</span> : null}
          </li>
        );
      })}
    </ul>
  );
}
