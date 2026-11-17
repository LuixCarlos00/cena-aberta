"use client";
import {useEffect,useState,type FormEvent} from "react";
import {signIn} from "next-auth/react";

type Mode="login"|"register"|"reset";

export default function Conta() {
  const [mode,setMode]=useState<Mode>("login");
  const [token,setToken]=useState("");
  const [message,setMessage]=useState("");
  const [error,setError]=useState("");
  const [busy,setBusy]=useState(false);
  const [emailAddress,setEmailAddress]=useState("");
  useEffect(()=>{
    const params=new URLSearchParams(location.search);
    const fragment=new URLSearchParams(location.hash.slice(1));
    for(const purpose of ["register","reset"] as const) {
      const link=fragment.get(purpose);
      if(link) {
        setMode(purpose);setToken(link);
        history.replaceState(null,"","/conta");
        return;
      }
    }
    if(params.get("modo")==="register")setMode("register");
  },[]);

  async function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();setError("");setMessage("");
    const form=new FormData(event.currentTarget);
    const email=String(form.get("email")??"");
    const password=String(form.get("password")??"");
    if(token && password!==form.get("confirmation")){setError("As senhas não coincidem.");return;}
    if(token && new TextEncoder().encode(password).length>72){setError("Use uma senha de até 72 bytes.");return;}
    setBusy(true);
    try {
      if(mode==="login"||token) {
        const result=await signIn("email-account",{
          email,password,token,purpose:mode,rememberMe:String(form.has("rememberMe")),redirect:false,callbackUrl:"/",
        });
        if(result?.ok && !result.error){location.replace("/");return;}
        setError(result?.error==="CredentialsSignin"?"E-mail ou senha inválidos.":result?.error||"Não foi possível entrar.");
      } else {
        const response=await fetch("/api/accounts/"+mode,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({email})});
        const result=await response.json();
        if(response.ok)setMessage(result.message);
        else setError(result.message||"Não foi possível solicitar o link.");
      }
    } catch {setError("Serviço de contas temporariamente indisponível.");}
    finally {setBusy(false);}
  }

  function change(next:Mode){
    setMode(next);setToken("");setError("");setMessage("");
    history.replaceState(null,"",next==="register"?"/conta?modo=register":"/conta");
  }
  const title=token?"Defina sua senha":mode==="register"?"Criar conta":mode==="reset"?"Recuperar senha":"Entrar";
  const description=token?"Escolha a senha que você vai usar para entrar.":mode==="register"?"Confirme seu e-mail para criar sua conta.":mode==="reset"?"Vamos enviar um link para você definir uma nova senha.":"Acesse seu diário, suas listas e suas avaliações.";
  return <main className="wrap account-page"><section className="panel account-panel" aria-labelledby="account-title">
    <a className="account-brand" href="/"><span aria-hidden="true">◈</span> Cena Aberta</a>
    <h1 id="account-title">{title}</h1>
    <p className="account-description">{description}</p>
    {error&&<p role="alert" className="message error">{error}</p>}{message&&<p role="status" className="message">{message}</p>}
    <form className="form" key={mode+Boolean(token)} onSubmit={submit}>
      {!token&&<label>E-mail<input name="email" type="email" autoComplete="email" placeholder="voce@exemplo.com" value={emailAddress} onChange={event=>setEmailAddress(event.target.value)} maxLength={254} required/></label>}
      {(mode==="login"||token)&&<div className="account-password">
        <div className="account-password-label"><label htmlFor="account-password">Senha</label>
          {mode==="login"&&<button type="button" className="account-signin-link" onClick={()=>change("reset")} disabled={busy}>Esqueci a senha</button>}
        </div>
        <input id="account-password" name="password" type="password" autoComplete={token?"new-password":"current-password"} minLength={token?12:undefined} maxLength={72} required/>
      </div>}
      {token&&<><label>Confirme a senha<input name="confirmation" type="password" autoComplete="new-password" minLength={12} maxLength={72} required/></label><small>Use pelo menos 12 caracteres.</small></>}
      {mode==="login"&&<label className="check"><input type="checkbox" name="rememberMe"/>Manter conectado</label>}
      <button className="primary account-submit" disabled={busy}>{busy?"Aguarde…":token?"Salvar senha e entrar":mode==="login"?"Entrar":mode==="reset"?"Enviar link de recuperação":"Enviar link de confirmação"}</button>
    </form>
    {token&&<div className="row account-actions"><button onClick={()=>change(mode)} disabled={busy}>Solicitar outro link</button></div>}
    <div className="account-signin">
      {mode==="login"?<><span>Novo por aqui?</span><button className="account-signin-link" onClick={()=>change("register")} disabled={busy}>Criar conta</button></>:
        <>{mode==="register"&&<span>Já tem uma conta?</span>}<button className="account-signin-link" onClick={()=>change("login")} disabled={busy}>{mode==="reset"?"Voltar para entrar":"Entrar"}</button></>}
    </div>
  </section></main>;
}
