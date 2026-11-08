import { useEffect, useState } from "react";
import { ExternalLink, MapPin } from "lucide-react";

type StoreMiniMapProps = {
  address: string;
  mapUrl: string;
};

type Coords = { lat: number; lon: number };

/** Compact address preview map via OpenStreetMap (CSP allows osm.org frames; Google embeds are blocked). */
export default function StoreMiniMap({ address, mapUrl }: StoreMiniMapProps) {
  const [coords, setCoords] = useState<Coords | null>(null);
  const [status, setStatus] = useState<"idle" | "loading" | "ready" | "empty" | "error">(
    address.trim() ? "loading" : "empty",
  );

  useEffect(() => {
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
          `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`,
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
  }, [address]);

  const delta = 0.012;
  const embedUrl = coords
    ? `https://www.openstreetmap.org/export/embed.html?bbox=${coords.lon - delta}%2C${coords.lat - delta}%2C${coords.lon + delta}%2C${coords.lat + delta}&layer=mapnik&marker=${coords.lat}%2C${coords.lon}`
    : null;

  return (
    <div className={`store-mini-map${status === "ready" ? " has-map" : ""}`}>
      {status === "ready" && embedUrl ? (
        <iframe title="แผนที่ร้านค้า" src={embedUrl} loading="lazy" referrerPolicy="no-referrer-when-downgrade" />
      ) : (
        <div className="store-mini-map-empty" aria-live="polite">
          <MapPin aria-hidden />
          <span>
            {status === "loading"
              ? "กำลังโหลดแผนที่..."
              : status === "empty"
                ? "ยังไม่มีพิกัดจากที่อยู่"
                : "ไม่พบตำแหน่งจากที่อยู่"}
          </span>
        </div>
      )}
      <a className="store-maps-link" href={mapUrl} target="_blank" rel="noreferrer">
        <ExternalLink aria-hidden />เปิดใน Google Maps
      </a>
    </div>
  );
}
