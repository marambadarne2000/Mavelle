import {useEffect,useRef,useState,type FormEvent} from 'react';
import {AreaChart,Area,XAxis,YAxis,CartesianGrid,Tooltip,ResponsiveContainer} from 'recharts';
import {t,useLanguage,formatMoney,formatDate,formatTime} from './i18n';
import {useSession} from './session';
import {check,ApiError} from './api';
type Metrics={from:string;to:string;timezone:string;currency:string;threshold:number;orders:number;cancelled:number;amount:number;lowStockCount:number;lowStock:{id:number;sku:string;name:string;stockQuantity:number}[];days:{date:string;orders:number;cancelled:number;amount:number}[];generatedAt:string};
const today=()=>new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Jerusalem',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
const beginning=()=>{const date=new Date(today()+'T12:00:00Z');date.setUTCDate(date.getUTCDate()-29);return date.toISOString().slice(0,10);};
export function Dashboard(){
 const {language}=useLanguage(),session=useSession();const allowed=session.user?.role==='ADMIN';
 const [from,setFrom]=useState(beginning),[to,setTo]=useState(today),[threshold,setThreshold]=useState('5');
 const [query,setQuery]=useState(()=>({from:beginning(),to:today(),threshold:'5'}));
 const [data,setData]=useState<Metrics|null>(null),[error,setError]=useState(''),[busy,setBusy]=useState(false),[refresh,setRefresh]=useState(0);const lastQuery=useRef('');
 useEffect(()=>{
  if(!allowed)return;let disposed=false,inflight=false,timer:ReturnType<typeof setTimeout>;const abort=new AbortController();const key=session.user?.id+':'+JSON.stringify(query);if(lastQuery.current!==key){setData(null);lastQuery.current=key;}setError('');
  async function load(){
   if(inflight||disposed)return;inflight=true;setBusy(true);
   try{const response=await fetch('/api/admin/dashboard?'+new URLSearchParams(query),{signal:AbortSignal.any([abort.signal,AbortSignal.timeout(15000)])});await check(response);const next=await response.json() as Metrics;if(!disposed){setData(next);setError('');}}
   catch(e){if(!disposed){setError(e instanceof Error?e.message:'The request failed. Please try again.');if(e instanceof ApiError&&(e.status===401||e.status===403))void session.refresh();}}
   finally{inflight=false;if(!disposed){setBusy(false);timer=setTimeout(load,5000);}}
  }
  void load();return()=>{disposed=true;abort.abort();clearTimeout(timer);};
 },[allowed,session.user?.id,query,refresh]);
 function apply(e:FormEvent){e.preventDefault();if(from>to){setError('Choose a date range of up to 366 days.');return;}setQuery({from,to,threshold});}
 if(session.loading)return <main className="simple-page"><p>{t('Checking administrator access…')}</p></main>;
 if(!allowed)return <main className="simple-page"><h1>{t('Administrator access required.')}</h1><a className="gold-button" href="#login">{t('Sign in')}</a></main>;
 const locale=language==='he'?'he-IL':language==='ar'?'ar-IL':'en-IL';
 return <main className="simple-page dashboard"><div className="orders-heading"><div><p className="eyebrow">{t('MAVELLE / CONTROL CENTER')}</p><h1>{t('Your business, at a glance.')}</h1><p>{t('Welcome back, ')}<strong className="ceo-name">Maram Abbas</strong></p></div><div className="dashboard-links"><a className="outline-button" href="#admin-orders">{t('Order management')}</a><a className="outline-button" href="#admin">{t('Product management')}</a></div></div>
 <form className="order-admin-filters dashboard-filters" onSubmit={apply}><label>{t('From date')}<input data-testid="dashboard-from" type="date" required value={from} onChange={e=>setFrom(e.target.value)}/></label><label>{t('To date')}<input data-testid="dashboard-to" type="date" required value={to} onChange={e=>setTo(e.target.value)}/></label><label>{t('Low stock threshold')}<input data-testid="dashboard-threshold" type="number" required min="0" max="100" step="1" value={threshold} onChange={e=>setThreshold(e.target.value)}/></label><button data-testid="dashboard-apply" className="gold-button" type="submit">{t('Apply filters')}</button><button data-testid="dashboard-refresh" className="outline-button" type="button" disabled={busy} onClick={()=>setRefresh(n=>n+1)}>{t('Refresh now')}</button></form>
 <div className="dashboard-freshness"><span className={error?'connection-dot failed':'connection-dot'} aria-hidden="true"/>{t('Automatic refresh every 5 seconds.')}{data&&<span>{t('Last successful update:')} <time dateTime={data.generatedAt} title={formatDate(new Date(data.generatedAt))}>{formatTime(new Date(data.generatedAt))}</time></span>}{busy&&<span>{t('Updating…')}</span>}</div>
 {error&&<div className="order-admin-error" role="alert"><strong>{t('Data could not be refreshed.')}</strong><p>{t(error)}</p>{data&&<p>{t('Showing the last successful data. Values may be outdated.')}</p>}</div>}
 {!data&&!error&&<p role="status">{t('Loading dashboard…')}</p>}
 {data&&<><p className="dashboard-range">{data.from} — {data.to} · {t('Asia/Jerusalem · inclusive dates')} · {t('Demo order value, not collected payments.')}</p><section className="metric-grid" aria-label={t('Dashboard metrics')}>
  <article className="metric-card"><span>{t('Orders in range')}</span><strong data-testid="dashboard-orders">{data.orders}</strong><small>{t('Includes cancelled orders')}</small></article>
  <article className="metric-card metric-gold"><span>{t('Active demo order value')}</span><strong data-testid="dashboard-amount">{formatMoney(data.amount)}</strong><small>{t('Cancelled orders excluded')}</small></article>
  <article className="metric-card"><span>{t('Cancelled orders')}</span><strong>{data.cancelled}</strong><small>{t('Within the selected creation dates')}</small></article>
  <article className="metric-card"><span>{t('Low stock products')}</span><strong data-testid="dashboard-low-stock">{data.lowStockCount}</strong><small>{t('Current active products, including sold out')}</small></article>
 </section><div className="dashboard-columns"><section className="dashboard-chart"><div className="section-top"><div><p className="eyebrow">{t('THE BIG PICTURE')}</p><h2>{t('Daily order activity')}</h2></div></div>{data.orders===0&&<p>{t('No orders in this date range. The chart shows zero activity.')}</p>}<div className="chart-frame" dir="ltr"><ResponsiveContainer width="100%" height="100%"><AreaChart data={data.days} accessibilityLayer margin={{left:10,right:20,top:15,bottom:10}}><defs><linearGradient id="dashboardGold" x1="0" y1="0" x2="0" y2="1"><stop offset="0%" stopColor="#e2b01b" stopOpacity={.55}/><stop offset="100%" stopColor="#e2b01b" stopOpacity={.04}/></linearGradient></defs><CartesianGrid stroke="#ded6c2" strokeDasharray="3 3"/><XAxis dataKey="date" tickFormatter={v=>new Intl.DateTimeFormat(locale,{month:'short',day:'numeric',timeZone:'UTC'}).format(new Date(v+'T12:00:00Z'))} minTickGap={35} tick={{fontSize:11}}/><YAxis tick={{fontSize:11}} width={60}/><Tooltip formatter={value=>[formatMoney(Number(value)),t('Active demo order value')]} labelFormatter={label=>String(label)}/><Area type="monotone" dataKey="amount" stroke="#a87500" strokeWidth={3} fill="url(#dashboardGold)" isAnimationActive={false}/></AreaChart></ResponsiveContainer></div><details className="chart-table"><summary>{t('View chart data as a table')}</summary><div className="table-scroll"><table><thead><tr><th>{t('Date')}</th><th>{t('Orders')}</th><th>{t('Cancelled orders')}</th><th>{t('Active demo order value')}</th></tr></thead><tbody>{data.days.map(d=><tr key={d.date}><td>{d.date}</td><td>{d.orders}</td><td>{d.cancelled}</td><td>{formatMoney(d.amount)}</td></tr>)}</tbody></table></div></details></section>
 <section className="dashboard-stock"><p className="eyebrow">{t('INVENTORY WATCH')}</p><h2>{t('A little attention needed.')}</h2><p>{t('Active products at or below the selected threshold. Inventory is independent of the date range.')}</p>{data.lowStock.length?<ul>{data.lowStock.map(p=><li key={p.id}><div><strong>{t(p.name)}</strong><small>{p.sku}</small></div><span className="status-badge" data-status={p.stockQuantity===0?'CANCELLED':'PENDING'}>{p.stockQuantity===0?t('Out of stock'):p.stockQuantity+' '+t('units')}</span></li>)}</ul>:<p className="stock-healthy">{t('All active products are above this threshold.')}</p>}{data.lowStockCount>20&&<p>{t('Showing the 20 lowest-stock products. Open product management for the full library.')}</p>}<a className="gold-button" href="#admin">{t('Manage inventory')}</a></section></div></>}
 </main>;
}


