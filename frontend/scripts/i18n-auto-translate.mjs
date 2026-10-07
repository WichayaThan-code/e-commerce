/**
 * Fill empty en/zh strings from Thai using Google Translate or DeepL.
 * Usage: node scripts/i18n-auto-translate.mjs [--dry-run] [--force]
 */
import { readFileSync, writeFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import {
  LANGS,
  LOCALES,
  NAMESPACES,
  ROOT,
  applyGlossary,
  flatten,
  getNested,
  isEmptyTranslation,
  loadEnvLocal,
  loadNamespace,
  looksLikeKeyPlaceholder,
  restoreGlossary,
  saveNamespace,
  setNested,
  splitProtected,
} from "./i18n-lib.mjs";

loadEnvLocal();

const args = new Set(process.argv.slice(2));
const dryRun = args.has("--dry-run");
const force = args.has("--force");
const glossary = JSON.parse(readFileSync(join(ROOT, "scripts", "i18n-glossary.json"), "utf8"));
const machinePath = join(ROOT, "scripts", "i18n-machine-translated.json");
const machineLog = existsSync(machinePath) ? JSON.parse(readFileSync(machinePath, "utf8")) : {};

const TARGETS = [
  { lang: "en", code: "en" },
  { lang: "zh", code: "zh-CN" },
];

function needsTranslation(thValue, targetValue) {
  if (typeof thValue !== "string" || !thValue.trim()) return false;
  if (force) return true;
  if (isEmptyTranslation(targetValue)) return true;
  if (typeof targetValue === "string" && targetValue.trim() === thValue.trim()) return true;
  if (looksLikeKeyPlaceholder(targetValue)) return true;
  return false;
}

async function translateBatchGoogle(texts, target) {
  const key = process.env.GOOGLE_TRANSLATE_API_KEY;
  if (!key) throw new Error("GOOGLE_TRANSLATE_API_KEY is not set");
  const url = `https://translation.googleapis.com/language/translate/v2?key=${encodeURIComponent(key)}`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ q: texts, source: "th", target, format: "text" }),
  });
  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Google Translate failed (${response.status}): ${body.slice(0, 200)}`);
  }
  const json = await response.json();
  return json.data.translations.map((row) => row.translatedText);
}

async function translateBatchDeepL(texts, targetLang) {
  const key = process.env.DEEPL_API_KEY;
  if (!key) throw new Error("DEEPL_API_KEY is not set");
  const endpoint = key.endsWith(":fx") ? "https://api-free.deepl.com/v2/translate" : "https://api.deepl.com/v2/translate";
  const body = new URLSearchParams();
  body.set("auth_key", key);
  body.set("source_lang", "TH");
  body.set("target_lang", targetLang === "zh-CN" ? "ZH" : "EN");
  for (const text of texts) body.append("text", text);
  const response = await fetch(endpoint, { method: "POST", body });
  if (!response.ok) {
    const raw = await response.text();
    throw new Error(`DeepL failed (${response.status}): ${raw.slice(0, 200)}`);
  }
  const json = await response.json();
  return json.translations.map((row) => row.text);
}

async function translateText(text, targetCode, provider) {
  const parts = splitProtected(text);
  const out = [];
  for (const part of parts) {
    if (part.protected) {
      out.push(part.text);
      continue;
    }
    const { text: glossed, tokens } = applyGlossary(part.text, glossary);
    if (!glossed.trim()) {
      out.push(part.text);
      continue;
    }
    const [translated] = provider === "deepl"
      ? await translateBatchDeepL([glossed], targetCode)
      : await translateBatchGoogle([glossed], targetCode === "zh-CN" ? "zh-CN" : "en");
    out.push(restoreGlossary(translated, tokens));
  }
  return out.join("");
}

function collectJobs() {
  /** @type {{ ns: string, key: string, th: string, lang: string, code: string }[]} */
  const jobs = [];
  for (const ns of NAMESPACES) {
    const thFlat = flatten(loadNamespace("th", ns));
    for (const target of TARGETS) {
      const targetFlat = flatten(loadNamespace(target.lang, ns));
      for (const [key, thValue] of thFlat) {
        if (typeof thValue !== "string") continue;
        const current = targetFlat.get(key) ?? getNested(loadNamespace(target.lang, ns), key);
        if (!needsTranslation(thValue, current)) continue;
        jobs.push({ ns, key, th: thValue, lang: target.lang, code: target.code });
      }
    }
  }
  return jobs;
}

async function main() {
  const provider = (process.env.TRANSLATE_PROVIDER || "google").toLowerCase();
  if (!["google", "deepl"].includes(provider)) {
    throw new Error(`TRANSLATE_PROVIDER must be google or deepl (got ${provider})`);
  }
  const jobs = collectJobs();
  if (!jobs.length) {
    console.log("i18n:translate — nothing to translate.");
    return;
  }
  console.log(`i18n:translate — ${jobs.length} string(s)${dryRun ? " (dry-run)" : ""} via ${provider}.`);
  if (dryRun) {
    for (const job of jobs.slice(0, 40)) console.log(`  [${job.lang}] ${job.ns}:${job.key}`);
    if (jobs.length > 40) console.log(`  ... and ${jobs.length - 40} more`);
    return;
  }
  if (provider === "google" && !process.env.GOOGLE_TRANSLATE_API_KEY) {
    throw new Error("Set GOOGLE_TRANSLATE_API_KEY in frontend/.env.local");
  }
  if (provider === "deepl" && !process.env.DEEPL_API_KEY) {
    throw new Error("Set DEEPL_API_KEY in frontend/.env.local");
  }

  const cache = new Map();
  let updated = 0;
  for (const target of TARGETS) {
    const byNs = new Map();
    for (const job of jobs.filter((row) => row.lang === target.lang)) {
      if (!byNs.has(job.ns)) byNs.set(job.ns, []);
      byNs.get(job.ns).push(job);
    }
    for (const [ns, rows] of byNs) {
      const data = loadNamespace(target.lang, ns);
      for (let index = 0; index < rows.length; index += 20) {
        const chunk = rows.slice(index, index + 20);
        for (const job of chunk) {
          const cacheKey = `${target.lang}::${job.th}`;
          let translated = cache.get(cacheKey);
          if (!translated) {
            translated = await translateText(job.th, job.code, provider);
            cache.set(cacheKey, translated);
          }
          setNested(data, job.key, translated);
          machineLog[`${target.lang}/${ns}/${job.key}`] = new Date().toISOString();
          updated += 1;
        }
      }
      saveNamespace(target.lang, ns, data);
    }
  }
  writeFileSync(machinePath, `${JSON.stringify(machineLog, null, 2)}\n`, "utf8");
  console.log(`i18n:translate — wrote ${updated} translation(s).`);
}

main().catch((error) => {
  console.error(error.message || error);
  process.exit(1);
});
