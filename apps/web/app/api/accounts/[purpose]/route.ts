import {accountRequest} from "../../../../lib/accounts";

export async function POST(request:Request,{params}:{params:Promise<{purpose:string}>}) {
  const {purpose}=await params;
  if(process.env.OIDC_ISSUER || process.env.EMAIL_ACCOUNTS!=="true" || !["register","reset"].includes(purpose))
    return Response.json({message:"Operação indisponível."},{status:404});
  const origin=new URL(process.env.NEXTAUTH_URL??request.url).origin;
  if(request.headers.get("origin")!==origin)return Response.json({message:"Origem inválida."},{status:403});
  try {
    const body=await request.text();
    if(body.length>2048)return Response.json({message:"Solicitação muito grande."},{status:413});
    const response=await accountRequest(purpose,{method:"POST",body});
    if(response.status===202)return Response.json({message:"Se o endereço puder receber o link, ele chegará por e-mail."},{status:202});
    const error=await response.json().catch(()=>null);
    return Response.json({message:error?.message??"Serviço de e-mail indisponível."},{status:response.status});
  } catch {
    return Response.json({message:"Serviço de e-mail indisponível."},{status:503});
  }
}
