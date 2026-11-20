import type { Metadata } from "next";
import "./style.css";
import {ThemePicker} from "./theme-picker";
import {FeedbackWidget} from "./feedback-widget";
import {version} from "../package.json";
export const metadata: Metadata = {title:"Cena Aberta",description:"Seu diário de filmes, séries e animes."};
export default function Layout({children}:{children:React.ReactNode}) {
  return <html lang="pt-BR" suppressHydrationWarning><head><script dangerouslySetInnerHTML={{__html:`try{var t=localStorage.getItem('cena-theme');document.documentElement.dataset.theme=t==='light'?'light':t==='system'?(matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'):'dark'}catch{}`}}/></head><body>{children}<ThemePicker/><FeedbackWidget version={version}/></body></html>;
}
