"use client";
import {useEffect,useState} from "react";
type Theme="dark"|"light"|"system";
export function ThemePicker(){
 const [theme,setTheme]=useState<Theme>("dark"),[ready,setReady]=useState(false);
 useEffect(()=>{try{const saved=localStorage.getItem("cena-theme");if(saved==="dark"||saved==="light"||saved==="system")setTheme(saved);}catch{}setReady(true);},[]);
 useEffect(()=>{if(!ready)return;const media=matchMedia("(prefers-color-scheme: dark)");const apply=()=>{document.documentElement.dataset.theme=theme==="system"?(media.matches?"dark":"light"):theme;};apply();media.addEventListener("change",apply);return()=>media.removeEventListener("change",apply);},[theme,ready]);
 function choose(value:Theme){setTheme(value);try{localStorage.setItem("cena-theme",value);}catch{}}
 return <div className="theme-picker wrap"><label htmlFor="app-theme">Aparência</label><select id="app-theme" value={theme} onChange={e=>choose(e.target.value as Theme)}><option value="dark">Escuro</option><option value="light">Claro</option><option value="system">Sistema</option></select></div>;
}
