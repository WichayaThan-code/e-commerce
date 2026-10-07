import { readFileSync, writeFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";

export const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
export const LOCALES = join(ROOT, "src", "locales");
export const LANGS = ["th", "en", "zh"];
export const NAMESPACES = ["common", "auth", "public", "merchant", "admin"];

export function loadEnvLocal() {
  for (const file of [join(ROOT, ".env.local"), join(ROOT, "..", ".env.local")]) {
    if (!existsSync(file)) continue;
    for (const line of readFileSync(file, "utf8").split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const eq = trimmed.indexOf("=");
      if (eq <= 0) continue;
      const key = trimmed.slice(0, eq).trim();
      let value = trimmed.slice(eq + 1).trim();
      if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
        value = value.slice(1, -1);
      }
      if (!(key in process.env)) process.env[key] = value;
    }
  }
}

export function walkFiles(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    const stat = statSync(path);
    if (stat.isDirectory()) walkFiles(path, out);
    else if (/\.(ts|tsx)$/.test(name)) out.push(path);
  }
  return out;
}

export function flatten(obj, prefix = "") {
  const keys = new Map();
  if (obj === null || typeof obj !== "object") return keys;
  if (Array.isArray(obj)) {
    if (prefix) keys.set(prefix, obj);
    return keys;
  }
  for (const [key, value] of Object.entries(obj)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (value !== null && typeof value === "object" && !Array.isArray(value)) {
      for (const [childPath, childValue] of flatten(value, path)) keys.set(childPath, childValue);
    } else {
      keys.set(path, value);
    }
  }
  return keys;
}

export function setNested(target, path, value) {
  const parts = path.split(".");
  let node = target;
  for (let index = 0; index < parts.length - 1; index += 1) {
    const part = parts[index];
    if (typeof node[part] !== "object" || node[part] === null || Array.isArray(node[part])) {
      node[part] = {};
    }
    node = node[part];
  }
  node[parts[parts.length - 1]] = value;
}

export function getNested(obj, path) {
  return path.split(".").reduce((node, part) => (node && typeof node === "object" ? node[part] : undefined), obj);
}

export function loadNamespace(lang, ns) {
  return JSON.parse(readFileSync(join(LOCALES, lang, `${ns}.json`), "utf8"));
}

export function saveNamespace(lang, ns, data) {
  writeFileSync(join(LOCALES, lang, `${ns}.json`), `${JSON.stringify(data, null, 2)}\n`, "utf8");
}

export function isEmptyTranslation(value) {
  return value === undefined || value === null || (typeof value === "string" && value.trim() === "");
}

export function looksLikeKeyPlaceholder(value) {
  return typeof value === "string" && /^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$/i.test(value);
}

const INTERPOLATION = /(\{\{[^}]+\}\}|<\/?\d+>)/g;

export function splitProtected(text) {
  if (typeof text !== "string" || !text) return [{ text: text ?? "", protected: false }];
  const parts = [];
  let last = 0;
  for (const match of text.matchAll(INTERPOLATION)) {
    const index = match.index ?? 0;
    if (index > last) parts.push({ text: text.slice(last, index), protected: false });
    parts.push({ text: match[0], protected: true });
    last = index + match[0].length;
  }
  if (last < text.length) parts.push({ text: text.slice(last), protected: false });
  return parts.length ? parts : [{ text, protected: false }];
}

export function applyGlossary(text, glossary) {
  let out = text;
  const tokens = [];
  glossary.forEach((term, index) => {
    if (!term || !out.includes(term)) return;
    const token = `__GLOSSARY_${index}__`;
    tokens.push({ token, term });
    out = out.split(term).join(token);
  });
  return { text: out, tokens };
}

export function restoreGlossary(text, tokens) {
  let out = text;
  for (const { token, term } of tokens) out = out.split(token).join(term);
  return out;
}

export function rel(path) {
  return relative(ROOT, path).replace(/\\/g, "/");
}
