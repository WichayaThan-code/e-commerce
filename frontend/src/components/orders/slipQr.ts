import jsQR from "jsqr";

/** Matches backend OrderPaymentService REFERENCE pattern. */
const REFERENCE = /^[A-Za-z0-9][A-Za-z0-9 ._/#-]{3,119}$/;

/** Common BoT bank codes → display name (Thai). */
const BANK_NAMES: Record<string, string> = {
  "002": "ธนาคารกรุงเทพ",
  "004": "ธนาคารกสิกรไทย",
  "006": "ธนาคารกรุงไทย",
  "011": "ธนาคารทหารไทยธนชาต",
  "014": "ธนาคารไทยพาณิชย์",
  "017": "ธนาคารซิตี้แบงก์",
  "022": "ธนาคารซีไอเอ็มบีไทย",
  "024": "ธนาคารยูโอบี",
  "025": "ธนาคารกรุงศรีอยุธยา",
  "030": "ธนาคารออมสิน",
  "033": "ธนาคารอาคารสงเคราะห์",
  "034": "ธนาคารเพื่อการเกษตรและสหกรณ์การเกษตร",
  "067": "ธนาคารทิสโก้",
  "069": "ธนาคารเกียรตินาคินภัทร",
  "070": "ธนาคารไอซีบีซี (ไทย)",
  "071": "ธนาคารไทยเครดิต",
  "073": "ธนาคารแลนด์ แอนด์ เฮ้าส์",
};

export type SlipQrFields = {
  reference: string;
  bankCode?: string;
  bankName?: string;
};

function parseTlv(payload: string): Map<string, string> {
  const out = new Map<string, string>();
  let i = 0;
  while (i + 4 <= payload.length) {
    const tag = payload.slice(i, i + 2);
    const len = Number.parseInt(payload.slice(i + 2, i + 4), 10);
    if (!Number.isFinite(len) || len < 0 || i + 4 + len > payload.length) break;
    out.set(tag, payload.slice(i + 4, i + 4 + len));
    i += 4 + len;
  }
  return out;
}

function cleanReference(value: string | null | undefined): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  if (!REFERENCE.test(trimmed)) return null;
  return trimmed;
}

/** Thai Slip-Verify Mini-QR (BoT): tag 00 → nested 01=bank, 02=transRef. */
function parseSlipVerify(payload: string): SlipQrFields | null {
  const root = parseTlv(payload.trim());
  const nested = parseTlv(root.get("00") ?? "");
  const api = nested.get("00");
  const bankCode = nested.get("01")?.trim();
  const transRef = cleanReference(nested.get("02"));
  if (api !== "000001" || !transRef) return null;
  return {
    reference: transRef,
    bankCode,
    bankName: bankCode ? BANK_NAMES[bankCode] : undefined,
  };
}

/** TrueMoney slip QR: nested 03 = transactionId. */
function parseTrueMoneySlip(payload: string): SlipQrFields | null {
  const root = parseTlv(payload.trim());
  const nested = parseTlv(root.get("00") ?? "");
  if (nested.get("00") !== "01" || nested.get("01") !== "01") return null;
  const reference = cleanReference(nested.get("03"));
  if (!reference) return null;
  return { reference };
}

function parseJsonPayload(raw: string): SlipQrFields | null {
  try {
    const data = JSON.parse(raw) as Record<string, unknown>;
    const candidates = [
      data.transRef, data.transactionId, data.transactionRef, data.reference,
      data.ref, data.refNo, data.referenceNo, data.RefNo,
    ];
    for (const value of candidates) {
      const reference = cleanReference(typeof value === "string" ? value : null);
      if (!reference) continue;
      const bankCode = typeof data.sendingBank === "string" ? data.sendingBank.trim()
        : typeof data.bankCode === "string" ? data.bankCode.trim()
        : undefined;
      return {
        reference,
        bankCode,
        bankName: bankCode ? BANK_NAMES[bankCode] : (typeof data.bankName === "string" ? data.bankName : undefined),
      };
    }
  } catch {
    /* not JSON */
  }
  return null;
}

function parseUrlPayload(raw: string): SlipQrFields | null {
  try {
    const url = new URL(raw);
    for (const key of ["transRef", "transactionId", "reference", "ref", "refNo"]) {
      const reference = cleanReference(url.searchParams.get(key));
      if (reference) return { reference };
    }
  } catch {
    /* not a URL */
  }
  return null;
}

/** Extract transfer reference (and optional bank) from a decoded QR payload. */
export function parseSlipQrPayload(raw: string): SlipQrFields | null {
  const text = raw.trim();
  if (!text) return null;
  return (
    parseSlipVerify(text)
    ?? parseTrueMoneySlip(text)
    ?? parseJsonPayload(text)
    ?? parseUrlPayload(text)
    ?? (cleanReference(text) ? { reference: cleanReference(text)! } : null)
  );
}

async function imageDataFromFile(file: File): Promise<ImageData | null> {
  if (!file.type.startsWith("image/")) return null;
  const bitmap = await createImageBitmap(file);
  try {
    const max = 1600;
    const scale = Math.min(1, max / Math.max(bitmap.width, bitmap.height));
    const width = Math.max(1, Math.round(bitmap.width * scale));
    const height = Math.max(1, Math.round(bitmap.height * scale));
    const canvas = document.createElement("canvas");
    canvas.width = width;
    canvas.height = height;
    const ctx = canvas.getContext("2d", { willReadFrequently: true });
    if (!ctx) return null;
    ctx.drawImage(bitmap, 0, 0, width, height);
    return ctx.getImageData(0, 0, width, height);
  } finally {
    bitmap.close();
  }
}

async function decodeWithBarcodeDetector(file: File): Promise<string | null> {
  const Detector = (window as Window & { BarcodeDetector?: new (opts?: { formats: string[] }) => {
    detect: (source: ImageBitmapSource) => Promise<Array<{ rawValue: string }>>;
  } }).BarcodeDetector;
  if (!Detector) return null;
  try {
    const detector = new Detector({ formats: ["qr_code"] });
    const bitmap = await createImageBitmap(file);
    try {
      const codes = await detector.detect(bitmap);
      return codes[0]?.rawValue?.trim() || null;
    } finally {
      bitmap.close();
    }
  } catch {
    return null;
  }
}

async function decodeWithJsQr(file: File): Promise<string | null> {
  const image = await imageDataFromFile(file);
  if (!image) return null;
  const result = jsQR(image.data, image.width, image.height, { inversionAttempts: "attemptBoth" });
  return result?.data?.trim() || null;
}

/**
 * Read QR from a slip image and map it to form fields.
 * Returns null when no usable QR/reference is found (caller keeps manual entry).
 */
export async function readSlipQrFromFile(file: File): Promise<SlipQrFields | null> {
  if (!file.type.startsWith("image/")) return null;
  const raw = (await decodeWithBarcodeDetector(file)) ?? (await decodeWithJsQr(file));
  if (!raw) return null;
  return parseSlipQrPayload(raw);
}
