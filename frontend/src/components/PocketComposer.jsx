import {useRef} from "react"
import {NotebookPen,Send,LoaderCircle} from "lucide-react"
import {Button} from "@/components/ui/button"
import {Textarea} from "@/components/ui/textarea"
import {wrapAction} from "@/lib/replySuggestions"

export default function PocketComposer({value,onChange,onKeyDown,onSend,disabled,recommendations,onToggle}) {
  const input=useRef(null)
  function focus(start,end=start) {
    input.current?.focus()
    requestAnimationFrame(()=>{input.current?.focus();input.current?.setSelectionRange(start,end)})
  }
  function action() {
    const result=wrapAction(value,input.current?.selectionStart,input.current?.selectionEnd)
    onChange(result.value)
    focus(result.start,result.end)
  }
  return <div className="shrink-0 border-t bg-card p-3 sm:p-4">
    {recommendations.enabled && <div className="mx-auto mb-3 max-w-3xl" aria-label="문장 추천" aria-live="polite">
      {recommendations.status==="LOADING" && <p className="flex items-center gap-2 text-xs text-muted-foreground"><LoaderCircle className="size-3 animate-spin"/>추천 문장 3개를 만드는 중…</p>}
      {recommendations.status==="PENDING" && <p className="text-xs text-muted-foreground">다른 요청에서 추천을 생성 중이거나 이전 생성이 중단됐어요. 추가 호출하지 않습니다.</p>}
      {recommendations.error && <p className="text-xs text-muted-foreground">{recommendations.error}</p>}
      {["READY","PARTIAL"].includes(recommendations.status) && <div className="grid gap-1.5">
        {recommendations.suggestions.map((text,index)=><button key={index} type="button" disabled={disabled}
          title="입력창에 넣기" onClick={()=>{onChange(text);focus(text.length)}}
          className="rounded-md border bg-background px-3 py-2 text-left text-sm leading-6 hover:bg-muted disabled:opacity-50">{text}</button>)}
      </div>}
    </div>}
    <div className="mx-auto flex max-w-3xl items-end gap-2">
      <Button type="button" variant="ghost" size="icon" className="size-11 shrink-0" disabled={disabled}
        onPointerDown={event=>event.preventDefault()} onClick={action} aria-label="행동 지문 별표 넣기" title="선택한 글을 *로 감싸기">
        <span aria-hidden="true" className="translate-y-1 text-3xl leading-none">*</span>
      </Button>
      <Button type="button" variant={recommendations.enabled?"secondary":"ghost"} size="icon" className="size-11 shrink-0"
        onClick={onToggle} aria-pressed={recommendations.enabled} aria-label={recommendations.enabled?"문장 추천 끄기":"문장 추천 켜기"}
        title="켜면 새 지문마다 Luna로 추천 3개를 만들어요">
        <NotebookPen className="size-5"/>
      </Button>
      <Textarea ref={input} id="pocket-chat-input" name="message" autoComplete="off" value={value} onChange={event=>onChange(event.target.value)} onKeyDown={onKeyDown} enterKeyHint="enter"
        placeholder="행동이나 대사를 입력해 주세요." aria-label="행동이나 대사" disabled={disabled} rows={1}
        className="max-h-36 min-h-11 min-w-0 resize-none text-base"/>
      <Button type="button" size="icon" className="size-11 shrink-0" disabled={disabled}
        onClick={onSend} aria-label="메시지 보내기"><Send className="size-4"/></Button>
    </div>
    <p className="mx-auto mt-2 max-w-3xl text-center text-xs text-muted-foreground">PC: Enter 전송 · Shift + Enter 줄바꿈 / 모바일: Enter 줄바꿈 · 버튼으로 전송</p>
  </div>
}
