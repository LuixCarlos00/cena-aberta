import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";
import { setTimeout as delay } from "node:timers/promises";
import { createServer, request as httpRequest } from "node:http";
import { createServer as createHttpsServer } from "node:https";
import { readFileSync } from "node:fs";
import { isIPv4 } from "node:net";

const catalog = "http://127.0.0.1:19181";
const social = "http://127.0.0.1:19182";
const previewHost=process.env.SMOKE_PREVIEW_HOST;
if(previewHost && (process.env.SMOKE_PREVIEW!=="true" ||
    !isIPv4(previewHost) || !/^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)/.test(previewHost))) {
  throw new Error("A prévia em rede exige SMOKE_PREVIEW=true e um IPv4 privado da rede local.");
}
const webHost=previewHost??"127.0.0.1";
const tlsCert=process.env.SMOKE_TLS_CERT;
const tlsKey=process.env.SMOKE_TLS_KEY;
const useTls=Boolean(tlsCert||tlsKey);
if(useTls&&(!tlsCert||!tlsKey||process.env.SMOKE_PREVIEW!=="true")) {
  throw new Error("Informe SMOKE_TLS_CERT e SMOKE_TLS_KEY com SMOKE_PREVIEW=true.");
}
const web = (useTls?"https://":"http://")+webHost+(useTls?":19443":":19180");
const tlsPreview=useTls?createHttpsServer({cert:readFileSync(tlsCert),key:readFileSync(tlsKey)},(req,res)=>{
  const upstream=httpRequest({
    hostname:"127.0.0.1",port:19180,path:req.url,method:req.method,
    headers:{...req.headers,"x-forwarded-host":new URL(web).host,"x-forwarded-proto":"https","x-forwarded-port":"19443"},
  },response=>{
    res.writeHead(response.statusCode,response.headers);
    response.pipe(res);
  });
  upstream.on("error",()=>{
    if(res.headersSent)res.destroy();
    else {res.writeHead(502);res.end("Prévia temporariamente indisponível.");}
  });
  req.on("aborted",()=>upstream.destroy());
  req.pipe(upstream);
}):null;
const email="conta-"+randomUUID()+"@example.test";
const key = randomUUID();
const actor = "demo_smoke_user";
const title = "demo-lua-de-papel";
const children = [];
const mockCalls=[];
const mock=createServer((req,res)=>{
  const url=new URL(req.url,"http://localhost");
  mockCalls.push(url);
  res.setHeader("Content-Type","application/json");
  if(req.headers.authorization!=="Bearer fake-smoke-token" || url.searchParams.get("language")!=="pt-BR"){
    res.statusCode=401;res.end("{}");return;
  }
  if(url.searchParams.get("query")==="limite"){res.statusCode=429;res.end("{}");return;}
  const film={id:777,title:"Filme simulado",original_title:"Mock Movie",release_date:"2024-01-01",original_language:"pt",overview:"Sinopse",poster_path:"/example.jpg"};
  const series={id:777,name:"Série simulada",original_language:"ja",first_air_date:"2025-01-01",genre_ids:[16]};
  if(url.pathname==="/3/search/movie") res.end(JSON.stringify({results:[film],total_pages:2}));
  else if(url.pathname==="/3/discover/movie")res.end(JSON.stringify({results:[],total_pages:1}));
  else if(url.pathname==="/3/discover/tv")res.end(JSON.stringify({results:[series],total_pages:1}));
  else if(url.pathname==="/3/movie/777")res.end(JSON.stringify({...film,runtime:124,genres:[{name:"Drama"}],credits:{crew:[{job:"Director",name:"Diretora Exemplo"}],cast:[{name:"Artista Exemplo"}]}}));
  else if(url.pathname==="/3/tv/777")res.end(JSON.stringify({...series,number_of_seasons:2,number_of_episodes:24,created_by:[{name:"Criadora Exemplo"}]}));
  else {res.statusCode=404;res.end("{}");}
});
function start(command, args, env, cwd) {
  const child = spawn(command,args,{cwd,env:{...process.env,...env},stdio:["ignore","pipe","pipe"]});
  children.push(child);
  child.stdout.on("data",chunk=>{child.log=(child.log??"")+chunk;});
  child.stderr.on("data",chunk=>{child.log=(child.log??"")+chunk;});
  child.on("error",error=>{child.log=String(error);});
  return child;
}
async function ready(url,child) {
  for(let i=0;i<100;i++){
    if(child.exitCode!==null)break;
    if(await fetch(url).then(r=>r.ok,()=>false))return;
    await delay(400);
  }
  throw new Error("Servidor indisponível: "+url+"\n"+(child.log??""));
}
async function request(base,path,{method="GET",body,user=actor,idem,expected=200,authorized=true}={}) {
  const headers=authorized?{"X-Internal-Key":key,"X-Actor":user}:{};
  if(body!==undefined)headers["Content-Type"]="application/json";
  if(idem)headers["Idempotency-Key"]=idem;
  const response=await fetch(base+path,{method,headers,body:body===undefined?undefined:JSON.stringify(body)});
  const text=await response.text();
  assert.equal(response.status,expected,method+" "+path+" "+text);
  return text?JSON.parse(text):null;
}
try {
  const db=name=>"jdbc:h2:mem:"+name+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
  const database=(name)=>({
    DATABASE_URL:previewHost||useTls?db(name):process.env["SMOKE_"+name.toUpperCase()+"_JDBC"]??db(name),
    DATABASE_USER:previewHost||useTls?"sa":process.env.SMOKE_DB_USER??"sa",
    DATABASE_PASSWORD:previewHost||useTls?"":process.env.SMOKE_DB_PASSWORD??"",
    BIND_ADDRESS:"127.0.0.1",
  });
  const cat=start("java",["-jar","services/catalog/target/catalog-0.1.0-alpha.jar"],{...database("catalog"),PORT:"19181",INTERNAL_SECRET:key,TMDB_TOKEN:""});
  const soc=start("java",["-jar","services/social/target/social-0.1.0-alpha.jar"],{...database("social"),PORT:"19182",INTERNAL_SECRET:key,CATALOG_INTERNAL_SECRET:key,CATALOG_URL:catalog,INVITE_ONLY:"true",INVITED_EMAILS:email.toUpperCase(),EMAIL_MODE:"log",FRONTEND_URL:"http://127.0.0.1:19180"});
  await Promise.all([ready(catalog+"/health",cat),ready(social+"/health",soc)]);
  await request(catalog,"/titles",{authorized:false,expected:401});
  const anime=await request(catalog,"/titles?type=anime");
  assert.deepEqual(anime.items.map(item=>item.format).sort(),["movie","tv"]);
  assert.equal((await request(catalog,"/titles?query=mare")).items[0].id,"demo-mare-alta");
  await new Promise((resolve,reject)=>{mock.once("error",reject);mock.listen(0,"127.0.0.1",resolve);});
  const realMode="http://127.0.0.1:19183";
  const tmdb=start("java",["-jar","services/catalog/target/catalog-0.1.0-alpha.jar",
    "--tmdb.api-base=http://127.0.0.1:"+mock.address().port+"/3"],{...database("catalog"),PORT:"19183",INTERNAL_SECRET:key,TMDB_TOKEN:"fake-smoke-token"});
  await ready(realMode+"/health",tmdb);
  const search=await request(realMode,"/titles?type=movie&query=Cidade%20de%20Deus");
  assert.equal(search.demo,false);
  assert.equal(search.items[0].id,"tmdb-movie-777");
  assert.equal(search.hasMore,true);
  assert.equal(mockCalls.at(-1).searchParams.get("query"),"Cidade de Deus");
  const externalAnime=await request(realMode,"/titles?type=anime");
  assert.equal(externalAnime.items[0].id,"tmdb-tv-777");
  assert.equal(mockCalls.at(-1).searchParams.get("with_genres"),"16");
  const film=await request(realMode,"/titles/tmdb-movie-777");
  assert.match(film.poster,/image\.tmdb\.org/);
  assert.equal(mockCalls.at(-1).searchParams.get("append_to_response"),"credits");
  assert.deepEqual(film.details,{genres:["Drama"],runtime:124,seasons:null,episodes:null,directors:["Diretora Exemplo"],creators:[],cast:["Artista Exemplo"]});
  const seriesDetail=await request(realMode,"/titles/tmdb-tv-777");
  assert.equal(seriesDetail.details.episodes,24);
  assert.equal(seriesDetail.details.seasons,2);
  assert.deepEqual(seriesDetail.details.creators,["Criadora Exemplo"]);
  const calls=mockCalls.length;
  assert.deepEqual(await request(realMode,"/titles/tmdb-movie-777"),film);
  assert.equal(mockCalls.length,calls);
  await request(realMode,"/titles?type=movie&query=limite",{expected:503});
  console.log("TMDB simulado verificado: token, PT-BR, busca, anime, detalhes, persistência e limite de consultas.");
  const avatar="data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAxNDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjL/wAARCAAEAAQDASIAAhEBAxEB/8QAFQABAQAAAAAAAAAAAAAAAAAAAAf/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/8QAFQEBAQAAAAAAAAAAAAAAAAAABgf/xAAUEQEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIRAxEAPwChAI8Tv//Z";
  const profile=(user,username,body,expected=200)=>request(social,"/me/profile",{user,method:"PUT",body:{username,name:"Testador",bio:"",...body},expected});
  await profile(actor,"testador",{},400);
  await profile(actor,"testador",{cpf:"000.000.000-00"},400);
  await profile(actor,"testador",{cpf:"529.982.247-25",avatar:"data:image/jpeg;base64,YQ=="},400);
  await profile(actor,"testador",{cpf:"529.982.247-25",avatar});
  await profile("demo_second_user","segundo",{cpf:"52998224725"},409);
  await profile("demo_second_user","segundo",{cpf:"11144477735"});
  const episodePath="/me/episodes/demo-mare-alta/1";
  assert.equal((await request(catalog,"/titles/demo-mare-alta/seasons/1"))[0].number,1);
  await request(social,"/me/library/demo-mare-alta/interest",{method:"PUT",body:{value:true}});
  await request(social,episodePath+"/1",{method:"PUT",body:{rating:7}});
  const ratedSeries=(await request(social,"/me")).library.find(item=>item.title==="demo-mare-alta");
  assert.equal(ratedSeries.watched,true);
  assert.equal(ratedSeries.interest,false);
  assert.equal((await request(social,episodePath))[0].rating,7);
  assert.deepEqual(await request(social,episodePath,{user:"demo_second_user"}),[]);
  const seriesCommunity="/titles/demo-mare-alta/community";
  const seriesVisibility="/me/library/demo-mare-alta/visibility";
  assert.equal((await request(social,seriesCommunity)).average,null);
  await request(social,seriesVisibility,{method:"PUT",body:{value:true}});
  await request(social,episodePath+"/2",{method:"PUT",body:{rating:9}});
  await request(social,episodePath+"/1",{method:"PUT",user:"demo_second_user",body:{rating:10}});
  assert.deepEqual(await request(social,seriesCommunity),{rating_count:1,episode_rating_count:2,average:4,seasons:[{season:1,average:4}],reviews:[]});
  await request(social,seriesVisibility,{method:"PUT",user:"demo_second_user",body:{value:true}});
  const seriesStats=await request(social,seriesCommunity);
  assert.equal(seriesStats.rating_count,2);
  assert.equal(seriesStats.episode_rating_count,3);
  assert.ok(Math.abs(seriesStats.average-13/3)<0.0001);
  const laterEpisode="/me/episodes/demo-mare-alta/2/1";
  await request(social,laterEpisode,{method:"PUT",body:{rating:2}});
  const twoSeasons=await request(social,seriesCommunity);
  assert.ok(Math.abs(twoSeasons.average-(13/3+1)/2)<0.0001);
  assert.equal(twoSeasons.rating_count,2);
  assert.equal(twoSeasons.seasons[1].average,1);
  assert.deepEqual(await request(social,"/me/episodes/demo-mare-alta"),[{season:1,average:4},{season:2,average:1}]);
  await request(social,"/me/library/demo-mare-alta/rating",{method:"PUT",body:{rating:10},expected:400});
  await request(social,laterEpisode,{method:"PUT",body:{rating:null}});
  await request(social,seriesVisibility,{method:"PUT",body:{value:false}});
  await request(social,episodePath+"/2",{method:"PUT",body:{rating:null}});
  await request(social,episodePath+"/1",{method:"PUT",body:{rating:11},expected:400});
  await request(social,episodePath+"/999",{method:"PUT",body:{rating:5},expected:404});
  await request(social,episodePath+"/1",{method:"PUT",body:{rating:null}});
  assert.deepEqual(await request(social,episodePath),[]);
  await request(social,"/me/library/"+title+"/interest",{method:"PUT",body:{value:true}});
  const change=(action,body,expected=200)=>request(social,"/me/library/"+title+"/"+action,{method:"PUT",body,expected});
  assert.equal((await change("rating",{rating:7})).interest,false);
  await change("interest",{value:true},409);
  await change("watched",{value:false});
  await change("interest",{value:true});
  const ratedAgain=await change("rating",{rating:8});
  assert.equal(ratedAgain.watched,true);
  assert.equal(ratedAgain.interest,false);
  await change("rating",{rating:11},400);
  const rewatch=await change("rewatching",{value:true});
  assert.equal(rewatch.rewatching,true);
  assert.equal(rewatch.watched,true);
  assert.equal(rewatch.interest,false);
  assert.equal((await change("rewatching",{value:false})).rewatching,false);
  const initialRanking=await request(social,"/me/ranking");
  assert.deepEqual(initialRanking.items,[]);
  const ranked=await request(social,"/me/ranking",{method:"PUT",body:{version:0,items:[{title,tier:0}]}});
  assert.deepEqual(await request(social,"/me/ranking",{method:"PUT",body:{version:0,items:[{title,tier:0}]}}),ranked);
  assert.equal(ranked.items[0].title,title);
  await request(social,"/me/ranking",{method:"PUT",body:{version:0,items:[]},expected:409});
  await request(social,"/me/ranking",{method:"PUT",body:{version:ranked.version,items:[{title,tier:8}]},expected:400});
  await request(social,"/me/library/demo-mare-alta/watched",{method:"PUT",body:{value:true}});
  const seriesRank="/me/ranking/demo-mare-alta";
  const mixed=await request(social,seriesRank,{method:"PUT",body:{tier:0}});
  assert.deepEqual(mixed.items,[{title,tier:0},{title:"demo-mare-alta",tier:0}]);
  const reordered=await request(social,"/me/ranking",{method:"PUT",body:{version:mixed.version,items:[...mixed.items].reverse()}});
  assert.deepEqual(reordered.items,[...mixed.items].reverse());
  await request(social,seriesRank,{method:"PUT",body:{tier:-1}});
  assert.deepEqual((await request(social,"/me/ranking")).items,[{title,tier:0}]);
  assert.deepEqual((await request(social,"/me/ranking",{user:"demo_second_user"})).items,[]);
  await change("watched",{value:false});
  assert.deepEqual((await request(social,"/me/ranking")).items,[]);
  await change("watched",{value:true});
  const viewing={method:"POST",body:{title,date:"2024-01-01",rewatch:true},idem:"viewing-test-1"};
  await request(social,"/me/viewings",{...viewing,expected:409});
  await change("review",{version:0,review:"   ",spoiler:false});
  assert.deepEqual((await request(social,"/me")).viewings,[]);
  const savedReview=await change("review",{version:1,review:"Final fictício",spoiler:true});
  assert.deepEqual(await change("review",{version:1,review:"Final fictício",spoiler:true}),savedReview);
  await change("review",{version:0,review:"Sobrescrita",spoiler:false},409);
  await change("review",{version:2,review:"Final fictício revisado",spoiler:true});
  const communityPath="/titles/"+title+"/community";
  assert.deepEqual(await request(social,communityPath),{rating_count:0,average:null,reviews:[]});
  const privateProfile=await request(social,"/profiles/testador",{user:"demo_second_user"});
  const privateEntry=privateProfile.library.find(item=>item.title===title);
  assert.equal(privateEntry.rating,null);
  assert.equal(privateEntry.review,null);
  await change("visibility",{value:true});
  const community=await request(social,communityPath,{user:"demo_second_user"});
  assert.equal(community.average,4);
  assert.equal(community.rating_count,1);
  assert.equal(community.reviews[0].spoiler,true);
  assert.equal((await request(social,"/titles/"+title+"/reviews")).length,1);
  await change("visibility",{value:false});
  assert.deepEqual(await request(social,communityPath),{rating_count:0,average:null,reviews:[]});
  const [initialViewing,...extraViewings]=(await request(social,"/me")).viewings;
  assert.equal(extraViewings.length,0);
  assert.equal(initialViewing.title,title);
  assert.equal(initialViewing.rewatch,false);
  assert.ok(Date.now()-Date.parse(initialViewing.watched_at)<60_000);
  await change("rewatching",{value:true});
  await request(social,"/me/viewings",{...viewing,body:{...viewing.body,rewatch:false},expected:409});
  const first=await request(social,"/me/viewings",viewing);
  assert.deepEqual(await request(social,"/me/viewings",viewing),first);
  await request(social,"/me/viewings",{...viewing,body:{...viewing.body,date:"2024-01-02"},expected:409});
  const watchedAt="2024-01-02T01:15:00Z";
  await request(social,"/me/viewings",{...viewing,body:{title,watchedAt,rewatch:true},idem:"viewing-test-2"});
  await request(social,"/me/viewings/"+first.id,{method:"DELETE",user:"demo_second_user",expected:404});
  await request(social,"/me/viewings/"+first.id,{method:"DELETE"});
  const list=await request(social,"/me/lists",{method:"POST",idem:"list-test-1",body:{name:"Minha lista",items:[title,"demo-mare-alta"]}});
  const updated=["demo-mare-alta",title];
  await request(social,"/me/lists/"+list.id,{method:"PUT",body:{name:"Minha lista",items:updated,version:0}});
  const me=await request(social,"/me");
  assert.equal(me.viewings.length,2);
  assert.equal(me.viewings[0].id,initialViewing.id);
  assert.equal(me.viewings[1].watched_on,"2024-01-01");
  assert.equal(new Date(me.viewings[1].watched_at).toISOString(),new Date(watchedAt).toISOString());
  assert.equal(me.viewings[1].rewatch,true);
  assert.equal(me.library[0].rating,8);
  assert.deepEqual(me.lists[0].items,updated);
  const publicProfile=await request(social,"/profiles/testador",{user:"demo_second_user"});
  assert.deepEqual(publicProfile.viewings,[]);
  assert.match(publicProfile.profile.avatar,/^data:image\/jpeg;base64,/);
  assert.equal("cpf" in publicProfile.profile,false);
  assert.equal("cpf" in (await request(social,"/me")).profile,false);
  await profile(actor,"testador",{avatar:"preset:cinema"});
  assert.equal((await request(social,"/profiles/testador",{user:"demo_second_user"})).profile.avatar,"preset:cinema");
  await profile(actor,"testador",{avatar:"preset:inexistente"},400);
  await profile(actor,"testador",{avatar:""});
  assert.equal((await request(social,"/me")).profile.avatar,"");
  assert.equal("interest" in publicProfile.library[0],false);
  console.log("APIs verificadas: autenticação interna, catálogo, interesse, notas, resenhas, diário, idempotência, propriedade e listas.");

  const app=start(process.execPath,["node_modules/next/dist/bin/next","start","--port","19180","--hostname",useTls?"127.0.0.1":webHost],{
    CATALOG_URL:catalog,SOCIAL_URL:social,NEXTAUTH_URL:web,NEXTAUTH_SECRET:randomUUID(),
    OIDC_ISSUER:"",EMAIL_ACCOUNTS:"true",SOCIAL_INTERNAL_SECRET:key,CATALOG_INTERNAL_SECRET:key,
  },"apps/web");
  if(tlsPreview)await new Promise((resolve,reject)=>{
    tlsPreview.once("error",reject);
    tlsPreview.listen(19443,webHost,resolve);
  });
  await ready(web,app);
  const landing=await (await fetch(web)).text();
  assert.match(landing,/Seu diário de/);
  assert.match(landing,/Criar conta/);
  assert.match(landing,/Monte seu diário/);
  assert.doesNotMatch(landing,/Experimentar demonstração/);
  const providers=await (await fetch(web+"/api/auth/providers")).json();
  assert.deepEqual(Object.keys(providers),["email-account"]);
  for(const path of ["me","titles?type=anime"]) {
    assert.equal((await fetch(web+"/api/v1/"+path)).status,401);
  }

  const password=randomUUID()+"!Aa1";
  const newPassword=randomUUID()+"!Bb2";
  async function emailSignIn(fields,{rawCookie=false}={}) {
    const csrf=await fetch(web+"/api/auth/csrf");
    const csrfCookie=csrf.headers.getSetCookie().map(value=>value.split(";")[0]).join("; ");
    const {csrfToken}=await csrf.json();
    const response=await fetch(web+"/api/auth/callback/email-account",{method:"POST",redirect:"manual",
      headers:{Cookie:csrfCookie,"Content-Type":"application/x-www-form-urlencoded"},body:new URLSearchParams({csrfToken,callbackUrl:web,json:"true",...fields})});
    const cookie=response.headers.getSetCookie().find(value=>value.includes("session-token="));
    return rawCookie?cookie:cookie?.split(";")[0];
  }
  async function link(purpose) {
    const response=await fetch(web+"/api/accounts/"+purpose,{method:"POST",headers:{Origin:web,"Content-Type":"application/json"},body:JSON.stringify({email})});
    assert.equal(response.status,202);
    for(let i=0;i<30;i++){
      const match=(soc.log??"").match(new RegExp("#"+purpose+"=([A-Za-z0-9_-]{43})"));
      if(match)return match[1];
      await delay(100);
    }
    assert.fail("Link de e-mail de teste não gerado.");
  }
  assert.match(await (await fetch(web)).text(),/Criar conta/);
  const outsiderRequest=await fetch(web+"/api/accounts/register",{method:"POST",headers:{Origin:web,"Content-Type":"application/json"},body:JSON.stringify({email:"outsider@example.test"})});
  assert.equal(outsiderRequest.status,202);
  const deniedMessage=await outsiderRequest.json();
  assert.equal((soc.log??"").includes("#register="),false,"Endereço não convidado recebeu link.");
  const token=await link("register");
  const repeated=await fetch(web+"/api/accounts/register",{method:"POST",headers:{Origin:web,"Content-Type":"application/json"},body:JSON.stringify({email})});
  assert.equal(repeated.status,202);
  assert.deepEqual(await repeated.json(),deniedMessage,"Resposta revela a lista de convidados.");
  assert.equal(await emailSignIn({email,password}),undefined);
  assert.equal(await emailSignIn({purpose:"register",token,password:"curta"}),undefined);
  const account=await emailSignIn({purpose:"register",token,password});
  assert.ok(account,"Sessão após confirmação não criada.");
  assert.equal(await emailSignIn({purpose:"register",token,password}),undefined);
  const accountHeaders={Cookie:account,Origin:web,"Content-Type":"application/json"};
  const ownProfile=await fetch(web+"/api/v1/me/profile",{method:"PUT",headers:accountHeaders,body:JSON.stringify({username:"conta_teste",name:"Conta teste",cpf:"12345678909",avatar})});
  assert.equal(ownProfile.status,200,await ownProfile.text());
  const result=await fetch(web+"/api/v1/titles?type=anime",{headers:accountHeaders});
  assert.equal(result.status,200);
  assert.equal((await result.json()).items.length,2);
  const denied=await fetch(web+"/api/v1/me/profile",{method:"PUT",headers:{...accountHeaders,Origin:"https://example.com"},body:"{}"});
  assert.equal(denied.status,403);
  const temporaryCookie=await emailSignIn({email,password},{rawCookie:true});
  assert.match(temporaryCookie,/HttpOnly/i);
  assert.doesNotMatch(temporaryCookie,/;\s*(?:Expires|Max-Age)=/i);
  const temporary=await fetch(web+"/api/auth/session",{headers:{Cookie:temporaryCookie.split(";")[0]}});
  assert.doesNotMatch(temporary.headers.getSetCookie().find(value=>value.includes("session-token=")),/;\s*(?:Expires|Max-Age)=/i);
  const temporaryAge=Date.parse((await temporary.json()).expires)-Date.now();
  assert.ok(temporaryAge>7*60*60*1000 && temporaryAge<=8*60*60*1000);

  const persistentCookie=await emailSignIn({email,password,rememberMe:"true"},{rawCookie:true});
  assert.match(persistentCookie,/HttpOnly/i);
  const expires=Date.parse(persistentCookie.match(/; Expires=([^;]+)/i)[1]);
  const persistentAge=expires-Date.now();
  assert.ok(persistentAge>29*24*60*60*1000 && persistentAge<=30*24*60*60*1000);
  const persistentHeaders={Cookie:persistentCookie.split(";")[0]};
  const persistent=await fetch(web+"/api/auth/session",{headers:persistentHeaders});
  assert.equal(Date.parse((await persistent.json()).expires),expires);
  const refreshed=persistent.headers.getSetCookie().find(value=>value.includes("session-token="));
  assert.equal(Date.parse(refreshed.match(/; Expires=([^;]+)/i)[1]),expires);
  const reset=await link("reset");
  const updatedSession=await emailSignIn({purpose:"reset",token:reset,password:newPassword});
  assert.ok(updatedSession);
  assert.equal((await fetch(web+"/api/v1/me",{headers:accountHeaders})).status,401);
  assert.equal((await fetch(web+"/api/v1/me",{headers:persistentHeaders})).status,401);
  assert.equal(await emailSignIn({email,password}),undefined);
  const signed=await emailSignIn({email:email.toUpperCase(),password:newPassword});
  assert.ok(signed);
  const preserved=await fetch(web+"/api/v1/me",{headers:{Cookie:signed}});
  assert.equal((await preserved.json()).profile.username,"conta_teste");
  assert.equal((await fetch(web+"/obra/demo-lua-de-papel",{redirect:"manual"})).status,307);
  const dedicated=await fetch(web+"/obra/demo-lua-de-papel",{headers:{Cookie:signed}});
  assert.equal(dedicated.status,200);
  assert.match(await dedicated.text(),/Carregando detalhes da obra/);
  const finalHeaders={Cookie:signed,Origin:web,"Content-Type":"application/json"};
  for(const [id,action,value] of [["demo-lua-de-papel","watched",true],["demo-ultimo-rolo","watched",true],["demo-fim-da-linha","interest",true]]) {
    const response=await fetch(web+"/api/v1/me/library/"+id+"/"+action,{method:"PUT",headers:finalHeaders,body:JSON.stringify({value})});
    assert.equal(response.status,200);
  }
  const savedRanking=await fetch(web+"/api/v1/me/ranking",{method:"PUT",headers:finalHeaders,body:JSON.stringify({version:0,items:[{title:"demo-lua-de-papel",tier:0}]})});
  assert.equal(savedRanking.status,200);
  assert.equal((await fetch(web+"/ranking",{redirect:"manual"})).status,307);
  const rankingPage=await fetch(web+"/ranking",{headers:finalHeaders});
  assert.equal(rankingPage.status,200);
  const updatedRank=await fetch(web+"/api/v1/me/ranking/demo-lua-de-papel",{
    method:"PUT",headers:finalHeaders,body:JSON.stringify({tier:1})
  });
  assert.equal(updatedRank.status,200);
  assert.deepEqual((await updatedRank.json()).items,[{title:"demo-lua-de-papel",tier:1}]);
  console.log("Página individual e ranking por usuário com persistência e controle de versão verificados.");
  console.log("Cadastro por e-mail, perfil com CPF privado e foto, link de uso único e recuperação verificados.");
  if(process.env.SMOKE_PREVIEW==="true") {
    if(previewHost||useTls) {
      const watched=await fetch(web+"/api/v1/me/library/demo-fim-da-linha/watched",{
        method:"PUT",headers:finalHeaders,body:JSON.stringify({value:true})
      });
      assert.equal(watched.status,200);
      for(const id of ["demo-ultimo-rolo","demo-fim-da-linha"]) {
        const ranked=await fetch(web+"/api/v1/me/ranking/"+id,{
          method:"PUT",headers:finalHeaders,body:JSON.stringify({tier:0})
        });
        assert.equal(ranked.status,200);
      }
    }
    console.log("Prévia isolada em "+web+" | E-mail fictício: "+email+" | Senha de teste: "+newPassword);
    await new Promise(resolve=>{process.once("SIGINT",resolve);process.once("SIGTERM",resolve);});
  }
} finally {
  for(const child of children)child.kill();
  mock.close();
  tlsPreview?.close();
}
