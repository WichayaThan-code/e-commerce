/**
 * Scan src for i18n keys and merge missing entries into locale JSON (never overwrite existing translations).
 * Primary language: Thai — uses t("key", "default") / defaultValue from source when adding th keys.
 */
import { existsSync, readFileSync } from "node:fs";
import { join } from "node:path";
import {
  LANGS,
  LOCALES,
  NAMESPACES,
  ROOT,
  flatten,
  getNested,
  isEmptyTranslation,
  loadNamespace,
  looksLikeKeyPlaceholder,
  rel,
  saveNamespace,
  setNested,
  walkFiles,
} from "./i18n-lib.mjs";

const SRC = join(ROOT, "src");
const SEED = join(ROOT, "scripts", "seeds", "merchant-catalog.json");
const PUBLIC_SHOP_SEED = join(ROOT, "scripts", "seeds", "public-shop.json");

const RE_USE = /useTranslation\s*\(\s*(?:\[([^\]]+)\]|['"]([^'"]+)['"])/g;
const RE_T = /\b(t|tp|tc)\s*\(\s*(['"])([^'"]+)\2/g;

function parseDefaultAfter(content, startIndex) {
  const tail = content.slice(startIndex, startIndex + 240);
  const stringDefault = tail.match(/^\s*,\s*(['"])([\s\S]*?)\1/);
  if (stringDefault) return stringDefault[2];
  const objectDefault = tail.match(/^\s*,\s*\{[^}]*defaultValue\s*:\s*(['"])([\s\S]*?)\1/);
  if (objectDefault) return objectDefault[2];
  return undefined;
}

function fileNamespaces(content) {
  const set = new Set(["common"]);
  for (const match of content.matchAll(RE_USE)) {
    const list = match[1];
    if (list) {
      for (const part of list.split(",")) {
        const ns = part.trim().replace(/^['"]|['"]$/g, "");
        if (ns) set.add(ns);
      }
    } else if (match[2]) set.add(match[2]);
  }
  if (content.includes("usePublicTranslation")) {
    set.add("public");
    set.add("common");
  }
  return set;
}

function resolveNamespace(fn, key, fileNs, explicit) {
  if (explicit) return explicit;
  if (fn === "tp") return "public";
  if (fn === "tc") return "common";
  const split = key.indexOf(":");
  if (split > 0) return key.slice(0, split);
  if (fileNs.has("merchant") && key.startsWith("catalog.")) return "merchant";
  if (fileNs.has("admin") && key.startsWith("admin.")) return "admin";
  if (fileNs.has("auth") && key.startsWith("auth.")) return "auth";
  if (fileNs.has("public") && !fileNs.has("merchant")) return "public";
  return [...fileNs][0] || "common";
}

function resolveKey(key) {
  const split = key.indexOf(":");
  return split > 0 ? key.slice(split + 1) : key;
}

function scanSources() {
  /** @type {Map<string, { ns: string, key: string, defaultValue?: string, file: string }>} */
  const found = new Map();
  for (const file of walkFiles(SRC)) {
    const content = readFileSync(file, "utf8");
    const fileNs = fileNamespaces(content);
    for (const match of content.matchAll(RE_T)) {
      const fn = match[1];
      const rawKey = match[3];
      if (rawKey.includes("${")) continue;
      const end = (match.index ?? 0) + match[0].length;
      const defaultValue = parseDefaultAfter(content, end);
      const ns = resolveNamespace(fn, rawKey, fileNs);
      const key = resolveKey(rawKey);
      const id = `${ns}::${key}`;
      found.set(id, { ns, key, defaultValue, file: rel(file) });
    }
  }
  return found;
}

function mergeSeed(data, lang, ns) {
  const seedPath = join(LOCALES, "_seeds", lang, `${ns}.json`);
  if (!existsSync(seedPath)) return 0;
  const seed = JSON.parse(readFileSync(seedPath, "utf8"));
  let added = 0;
  for (const [path, value] of flatten(seed)) {
    const current = getNested(data, path);
    if (isEmptyTranslation(current) || looksLikeKeyPlaceholder(current)) {
      setNested(data, path, value);
      added += 1;
    }
  }
  return added;
}

function mergeSeedRoot(lang, ns, root) {
  const data = loadNamespace(lang, ns);
  let fixed = 0;
  for (const [path, value] of flatten(root)) {
    const current = getNested(data, path);
    if (isEmptyTranslation(current) || looksLikeKeyPlaceholder(current)) {
      setNested(data, path, value);
      fixed += 1;
    }
  }
  if (fixed) saveNamespace(lang, ns, data);
}

function applyMerchantCatalogSeed() {
  if (!existsSync(SEED)) return;
  const seed = JSON.parse(readFileSync(SEED, "utf8"));
  for (const lang of LANGS) {
    if (seed[lang]?.catalog) mergeSeedRoot(lang, "merchant", { catalog: seed[lang].catalog });
  }
}

function applyPublicShopSeed() {
  if (!existsSync(PUBLIC_SHOP_SEED)) return;
  const seed = JSON.parse(readFileSync(PUBLIC_SHOP_SEED, "utf8"));
  for (const lang of LANGS) {
    if (seed[lang]?.shop) mergeSeedRoot(lang, "public", { shop: seed[lang].shop });
  }
}

function mergeFound(found) {
  let added = 0;
  for (const lang of LANGS) {
    for (const ns of NAMESPACES) {
      const data = loadNamespace(lang, ns);
      let changed = 0;
      for (const item of found.values()) {
        if (item.ns !== ns) continue;
        const current = getNested(data, item.key);
        const shouldFill =
          isEmptyTranslation(current) ||
          looksLikeKeyPlaceholder(current);
        if (!shouldFill) continue;
        const value = lang === "th" ? item.defaultValue?.trim() || "" : "";
        setNested(data, item.key, value);
        added += 1;
        changed += 1;
      }
      if (changed) mergeSeed(data, lang, ns);
      if (changed) saveNamespace(lang, ns, data);
    }
  }
  return added;
}

const found = scanSources();
applyMerchantCatalogSeed();
applyPublicShopSeed();
const added = mergeFound(found);
console.log(`i18n:extract — scanned ${found.size} key(s), added/updated ${added} empty slot(s).`);
console.log("Tip: use t('merchant:catalog.newKey', 'ข้อความไทย') so Thai defaults flow into th/*.json.");
