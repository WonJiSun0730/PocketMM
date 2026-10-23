// NDJSON frames may cross UTF-8 and network chunk boundaries.
export async function readPocketChatStream(response, onEvent) {
  if (!response.ok || !response.body) throw new Error("대화를 연결하지 못했어요.")
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ""
  let terminal = false
  function consume(line) {
    if (!line.trim()) return
    const event = JSON.parse(line)
    if (terminal) throw new Error("대화 응답 순서가 올바르지 않아요.")
    if (event.type === "failed") {
      const failure=new Error(event.message)
      failure.requestId=event.requestId
      if(event.reviewFailure)failure.reviewFailure={...event.reviewFailure,diagnosticsSaved:event.diagnosticsSaved!==false}
      throw failure
    }
    if (event.type === "complete") terminal = true
    if (!["started", "delta", "preview", "complete"].includes(event.type)) throw new Error("알 수 없는 대화 응답입니다.")
    onEvent(event)
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      let end
      while ((end = buffer.indexOf("\n")) !== -1) {
        consume(buffer.slice(0, end))
        buffer = buffer.slice(end + 1)
      }
      if (done) break
    }
    consume(buffer)
    if (!terminal) throw new Error("연결이 끊겼어요. 저장된 대화를 확인해 주세요. 자동 재시도하지 않습니다.")
  } finally {
    reader.releaseLock()
  }
}
