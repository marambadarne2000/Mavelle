import {useEffect,useState} from 'react';
import {check} from './api';
import {t,useLanguage} from './i18n';
export function OrderEmail({number}:{number:string}){
 useLanguage();const [status,setStatus]=useState<{status:string;deliveryMode:string|null}|null>(null);
 useEffect(()=>{const abort=new AbortController();let timer:ReturnType<typeof setTimeout>;let attempts=0;
  async function load(){try{const r=await fetch('/api/orders/'+encodeURIComponent(number)+'/email',{signal:abort.signal});await check(r);const next=await r.json();if(abort.signal.aborted)return;setStatus(next);if(['PENDING','RUNNING'].includes(next.status)&&++attempts<12)timer=setTimeout(()=>void load(),5000);}catch{/* Order remains available even when mail status cannot load. */}}
  void load();return()=>{abort.abort();clearTimeout(timer);};
 },[number]);
 if(!status)return null;
 const key=status.status==='COMPLETED'?(status.deliveryMode==='smtp'?'Order confirmation accepted by the email provider. Check your inbox and spam folder.':'Order confirmation saved locally. Email delivery is not connected.'):
 status.status==='FAILED'?'Order confirmation email failed. Your order is still saved.':status.status==='NOT_REQUESTED'?'This older order has no email confirmation queued.':'Order confirmation is queued for delivery.';
 return <p className="demo-note" role="status" data-testid="order-email-status">{t(key)}</p>;
}
