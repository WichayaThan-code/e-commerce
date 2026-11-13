import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, Copy, Mail } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import { formatBaht } from "../cart/cartFormat";
import {
  moneyValue,
  platformContactApi,
  type MerchantCatalogPlan,
  type MerchantPlanMetric,
  type MerchantPlanUsage,
  type PlanBillingCycle,
} from "../../services/api";
import "../../styles/form-fields.css";
import "../../styles/settings.css";

type Props = {
  plan: MerchantCatalogPlan;
  usage: MerchantPlanUsage;
  billingCycle: PlanBillingCycle;
  shopSlug: string;
  contactEmail: string;
  isDowngrade: boolean;
  onClose: () => void;
  onContacted: () => void;
  notify: (text: string) => void;
};

type CompareRow = {
  key: "PRODUCTS" | "ORDERS_ROLLING_30D" | "STAFF" | "STORAGE";
  currentLabel: string;
  nextLabel: string;
  usedLabel: string | null;
  improved: boolean;
  overQuota: boolean;
};

function formatCount(n: number) {
  return n.toLocaleString("en-US");
}

function formatBytes(bytes: number) {
  if (bytes < 1024) return `${bytes} B`;
  const units = ["KiB", "MiB", "GiB", "TiB"] as const;
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit += 1;
  }
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[unit]}`;
}

function limitLabel(
  value: number | null | undefined,
  unit: "count" | "bytes",
  unlimited: string,
) {
  if (value == null) return unlimited;
  return unit === "bytes" ? formatBytes(value) : formatCount(value);
}

function metricUsed(metrics: MerchantPlanMetric[], code: string) {
  return metrics.find((row) => row.code === code)?.used ?? null;
}

function isImproved(
  current: number | null | undefined,
  next: number | null | undefined,
) {
  if (next == null && current != null) return true;
  if (next == null && current == null) return false;
  if (next != null && current == null) return false;
  return (next as number) > (current as number);
}

function isOver(used: number | null, nextLimit: number | null | undefined) {
  if (used == null || nextLimit == null) return false;
  return used > nextLimit;
}

export function yearlyPricing(monthlyRaw: number | string | null | undefined, yearlyRaw: number | string | null | undefined) {
  const monthly = moneyValue(monthlyRaw);
  const yearly = moneyValue(yearlyRaw);
  const fullYear = monthly * 12;
  const savingsBaht = Math.max(0, fullYear - yearly);
  const savingsPercent = fullYear > 0 ? (savingsBaht / fullYear) * 100 : 0;
  const avgPerMonth = yearly > 0 ? yearly / 12 : 0;
  return { monthly, yearly, savingsBaht, savingsPercent, avgPerMonth };
}

/** Compact plan summary + mailto admin (no payment copy, no dialog scroll). */
export default function PlanPurchaseDialog({
  plan,
  usage,
  billingCycle,
  shopSlug,
  contactEmail,
  isDowngrade,
  onClose,
  onContacted,
  notify,
}: Props) {
  const { t, i18n } = useTranslation("merchant");
  const [adminEmail, setAdminEmail] = useState<string | null>(null);
  const [emailReady, setEmailReady] = useState(false);

  const localeTag = i18n.language?.startsWith("zh")
    ? "zh-CN"
    : i18n.language?.startsWith("en")
      ? "en-GB"
      : "th-TH";

  useEffect(() => {
    let alive = true;
    platformContactApi
      .get()
      .then((data) => {
        if (!alive) return;
        const address = data.email?.address?.trim() || "";
        setAdminEmail(address || null);
      })
      .catch(() => {
        if (alive) setAdminEmail(null);
      })
      .finally(() => {
        if (alive) setEmailReady(true);
      });
    return () => {
      alive = false;
    };
  }, []);

  const pricing = useMemo(
    () => yearlyPricing(plan.priceMonthly, plan.priceYearly),
    [plan.priceMonthly, plan.priceYearly],
  );

  const amount = billingCycle === "YEARLY" ? pricing.yearly : pricing.monthly;
  const cycleLabel = t(`plan.billing.${billingCycle === "YEARLY" ? "yearly" : "monthly"}`);
  const currentPlanName = usage.plan.name || usage.plan.code || t("plan.noPlan");
  const currentLimits = usage.catalog.find((row) => row.code.toUpperCase() === (usage.plan.code ?? "").toUpperCase())?.limits
    ?? null;
  const nextLimits = plan.limits;

  const rows: CompareRow[] = useMemo(() => {
    const unlimited = t("plan.unlimited");
    const defs: Array<{
      key: CompareRow["key"];
      current: number | null | undefined;
      next: number | null | undefined;
      unit: "count" | "bytes";
    }> = [
      { key: "PRODUCTS", current: currentLimits?.products, next: nextLimits?.products, unit: "count" },
      { key: "ORDERS_ROLLING_30D", current: currentLimits?.ordersPerMonth, next: nextLimits?.ordersPerMonth, unit: "count" },
      { key: "STAFF", current: currentLimits?.staff, next: nextLimits?.staff, unit: "count" },
      { key: "STORAGE", current: currentLimits?.storageBytes, next: nextLimits?.storageBytes, unit: "bytes" },
    ];
    return defs.map((def) => {
      const used = metricUsed(usage.metrics, def.key);
      const usedLabel =
        used == null
          ? null
          : def.current == null
            ? `${def.unit === "bytes" ? formatBytes(used) : formatCount(used)}`
            : `${def.unit === "bytes" ? formatBytes(used) : formatCount(used)}/${def.unit === "bytes" ? formatBytes(def.current) : formatCount(def.current)}`;
      return {
        key: def.key,
        currentLabel: limitLabel(def.current, def.unit, unlimited),
        nextLabel: limitLabel(def.next, def.unit, unlimited),
        usedLabel,
        improved: isImproved(def.current, def.next),
        overQuota: isOver(used, def.next),
      };
    });
  }, [currentLimits, nextLimits, t, usage.metrics]);

  const overRows = rows.filter((row) => row.overQuota);
  const hasAdminEmail = Boolean(adminEmail);

  const openMailto = () => {
    if (!adminEmail) return;
    const subject = t("plan.upgrade.mailSubject", {
      plan: plan.name,
      cycle: cycleLabel,
      shop: usage.shopName,
    });
    const body = t("plan.upgrade.mailBody", {
      shopName: usage.shopName,
      shopSlug: shopSlug || String(usage.shopId),
      currentPlan: currentPlanName,
      targetPlan: plan.name,
      cycle: cycleLabel,
      price: formatBaht(amount, localeTag),
      contactEmail: contactEmail || t("plan.upgrade.mailContactMissing"),
    });
    window.location.href = `mailto:${adminEmail}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(body)}`;
    onContacted();
  };

  const copyEmail = async () => {
    if (!adminEmail) return;
    try {
      await navigator.clipboard.writeText(adminEmail);
      notify(t("plan.upgrade.emailCopied"));
    } catch {
      notify(t("plan.upgrade.emailCopyFailed"));
    }
  };

  const priceLine =
    billingCycle === "YEARLY"
      ? t("plan.upgrade.priceYearly", {
          amount: formatBaht(pricing.yearly, localeTag),
          avg: formatBaht(pricing.avgPerMonth, localeTag),
          save: formatBaht(pricing.savingsBaht, localeTag),
        })
      : t("plan.upgrade.priceMonthly", { amount: formatBaht(pricing.monthly, localeTag) });

  return (
    <ConfirmDialog
      className="plan-upgrade-dialog"
      title={t(isDowngrade ? "plan.upgrade.titleChange" : "plan.upgrade.title", { plan: plan.name })}
      message={t("plan.upgrade.messageShort", { cycle: cycleLabel, price: priceLine })}
      cancelLabel={t("plan.upgrade.cancel")}
      confirmLabel={t("plan.upgrade.contactAdmin")}
      confirmIcon={<Mail aria-hidden="true" />}
      confirmDisabled={!emailReady || !hasAdminEmail}
      confirmTitle={!hasAdminEmail && emailReady ? t("plan.upgrade.noAdminEmail") : undefined}
      onCancel={onClose}
      onConfirm={openMailto}
    >
      <div className="plan-upgrade-summary">
        <div className="plan-upgrade-compare" role="table" aria-label={t("plan.upgrade.compareAria")}>
          <div className="plan-upgrade-compare-head" role="row">
            <span role="columnheader">{t("plan.upgrade.compareMetric")}</span>
            <span role="columnheader">{t("plan.upgrade.compareCurrent")}</span>
            <span role="columnheader">{t("plan.upgrade.compareNext")}</span>
          </div>
          {rows.map((row) => (
            <div key={row.key} className={`plan-upgrade-compare-row${row.improved ? " is-improved" : ""}`} role="row">
              <span role="cell">
                <b>{t(`plan.metrics.${row.key}.name`)}</b>
                {row.usedLabel ? (
                  <em className={row.overQuota ? "is-over" : undefined}>
                    {t("plan.upgrade.usageInline", { value: row.usedLabel })}
                  </em>
                ) : null}
              </span>
              <span role="cell">{row.currentLabel}</span>
              <span role="cell" className={row.improved ? "is-better" : undefined}>{row.nextLabel}</span>
            </div>
          ))}
        </div>

        {overRows.length > 0 ? (
          <p className="form-alert plan-upgrade-warn" role="alert">
            <AlertTriangle aria-hidden="true" />
            <span>
              {t("plan.upgrade.downgradeWarn", {
                items: overRows.map((row) => t(`plan.metrics.${row.key}.name`)).join(", "),
              })}
            </span>
          </p>
        ) : null}

        {emailReady && !hasAdminEmail ? (
          <p className="plan-upgrade-hint" role="status">{t("plan.upgrade.noAdminEmail")}</p>
        ) : null}

        {hasAdminEmail ? (
          <div className="plan-upgrade-email-row">
            <code>{adminEmail}</code>
            <button type="button" className="ghost" onClick={() => void copyEmail()}>
              <Copy aria-hidden="true" />
              {t("plan.upgrade.copyEmail")}
            </button>
          </div>
        ) : null}
      </div>
    </ConfirmDialog>
  );
}
