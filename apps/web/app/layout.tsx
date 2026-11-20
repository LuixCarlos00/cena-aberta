import type { Metadata } from "next";
import "./style.css";
import {FeedbackWidget} from "./feedback-widget";
import {version} from "../package.json";
export const metadata: Metadata = {title:"Cena Aberta",description:"Seu diário de filmes, séries e animes."};
export default function Layout({children}:{children:React.ReactNode}) {
  return <html lang="pt-BR"><body>{children}<FeedbackWidget version={version}/></body></html>;
}
