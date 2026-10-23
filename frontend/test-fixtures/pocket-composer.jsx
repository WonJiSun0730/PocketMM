import React,{useEffect,useMemo,useState,useSyncExternalStore} from "react"
import {createRoot} from "react-dom/client"
import PocketComposer from "../src/components/PocketComposer"
import {createReplySuggestions} from "../src/lib/replySuggestions"
import "../src/index.css"

function Fixture() {
  const [value,setValue]=useState("")
  const [scene,setScene]=useState(1)
  const [calls,setCalls]=useState(0)
  const [sent,setSent]=useState("")
  const manager=useMemo(()=>createReplySuggestions(async()=>{
    setCalls(count=>count+1)
    return {status:"READY",suggestions:["*의료 가방을 고쳐 쥔다.* 함께 가죠.","그 소년의 말을 더 들어보는 게 좋겠어요.","*창밖의 마차를 잠시 살핀다.* 지금 출발할까요?"]}
  }),[])
  const state=useSyncExternalStore(manager.subscribe,manager.getSnapshot)
  useEffect(()=>manager.setScene({key:String(scene)}),[manager,scene])
  return <main className="mx-auto flex min-h-screen max-w-3xl flex-col bg-background text-foreground">
    <header className="border-b p-5"><h1 className="font-semibold">The Baker Street Case</h1><p className="mt-1 text-xs text-muted-foreground">무료 모의 화면 · 실제 API 호출 없음</p></header>
    <section className="flex-1 p-6 leading-8"><p>홈즈가 문을 열었다. 헤일은 길가에서 기다리는 마차를 가리켰다.</p><p className="mt-5">Sherlock Holmes: “준비되셨다면 함께 출발하시죠.”</p>
      <p className="mt-8 text-xs text-muted-foreground">장면 {scene} · 모의 호출 {calls}회</p>
      <button className="mt-2 rounded border px-3 text-sm" onClick={()=>setScene(value=>value+1)}>다음 지문 (모의)</button>
      {sent && <p className="mt-4">보낸 입력: {sent}</p>}
    </section>
    <PocketComposer value={value} onChange={setValue} onSend={()=>{setSent(value);setValue("")}} recommendations={state} onToggle={manager.toggle}/>
  </main>
}
createRoot(document.getElementById("root")).render(<Fixture/>);
