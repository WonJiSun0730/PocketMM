import React,{useState} from "react"
import {createRoot} from "react-dom/client"
import ChatFailurePanel from "../src/components/ChatFailurePanel"
import {readPocketChatStream} from "../src/lib/pocketChatStream"
import {mergeChatFailures} from "../src/lib/chatFailures"
import "../src/index.css"
export default function Check() {
  const [error,setError]=useState("");const [records,setRecords]=useState([])
  async function check() {
    const id="11111111-2222-4333-8444-555555555555"
    const event={type:"failed",requestId:id,diagnosticsSaved:true,message:`모의 검사 거절 [CONTINUITY_REJECTED · ${id}]`,reviewFailure:{
      requestId:id,sessionId:55,createdAt:new Date().toISOString(),phase:"CONTINUITY",mode:"REVIEW",approved:false,
      reasons:["검사기가 승인하지 않았습니다.","검사기가 충돌을 보고했습니다."],
      conflicts:["모의 설명: 이전 상태에서 책상 위에 있던 봉투가 이동 근거 없이 가방 안에 등장합니다."],
      candidate:"그는 가방 안에서 봉투를 꺼내 테이블에 놓았다.",truncated:false}}
    const response=new Response(new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode(JSON.stringify(event)+"\n"));controller.close()}}))
    try {await readPocketChatStream(response,()=>{})}
    catch(failure){setError(failure.message);setRecords(current=>mergeChatFailures(current,[failure.reviewFailure],55))}
  }
  return <main style={{padding:24,maxWidth:850,margin:"auto"}}>
    <h1>무료 거절 사유 화면 검증</h1><p>모의 데이터 · API 호출 없음 · 실제 화면 컴포넌트 사용</p>
    <button onClick={check}>모의 거절 결과 표시</button>
    {error && <p role="alert" className="my-3 text-destructive">{error}</p>}
    <ChatFailurePanel records={records} error={error}/>
  </main>
}
createRoot(document.getElementById("root")).render(<Check/> )
