// CUSTOM theme CSS scope check (AG1 tokens + AG3 layout blocks): every selector that uses a CUSTOM-only class/variable
// must be scoped to [data-template="CUSTOM"], so GENERAL / FASHION / FOOD_BEVERAGE storefronts never match a new rule.
// Usage: node scripts/check-ag1-css-scope.mjs   (exit 1 on violation)
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..", "src");
const SCOPE = '[data-template="CUSTOM"]';
// AG1: sectionSettings / productCard / gradient tokens.  AG3: layout blocks (shop-block-*, shop-layout-*, --shop-block-*).  AG4: content sections (shop-steps-/feature-/gallery-/social-/richtext-*).  AG5: chrome (shop-hero-media*, shop-chrome-*, shop-custom-header/footer, shop-sticker*).
const CUSTOM_CLASS = /\.(shop-sec-(bg|align|pad|cols)-|shop-products-(edge|hide-price|no-cta|title-center)|shop-hero-gradient|shop-block-|shop-layout-|shop-steps-|shop-feature-|shop-gallery-|shop-social-|shop-richtext-|shop-ag4-|shop-hero-media|shop-chrome-|shop-custom-header|shop-custom-footer|shop-sticker|shop-collection-|shop-configurator-)|\.shop-block\b|--shop-(product-image-ratio|product-cta-radius|product-title-align|gradient-|heading-shadow|elevation|cta-bg|cta-text|block-)/;
// Everything after a marker comment (to the end of the file) must be CUSTOM-scoped, even if it uses no known class.
const MARKERS = ["/* AG1 Phase A", "/* AG3 blocks", "/* AG4 sections", "/* AG5 chrome"];

function cssFiles(dir) {
  return readdirSync(dir).flatMap((name) => {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) return cssFiles(full);
    return full.endsWith(".css") ? [full] : [];
  });
}

/** Split a stylesheet into [selector, body] rules (flattening @media blocks). */
function rules(css) {
  const out = [];
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, "");
  const re = /([^{}]+)\{([^{}]*)\}/g;
  let m;
  while ((m = re.exec(clean))) {
    const selector = m[1].replace(/@media[^{]*$/, "").trim().replace(/^@media[^{]*\{/, "").trim();
    out.push([selector, m[2]]);
  }
  return out;
}

const errors = [];
for (const file of cssFiles(root)) {
  const css = readFileSync(file, "utf8").replace(/\r\n/g, "\n");
  for (const [selector, body] of rules(css)) {
    if (!selector || selector.startsWith("@")) continue;
    const parts = selector.split(",").map((s) => s.trim()).filter(Boolean);
    const isCustomOnly = CUSTOM_CLASS.test(selector) || CUSTOM_CLASS.test(body);
    if (!isCustomOnly) continue;
    for (const part of parts) {
      if (!part.includes(SCOPE)) errors.push(`${file}: "${part}" uses a CUSTOM-only class/variable without ${SCOPE}`);
    }
  }
  for (const marker of MARKERS) {
    const markerAt = css.indexOf(marker);
    if (markerAt < 0) continue;
    for (const [selector] of rules(css.slice(markerAt))) {
      if (!selector || selector.startsWith("@")) continue;
      for (const part of selector.split(",").map((s) => s.trim()).filter(Boolean)) {
        if (!part.includes(SCOPE)) errors.push(`${file}: block after "${marker}" has selector "${part}" lacking ${SCOPE}`);
      }
    }
  }
}

if (errors.length) {
  console.error(`CUSTOM theme CSS scope check FAILED (${errors.length}):\n` + [...new Set(errors)].join("\n"));
  process.exit(1);
}
console.log("CUSTOM theme CSS scope check OK: all CUSTOM-only selectors are scoped to " + SCOPE);
