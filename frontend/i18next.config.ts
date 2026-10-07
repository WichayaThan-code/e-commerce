import { defineConfig } from "i18next-cli";

export default defineConfig({
  locales: ["th", "en", "zh"],
  extract: {
    primaryLanguage: "th",
    secondaryLanguages: ["en", "zh"],
    input: ["src/**/*.{ts,tsx}"],
    output: "src/locales/{{language}}/{{namespace}}.json",
    defaultNS: "common",
    nsSeparator: ":",
    keySeparator: ".",
    functions: ["t", "tp", "tc", "i18n.t"],
    useTranslationNames: ["useTranslation", "usePublicTranslation"],
    removeUnusedKeys: false,
    sort: false,
    indentation: 2,
  },
});
