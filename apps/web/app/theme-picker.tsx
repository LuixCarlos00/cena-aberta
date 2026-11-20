"use client";
import {useEffect,useState} from "react";
type Theme="dark"|"light"|"system";
function savedTheme():Theme{try{const value=localStorage.getItem("cena-theme");if(value==="light"||value==="system")return value;}catch{}return "dark";}
export function ThemeSync(){
 useEffect(()=>{const media=matchMedia("(prefers-color-scheme: dark)");const apply=()=>{const theme=savedTheme();document.documentElement.dataset.theme=theme==="system"?(media.matches?"dark":"light"):theme;};apply();media.addEventListener("change",apply);window.addEventListener("cena-theme-change",apply);window.addEventListener("storage",apply);return()=>{media.removeEventListener("change",apply);window.removeEventListener("cena-theme-change",apply);window.removeEventListener("storage",apply);};},[]);
 return null;
}
export function ThemePicker(){
 const [theme,setTheme]=useState<Theme>("dark");
 useEffect(()=>{const sync=()=>setTheme(savedTheme());sync();window.addEventListener("storage",sync);return()=>window.removeEventListener("storage",sync);},[]);
 function choose(value:Theme){setTheme(value);try{localStorage.setItem("cena-theme",value);}catch{}document.documentElement.dataset.theme=value==="system"?(matchMedia("(prefers-color-scheme: dark)").matches?"dark":"light"):value;window.dispatchEvent(new Event("cena-theme-change"));}
 return <div className="theme-picker"><label htmlFor="app-theme">Aparência</label><select id="app-theme" value={theme} onChange={e=>choose(e.target.value as Theme)}><option value="dark">Escuro</option><option value="light">Claro</option><option value="system">Sistema</option></select></div>;
}
