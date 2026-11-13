/** Temporary ambient types until `npm install react-easy-crop` is run. */
declare module "react-easy-crop" {
  import type { ComponentType, CSSProperties } from "react";

  export type Area = { x: number; y: number; width: number; height: number };
  export type Point = { x: number; y: number };
  export type Size = { width: number; height: number };
  export type MediaSize = {
    width: number;
    height: number;
    naturalWidth: number;
    naturalHeight: number;
  };

  export function getInitialCropFromCroppedAreaPercentages(
    croppedAreaPercentages: Area,
    mediaSize: MediaSize,
    rotation: number,
    cropSize: Size,
    minZoom: number,
    maxZoom: number,
  ): { crop: Point; zoom: number };

  export type CropperProps = {
    image: string;
    crop: Point;
    zoom: number;
    aspect?: number;
    minZoom?: number;
    maxZoom?: number;
    showGrid?: boolean;
    initialCroppedAreaPercentages?: Area;
    onCropChange: (location: Point) => void;
    onZoomChange?: (zoom: number) => void;
    onCropComplete?: (croppedArea: Area, croppedAreaPixels: Area) => void;
    onCropAreaChange?: (croppedArea: Area, croppedAreaPixels: Area) => void;
    onMediaLoaded?: (mediaSize: MediaSize) => void;
    onCropSizeChange?: (cropSize: Size) => void;
    style?: { containerStyle?: CSSProperties; mediaStyle?: CSSProperties; cropAreaStyle?: CSSProperties };
    classes?: { containerClassName?: string; mediaClassName?: string; cropAreaClassName?: string };
  };

  const Cropper: ComponentType<CropperProps>;
  export default Cropper;
}
