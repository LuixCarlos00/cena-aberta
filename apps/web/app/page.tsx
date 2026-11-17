import {getServerSession} from "next-auth";
import {authOptions,emailAccounts} from "../lib/auth";
import {App} from "./app";
export const dynamic="force-dynamic";
export default async function Page({searchParams}:{searchParams:Promise<{aba?:string;usuario?:string}>}) {
  const query=await searchParams;
  const session=await getServerSession(authOptions) as {actor?:string} | null;
  const initialTab=query.usuario?"profile":["all","movie","tv","anime","profile"].includes(query.aba??"")?query.aba!:"all";
  return <App authenticated={Boolean(session?.actor)} inviteLogin={Boolean(process.env.OIDC_ISSUER)} emailAccounts={emailAccounts} initialTab={initialTab} profileUser={query.usuario}/>;
}
