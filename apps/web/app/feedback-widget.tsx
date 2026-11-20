"use client";
import {useEffect,useRef,useState} from "react";
import {usePathname} from "next/navigation";
export function FeedbackWidget({version}:{version:string}){
 const pathname=usePathname();const dialog=useRef<HTMLDialogElement>(null),trigger=useRef<HTMLButtonElement>(null);
 const [access,setAccess]=useState<{admin:boolean}|null>(null),[kind,setKind]=useState("bug"),[description,setDescription]=useState(""),[busy,setBusy]=useState(false),[error,setError]=useState(""),[sent,setSent]=useState(false);
 const id=useRef("");
 useEffect(()=>{let active=true;fetch("/api/v1/feedback/access",{cache:"no-store"}).then(async r=>{if(active)setAccess(r.ok?await r.json():null);}).catch(()=>{if(active)setAccess(null);});return()=>{active=false;};},[pathname]);
 function open(){id.current=crypto.randomUUID();setError("");setSent(false);setDescription("");dialog.current?.showModal();}
 async function send(e:React.FormEvent){e.preventDefault();setBusy(true);setError("");try{const r=await fetch("/api/v1/feedback",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({id:id.current,kind,description,page:pathname,version})});const data=await r.json();if(!r.ok)throw new Error(data.message??"Falha ao enviar.");setSent(true);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 if(!access)return null;
 return <><div className="feedback-dock">{access.admin&&<a className="feedback-admin-link" href="/admin/feedback" aria-label="Abrir painel de feedback">Painel ADM</a>}<button ref={trigger} className="feedback-trigger" onClick={open}><svg viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" aria-hidden="true"><path d="M5 3h14a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H9l-6 4V5a2 2 0 0 1 2-2Z"/><path d="M8 8h8M8 12h5"/></svg><span>Enviar feedback</span></button></div>
 <dialog ref={dialog} className="feedback-dialog" aria-labelledby="feedback-title" onClose={()=>trigger.current?.focus()} onCancel={e=>{if(busy)e.preventDefault();}}>
 <div className="feedback-heading"><h2 id="feedback-title">{sent?"Obrigado pelo feedback!":"Ajude a melhorar o Cena Aberta"}</h2><button type="button" aria-label="Fechar feedback" disabled={busy} onClick={()=>dialog.current?.close()}>×</button></div>
 {sent?<><p role="status">Seu relato foi enviado para a caixa interna dos administradores.</p><button className="primary" onClick={()=>dialog.current?.close()}>Concluir</button></>:<form className="form" onSubmit={send}>
 <p className="muted">Relatos privados, visíveis apenas à administração.</p><fieldset className="feedback-types"><legend>O que você quer reportar?</legend><label><input type="radio" name="feedback-kind" value="bug" checked={kind==="bug"} onChange={()=>setKind("bug")}/> Bug</label><label><input type="radio" name="feedback-kind" value="suggestion" checked={kind==="suggestion"} onChange={()=>setKind("suggestion")}/> Sugestão de melhoria</label></fieldset>
 <label>Descrição<textarea required minLength={10} maxLength={3000} value={description} onChange={e=>setDescription(e.target.value)} placeholder={kind==="bug"?"O que aconteceu?":"O que você gostaria de melhorar e por quê?"}/></label><small>{description.length}/3000 · Não inclua senhas ou outros dados sensíveis.</small>
 <small className="muted">Página: {pathname} · Versão: {version}. Parâmetros e links de confirmação não são enviados.</small>
 {error&&<p role="alert" className="message error">{error}</p>}<button className="primary" disabled={busy||description.trim().length<10}>{busy?"Enviando…":"Enviar feedback"}</button>
 </form>}
 </dialog></>;
}
