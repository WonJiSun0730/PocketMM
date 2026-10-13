import usePocketSceneLook from "@/hooks/usePocketSceneLook"
import { LOOK_PRESETS } from "@/lib/pocketSceneLook"
export default function PocketSceneLookControls() {
  const [look, update] = usePocketSceneLook()
  return <details className="my-2 rounded-md border p-3 text-xs">
    <summary className="cursor-pointer font-medium">화면 분위기</summary>
    <div className="mt-3 flex flex-wrap gap-2">{Object.entries(LOOK_PRESETS).map(([key, preset]) =>
      <button type="button" key={key} onClick={() => update(preset)}
        aria-pressed={Object.keys(look).every(k => look[k] === preset[k])}
        className="rounded-md border px-2 py-1 text-primary aria-pressed:bg-primary aria-pressed:text-primary-foreground">{preset.label}</button>)}</div>
    <div className="mt-3 grid gap-3">{[["brightness", "밝기", 80, 160], ["warmth", "따뜻함", 0, 40], ["saturation", "색감", 60, 150], ["contrast", "대비", 80, 120]].map(([key, label, min, max]) =>
      <label key={key} className="grid gap-1"><span>{label} <span className="float-right text-muted-foreground">{look[key]}%</span></span>
        <input type="range" min={min} max={max} value={look[key]} className="w-full accent-[var(--primary)]"
          onChange={e => update({ ...look, [key]: Number(e.target.value) })} /></label>)}</div>
    <p className="mt-2 text-muted-foreground">장면 전체에 적용해요. 이 기기에 저장되며 이미지 생성 비용은 없어요.</p>
  </details>
}
