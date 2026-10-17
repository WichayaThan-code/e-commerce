import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { FieldError } from "../form/FormField";
import ToggleSwitch from "../form/ToggleSwitch";
import SearchableSelect from "../SearchableSelect";
import { productApi, type ProductImage } from "../../services/api";
import type { LocalImage } from "./ProductImagesField";
import {
  applyGeneratedSkuCodes,
  axesFromGroups,
  dedupeSkuCode,
  emptySku,
  optionKey,
  reconcileVariants,
  skuAxesFromGroups,
  variantIssues,
  type OptionGroupDraft,
  type SkuDraft,
  type VariantIssue,
} from "../../pages/productFormModel";

const IMMEDIATE_CODES = new Set(["SKU_CODE_DUPLICATE_IN_FORM", "SKU_CODE_TAKEN"]);

type Props = {
  shopId: number;
  productId?: string;
  codeStem: string;
  groups: OptionGroupDraft[];
  skus: SkuDraft[];
  price: string;
  images: ProductImage[];
  localImages: LocalImage[];
  serverErrors: Record<string, string>;
  submitAttempted: boolean;
  onChange: (groups: OptionGroupDraft[], skus: SkuDraft[]) => void;
  onIssues: (issues: VariantIssue[]) => void;
};

function moveItem<T>(items: T[], from: number, to: number) {
  if (from === to || from < 0 || to < 0 || from >= items.length || to >= items.length) return items;
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
}

