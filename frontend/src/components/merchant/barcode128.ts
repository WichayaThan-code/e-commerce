/** Minimal Code 128B → SVG barcode (no external dependency). */

/** Patterns 0–106 as digit strings (bar/space widths). Index 106 is STOP (7 modules). */
const PATTERNS = [
  "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
  "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
  "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
  "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
  "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
  "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
  "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
  "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
  "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
  "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
  "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
] as const;

const START_B = 104;
const STOP = 106;

function patternWidths(code: number): number[] {
  return [...(PATTERNS[code] ?? "212222")].map((digit) => Number(digit));
}

/** Encode printable ASCII (32–126) as Code 128B; other chars become "-". */
export function encodeCode128B(value: string): number[] {
  const cleaned = [...value]
    .map((ch) => {
      const code = ch.charCodeAt(0);
      return code >= 32 && code <= 126 ? ch : "-";
    })
    .join("")
    .slice(0, 48) || "-";

  const codes = [START_B];
  let checksum = START_B;
  for (let i = 0; i < cleaned.length; i += 1) {
    const code = cleaned.charCodeAt(i) - 32;
    codes.push(code);
    checksum += code * (i + 1);
  }
  codes.push(checksum % 103);
  codes.push(STOP);
  return codes;
}

export function barcode128Svg(
  value: string,
  options?: { height?: number; module?: number; className?: string },
): string {
  const height = options?.height ?? 48;
  const module = options?.module ?? 1.6;
  const codes = encodeCode128B(value);
  const widths = codes.flatMap(patternWidths);
  const total = widths.reduce((sum, w) => sum + w, 0);
  const width = total * module;
  let x = 0;
  const bars: string[] = [];
  let bar = true;
  for (const w of widths) {
    const px = w * module;
    if (bar) {
      bars.push(`<rect x="${x.toFixed(2)}" y="0" width="${px.toFixed(2)}" height="${height}" fill="#000"/>`);
    }
    x += px;
    bar = !bar;
  }
  const safe = value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
  const classAttr = options?.className ? ` class="${options.className}"` : "";
  return `<svg${classAttr} xmlns="http://www.w3.org/2000/svg" width="${width.toFixed(2)}" height="${height}" viewBox="0 0 ${width.toFixed(2)} ${height}" role="img" aria-label="${safe}">${bars.join("")}</svg>`;
}
