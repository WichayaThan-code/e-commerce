import { useState } from "react";
import { Store } from "lucide-react";
import { mediaUrl } from "../services/api";

export default function ShopLogo({ src, name }: { src?: string | null; name: string }) {
  const [failed, setFailed] = useState(false);
  if (!src || failed) return <Store />;
  return <img src={mediaUrl(src)} alt={`โลโก้ ${name}`} onError={() => setFailed(true)} />;
}
