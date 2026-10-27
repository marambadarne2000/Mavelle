import {createContext,useCallback,useContext,useEffect,useRef,useState,type ReactNode} from 'react';
import {write,check} from './api';
export type SessionUser={id:number;email:string;role:'CUSTOMER'|'ADMIN'};
type Intent={kind:'add';productId:number;returnTo:string}|{kind:'return';returnTo:string};
type Session={user:SessionUser|null;loading:boolean;refresh:()=>Promise<SessionUser|null>;signedIn:(user:SessionUser)=>void;logout:()=>Promise<void>;requireLogin:(intent?:Intent)=>void;takeIntent:()=>Intent|null;cancelIntent:()=>void;destination:()=>string};
const Context=createContext<Session|null>(null);
export function SessionProvider({children}:{children:ReactNode}){
 const [user,setUser]=useState<SessionUser|null>(null);const current=useRef<SessionUser|null>(null);const [loading,setLoading]=useState(true);const serial=useRef(0);const intent=useRef<Intent|null>(null);
 const signedIn=useCallback((next:SessionUser)=>{serial.current++;current.current=next;setUser(next);setLoading(false);},[]);
 const refresh=useCallback(async()=>{const token=++serial.current;try{const response=await fetch('/api/auth/me',{signal:AbortSignal.timeout(10000)});if(response.status!==401)await check(response);const next=response.ok?await response.json() as SessionUser:null;if(token===serial.current){current.current=next;setUser(next);}return token===serial.current?next:current.current;}finally{if(token===serial.current)setLoading(false);}},[]);
 useEffect(()=>{refresh().catch(()=>setLoading(false));const focus=()=>refresh().catch(()=>{});window.addEventListener('focus',focus);return()=>window.removeEventListener('focus',focus);},[refresh]);
 useEffect(()=>{const navigate=()=>{if(!current.current&&!['#login','#register'].includes(window.location.hash))intent.current=null;};window.addEventListener('hashchange',navigate);return()=>window.removeEventListener('hashchange',navigate);},[]);
 const requireLogin=(next?:Intent)=>{intent.current=next??null;window.location.hash='#login';};
 const logout=async()=>{await check(await write('/api/auth/logout'));serial.current++;current.current=null;setUser(null);intent.current=null;window.location.hash='#home';};
 const takeIntent=()=>{const value=intent.current;intent.current=null;return value;};
 const destination=()=>intent.current?.returnTo??'#account';
 return <Context.Provider value={{user,loading,refresh,signedIn,logout,requireLogin,takeIntent,cancelIntent:()=>{intent.current=null;},destination}}>{children}</Context.Provider>;
}
export function useSession(){const value=useContext(Context);if(!value)throw new Error('SessionProvider is required.');return value;}
