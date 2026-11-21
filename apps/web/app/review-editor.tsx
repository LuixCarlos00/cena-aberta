"use client";
import {useEffect,useId,useRef,useState,type ReactNode} from "react";
import {SpoilerControl} from "./action-icons";
export function ReviewEditor({title,initial,disabled,onSaved,onPending,visibility}:{title:string;initial:{review:string|null;spoiler:boolean;version:number};disabled:boolean;onSaved:()=>Promise<void>;onPending:(value:boolean)=>void;visibility:ReactNode}){
 const inputId=useId();
 const [review,setReview]=useState(initial.review??""),[spoiler,setSpoiler]=useState(initial.spoiler),[saving,setSaving]=useState(false),[error,setError]=useState("");
 const [confirmed,setConfirmed]=useState(false);
 const saved=useRef({review:initial.review??"",spoiler:initial.spoiler,version:initial.version}),pending=useRef(false);
 const dirty=review!==saved.current.review||spoiler!==saved.current.spoiler;
 async function save(){
  if(pending.current||!dirty)return;
  pending.current=true;setConfirmed(false);setSaving(true);setError("");
  try{
   const r=await fetch("/api/v1/me/library/"+title+"/review",{method:"PUT",headers:{"Content-Type":"application/json"},body:JSON.stringify({review,spoiler,version:saved.current.version})});
   const data=await r.json();if(!r.ok)throw Error(data.message||"Não foi possível salvar a resenha.");
   saved.current={review,spoiler,version:data.version};setConfirmed(true);void onSaved().catch(()=>{});
  }catch(e){setError((e as Error).message);}finally{pending.current=false;setSaving(false);}
 }
 useEffect(()=>{onPending(dirty||saving);},[dirty,saving,onPending]);
 useEffect(()=>{const warn=(event:BeforeUnloadEvent)=>{if(dirty||saving){event.preventDefault();event.returnValue="";}};window.addEventListener("beforeunload",warn);return()=>window.removeEventListener("beforeunload",warn);},[dirty,saving]);
 return <div className="form"><label htmlFor={inputId}>Minha resenha</label><textarea id={inputId} name="review" maxLength={3000} value={review} disabled={disabled||saving} onChange={e=>{setConfirmed(false);setReview(e.target.value);setError("");}}/>
 <div className="row"><span className="review-controls"><SpoilerControl initial={spoiler} disabled={disabled||saving} onChange={value=>{setConfirmed(false);setSpoiler(value);setError("");}}/>{visibility}</span></div>
 <button type="button" className="primary" disabled={disabled||saving||!dirty} onClick={()=>void save()}>{saving?"Salvando…":"Salvar resenha"}</button>
 <div role="status" aria-atomic="true" className="review-status">
  {confirmed ? <p className="message"><span aria-hidden="true">✓ </span>Resenha salva.</p> : <small>{saving ? "Salvando resenha…" : dirty ? "Alterações ainda não salvas." : ""}</small>}
 </div>
 {error&&<p className="message error" role="alert">{error} <button type="button" disabled={saving} onClick={()=>void save()}>Tentar novamente</button></p>}
 </div>;
}
