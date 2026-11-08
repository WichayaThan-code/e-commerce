/**
 * Lightweight validation checks (no vitest in package.json).
 * Run: node scripts/check-shop-channel-validation.mjs
 */
import assert from "assert";

const CONTROL = /[\u0000-\u001F\u007F]/;
const FACEBOOK = /^([\w-]+\.)*(facebook|fb)\.com$/i;
const INSTAGRAM = /^([\w-]+\.)*instagram\.com$/i;
const TIKTOK = /^([\w-]+\.)*tiktok\.com$/i;
const LINE_HOST = /^(line\.me|lin\.ee)$/i;
const LINE_ID = /^@?[A-Za-z0-9._-]{2,50}$/;
const HOST_LIKE = /^[\w-]+(\.[\w-]+)+(:\d+)?(\/.*)?$/i;
const HAS_SCHEME = /^[a-z][a-z0-9+.-]*:/i;

function blank(value) {
  const trimmed = value?.trim() ?? "";
  return trimmed ? trimmed : "";
}

function validateAndNormalizeChannels(input) {
  const errors = {};
  const values = { website: "", lineId: "", facebookUrl: "", instagramUrl: "", tiktokUrl: "" };
  const messages = {
    max: "max", url: "url", host: "host", protocol: "protocol", creds: "creds", line: "line", lineId: "lineId",
  };

  const rejectDangerous = (value, field) => {
    if (CONTROL.test(value) || /^javascript:/i.test(value) || /^data:/i.test(value)) {
      errors[field] = messages.protocol;
      return true;
    }
    return false;
  };

  const normalizeHttpUrl = (raw, field, hostOk) => {
    if (rejectDangerous(raw, field)) return "";
    try {
      const url = new URL(HAS_SCHEME.test(raw) ? raw : `https://${raw}`);
      if (url.protocol !== "http:" && url.protocol !== "https:") {
        errors[field] = messages.protocol;
        return "";
      }
      if (url.username || url.password) {
        errors[field] = messages.creds;
        return "";
      }
      if (!url.hostname || !hostOk(url.hostname)) {
        errors[field] = messages.host;
        return "";
      }
      return `https://${url.hostname}${url.pathname}${url.search}`;
    } catch {
      errors[field] = messages.url;
      return "";
    }
  };

  const website = blank(input.website);
  if (website) values.website = normalizeHttpUrl(website, "website", () => true);

  const lineId = blank(input.lineId).replace(/\s+/g, "");
  if (lineId) {
    if (/^https:\/\//i.test(lineId)) {
      values.lineId = normalizeHttpUrl(lineId, "lineId", (host) => LINE_HOST.test(host));
    } else if (HAS_SCHEME.test(lineId) || lineId.includes("/")) {
      errors.lineId = messages.line;
    } else if (!LINE_ID.test(lineId)) {
      errors.lineId = messages.lineId;
    } else values.lineId = lineId;
  }

  const network = (field, hostOk, base) => {
    const raw = blank(input[field]);
    if (!raw) return;
    if (HAS_SCHEME.test(raw) || HOST_LIKE.test(raw)) {
      values[field] = normalizeHttpUrl(raw, field, (host) => hostOk.test(host));
      return;
    }
    const handle = raw.replace(/^@/, "");
    if (!handle || HAS_SCHEME.test(handle) || /[\s/\\]/.test(handle)) {
      errors[field] = messages.url;
      return;
    }
    values[field] = `${base}${handle}`;
  };

  network("facebookUrl", FACEBOOK, "https://www.facebook.com/");
  network("instagramUrl", INSTAGRAM, "https://www.instagram.com/");
  network("tiktokUrl", TIKTOK, "https://www.tiktok.com/@");
  return { values, errors };
}

const ok = validateAndNormalizeChannels({
  website: "example.com",
  lineId: "@shop",
  facebookUrl: "facebook.com/x",
  instagramUrl: "@x",
  tiktokUrl: "https://www.tiktok.com/@x",
});
assert.equal(ok.values.website, "https://example.com/");
assert.equal(ok.values.lineId, "@shop");
assert.equal(ok.values.facebookUrl, "https://facebook.com/x");
assert.equal(Object.keys(ok.errors).length, 0);

const fake = validateAndNormalizeChannels({
  website: "",
  lineId: "",
  facebookUrl: "https://facebook.evil.com/x",
  instagramUrl: "javascript:alert(1)",
  tiktokUrl: "https://user:pass@tiktok.com/@x",
});
assert.ok(fake.errors.facebookUrl);
assert.ok(fake.errors.instagramUrl);
assert.ok(fake.errors.tiktokUrl);

console.log("shop-channel-validation: ok");
