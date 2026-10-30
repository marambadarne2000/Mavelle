import {useRef} from 'react';
import {FormFeedback} from './FormFeedback';
import {t,useLanguage} from './i18n';
import { useState, type FormEvent } from 'react';
import { useSession, type SessionUser } from './session';
import { check, write } from './api';
import {CustomerCenter} from './Marketplace';
export function AccountPanel() { useLanguage(); const session = useSession(); return <a className="account-trigger" href={session.user ? '#account' : '#login'}>{t(session.user ? 'My account' : 'Sign in')}</a>; }
export function AuthPage({ register = false }: {
    register?: boolean;
}) {
    useLanguage();
    const session = useSession();
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const submitting = useRef(false);
    async function submit(event: FormEvent) {
        event.preventDefault(); if(submitting.current)return;submitting.current=true;
        setBusy(true);
        setError('');
        setNotice('');
        try {
            if (register) {
                if (Array.from(password).length < 12 || new TextEncoder().encode(password).length > 72)
                    throw new Error('Use at least 12 characters, up to 72 UTF-8 bytes.');
                await check(await write('/api/auth/register', JSON.stringify({ email, password }), 'application/json'));
                setPassword('');
                window.location.hash = '#login';
                return;
            }
            const response = await write('/api/auth/login', new URLSearchParams({ username: email.trim(), password }), 'application/x-www-form-urlencoded');
            await check(response);
            const user=await response.json() as SessionUser;
            const route = user.role==='ADMIN'?'#marketplace-admin':user.role==='MERCHANT'?'#merchant':user.role==='EMPLOYEE'?'#staff-orders':session.destination();
            session.signedIn(user);
            setPassword('');
            window.location.hash = route;
        }
        catch (e) {
            setError(e instanceof Error ? e.message : 'Please try again.');
        }
        finally {
            submitting.current=false;setBusy(false);
        }
    }
    const title=register?'Make yourself at home.':'Welcome back.';
    return <main className="auth-page"><section className="auth-intro"><p className="eyebrow">{t('MARAMIGO / YOUR WORLD')}</p><h1>{t(title)}</h1><p>{t('One sign-in for customers, businesses and the platform team.')}</p><div className="auth-emblem" aria-hidden="true">{t("m.")}</div></section>
    <section className="auth-card"><h2>{t(register?'Create an account':'Sign in')}</h2><form onSubmit={submit} data-testid="auth-form" aria-busy={busy}><fieldset disabled={busy}><label>{t('Email address')}<input data-testid="auth-email" type="email" autoComplete="username" required maxLength={254} value={email} onChange={e=>setEmail(e.target.value)}/></label><label>{t('Password')}<input data-testid="auth-password" type="password" autoComplete={register?'new-password':'current-password'} required value={password} onChange={e=>setPassword(e.target.value)}/></label>{register&&<p>{t('At least 12 characters; maximum 72 UTF-8 bytes.')}</p>}<button className="gold-button" data-testid="auth-submit" type="submit">{t(busy?'Please wait…':register?'Create account':'Sign in')}</button></fieldset></form><FormFeedback error={error}/>{notice&&<p role="status">{t(notice)}</p>}
    <a className="auth-switch" href={register?'#login':'#register'}>{t(register?'Already have an account? Sign in':'New here? Create an account')}</a>
    <a className="auth-switch" data-testid="business-entry" href="#join-business">{t('Join with your business')}</a>
    <a className="auth-switch" href="#staff-register">{t('Have an invitation? Activate your work account')}</a>
    <a className="auth-switch" href="#collection" onClick={session.cancelIntent}>{t('Continue browsing')}</a></section></main>;

}
export function AccountPage(){useLanguage();const session=useSession();const [error,setError]=useState('');
 if(session.loading)return <main className="simple-page"><p>{t('Loading your account…')}</p></main>;
 if(!session.user)return <main className="simple-page"><h1>{t('Please sign in to continue.')}</h1><a className="gold-button" href="#login">{t('Sign in')}</a></main>;
 if(session.user.role==='CUSTOMER')return <CustomerCenter key={session.user.id}/>;
 return <main className="simple-page account-home"><p className="eyebrow">{t(session.user.role==='ADMIN'?'MARAMIGO / OWNER':session.user.role==='EMPLOYEE'?'MARAMIGO / TEAM':'YOUR MARAMIGO')}</p><h1>{t('My account')}</h1><p dir="ltr">{session.user.email}</p><div className="account-actions">
 {session.user.role==='EMPLOYEE'?<a className="gold-button" href="#staff-orders">{t('Orders & deliveries')}</a>:session.user.role==='MERCHANT'?<a className="gold-button" href="#merchant">{t('My business dashboard')}</a>:<><a className="gold-button" href="#orders">{t('My orders')}</a><a className="outline-button" href="#basket">{t('My basket')}</a></>}
 {session.user.role==='ADMIN'&&<><a className="gold-button" href="#marketplace-admin">{t('Marketplace management')}</a><a className="gold-button" href="#owner">{t('Business & employees')}</a><a className="gold-button" href="#dashboard">{t('Dashboard')}</a><a className="outline-button" href="#admin">{t('Product management')}</a><a className="outline-button" href="#admin-orders">{t('Order management')}</a></>}
 <button data-testid="logout" className="outline-button" onClick={()=>session.logout().catch(e=>setError(e.message))}>{t('Sign out')}</button></div><FormFeedback error={error}/></main>;
}
