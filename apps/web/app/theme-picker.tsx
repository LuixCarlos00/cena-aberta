"use client";
import {useEffect,useState} from "react";
import {readPreference,writePreference} from "../lib/browser-preferences";
type Theme="dark"|"light"|"system";
function savedTheme():Theme{try{const value=readPreference("cena-theme");if(value==="light"||value==="system")return value;}catch{}return "dark";}
export function ThemeSync(){
 useEffect(()=>{const media=matchMedia("(prefers-color-scheme: dark)");const apply=()=>{const theme=savedTheme();document.documentElement.dataset.theme=theme==="system"?(media.matches?"dark":"light"):theme;};apply();media.addEventListener("change",apply);window.addEventListener("cena-theme-change",apply);window.addEventListener("storage",apply);return()=>{media.removeEventListener("change",apply);window.removeEventListener("cena-theme-change",apply);window.removeEventListener("storage",apply);};},[]);
 return null;
}
export function ThemePicker(){
 const [theme,setTheme]=useState<Theme>("dark");
 useEffect(()=>{const sync=()=>setTheme(savedTheme());sync();window.addEventListener("storage",sync);return()=>window.removeEventListener("storage",sync);},[]);
 function choose(value:Theme){setTheme(value);try{writePreference("cena-theme",value);}catch{}document.documentElement.dataset.theme=value==="system"?(matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"):value;window.dispatchEvent(new Event("cena-theme-change"));}
 const options=[{value:"dark",label:"Escuro"},{value:"light",label:"Claro"},{value:"system",label:"Sistema"}] as const;
 return <fieldset className="theme-picker"><legend>Aparência</legend><div className="theme-options">{options.map(option=><label className="theme-option" key={option.value} title={option.label+(theme===option.value?" · selecionado":"")}><input type="radio" name="appearance" value={option.value} checked={theme===option.value} onChange={()=>choose(option.value)} aria-label={option.label}/><span><svg width="23" height="23" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{option.value==="dark"?<path d="M21 13a9 9 0 0 1-10-10 9 9 0 1 0 10 10Z"/>:option.value==="light"?<><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></>:<><rect x="3" y="4" width="18" height="13" rx="2"/><path d="M8 21h8m-4-4v4"/></>}</svg></span></label>)}</div></fieldset>;
}
