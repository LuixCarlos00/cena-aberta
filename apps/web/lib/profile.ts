import {redirect} from "next/navigation";

export async function profileState(actor:string) {
  const secret=process.env.SOCIAL_INTERNAL_SECRET;
  if(!secret)throw new Error("Serviço de perfil indisponível.");
  const result=await fetch((process.env.SOCIAL_URL??"http://127.0.0.1:8182")+"/me",{
    headers:{"X-Internal-Key":secret,"X-Actor":actor},cache:"no-store",signal:AbortSignal.timeout(6500),
  });
  if(!result.ok)throw new Error("Não foi possível verificar seu perfil. Tente novamente.");
  return await result.json() as {needsProfile?:boolean};
}
export async function requireProfile(actor:string,next="/") {
  if((await profileState(actor)).needsProfile)redirect("/conta/perfil?next="+encodeURIComponent(next));
}
