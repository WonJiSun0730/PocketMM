import { useMemo } from "react"
import { roleplaySegments } from "@/lib/typedRoleplay"
export default function RpText({text,visibleCharacters=Infinity}) {
  const parts=useMemo(()=>roleplaySegments(text),[text])
  let remaining=visibleCharacters
  return <div className="whitespace-pre-wrap text-[15px] leading-7 text-foreground sm:text-base sm:leading-8">
    {parts.map((part,index)=>{
      const count=Math.max(0,Math.min(part.characters.length,remaining));remaining-=count
      const content=part.characters.slice(0,count).join("")
      return <span key={index} className={part.action ? "italic text-muted-foreground" : undefined}>{part.bold ? <strong className="font-bold">{content}</strong> : content}</span>
    })}
  </div>
}
