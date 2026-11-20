"use client";
import {useRef,useState} from "react";
type Row={kind:string;name:string;year:string;rating:number|null;review:string;date:string;rewatch:boolean;list:string;occurrence:number;title:string|null};
type Film={id:string;name:string;original:string;year:string;poster:string|null};
type Match={key:string;name:string;year:string;query:string;candidates:Film[];selected:string;status:string};
const key=(r:Row)=>r.name+"\u0000"+r.year;
const normal=(s:string)=>s.normalize("NFKD").replace(/\p{M}/gu,"").toLocaleLowerCase().replace(/[^\p{L}\p{N}]/gu,"");
async function result(response:Response){const data=await response.json();if(!response.ok)throw new Error(data.message??"Não foi possível concluir.");return data;}
export function LetterboxdImporter(){
 const [rows,setRows]=useState<Row[]>([]),[matches,setMatches]=useState<Match[]>([]),[warnings,setWarnings]=useState<string[]>([]),[error,setError]=useState(""),[busy,setBusy]=useState(false),[progress,setProgress]=useState(""),[confirmed,setConfirmed]=useState(false),[complete,setComplete]=useState(false);
 const stopped=useRef(false);
 const [fileName,setFileName]=useState(""),[onlyPending,setOnlyPending]=useState(false);
 function update(k:string,patch:Partial<Match>){setMatches(old=>old.map(m=>m.key===k?{...m,...patch}:m));setConfirmed(false);}
 async function search(m:Match):Promise<Match>{
  const data=await result(await fetch("/api/v1/titles?type=movie&query="+encodeURIComponent(m.query),{cache:"no-store"}));
  const candidates:Film[]=data.items;
  const exact=candidates.filter(f=>f.year===m.year&&[f.name,f.original].some(n=>normal(n)===normal(m.name)));
  return {...m,candidates,selected:exact.length===1?exact[0].id:"",status:exact.length===1?"Correspondência por título e ano":"Revise ou deixe de fora"};
 }
 async function upload(file:File){
  if(file.size>10_000_000){setError("Envie um ZIP de até 10 MB.");return;}
  setFileName(file.name);setBusy(true);setError("");setComplete(false);setRows([]);setMatches([]);setConfirmed(false);stopped.current=false;
  try{
   const form=new FormData();form.set("file",file);
   setProgress("Lendo o ZIP…");const data=await result(await fetch("/api/importar/letterboxd/preview",{method:"POST",body:form}));
   const parsed=data.rows as Row[];setRows(parsed);setWarnings([...data.warnings,data.note]);
   const unique=[...new Map(parsed.map(r=>[key(r),{key:key(r),name:r.name,year:r.year,query:r.name,candidates:[],selected:"",status:"Aguardando busca"} as Match])).values()];
   setMatches(unique);
   for(let i=0;i<unique.length&&!stopped.current;i++){
    setProgress(`Buscando filmes: ${i+1}/${unique.length}`);
    try{const found=await search(unique[i]);setMatches(old=>old.map(m=>m.key===found.key?found:m));}
    catch{update(unique[i].key,{status:"Busca falhou. Tente novamente nesta linha."});}
   }
   setProgress(stopped.current?"Busca pausada. Você pode buscar cada filme abaixo.":"Prévia pronta. Revise as correspondências antes de confirmar.");
  }catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 async function commit(){
  setBusy(true);setError("");let imported=0,skipped=0;
  const selected=new Map(matches.filter(m=>m.selected).map(m=>[m.key,m.selected]));
  const chosen=rows.filter(r=>selected.has(key(r))).map(r=>({...r,title:selected.get(key(r))}));
  try{
   for(let i=0;i<chosen.length;i+=30){
    setProgress(`Salvando ${i+1}–${Math.min(i+30,chosen.length)} de ${chosen.length} registros…`);
    const data=await result(await fetch("/api/importar/letterboxd/commit",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({rows:chosen.slice(i,i+30)})}));
    imported+=data.imported;skipped+=data.skipped;
   }
   setComplete(true);setProgress(`${imported} registros processados; ${skipped} já importados. ${rows.length-chosen.length} registros ficaram de fora.`);
  }catch(e){setError(`${(e as Error).message} ${imported} registros já foram processados nesta tentativa. Pode confirmar novamente: eles serão ignorados.`);}finally{setBusy(false);}
 }
 const selected=matches.filter(m=>m.selected).length;
 return <main className="wrap import-page"><header className="room-header"><a href="/" className="account-brand">Cena Aberta</a><a href="/?aba=profile" className="room-nav-button">Meu perfil</a></header>
 <div className="import-hero"><span className="room-eyebrow">SEU HISTÓRICO, UM NOVO CAPÍTULO</span><div className="import-brand-symbol" aria-hidden="true"><i/><i/><i/><span>→</span><b>CA</b></div><h1>Seu cinema vem com você.</h1><p>Traga seus filmes do Letterboxd e continue sua história no Cena Aberta.</p><ol className="import-steps" aria-label="Etapas da importação"><li className={!rows.length?"current":"done"}>1 <span>Enviar ZIP</span></li><li className={rows.length&&!complete?"current":""}>2 <span>Revisar filmes</span></li><li className={complete?"current":""}>3 <span>Concluir</span></li></ol></div>
 <section className="panel"><h2>1. Escolha seu arquivo</h2><p>Exporte sua conta no <a href="https://letterboxd.com/user/exportdata/" target="_blank" rel="noreferrer">Letterboxd</a> e envie o ZIP. Até 10 MB e 2000 registros.</p><label className={"import-upload "+(busy?"disabled":"")}><svg viewBox="0 0 24 24" width="36" height="36" fill="none" stroke="currentColor" strokeWidth="1.6" aria-hidden="true"><path d="M12 16V3m-5 5 5-5 5 5M4 15v5h16v-5"/></svg><strong>{fileName||"Escolher arquivo ZIP"}</strong><span>{fileName?"Clique para trocar o arquivo":"Selecione o arquivo exportado pelo Letterboxd"}</span><input aria-label="Arquivo ZIP" type="file" accept=".zip,application/zip" disabled={busy} onChange={e=>{const f=e.target.files?.[0];if(f)void upload(f);e.target.value="";}}/><small>ZIP · até 10 MB · até 2.000 registros</small></label><p className="muted">Aceita assistidos, <strong className="profile-section-name">Vou ver</strong>, notas, resenhas, <strong className="profile-section-name">Diário</strong> e <strong className="profile-section-name">Listas</strong>. Notas e resenhas existentes são preservadas; avaliações importadas ficam privadas. Formatação HTML vira texto. O ZIP não fica armazenado.</p></section>
 {error&&<p role="alert" className="message error">{error}</p>}{progress&&<p role="status" className="message">{progress}</p>}
 {busy&&!confirmed&&<button onClick={()=>{stopped.current=true;}}>Parar busca após este filme</button>}
 {!!warnings.length&&<details className="panel"><summary>Avisos da importação ({warnings.length})</summary><ul>{warnings.map((w,i)=><li key={i}>{w}</li>)}</ul></details>}
 {!!matches.length&&!complete&&<section className="panel"><h2>2. Revise os filmes</h2><div className="import-stats"><div><strong>{selected}</strong><span>selecionados</span></div><div><strong>{matches.length-selected}</strong><span>para revisar</span></div><div><strong>{rows.length}</strong><span>registros no ZIP</span></div></div><label className="check"><input type="checkbox" checked={onlyPending} onChange={e=>setOnlyPending(e.target.checked)}/> Mostrar apenas os não selecionados</label><p className="muted">Confira especialmente remakes e títulos traduzidos. Você pode alterar o texto e buscar novamente.</p>
 <div className="import-matches">{matches.filter(m=>!onlyPending||!m.selected).map(m=><article key={m.key} className="import-row"><div className="import-film-heading">{m.candidates.find(f=>f.id===m.selected)?.poster?<img src={m.candidates.find(f=>f.id===m.selected)!.poster!} alt={"Pôster de "+m.name} loading="lazy"/>:<span className="import-poster-placeholder" aria-hidden="true">◈</span>}<div><strong>{m.name}</strong><span className="muted"> {m.year}</span><small className={m.selected?"import-match-ok":"import-match-pending"}>{m.selected?"Selecionado · ":"Revisar · "}{m.status}</small></div></div><div className="import-search"><input aria-label={"Buscar "+m.name} value={m.query} maxLength={100} disabled={busy} onChange={e=>update(m.key,{query:e.target.value})}/><button disabled={busy} onClick={async()=>{setBusy(true);try{update(m.key,await search(m));}catch(e){setError((e as Error).message);}finally{setBusy(false);}}}>Buscar</button></div><label>Filme no Cena Aberta<select disabled={busy} value={m.selected} onChange={e=>update(m.key,{selected:e.target.value})}><option value="">Não importar este filme</option>{m.candidates.map(f=><option key={f.id} value={f.id}>{f.name} ({f.year}) · {f.original}</option>)}</select></label></article>)}</div>
 <details className="panel"><summary>Ver notas, resenhas e datas do arquivo</summary>{rows.map((r,i)=><div className="record" key={i}><strong>{r.name}</strong> · {r.kind}{r.list&&" · "+r.list}{r.rating!==null&&" · "+r.rating/2+"/5"}{r.date&&" · "+r.date}{r.review&&<p className="review-text">{r.review}</p>}</div>)}</details>
 <label className="check"><input type="checkbox" checked={confirmed} disabled={busy} onChange={e=>setConfirmed(e.target.checked)}/> Revisei os filmes selecionados e quero importar estes registros.</label><button className="primary" disabled={busy||!confirmed||selected===0} onClick={()=>void commit()}>{busy?"Aguarde…":"Confirmar importação"}</button></section>}
 {complete&&<section className="panel"><h2>Importação concluída</h2><a href="/?aba=profile" className="room-nav-button">Ver meu perfil</a></section>}
 </main>;
}
