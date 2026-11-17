import {getServerSession} from "next-auth";
import {redirect,notFound} from "next/navigation";
import {authOptions,emailAccounts} from "../../../lib/auth";
import {App} from "../../app";
export const dynamic="force-dynamic";
export default async function Obra({params}:{params:Promise<{id:string}>}) {
  const session=await getServerSession(authOptions) as {actor?:string}|null;
  if(!session?.actor)redirect("/conta");
  const {id}=await params;
  if(!/^(demo-[a-z0-9-]+|tmdb-(movie|tv)-[0-9]+)$/.test(id))notFound();
  return <App authenticated inviteLogin={Boolean(process.env.OIDC_ISSUER)} emailAccounts={emailAccounts}
    pageTitle={id} showCommunityDemo={process.env.COMMUNITY_DEMO_TITLE===id}/>;
}
