import { useEffect, useState } from "react";
import { ExternalLink, MapPin } from "lucide-react";
import "../../styles/store-mini-map.css";

type StoreMiniMapProps = {
  address: string;
  mapUrl: string;
  latitude?: number | null;
  longitude?: number | null;
  /** When false, only the embed/placeholder is shown (caller can render its own CTA). */
  showLink?: boolean;
  linkLabel?: string;
  title?: string;
  className?: string;
  loadingLabel?: string;
  emptyLabel?: string;
  errorLabel?: string;
};

type Coords = { lat: number; lon: number };

function validCoords(lat?: number | null, lon?: number | null): Coords | null {
  if (lat == null || lon == null) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  return { lat, lon };
}

function osmEmbedUrl(coords: Coords) {
  // Near OSM max (~zoom 19); tile maps do not support zoom 50
  const delta = 0.001;
  return `https://www.openstreetmap.org/export/embed.html?bbox=${coords.lon - delta}%2C${coords.lat - delta}%2C${coords.lon + delta}%2C${coords.lat + delta}&layer=mapnik&marker=${coords.lat}%2C${coords.lon}`;
}

/** Compact address preview map via OpenStreetMap (CSP allows osm.org frames; Google embeds are blocked). */
export default function StoreMiniMap({
  address,
  mapUrl,
  latitude,
  longitude,
  showLink = true,
  linkLabel = "เปิดใน Google Maps",
  title = "แผนที่ร้านค้า",
  className,
  loadingLabel = "กำลังโหลดแผนที่...",
  emptyLabel = "ยังไม่มีพิกัดจากที่อยู่",
  errorLabel = "ไม่พบตำแหน่งจากที่อยู่",
}: StoreMiniMapProps) {
  const pinned = validCoords(latitude, longitude);
  const [coords, setCoords] = useState<Coords | null>(pinned);
  const [status, setStatus] = useState<"idle" | "loading" | "ready" | "empty" | "error">(
    pinned ? "ready" : address.trim() ? "loading" : "empty",
  );

  useEffect(() => {
    const saved = validCoords(latitude, longitude);
    if (saved) {
      setCoords(saved);
      setStatus("ready");
      return;
    }

    const query = address.trim();
    if (!query) {
      setCoords(null);
      setStatus("empty");
      return;
    }

    let cancelled = false;
    setStatus("loading");
    const timer = window.setTimeout(async () => {
      try {
        const response = await fetch(
          `https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=th&q=${encodeURIComponent(query)}`,
          { headers: { Accept: "application/json" } },
        );
        if (!response.ok) throw new Error("geocode failed");
        const rows = (await response.json()) as Array<{ lat: string; lon: string }>;
        if (cancelled) return;
        const hit = rows[0];
        if (!hit) {
          setCoords(null);
          setStatus("error");
          return;
        }
        setCoords({ lat: Number(hit.lat), lon: Number(hit.lon) });
        setStatus("ready");
      } catch {
        if (!cancelled) {
          setCoords(null);
          setStatus("error");
        }
      }
    }, 350);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [address, latitude, longitude]);

  const embedUrl = coords ? osmEmbedUrl(coords) : null;

  return (
    <div className={["store-mini-map", className, status === "ready" ? "has-map" : ""].filter(Boolean).join(" ")}>
      {status === "ready" && embedUrl ? (
        <iframe title={title} src={embedUrl} loading="lazy" referrerPolicy="no-referrer-when-downgrade" />
      ) : (
        <div className="store-mini-map-empty" aria-live="polite">
          <MapPin aria-hidden />
          <span>
            {status === "loading" ? loadingLabel : status === "empty" ? emptyLabel : errorLabel}
          </span>
        </div>
      )}
      {showLink ? (
        <a className="store-maps-link" href={mapUrl} target="_blank" rel="noreferrer">
          <ExternalLink aria-hidden />
          {linkLabel}
        </a>
      ) : null}
    </div>
  );
}
