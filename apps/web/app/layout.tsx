import type { Metadata } from "next";
import "./style.css";
export const metadata: Metadata = {title:"Cena Aberta",description:"Seu diário de filmes, séries e animes."};
export default function Layout({children}:{children:React.ReactNode}) {
  return <html lang="pt-BR"><body>{children}</body></html>;
}
