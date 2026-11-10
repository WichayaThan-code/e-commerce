import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import "../../styles/store-mini-map.css";

const BANGKOK: L.LatLngExpression = [13.7563, 100.5018];

/** Fix Vite bundling of Leaflet default marker assets. */
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
});

type ShopMapPinEditorProps = {
  address: string;
  latitude?: number | null;
  longitude?: number | null;
  editable?: boolean;
  onChange?: (coords: { latitude: number; longitude: number }) => void;
  hint?: string;
  className?: string;
};

function validPair(lat?: number | null, lon?: number | null): [number, number] | null {
  if (lat == null || lon == null) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  return [lat, lon];
}

async function geocodeAddress(address: string): Promise<[number, number] | null> {
  const query = address.trim();
  if (!query) return null;
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=th&q=${encodeURIComponent(query)}`,
      { headers: { Accept: "application/json" } },
    );
    if (!response.ok) return null;
    const rows = (await response.json()) as Array<{ lat: string; lon: string }>;
    const hit = rows[0];
    if (!hit) return null;
    return [Number(hit.lat), Number(hit.lon)];
  } catch {
    return null;
  }
}

/** Interactive OSM map with a draggable pin for merchant My Store. */
export default function ShopMapPinEditor({
  address,
  latitude,
  longitude,
  editable = false,
  onChange,
  hint,
  className,
}: ShopMapPinEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const onChangeRef = useRef(onChange);
  onChangeRef.current = onChange;

  useEffect(() => {
    const el = containerRef.current;
    if (!el || mapRef.current) return;

    const map = L.map(el, {
      center: validPair(latitude, longitude) ?? BANGKOK,
      zoom: 50,
      scrollWheelZoom: false,
    });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(map);

    const emit = (latlng: L.LatLng) => {
      onChangeRef.current?.({ latitude: latlng.lat, longitude: latlng.lng });
    };

    const marker = L.marker(validPair(latitude, longitude) ?? BANGKOK, {
      draggable: editable,
      autoPan: true,
    }).addTo(map);

    marker.on("dragend", () => emit(marker.getLatLng()));
    map.on("click", (event: L.LeafletMouseEvent) => {
      if (!editable) return;
      marker.setLatLng(event.latlng);
      emit(event.latlng);
    });

    mapRef.current = map;
    markerRef.current = marker;

    // Prefer saved coords; otherwise try address geocode once for initial pin.
    let cancelled = false;
    void (async () => {
      if (validPair(latitude, longitude)) {
        window.setTimeout(() => map.invalidateSize(), 50);
        return;
      }
      const found = await geocodeAddress(address);
      if (cancelled || !mapRef.current || !markerRef.current) return;
      const next = found ?? BANGKOK;
      markerRef.current.setLatLng(next);
      mapRef.current.setView(next, found ? 50 : 12);
      if (found) {
        const latlng = markerRef.current.getLatLng();
        emit(latlng);
      }
      window.setTimeout(() => map.invalidateSize(), 50);
    })();

    return () => {
      cancelled = true;
      map.remove();
      mapRef.current = null;
      markerRef.current = null;
    };
    // Mount once; subsequent prop sync is handled below.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    const marker = markerRef.current;
    if (!marker) return;
    marker.dragging?.[editable ? "enable" : "disable"]();
  }, [editable]);

  useEffect(() => {
    const map = mapRef.current;
    const marker = markerRef.current;
    const pair = validPair(latitude, longitude);
    if (!map || !marker || !pair) return;
    const current = marker.getLatLng();
    if (Math.abs(current.lat - pair[0]) < 1e-7 && Math.abs(current.lng - pair[1]) < 1e-7) return;
    marker.setLatLng(pair);
    map.setView(pair, map.getZoom());
  }, [latitude, longitude]);

  return (
    <div className={["shop-map-pin", className].filter(Boolean).join(" ")}>
      <div ref={containerRef} className="shop-map-pin-canvas" role="presentation" />
      {hint ? <p className="shop-map-pin-hint">{hint}</p> : null}
    </div>
  );
}
