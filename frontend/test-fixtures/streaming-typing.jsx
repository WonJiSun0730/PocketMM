import React,{useState,useRef} from "react"
import {createRoot} from "react-dom/client"
import StreamingPocketReply from "../src/components/StreamingPocketReply"
import {readPocketChatStream} from "../src/lib/pocketChatStream"
import "../src/index.css"

const full="안녕하세요. 지금 첫 문장을 받고 있습니다. 다음 조각도 앞 문장을 지우지 않고 차례대로 표시됩니다. ".repeat(3)
export default function Check() {
  const [text,setText]=useState("");const [complete,setComplete]=useState(false);const [result,setResult]=useState(null)
  const [running,setRunning]=useState(false);const [samples,setSamples]=useState([])
  const reply=useRef(null);const times=useRef({})
  const elapsed=()=>Math.round(performance.now()-times.current.start)
  function progress() {
    const value=reply.current?.querySelector(".whitespace-pre-wrap")?.textContent??""
    if(value && times.current.firstDisplay==null)times.current.firstDisplay=elapsed()
    if(times.current.finalAt!=null && value.length<full.length)times.current.typedAfterFinal=true
    times.current.last=value
    setSamples(rows=>rows.length<5?[...rows,{at:elapsed(),text:value}]:rows)
  }
  async function start() {
    setRunning(true);times.current={start:performance.now()}
    const encoder=new TextEncoder()
    const response=new Response(new ReadableStream({start(controller){
      const emit=(event)=>controller.enqueue(encoder.encode(JSON.stringify(event)+"\n"))
      emit({type:"delta",reply:full.slice(0,12)})
      setTimeout(()=>emit({type:"delta",reply:full.slice(0,40)}),350)
      setTimeout(()=>emit({type:"preview",reply:full}),700)
      setTimeout(()=>{emit({type:"complete",reply:full});controller.close()},750)
    }}))
    await readPocketChatStream(response,event=>{
      setText(event.reply)
      if(event.type==="complete"){
        times.current.finalAt=elapsed();times.current.atFinal=times.current.last?.length??0;setComplete(true)
      }
    })
  }
  function settled(){setResult({...times.current,settledAt:elapsed(),fullLength:full.length,visibility:document.visibilityState,reducedMotion:matchMedia("(prefers-reduced-motion: reduce)").matches})}
  return <main style={{padding:32,maxWidth:850,margin:"auto"}}>
    <h1>무료 조각 수신 → 타이핑 화면 검증</h1><p>API 호출 없음 · 실제 표시 컴포넌트와 NDJSON 수신기 사용</p>
    <button onClick={start} disabled={running}>모의 조각 전송 시작</button>
    <section ref={reply} style={{border:"1px solid #aaa",padding:20,marginTop:20,minHeight:120}}>
      {text && <StreamingPocketReply text={text} finished={complete} onProgress={progress} onSettled={settled}/>}
    </section>
    <h2>중간 화면에 실제 표시된 문구</h2><ol>{samples.map((s,i)=><li key={i}>{s.at}ms: {s.text}</li>)}</ol>
    {result && <section><h2>검증 결과</h2>
      <p>완료 전 중간 본문 표시: {result.firstDisplay<result.finalAt?"PASS":"FAIL"}</p>
      <p>완료 시 타이핑 강제 생략 없음: {result.atFinal<result.fullLength && result.typedAfterFinal?"PASS":"FAIL"}</p>
      <p>첫 표시 {result.firstDisplay}ms / 최종 응답 {result.finalAt}ms / 타이핑 완료 {result.settledAt}ms</p>
      <p>최종 응답 시 표시 {result.atFinal} / 전체 {result.fullLength}자 · 문서 {result.visibility} · 모션 감소 {String(result.reducedMotion)}</p>
    </section>}
  </main>
}
createRoot(document.getElementById("root")).render(<Check/> )
