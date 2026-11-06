import {useEffect,useState,type FormEvent} from 'react';
import {t,useLanguage,formatMoney,formatDate} from './i18n';
import {useSession} from './session';
import {ApiError,check,write} from './api';

type Summary={orderNumber:string;status:string;total:number;currency:string;createdAt:string};
type Detail={version:number;order:Summary & {shipping:{fullName:string;phone:string;address:string;city:string;postalCode:string;country:string};items:{productId:number;sku:string;name:string;companyName:string;quantity:number;unitPrice:number;lineTotal:number}[]};history:{fromStatus:string;toStatus:string;reason:string;actorId:number;occurredAt:string}[]};
type Page={items:Summary[];total:number;page:number;size:number};

export function AdminOrders(){
 useLanguage();const session=useSession();
 const [page,setPage]=useState(0),[status,setStatus]=useState(''),[search,setSearch]=useState(''),[refresh,setRefresh]=useState(0);
 const [data,setData]=useState<Page|null>(null),[selected,setSelected]=useState(''),[detail,setDetail]=useState<Detail|null>(null);
 const [error,setError]=useState(''),[listError,setListError]=useState(''),[notice,setNotice]=useState('');
 const [busy,setBusy]=useState(false),[next,setNext]=useState(''),[reason,setReason]=useState(''),[confirmed,setConfirmed]=useState(false);
 const allowed=session.user?.role==='ADMIN';
 useEffect(()=>{
  if(!allowed)return;const abort=new AbortController();setData(null);setListError('');
  fetch(`/api/admin/orders?status=${status}&search=${encodeURIComponent(search)}&page=${page}`,{signal:abort.signal}).then(async r=>{await check(r);setData(await r.json() as Page);}).catch(e=>{if(!abort.signal.aborted)setListError(e.message);});
  return ()=>abort.abort();
 },[allowed,page,status,search,refresh]);
 useEffect(()=>{
  setDetail(null);setError('');setNext('');setReason('');setConfirmed(false);
  if(!selected||!allowed)return;const abort=new AbortController();
  fetch('/api/admin/orders/'+encodeURIComponent(selected),{signal:abort.signal}).then(async r=>{await check(r);setDetail(await r.json() as Detail);}).catch(e=>{if(!abort.signal.aborted)setError(e.message);});
  return ()=>abort.abort();
 },[selected,refresh,allowed]);
 async function submit(event:FormEvent){
  event.preventDefault();if(!detail||busy||!next||!reason.trim()||(next==='CANCELLED'&&!confirmed))return;
  setBusy(true);setError('');setNotice('');
  try{
   const response=await write('/api/admin/orders/'+encodeURIComponent(detail.order.orderNumber)+'/status',JSON.stringify({status:next,version:detail.version,reason:reason.trim()}),'application/json','PATCH');await check(response);
   const updated=await response.json() as Detail;setDetail(updated);setNext('');setReason('');setConfirmed(false);setNotice('Order updated.');setRefresh(n=>n+1);
  }catch(e){setError(e instanceof Error?e.message:'The request failed. Please try again.');if(e instanceof ApiError&&(e.status===401||e.status===403))void session.refresh();}
  finally{setBusy(false);}
 }
 if(session.loading)return <main className="simple-page"><p>{t('Checking administrator access…')}</p></main>;
 if(!allowed)return <main className="simple-page"><h1>{t('Administrator access required.')}</h1><a href="#login" className="gold-button">{t('Sign in')}</a></main>;
 const transitions=detail?.order.status==='PENDING'?['SHIPPED','CANCELLED']:detail?.order.status==='SHIPPED'?['DELIVERED']:[];
 return <main className="simple-page admin-orders"><div className="orders-heading"><div><p className="eyebrow">{t('MAVELLE / CONTROL CENTER')}</p><h1>{t('Order management')}</h1><p>{t('Review, dispatch and track every demo order.')}</p></div><a className="outline-button" href="#admin">{t('Product management')}</a></div>
  <div className="order-admin-filters"><label>{t('Search order number')}<input data-testid="admin-order-search" type="search" maxLength={100} value={search} disabled={busy} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label><label>{t('Order status')}<select data-testid="admin-order-filter" value={status} disabled={busy} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">{t('All statuses')}</option>{['PENDING','SHIPPED','DELIVERED','CANCELLED'].map(s=><option key={s} value={s}>{t(s)}</option>)}</select></label><button data-testid="admin-orders-refresh" className="outline-button" disabled={busy} onClick={()=>setRefresh(n=>n+1)}>{t('Refresh orders')}</button></div>
  {notice&&<p className="order-admin-notice" role="status">{t(notice)}</p>}
  <div className="order-admin-columns"><section className="order-admin-list" aria-label={t('Orders')}>
   {listError?<div role="alert"><p>{t(listError)}</p><button className="outline-button" onClick={()=>setRefresh(n=>n+1)}>{t('Try again')}</button></div>:!data?<p>{t('Loading orders…')}</p>:!data.items.length?<p>{t('No orders match these filters.')}</p>:<><p>{data.total} {t('Orders')}</p>{data.items.map(o=><button data-testid="admin-order-row" data-order-number={o.orderNumber} key={o.orderNumber} className={'order-admin-row '+(selected===o.orderNumber?'selected':'')} disabled={busy} onClick={()=>{setSelected(o.orderNumber);setNotice('');}}><span className="order-number" dir="ltr">{o.orderNumber}</span><span className="status-badge" data-status={o.status}>{t(o.status)}</span><span>{formatDate(new Date(o.createdAt))}</span><strong>{formatMoney(o.total)}</strong></button>)}<div className="pagination"><button disabled={busy||page===0} onClick={()=>setPage(n=>n-1)}>{t('Previous')}</button><span>{page+1} / {Math.ceil(data.total/20)}</span><button disabled={busy||(page+1)*20>=data.total} onClick={()=>setPage(n=>n+1)}>{t('Next')}</button></div></>}
  </section><section className="order-admin-detail" aria-label={t('Order details')}>
   {error&&<div className="order-admin-error" role="alert"><p>{t(error)}</p><button data-testid="admin-orders-refresh" className="outline-button" disabled={busy} onClick={()=>setRefresh(n=>n+1)}>{t('Reload order')}</button></div>}
   {!selected?<div className="order-placeholder"><span aria-hidden="true">◇</span><h2>{t('Every order has a story.')}</h2><p>{t('Select an order to review its details and history.')}</p></div>:!detail?(!error&&<p>{t('Loading orders…')}</p>):<>
    <div className="order-detail-heading"><h2>{t('Order details')}</h2><span className="status-badge" data-status={detail.order.status}>{t(detail.order.status)}</span></div><p className="order-number" dir="ltr">{detail.order.orderNumber}</p><p>{formatDate(new Date(detail.order.createdAt))}</p>
    <div className="order-items">{detail.order.items.map(i=><div key={i.productId}><span>{t(i.name)}<small>{i.companyName} · {i.sku} · {formatMoney(i.unitPrice)} × {i.quantity}</small></span><strong>{formatMoney(i.lineTotal)}</strong></div>)}</div><div className="checkout-total"><span>{t('Total')}</span><strong>{formatMoney(detail.order.total)}</strong></div>
    <h3>{t('Shipping details')}</h3><p className="order-shipping">{detail.order.shipping.fullName}<br/>{detail.order.shipping.address}<br/>{detail.order.shipping.city} {detail.order.shipping.postalCode} · {detail.order.shipping.country}<br/><bdi>{detail.order.shipping.phone}</bdi></p>
    {transitions.length>0?<form data-testid="order-status-form" className="order-transition" onSubmit={submit}><h3>{t('Update order status')}</h3><fieldset disabled={busy}><label>{t('Next status')}<select required value={next} onChange={e=>{setNext(e.target.value);setConfirmed(false);}}><option value="">{t('Choose a status')}</option>{transitions.map(s=><option key={s} value={s}>{t(s)}</option>)}</select></label><label>{t('Reason')}<input required maxLength={240} value={reason} onChange={e=>setReason(e.target.value)}/></label>{next==='CANCELLED'&&<label className="cancel-confirm"><input data-testid="cancel-confirm" type="checkbox" required checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>{t('Cancel this pending order and restore its inventory.')}</label>}<button data-testid="order-status-submit" className={next==='CANCELLED'?'danger-button':'gold-button'} type="submit" disabled={!next||!reason.trim()||(next==='CANCELLED'&&!confirmed)}>{t(busy?'Saving…':'Apply status change')}</button></fieldset></form>:<p className="order-final-note">{t('This order is complete. No further status changes are available.')}</p>}
    <section className="order-status-history"><h3>{t('Status history')}</h3>{detail.history.length?<ol>{detail.history.map((h,i)=><li key={i}><span>{t(h.fromStatus)} → {t(h.toStatus)}</span><p>{h.reason}</p><small>{formatDate(new Date(h.occurredAt))} · {t('Team member')} #{h.actorId}</small></li>)}</ol>:<p>{t('No status changes yet.')}</p>}</section>
   </>}
  </section></div>
 </main>;
}


