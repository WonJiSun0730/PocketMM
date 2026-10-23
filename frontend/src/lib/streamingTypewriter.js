import {roleplaySegments, TYPE_INTERVAL_MS} from "./typedRoleplay.js"

const characters = text => roleplaySegments(text).flatMap(part => part.characters)

// The cursor belongs to the request, not a chunk. Idle network time earns no typing credit.
export function createStreamingTypewriter() {
  let text="", target=[], count=0, lastTick=null, final=false, skipped=false
  const snapshot=()=>({text,count,total:target.length,done:final && count===target.length})
  return {
    update(next,now,finished=false) {
      const nextTarget=characters(next)
      if(!next.startsWith(text)) {
        let common=0
        while(common<target.length && common<nextTarget.length && target[common]===nextTarget[common])common++
        count=Math.min(count,common)
        lastTick=now-TYPE_INTERVAL_MS
      }
      if(count>=target.length)lastTick=now-TYPE_INTERVAL_MS
      text=next;target=nextTarget;count=Math.min(count,target.length);final=finished
      if(skipped)count=target.length
      return this.advance(now)
    },
    advance(now) {
      if(lastTick==null)lastTick=now-TYPE_INTERVAL_MS
      const steps=Math.max(0,Math.floor((now-lastTick)/TYPE_INTERVAL_MS+1e-9))
      count=Math.min(target.length,count+steps)
      lastTick=count===target.length?now:lastTick+steps*TYPE_INTERVAL_MS
      return snapshot()
    },
    skip() {skipped=true;count=target.length;return snapshot()},
    snapshot,
  }
}
