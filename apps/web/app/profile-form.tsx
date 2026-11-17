"use client";
import {useState,type ChangeEvent,type FormEvent} from "react";

export type Profile={username:string;name:string;bio:string;avatar?:string};
export type ProfileInput={username:string;name:string;bio:string;cpf?:string;avatar?:string};

const avatarPresets=[
  {id:"cinema",label:"Cinema",glyph:"🎬"},
  {id:"estrelas",label:"Estrelas",glyph:"🌟"},
  {id:"fantasia",label:"Fantasia",glyph:"🐉"},
  {id:"espaco",label:"Espaço",glyph:"🚀"},
  {id:"misterio",label:"Mistério",glyph:"🔍"},
  {id:"animacao",label:"Animação",glyph:"🦊"},
];

function formatCpf(value:string) {
  const digits=value.replace(/[^0-9]/g,"").slice(0,11);
  const groups=[digits.slice(0,3),digits.slice(3,6),digits.slice(6,9)].filter(Boolean).join(".");
  return digits.length>9?groups+"-"+digits.slice(9):groups;
}

export function ProfileAvatar({name,avatar}:{name:string;avatar?:string}) {
  const preset=avatarPresets.find(option=>avatar==="preset:"+option.id);
  if(preset)return <span className="profile-avatar profile-initial" aria-hidden="true">{preset.glyph}</span>;
  return avatar?.startsWith("data:image/jpeg;base64,") ? <img className="profile-avatar" src={avatar} alt=""/> :
    <span className="profile-avatar profile-initial" aria-hidden="true">{Array.from(name.trim())[0]?.toLocaleUpperCase("pt-BR")??"?"}</span>;
}

export function ProfileForm({profile,busy,onSave}:{profile?:Profile;busy:boolean;onSave:(input:ProfileInput)=>void}) {
  const [avatar,setAvatar]=useState<string>();
  const [loading,setLoading]=useState(false);
  const [error,setError]=useState("");
  const [cpf,setCpf]=useState("");
  const preview=avatar??profile?.avatar;

  async function selectImage(event:ChangeEvent<HTMLInputElement>) {
    const file=event.target.files?.[0];
    if(!file)return;
    setLoading(true);setError("");
    try {
      if(!["image/jpeg","image/png","image/webp"].includes(file.type)||file.size>5*1024*1024) {
        setError("Escolha uma imagem JPG, PNG ou WebP de até 5 MB.");
        return;
      }
      const image=await createImageBitmap(file);
      try {
        const canvas=document.createElement("canvas");
        canvas.width=canvas.height=256;
        const context=canvas.getContext("2d");
        if(!context)throw new Error();
        const side=Math.min(image.width,image.height);
        context.fillStyle="#1b202c";
        context.fillRect(0,0,256,256);
        context.drawImage(image,(image.width-side)/2,(image.height-side)/2,side,side,0,0,256,256);
        setAvatar(canvas.toDataURL("image/jpeg",0.82));
      } finally {image.close();}
    } catch {setError("Não foi possível abrir essa imagem.");}
    finally {setLoading(false);event.target.value="";}
  }

  function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form=new FormData(event.currentTarget);
    const cpf=String(form.get("cpf")??"").trim();
    onSave({name:String(form.get("name")),username:String(form.get("username")),bio:String(form.get("bio")??""),
      ...(cpf?{cpf}:{}),...(avatar!==undefined?{avatar}:{})});
  }

  return <form className="form" onSubmit={submit}>
    <fieldset className="profile-picker">
      <legend>Ícone de perfil <span className="muted">(opcional)</span></legend>
      <div className="avatar-options" role="group" aria-label="Ícones prontos">
        {avatarPresets.map(option=><button key={option.id} type="button" className="avatar-option" aria-label={option.label} title={option.label} aria-pressed={preview==="preset:"+option.id} disabled={loading||busy} onClick={()=>{setAvatar("preset:"+option.id);setError("");}}>{option.glyph}</button>)}
      </div>
      <div className="row profile-photo">
      <ProfileAvatar name={profile?.name??""} avatar={preview}/>
      <label>Enviar foto<input type="file" accept="image/jpeg,image/png,image/webp" onChange={selectImage} disabled={loading||busy} aria-describedby="profile-photo-help"/>
        <small id="profile-photo-help">JPG, PNG ou WebP, até 5 MB.</small>
      </label>
      {preview&&<button type="button" onClick={()=>setAvatar("")} disabled={loading||busy}>Remover</button>}
      </div>
    </fieldset>
    {error&&<p role="alert" className="message error">{error}</p>}
    <label>Nome<input name="name" required maxLength={80} defaultValue={profile?.name}/></label>
    <label>Usuário<input name="username" required minLength={3} maxLength={32} pattern="[a-zA-Z0-9_]+" defaultValue={profile?.username}/></label>
    <label>CPF<input name="cpf" inputMode="numeric" value={cpf} onChange={event=>setCpf(formatCpf(event.target.value))} pattern="[0-9]{3}[.][0-9]{3}[.][0-9]{3}-[0-9]{2}" placeholder="000.000.000-00" required={!profile} aria-describedby="profile-cpf-help"/>
      <small id="profile-cpf-help">{profile?"Preencha para adicionar ou atualizar o CPF. ":""}Visível apenas ao sistema.</small>
    </label>
    {profile&&<label>Bio<textarea name="bio" maxLength={300} defaultValue={profile.bio}/></label>}
    <button className="primary" disabled={busy||loading}>Salvar perfil</button>
  </form>;
}
