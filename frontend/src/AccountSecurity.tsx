import {useEffect,useRef,useState,type FormEvent} from 'react';
import {check,write} from './api';
import {t,useLanguage} from './i18n';
import {FormFeedback} from './FormFeedback';

type Options={deliveryMode:string;verificationRequired:boolean};
export function AccountRecoveryPage({kind,token,onReset}:{kind:'reset'|'verify';token?:string;onReset?:()=>Promise<unknown>}){
 useLanguage();const [email,setEmail]=useState('');const [password,setPassword]=useState('');const [confirm,setConfirm]=useState('');
 const [error,setError]=useState('');const [done,setDone]=useState(false);const [busy,setBusy]=useState(false);const submitting=useRef(false);
 const [options,setOptions]=useState<Options|null>(null);
 useEffect(()=>{const abort=new AbortController();fetch('/api/auth/options',{signal:abort.signal}).then(async r=>{await check(r);setOptions(await r.json() as Options);}).catch(()=>{});return()=>abort.abort();},[]);
 async function submit(event:FormEvent){event.preventDefault();if(submitting.current)return;submitting.current=true;setBusy(true);setError('');
  try{
   if(token&&kind==='reset'&&(password!==confirm||Array.from(password).length<12||new TextEncoder().encode(password).length>72))throw new Error('Passwords must match and contain at least 12 characters, up to 72 UTF-8 bytes.');
   const path=token?(kind==='reset'?'reset-password':'verify-email'):(kind==='reset'?'forgot-password':'verification-request');
   await check(await write('/api/auth/'+path,JSON.stringify(token?{token,...(kind==='reset'?{password}:{})}:{email}),'application/json'));
   setPassword('');setConfirm('');setDone(true);
   if(token&&kind==='reset')await onReset?.().catch(()=>{});
   if(token)window.history.replaceState(null,'',kind==='reset'?'#reset-password':'#verify-email');
  }catch(e){setError(e instanceof Error?e.message:'Please try again.');}finally{submitting.current=false;setBusy(false);}
 }
 return <main className="auth-page" data-testid="account-recovery"><section className="auth-intro"><p className="eyebrow">MARAMIGO / {t('Account security')}</p><h1>{t(kind==='reset'?'Reset your password':'Verify your email')}</h1><p>{t('Links are single use and expire. Never share them with anyone.')}</p></section>
 <section className="auth-card"><h2>{t(token?(kind==='reset'?'Choose a new password':'Confirm your email address'):'Request a secure link')}</h2>
 {options?.deliveryMode==='file'&&<p role="note">{t('Local demo: messages are saved privately on this computer, not sent to an email inbox.')}</p>}
 {done?<p role="status">{t(token?(kind==='reset'?'Password updated. Sign in again with your new password.':'Email verified. You can now sign in.'):'If this address belongs to an eligible account, a link will be prepared. You can request another after one minute.')}</p>:
 <form onSubmit={submit} aria-busy={busy}><fieldset disabled={busy}>
 {!token&&<label>{t('Email address')}<input type="email" autoComplete="email" maxLength={254} required value={email} onChange={e=>setEmail(e.target.value)}/></label>}
 {token&&kind==='reset'&&<><label>{t('New password')}<input type="password" autoComplete="new-password" required value={password} onChange={e=>setPassword(e.target.value)}/></label><label>{t('Confirm password')}<input type="password" autoComplete="new-password" required value={confirm} onChange={e=>setConfirm(e.target.value)}/></label><p>{t('At least 12 characters; maximum 72 UTF-8 bytes.')}</p></>}
 <button className="gold-button" type="submit">{t(busy?'Please wait…':token?(kind==='reset'?'Update password':'Verify email'):'Send link')}</button>
 </fieldset></form>}
 <FormFeedback error={error}/>{!done&&token&&<a className="auth-switch" href={kind==='reset'?'#forgot-password':'#verify-email'}>{t('Request a new link')}</a>}
 <a className="auth-switch" href="#login">{t('Sign in')}</a></section></main>;
}
export function AccountSecurity(){
 useLanguage();const [status,setStatus]=useState<(Options&{emailVerified:boolean})|null>(null);const [error,setError]=useState('');
 useEffect(()=>{const abort=new AbortController();fetch('/api/auth/security',{signal:abort.signal}).then(async r=>{await check(r);setStatus(await r.json());}).catch(e=>{if(!abort.signal.aborted)setError(e.message);});return()=>abort.abort();},[]);
 return <aside className="account-security" aria-labelledby="account-security-title"><h2 id="account-security-title">{t('Account security')}</h2>
 {status&&<><p>{t(status.emailVerified?'Email verified':'Email not yet verified')}</p>{!status.verificationRequired&&<p>{t('Demo mode allows sign-in before email verification.')}</p>}{!status.emailVerified&&<a className="outline-button" href="#verify-email">{t('Verify your email')}</a>}</>}
 <a className="outline-button" href="#forgot-password">{t('Reset your password')}</a><FormFeedback error={error}/></aside>;
}
