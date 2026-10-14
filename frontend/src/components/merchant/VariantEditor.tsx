import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import SearchableSelect from "../SearchableSelect";
import { productApi, type ProductImage } from "../../services/api";
import type { LocalImage } from "./ProductImagesField";
import {
  applyGeneratedSkuCodes,
  axesFromGroups,
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
  shopId, productId, groups, skus, price, images, localImages, serverErrors, submitAttempted, onChange, onIssues,
}: Props) {
  const { t } = useTranslation("merchant");
  const [prefix, setPrefix] = useState("");
  const [pick, setPick] = useState("");
  const [selected, setSelected] = useState<number[]>([]);
  const [bulk, setBulk] = useState({ price: "", compareAtPrice: "", costPrice: "", initialStock: "", weightGrams: "", image: "" });
  const [taken, setTaken] = useState<string[]>([]);
  const [drag, setDrag] = useState<{ kind: "group" | "value"; group: number; index: number } | null>(null);
  const [draftNames, setDraftNames] = useState<Record<string, string>>({});
  const [draftLabels, setDraftLabels] = useState<Record<string, string>>({});
  const [matrixError, setMatrixError] = useState("");
  const [touched, setTouched] = useState<Set<string>>(() => new Set());

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

  const updateGroups = (nextGroups: OptionGroupDraft[]) => {
    const reconciled = reconcileVariants(groups, nextGroups, skus, price);
    if (reconciled.error === "TOO_MANY") {
      setMatrixError("TOO_MANY");
      return;
    }
    setMatrixError("");
    setSelected([]);
    onChange(nextGroups.slice(0, 3), reconciled.skus);
  };

  const patchSku = (index: number, patch: Partial<SkuDraft>) => {
    onChange(groups, skus.map((sku, skuIndex) => skuIndex === index ? { ...sku, ...patch } : sku));
  };

  const applyBulk = () => {
    const marks: string[] = [];
    onChange(groups, skus.map((sku, index) => {
      if (sku.removed || !selected.includes(index)) return sku;
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

  const generateCodes = () => {
    const targets = new Set<number>();
    skus.forEach((sku, index) => {
      if (sku.removed || (selected.length && !selected.includes(index))) return;
      targets.add(index);
    });
    onChange(groups, applyGeneratedSkuCodes(skus, skuAxesFromGroups(groups), prefix.trim(), targets));
    touchMany([...targets].map((index) => `${skus[index].clientKey}.skuCode`));
  };

  const selectMatching = () => {
    const [name, label] = pick.split("::");
    if (!name) return;
    setSelected(skus.flatMap((sku, index) => !sku.removed && sku.optionValues[name] === label ? [index] : []));
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

  const activeIndexes = skus.flatMap((sku, index) => sku.removed ? [] : [index]);
  const allSelected = activeIndexes.length > 0 && activeIndexes.every((index) => selected.includes(index));
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
    const server = serverErrors[`skus.${index}.${field}`];
    return server && submitAttempted ? server : undefined;
  };
  const message = (code?: string) => (code ? t(`catalog.errors.${code}`, { defaultValue: t(`catalog.variants.${code}`, { defaultValue: code }) }) : "");

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
                {t("catalog.variants.groupName")}
                <input
                  value={draftNames[group.key] ?? group.name}
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
              </label>
              <label>
                {t("catalog.variants.valuePh")}
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
                  <button type="button" onClick={() => updateGroups(groups.map((item, index) => index === groupIndex ? { ...item, values: item.values.filter((entry) => entry.key !== value.key) } : item))}>×</button>
                </span>
              ))}
            </div>
            <button type="button" className="variant-action" onClick={() => updateGroups(groups.filter((_, index) => index !== groupIndex))}>{t("catalog.variants.removeGroup")}</button>
          </article>
        ))}
        <button type="button" className="variant-action" disabled={groups.length >= 3} onClick={() => updateGroups([...groups, { key: optionKey(), name: "", values: [] }])}>{t("catalog.variants.addGroup")}</button>
      </div>
      {matrixError ? <p className="variant-error">{t("catalog.variants.tooMany")}</p> : null}
      {submitAttempted && visibleIssues.length > 0 ? (
        <div className="variant-problems" id="variant-problems">
          <strong>{t("catalog.variants.problems", { count: visibleIssues.length })}</strong>
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
          {t("catalog.variants.price")}
          <input value={bulk.price} placeholder={t("catalog.variants.price")} onChange={(event) => setBulk({ ...bulk, price: event.target.value })} />
        </label>
        <label>
          {t("catalog.variants.compare")}
          <input value={bulk.compareAtPrice} placeholder={t("catalog.variants.compare")} onChange={(event) => setBulk({ ...bulk, compareAtPrice: event.target.value })} />
        </label>
        <label>
          {t("catalog.variants.cost")}
          <input value={bulk.costPrice} placeholder={t("catalog.variants.cost")} onChange={(event) => setBulk({ ...bulk, costPrice: event.target.value })} />
        </label>
        <label>
          {t("catalog.variants.stock")}
          <input value={bulk.initialStock} placeholder={t("catalog.variants.stock")} onChange={(event) => setBulk({ ...bulk, initialStock: event.target.value })} />
        </label>
        <label>
          {t("catalog.variants.weight")}
          <input value={bulk.weightGrams} placeholder={t("catalog.variants.weight")} onChange={(event) => setBulk({ ...bulk, weightGrams: event.target.value })} />
        </label>
        <SearchableSelect
          label={t("catalog.variants.image")}
          placeholder={t("catalog.variants.image")}
          options={imageChoices.map((choice) => choice.label)}
          value={imageChoices.find((choice) => choice.value === bulk.image)?.label ?? ""}
          onChange={(label) => setBulk({ ...bulk, image: imageChoices.find((choice) => choice.label === label)?.value ?? "" })}
        />
        <label>
          {t("catalog.variants.prefix")}
          <input value={prefix} placeholder={t("catalog.variants.prefix")} onChange={(event) => setPrefix(event.target.value)} />
        </label>
        <div className="variant-bulk-actions">
          <button type="button" className="variant-action" onClick={selectMatching}>{t("catalog.variants.selectMatching")}</button>
          <button type="button" className="variant-action primary" onClick={applyBulk}>{t("catalog.variants.apply")}</button>
          <button type="button" className="variant-action primary" onClick={generateCodes}>{t("catalog.variants.generate")}</button>
        </div>
      </div>
      <div className="variant-table-wrap">
        <table className="variant-table">
          <thead>
            <tr>
              <th><input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : activeIndexes)} aria-label={t("catalog.variants.selectAll")} /></th>
              <th>{t("catalog.variants.image")}</th>
              <th>{t("catalog.variants.combination")}</th>
              <th>{t("catalog.variants.sku")}</th>
              <th>{t("catalog.variants.price")}</th>
              <th>{t("catalog.variants.compare")}</th>
              <th>{t("catalog.variants.cost")}</th>
              <th>{t("catalog.variants.barcode")}</th>
              <th>{t("catalog.variants.weight")}</th>
              <th>{t("catalog.variants.stock")}</th>
              <th>{t("catalog.form.safetyStock")}</th>
              <th>{t("catalog.variants.active")}</th>
            </tr>
          </thead>
          <tbody>
            {skus.map((sku, index) => {
              const skuError = issueAt(index, "skuCode");
              const priceError = issueAt(index, "price");
              const comboError = issueAt(index, "optionValues");
              const stockError = issueAt(index, "initialStock");
              const safetyError = issueAt(index, "safetyStock");
              const imageValue = sku.imageLocalKey ? `local:${sku.imageLocalKey}` : sku.imageId || "";
              return (
                <tr id={`variant-row-${index}`} key={sku.clientKey} className={sku.removed ? "variant-removed" : undefined}>
                  <td><input type="checkbox" disabled={sku.removed} checked={!sku.removed && selected.includes(index)} onChange={() => setSelected((current) => current.includes(index) ? current.filter((item) => item !== index) : [...current, index])} /></td>
                  <td>
                    <select
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
                    {comboError ? <span className="variant-error">{message(comboError)}</span> : null}
                    {sku.removed ? <button type="button" className="variant-action" onClick={() => restore(index)}>{t("catalog.variants.restore")}</button> : null}
                  </td>
                  <td>
                    <input aria-invalid={Boolean(skuError)} value={sku.skuCode} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "skuCode")} onChange={(event) => { touch(sku.clientKey, "skuCode"); patchSku(index, { skuCode: event.target.value }); }} />
                    {skuError ? <span className="variant-error">{message(skuError)}</span> : null}
                  </td>
                  <td>
                    <input aria-invalid={Boolean(priceError)} value={sku.price} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "price")} onChange={(event) => { touch(sku.clientKey, "price"); patchSku(index, { price: event.target.value }); }} />
                    {priceError ? <span className="variant-error">{message(priceError)}</span> : null}
                  </td>
                  <td><input value={sku.compareAtPrice} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "compareAtPrice")} onChange={(event) => { touch(sku.clientKey, "compareAtPrice"); patchSku(index, { compareAtPrice: event.target.value }); }} /></td>
                  <td><input value={sku.costPrice} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "costPrice")} onChange={(event) => { touch(sku.clientKey, "costPrice"); patchSku(index, { costPrice: event.target.value }); }} /></td>
                  <td><input value={sku.barcode} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "barcode")} onChange={(event) => { touch(sku.clientKey, "barcode"); patchSku(index, { barcode: event.target.value }); }} /></td>
                  <td><input value={sku.weightGrams} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "weightGrams")} onChange={(event) => { touch(sku.clientKey, "weightGrams"); patchSku(index, { weightGrams: event.target.value }); }} /></td>
                  <td>
                    <input aria-invalid={Boolean(stockError)} value={sku.initialStock} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "initialStock")} onChange={(event) => { touch(sku.clientKey, "initialStock"); patchSku(index, { initialStock: event.target.value }); }} />
                    {stockError ? <span className="variant-error">{message(stockError)}</span> : null}
                  </td>
                  <td>
                    <input inputMode="numeric" aria-invalid={Boolean(safetyError)} value={sku.safetyStock} disabled={sku.removed} onBlur={() => touch(sku.clientKey, "safetyStock")} onChange={(event) => { touch(sku.clientKey, "safetyStock"); patchSku(index, { safetyStock: event.target.value }); }} />
                    {safetyError ? <span className="variant-error">{message(safetyError)}</span> : null}
                  </td>
                  <td>
                    <button type="button" className={sku.active ? "variant-status ready" : "variant-status low"} disabled={sku.removed} onClick={() => patchSku(index, { active: !sku.active })}>
                      {sku.active ? t("catalog.variants.active") : t("catalog.variants.inactive")}
                    </button>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
