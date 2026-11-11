export type ThaiBank = {
  code: string;
  nameTh: string;
  short: string;
  color: string;
  /** Keywords used to match free-text merchant bankName values. */
  aliases: string[];
};

/** Major Thai banks for transfer UI (logo badge + name match). */
export const THAI_BANKS: ThaiBank[] = [
  { code: "002", nameTh: "ธนาคารกรุงเทพ", short: "BBL", color: "#1e4598", aliases: ["กรุงเทพ", "bbl", "bangkok bank"] },
  { code: "004", nameTh: "ธนาคารกสิกรไทย", short: "KBANK", color: "#138f2d", aliases: ["กสิกร", "kbank", "kasikorn"] },
  { code: "006", nameTh: "ธนาคารกรุงไทย", short: "KTB", color: "#1ba5e1", aliases: ["กรุงไทย", "ktb", "krungthai"] },
  { code: "011", nameTh: "ธนาคารทหารไทยธนชาต", short: "TTB", color: "#fc4f1f", aliases: ["ทหารไทย", "ธนชาต", "ttb", "tmb"] },
  { code: "014", nameTh: "ธนาคารไทยพาณิชย์", short: "SCB", color: "#4e2e7f", aliases: ["ไทยพาณิชย์", "scb", "siam commercial"] },
  { code: "017", nameTh: "ธนาคารซิตี้แบงก์", short: "CITI", color: "#003b70", aliases: ["ซิตี้", "citi"] },
  { code: "022", nameTh: "ธนาคารซีไอเอ็มบีไทย", short: "CIMB", color: "#7e2f36", aliases: ["ซีไอเอ็มบี", "cimb"] },
  { code: "024", nameTh: "ธนาคารยูโอบี", short: "UOB", color: "#0b2485", aliases: ["ยูโอบี", "uob"] },
  { code: "025", nameTh: "ธนาคารกรุงศรีอยุธยา", short: "BAY", color: "#fec43b", aliases: ["กรุงศรี", "bay", "krungsri", "ayu"] },
  { code: "030", nameTh: "ธนาคารออมสิน", short: "GSB", color: "#ed1892", aliases: ["ออมสิน", "gsb", "government savings"] },
  { code: "033", nameTh: "ธนาคารอาคารสงเคราะห์", short: "GHB", color: "#ff6a13", aliases: ["อาคารสงเคราะห์", "ghb", "housing"] },
  { code: "034", nameTh: "ธ.ก.ส.", short: "BAAC", color: "#4b9b1d", aliases: ["ธ.ก.ส", "ธกส", "baac", "เพื่อการเกษตร"] },
  { code: "067", nameTh: "ธนาคารทิสโก้", short: "TISCO", color: "#12539e", aliases: ["ทิสโก้", "tisco"] },
  { code: "069", nameTh: "ธนาคารเกียรตินาคินภัทร", short: "KKP", color: "#199cc7", aliases: ["เกียรตินาคิน", "kkp", "kiatnakin"] },
  { code: "070", nameTh: "ธนาคารไอซีบีซี (ไทย)", short: "ICBC", color: "#c4161c", aliases: ["ไอซีบีซี", "icbc"] },
  { code: "071", nameTh: "ธนาคารไทยเครดิต", short: "TCRB", color: "#009688", aliases: ["ไทยเครดิต", "tcrb", "thai credit"] },
  { code: "073", nameTh: "ธนาคารแลนด์ แอนด์ เฮ้าส์", short: "LHB", color: "#6d6e71", aliases: ["แลนด์", "lh bank", "lhb", "land and houses"] },
];

const FALLBACK: ThaiBank = {
  code: "000",
  nameTh: "ธนาคาร",
  short: "BANK",
  color: "#1268d7",
  aliases: [],
};

function normalize(value: string) {
  return value.trim().toLowerCase().replace(/\s+/g, " ");
}

/** Resolve a free-text bank name (or BoT code) to a known Thai bank. */
export function resolveThaiBank(bankName: string | null | undefined): ThaiBank | null {
  if (!bankName?.trim()) return null;
  const raw = bankName.trim();
  if (/^\d{3}$/.test(raw)) {
    return THAI_BANKS.find((bank) => bank.code === raw) ?? null;
  }
  const needle = normalize(raw);
  for (const bank of THAI_BANKS) {
    if (normalize(bank.nameTh) === needle) return bank;
    if (bank.aliases.some((alias) => needle.includes(normalize(alias)) || normalize(alias).includes(needle))) {
      return bank;
    }
  }
  return null;
}

export function bankBadge(bankName: string | null | undefined, promptPay = false): ThaiBank {
  if (promptPay) {
    return { code: "PP", nameTh: "พร้อมเพย์", short: "PP", color: "#1e4598", aliases: [] };
  }
  return resolveThaiBank(bankName) ?? { ...FALLBACK, nameTh: bankName?.trim() || FALLBACK.nameTh };
}

/** Thai-style grouping for common account lengths. */
export function formatAccountNumber(value: string | null | undefined): string {
  if (!value) return "";
  const digits = value.replace(/\D/g, "");
  if (!digits) return value;
  if (digits.length === 10) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 4)}-${digits.slice(4, 9)}-${digits.slice(9)}`;
  }
  if (digits.length === 12) {
    return `${digits.slice(0, 3)}-${digits.slice(3, 4)}-${digits.slice(4, 11)}-${digits.slice(11)}`;
  }
  return digits.replace(/(\d{4})(?=\d)/g, "$1-");
}
