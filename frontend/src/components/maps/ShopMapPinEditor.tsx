import { useEffect, useRef } from "react";
import L from "leaflet";
import "leaflet/dist/leaflet.css";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";
import "../../styles/store-mini-map.css";

const BANGKOK: L.LatLngExpression = [13.7563, 100.5018];
/** Historic My Store default (Leaflet clamps to maxZoom). Keep as default so merchant behavior is unchanged. */
const DEFAULT_ZOOM = 50;

/** Fix Vite bundling of Leaflet default marker assets (avoid path + URL double-prefix). */
// eslint-disable-next-line @typescript-eslint/no-explicit-any -- Leaflet private hook
delete (L.Icon.Default.prototype as any)._getIconUrl;
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
  /** Initial / setView zoom. Defaults to legacy My Store value. */
  zoom?: number;
  /**
   * When true (default), geocode miss falls back to Bangkok like My Store.
   * When false, geocode miss hides the map via onUnavailable — never invent a pin.
   */
  fallbackToDefault?: boolean;
  /** sessionStorage key for one-shot geocode cache (e.g. shop slug). */
  cacheKey?: string;
  mapAriaLabel?: string;
  markerTitle?: string;
  /** Fired when no pin can be shown (no coords + geocode miss) and fallbackToDefault is false. */
  onUnavailable?: () => void;
};

function validPair(lat?: number | null, lon?: number | null): [number, number] | null {
  if (lat == null || lon == null) return null;
  if (!Number.isFinite(lat) || !Number.isFinite(lon)) return null;
  if (lat < -90 || lat > 90 || lon < -180 || lon > 180) return null;
  return [lat, lon];
}

function readGeocodeCache(key: string): [number, number] | null {
  try {
    const raw = sessionStorage.getItem(`shop-map-geocode:${key}`);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as { lat?: number; lng?: number };
    return validPair(parsed.lat, parsed.lng);
  } catch {
    return null;
  }
}

function writeGeocodeCache(key: string, pair: [number, number]) {
  try {
    sessionStorage.setItem(`shop-map-geocode:${key}`, JSON.stringify({ lat: pair[0], lng: pair[1] }));
  } catch {
    /* ignore quota / private mode */
  }
}

