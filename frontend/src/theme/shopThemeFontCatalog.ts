/**
 * Storefront theme font catalog (exactly 100 families).
 * - thaiNative: Google Fonts with real Thai + Latin glyphs in one family
 * - others: Latin primary + Noto Thai fallback in the CSS stack (readable TH + styled EN)
 */

export type ShopThemeFontKind = 'sans' | 'serif' | 'display' | 'hand'

export type ShopThemeFontRow = {
  id: string
  family: string
  kind: ShopThemeFontKind
  weights: readonly number[]
  /** True when the family itself includes Thai glyphs. */
  thaiNative: boolean
  /** IBM Plex Sans Thai is already linked from the app shell. */
  selfHosted?: boolean
}

/** Stable API / enum ids — order matches the merchant theme picker. */
export const SHOP_THEME_FONT_ROWS = [
  // —— Thai + Latin natives (Google Fonts Thai subset) ——
  { id: 'NOTO_SANS_THAI', family: 'Noto Sans Thai', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'NOTO_SERIF_THAI', family: 'Noto Serif Thai', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'NOTO_SANS_THAI_LOOPED', family: 'Noto Sans Thai Looped', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'IBM_PLEX_SANS_THAI', family: 'IBM Plex Sans Thai', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true, selfHosted: true },
  { id: 'IBM_PLEX_SANS_THAI_LOOPED', family: 'IBM Plex Sans Thai Looped', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'PROMPT', family: 'Prompt', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'MITR', family: 'Mitr', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'SARABUN', family: 'Sarabun', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'KANIT', family: 'Kanit', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'ANUPHAN', family: 'Anuphan', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'BAI_JAMJUREE', family: 'Bai Jamjuree', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'CHAKRA_PETCH', family: 'Chakra Petch', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'KRUB', family: 'Krub', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'PRIDI', family: 'Pridi', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'TAVIRAJ', family: 'Taviraj', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'TRIRONG', family: 'Trirong', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'NIRAMIT', family: 'Niramit', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'CHONBURI', family: 'Chonburi', kind: 'display', weights: [400], thaiNative: true },
  { id: 'ATHITI', family: 'Athiti', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'MALI', family: 'Mali', kind: 'hand', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'ITIM', family: 'Itim', kind: 'hand', weights: [400], thaiNative: true },
  { id: 'KODCHASAN', family: 'Kodchasan', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'CHARM', family: 'Charm', kind: 'hand', weights: [400, 700], thaiNative: true },
  { id: 'CHARMONMAN', family: 'Charmonman', kind: 'hand', weights: [400, 700], thaiNative: true },
  { id: 'FAHKWANG', family: 'Fahkwang', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'K2D', family: 'K2D', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'KOHO', family: 'KoHo', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'MAITREE', family: 'Maitree', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'PATTAYA', family: 'Pattaya', kind: 'display', weights: [400], thaiNative: true },
  { id: 'PLAYPEN_SANS_THAI', family: 'Playpen Sans Thai', kind: 'hand', weights: [400, 500, 600, 700], thaiNative: true },
  { id: 'SRIRACHA', family: 'Sriracha', kind: 'hand', weights: [400], thaiNative: true },
  { id: 'SRISAKDI', family: 'Srisakdi', kind: 'display', weights: [400, 700], thaiNative: true },
  { id: 'THASADITH', family: 'Thasadith', kind: 'sans', weights: [400, 700], thaiNative: true },

  // —— Latin primaries with Noto Thai fallback (styled EN + readable TH) ——
  { id: 'INTER', family: 'Inter', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'ROBOTO', family: 'Roboto', kind: 'sans', weights: [400, 500, 700], thaiNative: false },
  { id: 'OPEN_SANS', family: 'Open Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'LATO', family: 'Lato', kind: 'sans', weights: [400, 700], thaiNative: false },
  { id: 'MONTSERRAT', family: 'Montserrat', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'POPPINS', family: 'Poppins', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'RALEWAY', family: 'Raleway', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'NUNITO', family: 'Nunito', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'SOURCE_SANS_3', family: 'Source Sans 3', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'WORK_SANS', family: 'Work Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'DM_SANS', family: 'DM Sans', kind: 'sans', weights: [400, 500, 700], thaiNative: false },
  { id: 'MANROPE', family: 'Manrope', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'OUTFIT', family: 'Outfit', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'PLUS_JAKARTA_SANS', family: 'Plus Jakarta Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'SPACE_GROTESK', family: 'Space Grotesk', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'FIGTREE', family: 'Figtree', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'SORA', family: 'Sora', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'URBANIST', family: 'Urbanist', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'LEXEND', family: 'Lexend', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'RUBIK', family: 'Rubik', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'MUKTA', family: 'Mukta', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'BARLOW', family: 'Barlow', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'CABIN', family: 'Cabin', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'KARLA', family: 'Karla', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'MULISH', family: 'Mulish', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'JOSEFIN_SANS', family: 'Josefin Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'QUICKSAND', family: 'Quicksand', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'EXO_2', family: 'Exo 2', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'UBUNTU', family: 'Ubuntu', kind: 'sans', weights: [400, 500, 700], thaiNative: false },
  { id: 'FIRA_SANS', family: 'Fira Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'PT_SANS', family: 'PT Sans', kind: 'sans', weights: [400, 700], thaiNative: false },
  { id: 'NOTO_SANS', family: 'Noto Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'ROBOTO_CONDENSED', family: 'Roboto Condensed', kind: 'sans', weights: [400, 500, 700], thaiNative: false },
  { id: 'ARCHIVO', family: 'Archivo', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'LIBRE_FRANKLIN', family: 'Libre Franklin', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'INSTRUMENT_SANS', family: 'Instrument Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'ONEST', family: 'Onest', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'RED_HAT_TEXT', family: 'Red Hat Text', kind: 'sans', weights: [400, 500, 700], thaiNative: false },
  { id: 'ALBERT_SANS', family: 'Albert Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'PUBLIC_SANS', family: 'Public Sans', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'SCHIBSTED_GROTESK', family: 'Schibsted Grotesk', kind: 'sans', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'SYNE', family: 'Syne', kind: 'display', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'BEBAS_NEUE', family: 'Bebas Neue', kind: 'display', weights: [400], thaiNative: false },
  { id: 'OSWALD', family: 'Oswald', kind: 'display', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'ANTON', family: 'Anton', kind: 'display', weights: [400], thaiNative: false },
  { id: 'RIGHTEOUS', family: 'Righteous', kind: 'display', weights: [400], thaiNative: false },
  { id: 'PACIFICO', family: 'Pacifico', kind: 'hand', weights: [400], thaiNative: false },
  { id: 'LOBSTER', family: 'Lobster', kind: 'hand', weights: [400], thaiNative: false },
  { id: 'DANCING_SCRIPT', family: 'Dancing Script', kind: 'hand', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'CAVEAT', family: 'Caveat', kind: 'hand', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'PLAYFAIR_DISPLAY', family: 'Playfair Display', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'MERRIWEATHER', family: 'Merriweather', kind: 'serif', weights: [400, 700], thaiNative: false },
  { id: 'LORA', family: 'Lora', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'LIBRE_BASKERVILLE', family: 'Libre Baskerville', kind: 'serif', weights: [400, 700], thaiNative: false },
  { id: 'CORMORANT_GARAMOND', family: 'Cormorant Garamond', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'EB_GARAMOND', family: 'EB Garamond', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'CRIMSON_TEXT', family: 'Crimson Text', kind: 'serif', weights: [400, 600, 700], thaiNative: false },
  { id: 'SPECTRAL', family: 'Spectral', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'SOURCE_SERIF_4', family: 'Source Serif 4', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'FRAUNCES', family: 'Fraunces', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'BITTER', family: 'Bitter', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'DOMINE', family: 'Domine', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'LITERATA', family: 'Literata', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'NEWSREADER', family: 'Newsreader', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'ROBOTO_SERIF', family: 'Roboto Serif', kind: 'serif', weights: [400, 500, 600, 700], thaiNative: false },
  { id: 'DM_SERIF_DISPLAY', family: 'DM Serif Display', kind: 'serif', weights: [400], thaiNative: false },
  { id: 'TITILLIUM_WEB', family: 'Titillium Web', kind: 'sans', weights: [400, 600, 700], thaiNative: false },
] as const satisfies readonly ShopThemeFontRow[]

export type ShopThemeFontKey = (typeof SHOP_THEME_FONT_ROWS)[number]['id']

export const SHOP_THEME_FONTS = SHOP_THEME_FONT_ROWS.map((row) => row.id) as unknown as readonly [
  ShopThemeFontKey,
  ...ShopThemeFontKey[],
]

if (SHOP_THEME_FONT_ROWS.length !== 100) {
  throw new Error(`SHOP_THEME_FONT_ROWS must be 100 (got ${SHOP_THEME_FONT_ROWS.length})`)
}
