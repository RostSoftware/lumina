/** Crop settings for the create flow. */
export interface CropState {
  aspect: number | null;
  zoom: number;
  panX: number;
  panY: number;
}

export const ASPECTS: { label: string; value: number | null }[] = [
  { label: 'Original', value: null },
  { label: '1:1', value: 1 },
  { label: '4:5', value: 4 / 5 },
  { label: '16:9', value: 16 / 9 },
];

