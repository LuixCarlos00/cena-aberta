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
      <span className="rating-value" aria-live="polite">{active?(active/2).toLocaleString("pt-BR")+" / 5":"Sem nota"}</span>
      {value!==null&&<button type="button" className="rating-clear" onClick={()=>{setPreview(null);onChange(null);}}>Limpar</button>}
      </>:(
        <select className="rating-note-select" aria-label="Minha nota" value={value??""} onChange={event=>{setPreview(null);onChange(event.target.value?Number(event.target.value):null);}}>
          <option value="">Sem nota</option>
          {Array.from({length:10},(_,index)=><option key={index} value={index+1}>{((index+1)/2).toLocaleString("pt-BR")} estrelas</option>)}
        </select>
      )}
      {showPreference&&<button type="button" className="rating-mode" onClick={()=>{setPreview(null);changeMode(mode==="stars"?"list":"stars");}}>
        {mode==="stars"?"Usar lista de notas":"Usar estrelas"}
      </button>}
    </div>
  </fieldset>;
}
