import { Link } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { ChevronRight } from "lucide-react";
import HelpRichText from "./HelpRichText";

type Props = {
  faqId: string;
  html: string;
};

/** Clamps FAQ answer to 1 line and always offers a link to the full answer page. */
export default function FaqAnswerPreview({ faqId, html }: Props) {
  const { t } = useTranslation("public");

  return (
    <>
      <div className="faq-answer-clamp">
        <HelpRichText html={html} className="faq-rich" />
      </div>
      <Link className="faq-more" to={`/help/faqs/${faqId}`}>
        {t("helpPage.faq.viewFull")}
        <ChevronRight aria-hidden="true" />
      </Link>
    </>
  );
}
