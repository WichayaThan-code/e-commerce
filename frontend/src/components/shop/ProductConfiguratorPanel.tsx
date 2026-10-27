import { useEffect, useMemo, useRef, useState } from "react";
import { FEATURES } from "../../config/features";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { mediaUrl, type PublicShop } from "../../services/api";
import {
  labelFor,
  publicConfiguratorApi,
  type ConfiguratorQuote,
  type ConfiguratorSelections,
  type PublicConfigurator,
} from "../../services/collectionsApi";
import { isCustomTemplate, normalizeTheme, type ShopTheme } from "../../theme/shopTheme";
import "../../styles/shop-collections.css";

/**
 * V43 add-to-cart input. The page (which knows the chosen SKU, quantity and buyer access) sends it through
 * CartContext.addConfigured; the server re-quotes `selections` (client prices are never trusted) and keys the line by config hash.
 */
export type ConfiguredAddToCartInput = { productId: string; selections: ConfiguratorSelections; version: number; configHash: string };

const toNumber = (value: number | string | null | undefined) => (value == null ? 0 : Number(value));

/** Storefront configurator (CUSTOM template + FEATURES.productConfigurator only). Public API only; price is always server-quoted. */
export default function ProductConfiguratorPanel({ shop, productId, money, onAddToCart }: {
  shop: PublicShop; productId: string; money: (value: number) => string
  /** V43: returns true when the line was added; when absent the button stays disabled. */
  onAddToCart?: (input: ConfiguredAddToCartInput) => Promise<boolean>
}) {
  const { tp, localeTag } = usePublicTranslation();
  const custom = useMemo(() => {
    if (!shop.theme) return false;
    return isCustomTemplate(normalizeTheme(shop.theme as Partial<ShopTheme>).templateKey);
  }, [shop.theme]);
  const active = FEATURES.productConfigurator && custom;
  const [config, setConfig] = useState<PublicConfigurator | null>(null);
  const [selections, setSelections] = useState<ConfiguratorSelections>({});
  const [quote, setQuote] = useState<ConfiguratorQuote | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState(false);
  const [preview, setPreview] = useState<string | null>(null);
  const [adding, setAdding] = useState(false);
  const seq = useRef(0);

  useEffect(() => {
    if (!active) return;
    let alive = true;
    setConfig(null); setSelections({}); setQuote(null); setPreview(null);
    publicConfiguratorApi.get(shop.slug, productId)
      .then((row) => { if (alive && row.steps.length) setConfig(row); })
      .catch(() => { if (alive) setConfig(null); });
    return () => { alive = false; };
  }, [active, shop.slug, productId]);

  useEffect(() => {
    if (!config) return;
    const id = ++seq.current;
    setQuoting(true);
    const timer = window.setTimeout(() => {
      publicConfiguratorApi.quote(shop.slug, productId, selections, config.version)
        .then((next) => { if (id === seq.current) { setQuote(next); setQuoteError(false); } })
        .catch(() => { if (id === seq.current) setQuoteError(true); })
        .finally(() => { if (id === seq.current) setQuoting(false); });
    }, 250);
    return () => window.clearTimeout(timer);
  }, [config, selections, shop.slug, productId]);

  if (!active || !config) return null;

  const pick = (stepId: string, optionId: string, multi: boolean, max?: number, url?: string | null) => {
    setSelections((current) => {
      if (!multi) return { ...current, [stepId]: optionId };
      const list = Array.isArray(current[stepId]) ? (current[stepId] as string[]) : [];
      if (list.includes(optionId)) return { ...current, [stepId]: list.filter((x) => x !== optionId) };
      if (max != null && list.length >= max) return current;
      return { ...current, [stepId]: [...list, optionId] };
    });
    if (url) setPreview(url);
  };

  return (
    <section className="shop-configurator" aria-labelledby="shop-configurator-title">
      <h2 id="shop-configurator-title" className="shop-configurator-title">{tp("shop.configurator.title")}</h2>
      {preview ? <img className="shop-configurator-preview" src={mediaUrl(preview)} alt={tp("shop.configurator.previewAlt")} /> : null}
      {config.steps.map((step) => {
        const label = labelFor(step.label, localeTag);
        if (step.type === "TEXT") {
          const value = typeof selections[step.id] === "string" ? (selections[step.id] as string) : "";
          return (
            <div key={step.id} className="shop-configurator-step">
              <label className="shop-configurator-label">
                <span>{label}{step.required ? " *" : ""}{toNumber(step.priceDelta) ? ` (+${money(toNumber(step.priceDelta))})` : ""}</span>
                <input className="shop-configurator-input" value={value} maxLength={step.maxLength ?? 20}
                  onChange={(e) => setSelections((current) => ({ ...current, [step.id]: e.target.value }))} />
              </label>
              <small className="shop-configurator-hint">{tp("shop.configurator.chars", { count: value.length, max: step.maxLength ?? 20 })}</small>
            </div>
          );
        }
        const multi = step.type === "MULTI";
        const chosen = selections[step.id];
        return (
          <fieldset key={step.id} className="shop-configurator-step">
            <legend className="shop-configurator-label">
              {label}{step.required ? " *" : ""}
              {multi && step.max != null ? <small className="shop-configurator-hint"> {tp("shop.configurator.pickUpTo", { count: step.max })}</small> : null}
            </legend>
            <div className="shop-configurator-options">
              {(step.options ?? []).map((option) => {
                const selected = multi ? Array.isArray(chosen) && chosen.includes(option.id) : chosen === option.id;
                const delta = toNumber(option.priceDelta);
                return (
                  <button key={option.id} type="button" aria-pressed={selected} disabled={option.available === false}
                    className={`shop-configurator-option${selected ? " is-selected" : ""}`}
                    onClick={() => pick(step.id, option.id, multi, step.max, option.mediaUrl)}>
                    {option.mediaUrl ? <img src={mediaUrl(option.mediaUrl)} alt="" /> : null}
                    <span>{labelFor(option.label, localeTag)}</span>
                    {delta ? <small className="num">{delta > 0 ? "+" : "−"}{money(Math.abs(delta))}</small> : null}
                    {option.available === false ? <small>{tp("shop.configurator.unavailable")}</small> : null}
                  </button>
                );
              })}
            </div>
          </fieldset>
        );
      })}
      <div className="shop-configurator-summary" aria-live="polite">
        {quoteError ? <p className="shop-configurator-hint">{tp("shop.configurator.quoteError")}</p> : quote ? (
          <>
            <ul className="shop-configurator-lines">
              <li><span>{tp("shop.configurator.base")}</span><span className="num">{money(toNumber(quote.basePrice))}</span></li>
              {quote.lines.filter((line) => toNumber(line.priceDelta) !== 0).map((line) => (
                <li key={`${line.stepId}-${line.optionId ?? ""}`}><span>{line.label}</span><span className="num">{money(toNumber(line.priceDelta))}</span></li>
              ))}
            </ul>
            <p className="shop-configurator-total"><span>{tp("shop.configurator.total")}</span><b className="num">{money(toNumber(quote.total))}</b></p>
            {!quote.complete ? <p className="shop-configurator-hint">{tp("shop.configurator.incomplete")}</p> : null}
          </>
        ) : <p className="shop-configurator-hint">{quoting ? tp("shop.configurator.quoting") : ""}</p>}
      </div>
      {(() => {
        const canAdd = Boolean(onAddToCart && config && quote?.complete && !quoting && !quoteError && !adding);
        const add = async () => {
          if (!onAddToCart || !config || !quote?.complete) return;
          setAdding(true);
          try {
            await onAddToCart({ productId, selections: quote.selections as ConfiguratorSelections, version: config.version, configHash: quote.configHash });
          } finally {
            setAdding(false);
          }
        };
        return (
          <button type="button" className="shop-configurator-add" disabled={!canAdd} aria-disabled={!canAdd} onClick={() => { void add(); }}>
            {adding ? tp("shop.configurator.adding") : tp("shop.configurator.add")}
          </button>
        );
      })()}
    </section>
  );
}
