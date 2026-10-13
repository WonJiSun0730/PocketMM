import { LOOK_PRESETS, normalizeLook } from "@/lib/pocketSceneLook"
// Product default is fixed; earlier local tuning must not override the chosen appearance.
export default function usePocketSceneLook() {
  return [normalizeLook(LOOK_PRESETS.soft)]
}
