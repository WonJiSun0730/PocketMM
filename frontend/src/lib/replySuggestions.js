// One request per scene, including failed/in-flight requests. Toggling never retries.
export function createReplySuggestions(load) {
  const cache=new Map(),listeners=new Set()
  let scene=null,state={enabled:false,key:null,status:"IDLE",suggestions:[],error:""}
  const emit=patch=>{state={...state,...patch};listeners.forEach(listener=>listener())}
  function show() {
    if(!state.enabled || !scene) {
      emit({key:scene?.key??null,status:"IDLE",suggestions:[],error:""})
      return
    }
    const current=scene
    let entry=cache.get(current.key)
    if(!entry) {
      entry={status:"LOADING",suggestions:[],error:""}
      cache.set(current.key,entry)
      Promise.resolve().then(()=>{
        if(!state.enabled || scene?.key!==current.key){cache.delete(current.key);return {status:"IDLE",suggestions:[]}}
        return load(current.source)
      }).then(result=>{
        if(["READY","PARTIAL"].includes(result.status) && (!Array.isArray(result.suggestions) || result.suggestions.length<1 || result.suggestions.length>3))throw new Error("추천 응답을 확인하지 못했어요.")
        entry.status=result.status
        entry.suggestions=result.suggestions??[]
        entry.error=result.status==="FAILED"?`${result.message??"추천을 만들지 못했어요."} [${result.errorCode??"UNKNOWN"}]`:result.message??""
      }).catch(()=>{
        entry.status="FAILED"
        entry.error="추천을 불러오지 못했어요. 같은 지문에서는 자동 재시도하지 않습니다."
      }).finally(()=>{
        if(state.enabled && scene?.key===current.key && cache.get(current.key)===entry)emit({key:current.key,...entry})
      })
    }
    emit({key:current.key,...entry})
  }
  return {
    subscribe(listener){listeners.add(listener);return ()=>listeners.delete(listener)},
    getSnapshot:()=>state,
    setScene(next){if(scene?.key===next?.key)return;scene=next;show()},
    toggle(){emit({enabled:!state.enabled});show()},
  }
}

export function wrapAction(value,start,end=start) {
  start=Math.max(0,Math.min(value.length,start??value.length))
  end=Math.max(start,Math.min(value.length,end??start))
  return {value:value.slice(0,start)+"*"+value.slice(start,end)+"*"+value.slice(end),start:start+1,end:end+1}
}
