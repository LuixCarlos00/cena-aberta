"use client";
import {useId,useState,useSyncExternalStore} from "react";

const preferenceKey="cenaaberta.rating-control";
let fallbackMode:"stars"|"list"="stars";
function readMode() {
  try{return localStorage.getItem(preferenceKey)==="list"?"list":"stars";}
  catch{return fallbackMode;}
}
function subscribeMode(notify:()=>void) {
  window.addEventListener("storage",notify);
  window.addEventListener(preferenceKey,notify);
  return ()=>{
    window.removeEventListener("storage",notify);
    window.removeEventListener(preferenceKey,notify);
  };
}
function changeMode(mode:"stars"|"list") {
  fallbackMode=mode;
  try{localStorage.setItem(preferenceKey,mode);}catch{/* Keep the preference in this tab. */}
  window.dispatchEvent(new Event(preferenceKey));
}

export function StarRating({value,disabled,onChange,showPreference=false}:{value:number|null;disabled:boolean;onChange:(value:number|null)=>void;showPreference?:boolean}) {
  const name=useId();
  const mode=useSyncExternalStore(subscribeMode,readMode,()=>"stars");
  const [preview,setPreview]=useState<number|null>(null);
  const active=preview??value??0;
  return <fieldset className="star-rating" disabled={disabled}>
    <legend>Minha nota</legend>
    <div className="rating-row">
      {showPreference&&<button type="button" className="rating-mode rating-icon" title={mode==="stars"?"Usar lista de notas":"Usar estrelas"} aria-label={mode==="stars"?"Usar lista de notas":"Usar estrelas"} onClick={()=>{setPreview(null);changeMode(mode==="stars"?"list":"stars");}}>
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true">{mode==="stars"?<path d="M8 6h13M8 12h13M8 18h13M3 6h1M3 12h1M3 18h1"/>:<path d="m12 3 2.8 5.8 6.4.9-4.6 4.5 1.1 6.4L12 17.6l-5.7 3 1.1-6.4-4.6-4.5 6.4-.9Z"/>}</svg>
      </button>}
      <button type="button" className="rating-clear rating-icon" title="Limpar nota" aria-label="Limpar nota" disabled={disabled||value===null} onClick={()=>{setPreview(null);onChange(null);}}><svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" aria-hidden="true"><path d="m14 3 7 7-11 11H6l-4-4L14 3Zm-7 9 7 7m-4 2h12"/></svg></button>

      {mode==="stars"?<>
      <div className="rating-stars" onMouseLeave={()=>setPreview(null)}>
        {Array.from({length:5},(_,index)=>{
          const fill=Math.max(0,Math.min(2,active-index*2))*50;
          return <span className="rating-star" key={index}>
            <span className="star-shape" aria-hidden="true" style={{background:`linear-gradient(90deg, var(--accent) ${fill}%, #798398 ${fill}%)`}}/>
            {[1,2].map(half=>{
              const score=index*2+half;
              return <label key={half} className={"star-target star-half-"+half} onMouseEnter={()=>setPreview(score)}>
                <input type="radio" name={name} value={score} checked={value===score} onChange={()=>{setPreview(null);onChange(score);}}
                  onFocus={()=>setPreview(score)} onBlur={()=>setPreview(null)} aria-label={(score/2).toLocaleString("pt-BR")+" de 5 estrelas"}/>
              </label>;
            })}
          </span>;
        })}
      </div>
      <span className="rating-value" aria-live="polite">{active?(active/2).toLocaleString("pt-BR")+" / 5":""}</span>

      </>:(
        <select className="rating-note-select" aria-label="Minha nota" value={value??""} onChange={event=>{setPreview(null);onChange(event.target.value?Number(event.target.value):null);}}>
          <option value="" aria-label="Nenhuma nota">—</option>
          {Array.from({length:10},(_,index)=><option key={index} value={index+1}>{((index+1)/2).toLocaleString("pt-BR")} estrelas</option>)}
        </select>
      )}

    </div>
  </fieldset>;
}
