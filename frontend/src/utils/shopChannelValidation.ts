import type { MerchantChannelsPayload } from "../services/api";

export type ChannelField = keyof MerchantChannelsPayload;
export type ChannelErrors = Partial<Record<ChannelField, string>>;

const CONTROL = /[\u0000-\u001F\u007F]/;
const FACEBOOK = /^([\w-]+\.)*(facebook|fb)\.com$/i;
const INSTAGRAM = /^([\w-]+\.)*instagram\.com$/i;
const TIKTOK = /^([\w-]+\.)*tiktok\.com$/i;
const LINE_HOST = /^(line\.me|lin\.ee)$/i;
const LINE_ID = /^@?[A-Za-z0-9._-]{2,50}$/;
const HOST_LIKE = /^[\w-]+(\.[\w-]+)+(:\d+)?(\/.*)?$/i;
const HAS_SCHEME = /^[a-z][a-z0-9+.-]*:/i;

function blank(value: string | null | undefined) {
  const trimmed = value?.trim() ?? "";
  return trimmed ? trimmed : "";
}

function rejectDangerous(value: string, field: ChannelField, errors: ChannelErrors, message: string) {
  if (CONTROL.test(value) || /^javascript:/i.test(value) || /^data:/i.test(value)) {
    errors[field] = message;
    return true;
  }
  return false;
}

function normalizeHttpUrl(raw: string, field: ChannelField, hostOk: (host: string) => boolean, errors: ChannelErrors, messages: { invalid: string; host: string; creds: string; protocol: string }) {
  if (rejectDangerous(raw, field, errors, messages.protocol)) return "";
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
    errors[field] = messages.invalid;
    return "";
  }
}

export type ChannelMessages = {
  max: string;
  url: string;
  host: string;
  protocol: string;
  creds: string;
  line: string;
  lineId: string;
};

/** Validate + normalize contact channels (idempotent). Empty string clears the field. */
export function validateAndNormalizeChannels(
  input: MerchantChannelsPayload,
  messages: ChannelMessages,
): { values: MerchantChannelsPayload; errors: ChannelErrors } {
  const errors: ChannelErrors = {};
  const values: MerchantChannelsPayload = {
    website: "",
    lineId: "",
    facebookUrl: "",
    instagramUrl: "",
    tiktokUrl: "",
  };

  const website = blank(input.website);
  if (website) {
    if (website.length > 500) errors.website = messages.max;
    else values.website = normalizeHttpUrl(website, "website", () => true, errors, {
      invalid: messages.url,
      host: messages.url,
      creds: messages.creds,
      protocol: messages.protocol,
    });
  }

  const lineId = blank(input.lineId).replace(/\s+/g, "");
  if (lineId) {
    if (lineId.length > 500) errors.lineId = messages.max;
    else if (/^https:\/\//i.test(lineId)) {
      values.lineId = normalizeHttpUrl(lineId, "lineId", (host) => LINE_HOST.test(host), errors, {
        invalid: messages.line,
        host: messages.line,
        creds: messages.creds,
        protocol: messages.protocol,
      });
    } else if (HAS_SCHEME.test(lineId) || lineId.includes("/")) {
      errors.lineId = messages.line;
    } else if (!LINE_ID.test(lineId)) {
      errors.lineId = messages.lineId;
    } else {
      values.lineId = lineId;
    }
  }

  const network = (
    field: "facebookUrl" | "instagramUrl" | "tiktokUrl",
    hostOk: RegExp,
    base: string,
  ) => {
    const raw = blank(input[field]);
    if (!raw) return;
    if (raw.length > 500) {
      errors[field] = messages.max;
      return;
    }
    if (HAS_SCHEME.test(raw) || HOST_LIKE.test(raw)) {
      values[field] = normalizeHttpUrl(raw, field, (host) => hostOk.test(host), errors, {
        invalid: messages.url,
        host: messages.host,
        creds: messages.creds,
        protocol: messages.protocol,
      });
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

export function channelsEqual(a: MerchantChannelsPayload, b: MerchantChannelsPayload) {
  return (["website", "lineId", "facebookUrl", "instagramUrl", "tiktokUrl"] as const).every(
    (key) => (a[key] || "").trim() === (b[key] || "").trim(),
  );
}
