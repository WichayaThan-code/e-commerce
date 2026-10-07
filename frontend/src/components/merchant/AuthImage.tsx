import { useEffect, useState } from "react";
import { authorizedBlob, mediaUrl } from "../../services/api";

export default function AuthImage({ path, alt, className }: { path?: string | null; alt: string; className?: string }) {
  const [src, setSrc] = useState("");

  useEffect(() => {
    if (!path) {
      setSrc("");
      return;
    }
    if (!path.startsWith("/api/merchant/")) {
      setSrc(mediaUrl(path));
      return;
    }
    let active = true;
    let objectUrl = "";
    authorizedBlob(path)
      .then((blob) => {
        if (!active) return;
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      })
      .catch(() => {
        if (active) setSrc("");
      });
    return () => {
      active = false;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path]);

  if (!src) return <span className={className ? `pm-thumb-fallback ${className}` : "pm-thumb-fallback"} aria-hidden="true" />;
  return <img className={className} src={src} alt={alt} />;
}