export default function VariantEditor({
  shopId, productId, codeStem, groups, skus, price, images, localImages, serverErrors, submitAttempted, onChange, onIssues,
}: Props) {
  const { t } = useTranslation("merchant");
  const [prefix, setPrefix] = useState("");
  const [pick, setPick] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  const [bulk, setBulk] = useState({ price: "", compareAtPrice: "", costPrice: "", initialStock: "", weightGrams: "", image: "" });
  const [taken, setTaken] = useState<string[]>([]);
  const [drag, setDrag] = useState<{ kind: "group" | "value"; group: number; index: number } | null>(null);
  const [draftNames, setDraftNames] = useState<Record<string, string>>({});
  const [draftLabels, setDraftLabels] = useState<Record<string, string>>({});
  const [matrixError, setMatrixError] = useState("");
  const [touched, setTouched] = useState<Set<string>>(() => new Set());
  const codeToken = useRef(0);
  const skusRef = useRef(skus);
  const groupsRef = useRef(groups);
  skusRef.current = skus;
  groupsRef.current = groups;

  const touch = (rowKey: string, field: string) => {
    const key = `${rowKey}.${field}`;
    setTouched((current) => current.has(key) ? current : new Set(current).add(key));
  };
  const touchMany = (keys: string[]) => {
    if (!keys.length) return;
    setTouched((current) => {
      const next = new Set(current);
      keys.forEach((key) => next.add(key));
      return next;
    });
  };

  const codes = skus.filter((sku) => !sku.removed).map((sku) => sku.skuCode.trim()).filter(Boolean).join("\n");
  useEffect(() => {
    const list = codes.split("\n").filter(Boolean);
    if (!list.length) {
      setTaken([]);
      return;
    }
    const timer = window.setTimeout(() => {
      productApi.checkSkus(shopId, list, productId).then((result) => setTaken(result.taken)).catch(() => setTaken([]));
    }, 350);
    return () => window.clearTimeout(timer);
  }, [codes, shopId, productId]);

  const issues = useMemo(
    () => variantIssues({ productType: "VARIABLE", skus, optionSchema: axesFromGroups(groups) }, new Set(taken)),
    [groups, skus, taken],
  );
  useEffect(() => { onIssues(issues); }, [issues, onIssues]);
  useEffect(() => {
    const live = new Set(skus.map((sku) => sku.clientKey));
    setTouched((current) => {
      const next = new Set([...current].filter((key) => live.has(key.slice(0, key.lastIndexOf(".")))));
      return next.size === current.size ? current : next;
    });
  }, [skus]);

  const reserveCodes = (snapshot: SkuDraft[], targets: Set<number>, stem: string) => {
    const token = ++codeToken.current;
    const owned = new Map<string, Set<string>>();
    targets.forEach((index) => {
      const sku = snapshot[index];
      if (!sku || sku.id) return;
      owned.set(sku.clientKey, new Set([sku.skuCode.trim()].filter(Boolean)));
    });
    const run = async (working: SkuDraft[], pending: Set<number>, round: number) => {
      if (token !== codeToken.current || !pending.size || round > 8) return;
      const proposed = [...pending].map((index) => working[index]?.skuCode.trim() || "").filter(Boolean);
      if (!proposed.length) return;
      let taken: string[] = [];
      try {
        taken = (await productApi.checkSkus(shopId, proposed, productId)).taken;
      } catch {
        return;
      }
      if (token !== codeToken.current) return;
      const hits = new Set(taken);
      const collide = new Set([...pending].filter((index) => hits.has(working[index]?.skuCode.trim() || "")));
      if (!collide.size) {
        const latest = skusRef.current;
        const nextCode = new Map(working.map((sku) => [sku.clientKey, sku.skuCode]));
        onChange(groupsRef.current, latest.map((sku) => {
          const allowed = owned.get(sku.clientKey);
          const code = nextCode.get(sku.clientKey);
          if (!allowed || !code || (sku.skuCode.trim() && !allowed.has(sku.skuCode.trim()))) return sku;
          return { ...sku, skuCode: code };
        }));
        return;
      }
      const reserved = working.map((sku) => sku.skuCode.trim()).filter(Boolean);
      const bumped = applyGeneratedSkuCodes(working, skuAxesFromGroups(groupsRef.current), stem, collide, reserved);
      bumped.forEach((sku, index) => {
        if (!collide.has(index)) return;
        owned.get(sku.clientKey)?.add(sku.skuCode.trim());
      });
      void run(bumped, collide, round + 1);
    };
    void run(snapshot, targets, 0);
  };

  const updateGroups = (nextGroups: OptionGroupDraft[]) => {
    const known = new Set(skus.map((sku) => sku.clientKey));
    const reconciled = reconcileVariants(groups, nextGroups, skus, price, codeStem);
    if (reconciled.error === "TOO_MANY") {
      setMatrixError("TOO_MANY");
      return;
    }
    setMatrixError("");
    setSelected([]);
    const kept = nextGroups.slice(0, 3);
    onChange(kept, reconciled.skus);
    const generated = new Set<number>();
    reconciled.skus.forEach((sku, index) => {
      if (!sku.removed && !sku.id && !known.has(sku.clientKey) && sku.skuCode.trim()) generated.add(index);
    });
    if (generated.size) reserveCodes(reconciled.skus, generated, codeStem);
  };

  const patchSku = (index: number, patch: Partial<SkuDraft>) => {
    onChange(groups, skus.map((sku, skuIndex) => skuIndex === index ? { ...sku, ...patch } : sku));
  };

  const applyBulk = () => {
    const marks: string[] = [];
    onChange(groups, skus.map((sku, index) => {
      if (sku.removed || !selected.includes(sku.clientKey)) return sku;
      const next = { ...sku };
      if (bulk.price.trim()) { next.price = bulk.price; marks.push(`${sku.clientKey}.price`); }
      if (bulk.compareAtPrice.trim()) { next.compareAtPrice = bulk.compareAtPrice; marks.push(`${sku.clientKey}.compareAtPrice`); }
      if (bulk.costPrice.trim()) { next.costPrice = bulk.costPrice; marks.push(`${sku.clientKey}.costPrice`); }
      if (bulk.initialStock.trim()) { next.initialStock = bulk.initialStock; marks.push(`${sku.clientKey}.initialStock`); }
      if (bulk.weightGrams.trim()) { next.weightGrams = bulk.weightGrams; marks.push(`${sku.clientKey}.weightGrams`); }
      if (bulk.image) {
        marks.push(`${sku.clientKey}.imageId`);
        if (bulk.image.startsWith("local:")) {
          next.imageLocalKey = bulk.image.slice(6);
          next.imageId = undefined;
        } else {
          next.imageId = bulk.image;
          next.imageLocalKey = undefined;
        }
      }
      return next;
    }));
    touchMany(marks);
  };

  const fillCombinations = async () => {
    const optionGroups = groups
      .map((group) => ({
        name: group.name.trim(),
        values: group.values.map((value) => value.label.trim()).filter(Boolean).map((value) => ({ value })),
      }))
      .filter((group) => group.name && group.values.length);
    const applyLocal = () => {
      const known = new Set(skus.map((sku) => sku.clientKey));
      const reconciled = reconcileVariants(groups, groups, skus, price, codeStem);
      if (reconciled.error === "TOO_MANY") {
        setMatrixError("TOO_MANY");
        return;
      }
      setMatrixError("");
      onChange(groups, reconciled.skus);
      const generated = new Set<number>();
      reconciled.skus.forEach((sku, index) => {
        if (!sku.removed && !sku.id && !known.has(sku.clientKey) && sku.skuCode.trim()) generated.add(index);
      });
      if (generated.size) reserveCodes(reconciled.skus, generated, codeStem);
    };
    try {
      const rows = await productApi.variantCombinations(shopId, { skuPrefix: prefix.trim() || codeStem, optionGroups });
      const same = (left: Record<string, string>, right: Record<string, string>) => {
        const names = new Set([...Object.keys(left), ...Object.keys(right)]);
        for (const name of names) if ((left[name] || "") !== (right[name] || "")) return false;
        return true;
      };
      const used = new Set<number>();
      const taken = new Set(skus.map((sku) => sku.skuCode.trim()).filter(Boolean));
      const next: SkuDraft[] = [];
      const fresh = new Set<number>();
      rows.forEach((row) => {
        const found = skus.findIndex((sku, index) => !used.has(index) && same(sku.optionValues, row.optionValues || {}));
        if (found >= 0) {
          used.add(found);
          next.push({ ...skus[found], removed: false });
          return;
        }
        fresh.add(next.length);
        next.push(emptySku({
          optionLabel: row.optionLabel,
          optionValues: row.optionValues || {},
          price,
          skuCode: dedupeSkuCode(row.skuCode || "", taken),
        }));
      });
      skus.forEach((sku, index) => {
        if (used.has(index)) return;
        if (sku.id || sku.skuCode.trim() || sku.removed) next.push({ ...sku, removed: true });
      });
      setMatrixError("");
      onChange(groups, next);
      if (fresh.size) reserveCodes(next, fresh, prefix.trim() || codeStem);
    } catch {
      applyLocal();
    }
  };

  const generateCodes = () => {
    const targets = new Set<number>();
    skus.forEach((sku, index) => {
      if (sku.removed || sku.id || sku.skuCode.trim()) return;
      if (selected.length && !selected.includes(sku.clientKey)) return;
      targets.add(index);
    });
    if (!targets.size) return;
    const next = applyGeneratedSkuCodes(skus, skuAxesFromGroups(groups), codeStem, targets);
    onChange(groups, next);
    touchMany([...targets].map((index) => `${skus[index].clientKey}.skuCode`));
    reserveCodes(next, targets, codeStem);
  };

  const selectMatching = () => {
    const [name, label] = pick.split("::");
    if (!name) return;
    setSelected(skus.flatMap((sku) => !sku.removed && sku.optionValues[name] === label ? [sku.clientKey] : []));
  };

  const restore = (index: number) => {
    const sku = skus[index];
    const next = groups.map((group) => ({ ...group, values: [...group.values] }));
    Object.entries(sku.optionValues).forEach(([name, label]) => {
      let group = next.find((item) => item.name.trim() === name);
      if (!group && next.length < 3) {
        group = { key: optionKey(), name, values: [] };
        next.push(group);
      }
      if (group && label && !group.values.some((value) => value.label === label)) {
        group.values.push({ key: optionKey(), label });
      }
    });
    updateGroups(next);
  };

  const activeKeys = skus.flatMap((sku) => sku.removed ? [] : [sku.clientKey]);
  const allSelected = activeKeys.length > 0 && activeKeys.every((key) => selected.includes(key));
  const pickOptions = groups.flatMap((group) => group.values.filter((value) => value.label.trim()).map((value) => ({
    value: `${group.name}::${value.label}`,
    label: `${group.name || t("catalog.variants.groupName")}: ${value.label}`,
  })));
  const imageChoices = [
    ...images.map((image, index) => ({ value: image.id, label: t("catalog.variants.imageN", { n: index + 1 }) })),
    ...localImages.map((image, index) => ({ value: `local:${image.key}`, label: t("catalog.variants.localImage", { n: index + 1 }) })),
  ];
  const shown = (issue: VariantIssue) => {
    if (submitAttempted || IMMEDIATE_CODES.has(issue.code)) return true;
    if (issue.index < 0) return false;
    const rowKey = skus[issue.index]?.clientKey;
    return Boolean(rowKey && touched.has(`${rowKey}.${issue.field}`));
  };
  const visibleIssues = issues.filter(shown);
  const issueAt = (index: number, field: string) => {
    const code = issues.find((issue) => issue.index === index && issue.field === field)?.code;
    if (code && shown({ index, field, code })) return code;
    const server = serverErrors[`skus[${index}].${field}`];
    return server && submitAttempted ? server : undefined;
  };
  const message = (code?: string) => (code ? t(`catalog.errors.${code}`, { defaultValue: t(`catalog.variants.${code}`, { defaultValue: code }) }) : "");
  const optionError = (groupIndex: number, tail: string) => (
    submitAttempted ? serverErrors[`optionGroups[${groupIndex}].${tail}`] || serverErrors[`optionSchema[${groupIndex}].${tail}`] || "" : ""
  );
  const looseErrors = submitAttempted
    ? Object.entries(serverErrors).filter(([field]) => !/^skus\[\d+\]\./.test(field) && !/^(optionGroups|optionSchema)\[\d+\]\.(name|values\[\d+\]\.value)$/.test(field) && (field.startsWith("option") || field === "skus"))
    : [];

  return (
    <section className="variant-editor">
      <h3>{t("catalog.variants.title")}</h3>
      <div className="variant-groups">
        {groups.map((group, groupIndex) => (
          <article
            key={group.key}
            draggable
            onDragStart={() => setDrag({ kind: "group", group: groupIndex, index: groupIndex })}
            onDragOver={(event) => event.preventDefault()}
            onDrop={() => {
              if (drag?.kind === "group") updateGroups(moveItem(groups, drag.group, groupIndex));
              setDrag(null);
            }}
          >
            <div className="variant-group-grid">
              <label>
                <span className="pm-lang-label">{t("catalog.variants.groupName")}</span>
                <input
                  value={draftNames[group.key] ?? group.name}
                  aria-invalid={Boolean(optionError(groupIndex, "name"))}
                  placeholder={t("catalog.variants.groupName")}
                  onChange={(event) => setDraftNames((current) => ({ ...current, [group.key]: event.target.value }))}
                  onBlur={() => {
                    const name = (draftNames[group.key] ?? group.name).trim();
                    setDraftNames((current) => {
                      const next = { ...current };
                      delete next[group.key];
                      return next;
                    });
                    if (!name || name === group.name) return;
                    updateGroups(groups.map((item, index) => index === groupIndex ? { ...item, name } : item));
                  }}
                />
                <FieldError text={message(optionError(groupIndex, "name"))} />
              </label>
              <label>
                <span className="pm-lang-label">{t("catalog.variants.valuePh")}</span>
                <input
                  placeholder={t("catalog.variants.valuePh")}
                  onKeyDown={(event) => {
                    if (event.key !== "Enter") return;
                    event.preventDefault();
                    const label = event.currentTarget.value.trim();
                    if (!label || group.values.some((value) => value.label === label)) return;
                    updateGroups(groups.map((item, index) => index === groupIndex ? { ...item, values: [...item.values, { key: optionKey(), label }] } : item));
                    event.currentTarget.value = "";
                  }}
                />
              </label>
            </div>
            <div className="variant-chips">
              {group.values.map((value, valueIndex) => (
                <span
                  key={value.key}
                  className="variant-chip"
                  draggable
                  onDragStart={(event) => {
                    event.stopPropagation();
                    setDrag({ kind: "value", group: groupIndex, index: valueIndex });
                  }}
                  onDragOver={(event) => event.preventDefault()}
                  onDrop={(event) => {
                    event.stopPropagation();
                    if (drag?.kind === "value" && drag.group === groupIndex) {
                      updateGroups(groups.map((item, index) => index === groupIndex ? { ...item, values: moveItem(item.values, drag.index, valueIndex) } : item));
                    }
                    setDrag(null);
                  }}
                >
                  <input
                    value={draftLabels[value.key] ?? value.label}
                    aria-invalid={Boolean(optionError(groupIndex, `values[${valueIndex}].value`))}
                    aria-label={value.label}
                    onChange={(event) => setDraftLabels((current) => ({ ...current, [value.key]: event.target.value }))}
                    onBlur={() => {
                      const label = (draftLabels[value.key] ?? value.label).trim();
                      setDraftLabels((current) => {
                        const next = { ...current };
                        delete next[value.key];
                        return next;
                      });
                      if (!label || label === value.label) return;
                      updateGroups(groups.map((item, index) => index === groupIndex ? {
                        ...item,
                        values: item.values.map((entry) => entry.key === value.key ? { ...entry, label } : entry),
                      } : item));
                    }}
                  />
                  <FieldError text={message(optionError(groupIndex, `values[${valueIndex}].value`))} />
                  <button type="button" aria-label={t("catalog.variants.removeValue", { name: value.label })} onClick={() => updateGroups(groups.map((item, index) => index === groupIndex ? { ...item, values: item.values.filter((entry) => entry.key !== value.key) } : item))}>×</button>
                </span>
              ))}
            </div>
            <div className="pm-actions">
              <button type="button" onClick={() => updateGroups(groups.filter((_, index) => index !== groupIndex))}>{t("catalog.variants.removeGroup")}</button>
            </div>
          </article>
        ))}
        <div className="pm-actions">
          <button type="button" disabled={groups.length >= 3} onClick={() => updateGroups([...groups, { key: optionKey(), name: "", values: [] }])}>{t("catalog.variants.addGroup")}</button>
        </div>
      </div>
      {matrixError ? <FieldError text={t("catalog.variants.tooMany")} /> : null}
      {(submitAttempted && visibleIssues.length > 0) || looseErrors.length > 0 ? (
        <div className="variant-problems" id="variant-problems">
          <strong>{t("catalog.variants.problems", { count: visibleIssues.length + looseErrors.length })}</strong>
          {looseErrors.map(([field, code]) => (
            <span key={field}>{message(code)}</span>
          ))}
          {visibleIssues.filter((issue) => issue.index < 0).map((issue) => (
            <span key={`${issue.field}-${issue.code}`}>{message(issue.code)}</span>
          ))}
          {visibleIssues.filter((issue) => issue.index >= 0).map((issue) => (
            <button type="button" key={`${issue.index}-${issue.field}-${issue.code}`} onClick={() => document.getElementById(`variant-row-${issue.index}`)?.scrollIntoView({ behavior: "smooth", block: "center" })}>
              {t("catalog.variants.row", { row: issue.index + 1 })} · {message(issue.code)}
            </button>
          ))}
        </div>
      ) : null}
      <div className="variant-bulk">
        <SearchableSelect
          label={t("catalog.variants.selectBy")}
          placeholder={t("catalog.variants.selectBy")}
          options={pickOptions.map((option) => option.label)}
          value={pickOptions.find((option) => option.value === pick)?.label ?? ""}
          onChange={(label) => setPick(pickOptions.find((option) => option.label === label)?.value ?? "")}
        />
        <label>
          <span className="pm-lang-label">{t("catalog.variants.price")}</span>
          <input className="num" inputMode="decimal" value={bulk.price} placeholder={t("catalog.variants.price")} onChange={(event) => setBulk({ ...bulk, price: event.target.value })} />
        </label>
        <label>
          <span className="pm-lang-label">{t("catalog.variants.compare")}</span>
          <input className="num" inputMode="decimal" value={bulk.compareAtPrice} placeholder={t("catalog.variants.compare")} onChange={(event) => setBulk({ ...bulk, compareAtPrice: event.target.value })} />
        </label>
        <label>
          <span className="pm-lang-label">{t("catalog.variants.cost")}</span>
          <input className="num" inputMode="decimal" value={bulk.costPrice} placeholder={t("catalog.variants.cost")} onChange={(event) => setBulk({ ...bulk, costPrice: event.target.value })} />
        </label>
        <label>
          <span className="pm-lang-label">{t("catalog.variants.stock")}</span>
          <input className="num" inputMode="numeric" value={bulk.initialStock} placeholder={t("catalog.variants.stock")} onChange={(event) => setBulk({ ...bulk, initialStock: event.target.value })} />
        </label>
        <label>
          <span className="pm-lang-label">{t("catalog.variants.weight")}</span>
          <input className="num" inputMode="numeric" value={bulk.weightGrams} placeholder={t("catalog.variants.weight")} onChange={(event) => setBulk({ ...bulk, weightGrams: event.target.value })} />
        </label>
        <SearchableSelect
          label={t("catalog.variants.image")}
          placeholder={t("catalog.variants.image")}
          options={imageChoices.map((choice) => choice.label)}
          value={imageChoices.find((choice) => choice.value === bulk.image)?.label ?? ""}
          onChange={(label) => setBulk({ ...bulk, image: imageChoices.find((choice) => choice.label === label)?.value ?? "" })}
        />
        <label>
          <span className="pm-lang-label">{t("catalog.variants.prefix")}</span>
          <input value={prefix} placeholder={t("catalog.variants.prefix")} onChange={(event) => setPrefix(event.target.value)} />
        </label>
        <div className="pm-actions">
          <button type="button" onClick={selectMatching}>{t("catalog.variants.selectMatching")}</button>
          <button type="button" className="primary" onClick={applyBulk}>{t("catalog.variants.apply")}</button>
          <button type="button" className="primary" onClick={() => void fillCombinations()}>{t("catalog.variants.fillCombinations")}</button>
          <button type="button" className="primary" onClick={generateCodes}>{t("catalog.variants.generate")}</button>
        </div>
      </div>
      <div className="pm-detail">
        <div className="pm-table-scroll">
        <table>
          <thead>
            <tr>
              <th><input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : activeKeys)} aria-label={t("catalog.variants.selectAll")} /></th>
              <th><span className="pm-lang-label">{t("catalog.variants.image")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.combination")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.sku")} <em>*</em></span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.price")} <em>*</em></span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.compare")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.cost")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.barcode")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.weight")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.stock")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.safety")}</span></th>
              <th><span className="pm-lang-label">{t("catalog.variants.active")}</span></th>
            </tr>
          </thead>
          <tbody>
            {skus.map((sku, index) => {
              const skuError = issueAt(index, "skuCode");
              const priceError = issueAt(index, "price");
              const compareError = issueAt(index, "compareAtPrice");
              const costError = issueAt(index, "costPrice");
              const weightError = issueAt(index, "weightGrams");
              const comboError = issueAt(index, "optionValues");
              const stockError = issueAt(index, "initialStock");
              const safetyError = issueAt(index, "safetyStock");
              const barcodeInvalid = Boolean(sku.barcode.trim()) && !/^[A-Z0-9-]+$/.test(sku.barcode.trim());
              const barcodeError = barcodeInvalid ? "BARCODE_INVALID" : issueAt(index, "barcode");
              const imageValue = sku.imageLocalKey ? `local:${sku.imageLocalKey}` : sku.imageId || "";
              const rowName = sku.optionLabel || t("catalog.variants.combination");
              const named = (column: string) => `${column} ${rowName}`;
              return (
                <tr id={`variant-row-${index}`} key={sku.clientKey} className={sku.removed ? "variant-removed" : sku.active ? undefined : "variant-inactive"}>
                  <td><input type="checkbox" disabled={sku.removed} aria-label={named(t("catalog.variants.selectRow"))} checked={!sku.removed && selected.includes(sku.clientKey)} onChange={() => setSelected((current) => current.includes(sku.clientKey) ? current.filter((item) => item !== sku.clientKey) : [...current, sku.clientKey])} /></td>
                  <td>
                    <select
                      aria-label={named(t("catalog.variants.image"))}
                      value={imageValue}
                      disabled={sku.removed}
                      onBlur={() => touch(sku.clientKey, "imageId")}
                      onChange={(event) => {
                        touch(sku.clientKey, "imageId");
                        const value = event.target.value;
                        if (value.startsWith("local:")) patchSku(index, { imageLocalKey: value.slice(6), imageId: undefined });
                        else patchSku(index, { imageId: value || undefined, imageLocalKey: undefined });
                      }}
                    >
                      <option value="">{t("catalog.variants.noImage")}</option>
                      {images.map((image, imageIndex) => <option key={image.id} value={image.id}>{t("catalog.variants.imageN", { n: imageIndex + 1 })}</option>)}
                      {localImages.map((image, imageIndex) => <option key={image.key} value={`local:${image.key}`}>{t("catalog.variants.localImage", { n: imageIndex + 1 })}</option>)}
                    </select>
                  </td>
                  <td>
                    {sku.optionLabel || "—"}
                    <FieldError text={message(comboError)} />
                    {sku.removed ? <div className="pm-actions"><button type="button" onClick={() => restore(index)}>{t("catalog.variants.restore")}</button></div> : null}
                  </td>
                  <td>
                    <input className="num" aria-label={named(t("catalog.variants.sku"))} aria-invalid={Boolean(skuError)} value={sku.skuCode} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "skuCode")} onChange={(event) => { touch(sku.clientKey, "skuCode"); patchSku(index, { skuCode: event.target.value }); }} />
                    <FieldError text={message(skuError)} />
                  </td>
                  <td>
                    <input className="num" inputMode="decimal" aria-label={named(t("catalog.variants.price"))} aria-invalid={Boolean(priceError)} value={sku.price} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "price")} onChange={(event) => { touch(sku.clientKey, "price"); patchSku(index, { price: event.target.value }); }} />
                    <FieldError text={message(priceError)} />
                  </td>
                  <td>
                    <input className="num" inputMode="decimal" aria-label={named(t("catalog.variants.compare"))} aria-invalid={Boolean(compareError)} value={sku.compareAtPrice} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "compareAtPrice")} onChange={(event) => { touch(sku.clientKey, "compareAtPrice"); patchSku(index, { compareAtPrice: event.target.value }); }} />
                    <FieldError text={message(compareError)} />
                  </td>
                  <td>
                    <input className="num" inputMode="decimal" aria-label={named(t("catalog.variants.cost"))} aria-invalid={Boolean(costError)} value={sku.costPrice} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "costPrice")} onChange={(event) => { touch(sku.clientKey, "costPrice"); patchSku(index, { costPrice: event.target.value }); }} />
                    <FieldError text={message(costError)} />
                  </td>
                  <td>
                    <input className="num" aria-label={named(t("catalog.variants.barcode"))} aria-invalid={Boolean(barcodeError)} value={sku.barcode} disabled={sku.removed} onBlur={() => { touch(sku.clientKey, "barcode"); patchSku(index, { barcode: sku.barcode.trim().toUpperCase() }); }} onChange={(event) => { touch(sku.clientKey, "barcode"); patchSku(index, { barcode: event.target.value.toUpperCase() }); }} />
                    <FieldError text={message(barcodeError)} />
                  </td>
                  <td>
                    <input className="num" inputMode="numeric" aria-label={named(t("catalog.variants.weight"))} aria-invalid={Boolean(weightError)} value={sku.weightGrams} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "weightGrams")} onChange={(event) => { touch(sku.clientKey, "weightGrams"); patchSku(index, { weightGrams: event.target.value }); }} />
                    <FieldError text={message(weightError)} />
                  </td>
                  <td>
                    <input className="num" inputMode="numeric" aria-label={named(t("catalog.variants.stock"))} aria-invalid={Boolean(stockError)} value={sku.initialStock} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "initialStock")} onChange={(event) => { touch(sku.clientKey, "initialStock"); patchSku(index, { initialStock: event.target.value }); }} />
                    <FieldError text={message(stockError)} />
                  </td>
                  <td>
                    <input className="num" inputMode="numeric" aria-label={named(t("catalog.variants.safety"))} aria-invalid={Boolean(safetyError)} value={sku.safetyStock} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "safetyStock")} onChange={(event) => { touch(sku.clientKey, "safetyStock"); patchSku(index, { safetyStock: event.target.value }); }} />
                    <FieldError text={message(safetyError)} />
                  </td>
                  <td>
                    <ToggleSwitch
                      checked={sku.active}
                      disabled={sku.removed}
                      label={named(t("catalog.variants.active"))}
                      onText={t("catalog.toggle.on")}
                      offText={t("catalog.toggle.off")}
                      onChange={(checked) => patchSku(index, { active: checked })}
                    />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
        </div>
      </div>
    </section>
  );
}