async function geocodeAddressOnce(query: string): Promise<[number, number] | null> {
  const q = query.trim();
  if (!q) return null;
  try {
    const response = await fetch(
      `https://nominatim.openstreetmap.org/search?format=json&limit=1&countrycodes=th&q=${encodeURIComponent(q)}`,
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

/**
 * Nominatim often misses Thai house/soi lines but hits district/subdistrict.
 * Try full address, then progressively shorter tail (subdistrict → district → province).
 */
function geocodeVariants(address: string): string[] {
  const trimmed = address.trim();
  if (!trimmed) return [];
  const parts = trimmed.split(/\s+/).filter(Boolean);
  const variants = [trimmed];
  if (parts.length > 4) variants.push(parts.slice(-4).join(" "));
  if (parts.length > 3) variants.push(parts.slice(-3).join(" "));
  if (parts.length > 2) variants.push(parts.slice(-2).join(" "));
  return [...new Set(variants)];
}

async function geocodeAddress(address: string): Promise<[number, number] | null> {
  const variants = geocodeVariants(address);
  for (let i = 0; i < variants.length; i += 1) {
    const hit = await geocodeAddressOnce(variants[i]!);
    if (hit) return hit;
    // Gentle pacing between Nominatim calls (policy + theme-switch remounts).
    if (i < variants.length - 1) {
      await new Promise((resolve) => window.setTimeout(resolve, 700));
    }
  }
  // One more pass on the best (shortest reliable) variant after a pause.
  const fallback = variants[Math.min(2, variants.length - 1)];
  if (!fallback) return null;
  await new Promise((resolve) => window.setTimeout(resolve, 900));
  return geocodeAddressOnce(fallback);
}

function isCoarsePointer() {
  if (typeof window === "undefined") return false;
  return window.matchMedia("(pointer: coarse)").matches || "ontouchstart" in window;
}

/** Interactive OSM map with a draggable pin for merchant My Store (also read-only embed). */
export default function ShopMapPinEditor({
  address,
  latitude,
  longitude,
  editable = false,
  onChange,
  hint,
  className,
  zoom = DEFAULT_ZOOM,
  fallbackToDefault = true,
  cacheKey,
  mapAriaLabel,
  markerTitle,
  onUnavailable,
}: ShopMapPinEditorProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<L.Map | null>(null);
  const markerRef = useRef<L.Marker | null>(null);
  const onChangeRef = useRef(onChange);
  const onUnavailableRef = useRef(onUnavailable);
  onChangeRef.current = onChange;
  onUnavailableRef.current = onUnavailable;

  useEffect(() => {
    const el = containerRef.current;
    if (!el || mapRef.current) return;

    // React Strict Mode remounts leave Leaflet's internal id on the node.
    const leaked = el as HTMLDivElement & { _leaflet_id?: number };
    if (leaked._leaflet_id) {
      leaked._leaflet_id = undefined;
      el.replaceChildren();
    }

    const initial = validPair(latitude, longitude);
    const allowDrag = editable || !isCoarsePointer();
    // Avoid flashing Bangkok when contact mode must not invent a pin.
    if (!initial && !fallbackToDefault) el.classList.add("is-resolving");

    const map = L.map(el, {
      center: initial ?? BANGKOK,
      zoom,
      scrollWheelZoom: false,
      dragging: allowDrag,
      zoomControl: true,
    });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
      maxZoom: 19,
    }).addTo(map);

    const emit = (latlng: L.LatLng) => {
      onChangeRef.current?.({ latitude: latlng.lat, longitude: latlng.lng });
    };

    const placeMarker = (pair: [number, number], makeDraggable: boolean) => {
      if (markerRef.current) {
        markerRef.current.setLatLng(pair);
        markerRef.current.dragging?.[makeDraggable ? "enable" : "disable"]();
        if (markerTitle) markerRef.current.bindTooltip(markerTitle, { permanent: false, direction: "top" });
        return markerRef.current;
      }
      const marker = L.marker(pair, {
        draggable: makeDraggable,
        autoPan: true,
        title: markerTitle || undefined,
      }).addTo(map);
      marker.on("dragend", () => emit(marker.getLatLng()));
      if (markerTitle) marker.bindTooltip(markerTitle, { permanent: false, direction: "top" });
      markerRef.current = marker;
      return marker;
    };

    map.on("click", (event: L.LeafletMouseEvent) => {
      if (!editable) return;
      const marker = placeMarker([event.latlng.lat, event.latlng.lng], true);
      emit(marker.getLatLng());
    });

    mapRef.current = map;

    let cancelled = false;

    void (async () => {
      if (initial) {
        placeMarker(initial, editable);
        window.setTimeout(() => map.invalidateSize(), 50);
        return;
      }

      let found: [number, number] | null = null;
      if (cacheKey) found = readGeocodeCache(cacheKey);
      if (!found) {
        found = await geocodeAddress(address);
        // Only cache hits — never cache misses (rate limits / flaky Nominatim must not stick).
        if (found && cacheKey) writeGeocodeCache(cacheKey, found);
      }

      if (cancelled) return;

      if (found) {
        placeMarker(found, editable);
        map.setView(found, Math.min(zoom, 19));
        el.classList.remove("is-resolving");
        // Preserve My Store behavior: geocode hit writes coords into the form via onChange.
        emit(markerRef.current!.getLatLng());
        // Theme / layout shifts often leave a blank tile pane until size is refreshed.
        window.setTimeout(() => map.invalidateSize(), 50);
        window.setTimeout(() => map.invalidateSize(), 250);
        return;
      }

      if (!fallbackToDefault) {
        // Keep the slot height; clear Leaflet so we don't leave a blank Bangkok flash.
        try {
          map.remove();
        } catch {
          /* already torn down */
        }
        mapRef.current = null;
        markerRef.current = null;
        const leaked = el as HTMLDivElement & { _leaflet_id?: number };
        leaked._leaflet_id = undefined;
        el.replaceChildren();
        el.className = "shop-map-pin-canvas is-unavailable";
        onUnavailableRef.current?.();
        return;
      }

      // Legacy My Store: geocode miss → Bangkok pin (no emit unless user moves it).
      placeMarker(BANGKOK as [number, number], editable);
      map.setView(BANGKOK, 12);
      el.classList.remove("is-resolving");
      window.setTimeout(() => map.invalidateSize(), 50);
    })();

    const refreshSize = () => {
      mapRef.current?.invalidateSize();
    };
    window.addEventListener("resize", refreshSize);
    const ro = typeof ResizeObserver !== "undefined" ? new ResizeObserver(refreshSize) : null;
    ro?.observe(el);
    const io =
      typeof IntersectionObserver !== "undefined"
        ? new IntersectionObserver((entries) => {
            if (entries.some((entry) => entry.isIntersecting)) refreshSize();
          })
        : null;
    io?.observe(el);

    return () => {
      cancelled = true;
      window.removeEventListener("resize", refreshSize);
      ro?.disconnect();
      io?.disconnect();
      try {
        map.remove();
      } catch {
        /* Strict Mode / double-remove */
      }
      mapRef.current = null;
      markerRef.current = null;
      const leakedNode = el as HTMLDivElement & { _leaflet_id?: number };
      leakedNode._leaflet_id = undefined;
      el.replaceChildren();
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

  useEffect(() => {
    const marker = markerRef.current;
    if (!marker || !markerTitle) return;
    marker.unbindTooltip();
    marker.bindTooltip(markerTitle, { permanent: false, direction: "top" });
    marker.options.title = markerTitle;
  }, [markerTitle]);

  return (
    <div className={["shop-map-pin", className].filter(Boolean).join(" ")}>
      <div
        ref={containerRef}
        className="shop-map-pin-canvas"
        role={mapAriaLabel ? "img" : "presentation"}
        aria-label={mapAriaLabel}
      />
      {hint ? <p className="shop-map-pin-hint">{hint}</p> : null}
    </div>
  );
}
