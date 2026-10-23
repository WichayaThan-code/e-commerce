import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { authorizedBlob } from "../../services/api";
import AuthImage from "./AuthImage";

type Props = { path?: string | null; contentType?: string | null; alt: string };

/**
 * Slip / refund-proof file. Images go through AuthImage; PDFs (and files whose type the API does not report) are fetched
 * with authorizedBlob and shown from an object URL that is revoked on unmount.
 */
export default function SlipPreview({ path, contentType, alt }: Props) {
  const { t } = useTranslation("merchant");
  const type = (contentType || "").toLowerCase();
  const knownImage = type.startsWith("image/");
  const [url, setUrl] = useState("");
  const [blobType, setBlobType] = useState("");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!path || knownImage) {
      setUrl("");
      return;
    }
    let active = true;
    let objectUrl = "";
    setFailed(false);
    authorizedBlob(path)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setBlobType(blob.type.toLowerCase());
        setUrl(objectUrl);
      })
      .catch(() => {
        if (active) setFailed(true);
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path, knownImage]);

  if (!path) return null;
  if (knownImage) return <AuthImage className="om-slip-img" path={path} alt={alt} />;
  if (failed) return <span className="om-muted">{t("orderManagement.payment.fileLoadFailed")}</span>;
  if (!url) return <span className="pm-thumb-fallback om-slip-img" aria-hidden="true" />;
  const isPdf = type.includes("pdf") || blobType.includes("pdf");
  if (isPdf) {
    return (
      <div className="om-slip-pdf">
        <object data={url} type="application/pdf" aria-label={alt}>
          <a href={url} target="_blank" rel="noreferrer">{t("orderManagement.payment.openFile")}</a>
        </object>
        <a href={url} target="_blank" rel="noreferrer">{t("orderManagement.payment.openFile")}</a>
      </div>
    );
  }
  return (
    <a href={url} target="_blank" rel="noreferrer" className="om-slip-link">
      <img className="om-slip-img" src={url} alt={alt} />
    </a>
  );
}
