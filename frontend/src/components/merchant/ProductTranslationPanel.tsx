import { useTranslation } from "react-i18next";
import ToggleSwitch from "../form/ToggleSwitch";

type Locale = "en" | "zh";
type FieldKey = "name" | "description";

export type TranslationDraftPatch = {
  nameEn?: string;
  nameZh?: string;
  descriptionEn?: string;
  descriptionZh?: string;
};

type Props = {
  manual: boolean;
  onManualChange: (manual: boolean) => void;
  nameTh: string;
  descriptionTh: string;
  nameEn: string;
  nameZh: string;
  descriptionEn: string;
  descriptionZh: string;
  onChange: (patch: TranslationDraftPatch) => void;
};

const LOCALES: Locale[] = ["en", "zh"];
const FIELDS: FieldKey[] = ["name", "description"];

function draftKey(locale: Locale, field: FieldKey): keyof TranslationDraftPatch {
  if (field === "name") return locale === "en" ? "nameEn" : "nameZh";
  return locale === "en" ? "descriptionEn" : "descriptionZh";
}

/**
 * Manual-only translations for the product form. No translation API is called from here.
 * Fields are optional with no validation: anything left blank (or everything, when the toggle is off)
 * is saved with the Thai text as its English/Chinese value.
 */
export default function ProductTranslationPanel({
  manual,
  onManualChange,
  nameTh,
  descriptionTh,
  nameEn,
  nameZh,
  descriptionEn,
  descriptionZh,
  onChange,
}: Props) {
  const { t } = useTranslation("merchant");
  const values: Record<keyof TranslationDraftPatch, string> = { nameEn, nameZh, descriptionEn, descriptionZh };

  return (
    <section className="pm-translation-panel" aria-label={t("catalog.form.manualTranslationSection")}>
      <header className="pm-translation-panel-head">
        <div className="pm-translation-field-head">
          <h3>{t("catalog.form.manualTranslationSection")}</h3>
          <ToggleSwitch
            checked={manual}
            label={t("catalog.form.manualTranslation")}
            onText={t("catalog.form.manualTranslationOn")}
            offText={t("catalog.form.manualTranslationOff")}
            onChange={onManualChange}
          />
        </div>
        <p className="pm-lang-hint">{t(manual ? "catalog.form.manualTranslationHint" : "catalog.form.manualTranslationOffHint")}</p>
      </header>
      {manual
        ? LOCALES.map((locale) => (
          <div className="pm-lang-block" key={locale}>
            <h4>
              {t(`catalog.form.lang${locale === "en" ? "En" : "Zh"}`)}
              <small>{t("catalog.form.langOptional")}</small>
            </h4>
            {FIELDS.map((field) => {
              const key = draftKey(locale, field);
              const thai = (field === "name" ? nameTh : descriptionTh).trim();
              const placeholder = thai
                ? t("catalog.form.manualTranslationPh", { text: thai.length > 60 ? `${thai.slice(0, 60)}…` : thai })
                : t("catalog.form.manualTranslationBlankPh");
              return (
                <label className="pm-translation-field" key={key}>
                  <span className="pm-lang-label">{t(field === "name" ? "catalog.form.name" : "catalog.form.description")}</span>
                  {field === "name" ? (
                    <input value={values[key]} placeholder={placeholder} onChange={(event) => onChange({ [key]: event.target.value })} />
                  ) : (
                    <textarea value={values[key]} placeholder={placeholder} onChange={(event) => onChange({ [key]: event.target.value })} />
                  )}
                </label>
              );
            })}
          </div>
        ))
        : null}
    </section>
  );
}
