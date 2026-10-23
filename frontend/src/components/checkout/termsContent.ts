/** Platform terms snapshot shown in the checkout modal. Sent only when FEATURES.termsVersionApi is on. */
export const TERMS_VERSION = "2026-10-01";

/** i18n keys under checkout.terms.doc.* — no merchant phone/email. */
export const TERMS_SECTIONS = [
  { titleKey: "checkout.terms.doc.s1.title", bodyKey: "checkout.terms.doc.s1.body" },
  { titleKey: "checkout.terms.doc.s2.title", bodyKey: "checkout.terms.doc.s2.body" },
  { titleKey: "checkout.terms.doc.s3.title", bodyKey: "checkout.terms.doc.s3.body" },
  { titleKey: "checkout.terms.doc.s4.title", bodyKey: "checkout.terms.doc.s4.body" },
  { titleKey: "checkout.terms.doc.s5.title", bodyKey: "checkout.terms.doc.s5.body" },
] as const;
