import { ArrowLeft } from "lucide-react"
import PlayerProfilePicker from "@/components/PlayerProfilePicker"

export default function PlayerProfilesPage({ onBack }) {
  return <main className="min-h-svh bg-background px-4 py-8 text-foreground sm:px-8">
    <section className="mx-auto w-full max-w-3xl">
      <header className="mb-5 flex items-center gap-3">
        <button type="button" onClick={onBack} className="grid size-10 place-items-center rounded-xl border bg-card" aria-label="포켓으로 돌아가기">
          <ArrowLeft size={18}/>
        </button>
        <div>
          <span className="text-[11px] font-bold tracking-[.14em] text-accent-foreground">PLAYER</span>
          <h1 className="mt-1 text-2xl font-medium">플레이어 프로필</h1>
        </div>
      </header>
      <div className="rounded-2xl border bg-card">
        <PlayerProfilePicker manageOnly onCancel={onBack} />
      </div>
    </section>
  </main>
}
