import {PublicInfo} from './Portal';
import {MfaChallenge} from './Mfa';
import {AccountRecoveryPage} from './AccountSecurity';
import {PageTools,HomeDiscovery} from './Discovery';
import {BusinessRegistration,MerchantDashboard,MarketplaceAdmin,FavoriteButton} from './Marketplace';
import {OwnerWorkspace,StaffWorkspace,StaffRegistrationPage} from './Workspaces';
import {knownRoute,catalogSearch} from './route';
import {trapDialogTab} from './focus';

import {formatMoney} from './i18n';
import {AdminOrders} from './AdminOrders';
import {t,useLanguage} from './i18n';
import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { AdminProducts } from './AdminProducts';
import { AccountPanel, AuthPage, AccountPage } from './AuthPages';
import { SessionProvider, useSession } from './session';
import { CartProvider, useCart } from './cart/CartContext';
import { CartPage } from './cart/CartPage';
import { CheckoutPage, OrdersPage } from './OrderPages';
import { I18nProvider, LanguagePicker } from './i18n';
import './styles.css';
import './luxury.css';
import './clarity.css';
import './dashboard.css';
import './atelier.css';
const Dashboard = React.lazy(() => import('./Dashboard').then(module => ({default: module.Dashboard})));

type Company = {
    id: number;
    slug: string;
    name: string;
    description: string;
    monogram: string;
    demo: boolean;
    logoId?:string|null;
};
type Product = {
    id: number;
    sku: string;
    name: string;
    description: string;
    category: string;
    price: number;
    stockQuantity: number;
    companyId: number;
    imageId?:string|null;
};
type Catalog = {
    items: Product[];
    total: number;
    page: number;
    size: number;
};
const money = {format: formatMoney};
function CatalogView({ company, companies }: {
    company?: Company;
    companies: Company[];
}) {
    useLanguage();
    const basket = useCart();
    const [data, setData] = useState<Catalog | null>(null);
    const [query, setQuery] = useState('');
    const [category, setCategory] = useState('');
    const [sort, setSort] = useState('featured');
    const [page, setPage] = useState(0);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [retry, setRetry] = useState(0);
    const [selected, setSelected] = useState<Product | null>(null);
    const returnFocus = useRef<HTMLElement | null>(null);
    useEffect(() => { const controller = new AbortController(); setLoading(true); setError(''); fetch(`/api/products?search=${encodeURIComponent(catalogSearch(query))}&category=${category}&sort=${sort}&page=${page}&size=12${company ? '&companyId=' + company.id : ''}`, { signal: controller.signal }).then(async (response) => { if (!response.ok)
        throw new Error('Could not load the collection. Please try again.'); setData(await response.json() as Catalog); }).catch(e => { if (!controller.signal.aborted)
        setError(e.message); }).finally(() => { if (!controller.signal.aborted)
        setLoading(false); }); return () => controller.abort(); }, [query, category, sort, page, retry, company?.id]);
    useEffect(() => { if (!selected)
        return; const dialog = document.querySelector<HTMLDialogElement>('#product-dialog'); dialog?.showModal(); return () => { dialog?.close(); returnFocus.current?.focus(); }; }, [selected]);
    const open = (p: Product) => { returnFocus.current = document.activeElement as HTMLElement; setSelected(p); };
    const limit = (p: Product) => (basket.items.find(i => i.productId === p.id)?.quantity ?? 0) >= Math.min(99, p.stockQuantity);
    const label = (p: Product) => basket.adding.includes(p.id) ? 'Checking…' : p.stockQuantity === 0 ? 'Out of stock' : limit(p) ? 'Maximum quantity in basket' : 'Add to basket';
    return <section className="collection" id="collection" aria-labelledby="collection-title"><div className="section-top"><div><p className="eyebrow">{t("CURATED, NOT COMPLICATED")}</p><h2 id="collection-title">{t(company ? 'The store collection.' : 'The essentials.')}</h2></div><p>{t("Thoughtful pieces.")}<br />{t("Everyday companions.")}</p></div><div className="catalog-controls"><label className="search">{t("Search the collection")}<input data-testid="catalog-search" type="search" maxLength={100} value={query} onChange={e => { setQuery(e.target.value); setPage(0); }} placeholder={t("Find your next little luxury\u2026")}/></label><label className="catalog-filter">{t("Category")}<select value={category} onChange={e => { setCategory(e.target.value); setPage(0); }}><option value="">{t("All categories")}</option>{['Living', 'Wear', 'Accessories', 'Wellness', 'Makeup', 'Games', 'Other'].map(c => <option key={c} value={c}>{t(c)}</option>)}</select></label><label className="catalog-filter">{t("Sort by")}<select value={sort} onChange={e => { setSort(e.target.value); setPage(0); }}><option value="featured">{t("Featured")}</option><option value="price-asc">{t("Price: low to high")}</option><option value="price-desc">{t("Price: high to low")}</option><option value="name">{t("Name: A to Z")}</option></select></label></div>
 <div aria-live="polite" aria-busy={loading}>{loading ? <p className="status">{t("Preparing the collection\u2026")}</p> : error ? <div role="alert" className="status error"><p>{t(error)}</p><button onClick={() => setRetry(n => n + 1)}>{t("Try again")}</button></div> : !data?.items.length ? <p className="status">{t("No products found. Try another search.")}</p> : <div className="products">{data.items.map(p => <article className="product" key={p.id} data-testid="product-card"><button className={'product-art art-' + p.category.toLowerCase()} onClick={() => open(p)}><span className="product-shape" aria-hidden="true"/>{p.imageId&&<img className="catalog-photo" src={'/api/media/'+p.imageId} alt="" loading="lazy"/>}<span className="category">{t(p.category)}</span>{" "}<span className="view">{t("Discover \u2197")}</span>{" "}<span className="visually-hidden">{t(p.name)}</span></button><a className="product-store" href={companies.find(c => c.id === p.companyId) ? '#store/' + companies.find(c => c.id === p.companyId)!.slug : '#stores'}>{t(companies.find(c => c.id === p.companyId)?.name ?? 'Maramigo')}</a><div className="product-title"><h3><button onClick={() => open(p)}>{t(p.name)}</button></h3><span>{t(money.format(p.price))}</span></div><p className="stock">{t(p.stockQuantity === 0 ? 'Currently out of stock' : p.stockQuantity === 1 ? 'Only one left' : 'Available in the collection')}</p><button className="product-add" data-testid="add-to-basket" aria-label={`${t(label(p))}: ${t(p.name)}`} disabled={basket.adding.includes(p.id) || p.stockQuantity === 0 || limit(p)} onClick={() => basket.add(p.id)}>{t(label(p))}</button><FavoriteButton id={p.id}/></article>)}</div>}</div>
 {data && data.total > 12 && <nav className="pagination" aria-label={t("Catalog pages")}><button disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>{t("Previous")}</button><span>{t(page + 1)}{t(" / ")}{t(Math.ceil(data.total / 12))}</span><button disabled={loading || (page + 1) * 12 >= data.total} onClick={() => setPage(p => p + 1)}>{t("Next")}</button></nav>}
 {selected && <dialog onKeyDown={trapDialogTab} tabIndex={-1} data-testid="product-dialog" id="product-dialog" aria-labelledby="product-detail-title" onCancel={() => setSelected(null)} onClick={e => { if (e.target === e.currentTarget)
        setSelected(null); }}><button className="close" aria-label={t("Close product details")} autoFocus onClick={() => setSelected(null)}>{t("\u00D7")}</button><p className="eyebrow">{t(selected.category)}{t(" / ")}{t(selected.sku)}</p><h2 id="product-detail-title">{t(selected.name)}</h2><p>{t(selected.description)}</p><p className="price">{t(money.format(selected.price))}</p><p>{t(selected.stockQuantity > 0 ? 'Available in the collection' : 'Out of stock')}</p><button className="product-add" data-testid="add-to-basket" aria-label={`${t(label(selected))}: ${t(selected.name)}`} disabled={basket.adding.includes(selected.id) || selected.stockQuantity === 0 || limit(selected)} onClick={() => basket.add(selected.id)}>{t(label(selected))}</button>{basket.message && <p role={basket.message.kind === 'error' ? 'alert' : 'status'}>{t(basket.message.text)}</p>}<a className="dialog-basket-link" href="#basket">{t("View your basket \u2197")}</a></dialog>}
 </section>;
}
function App() {
    const {language} = useLanguage();
    const session = useSession();
    const basket = useCart();
    const [route, setRoute] = useState(window.location.hash || '#home');
    const previousRoute=useRef(route);
    useEffect(()=>{if((session.user?.role==='EMPLOYEE'||session.user?.role==='MERCHANT')&&(['#basket','#checkout','#orders'].includes(route)||route.startsWith('#order/')))window.location.hash=session.user.role==='MERCHANT'?'#merchant':'#staff-orders';},[session.user?.role,route]);
    const [storeQuery,setStoreQuery]=useState(''),[storePage,setStorePage]=useState(0);
    const [companies, setCompanies] = useState<Company[]>([]);
    useEffect(()=>{const count=companies.filter(c=>c.name.toLocaleLowerCase().includes(storeQuery.toLocaleLowerCase())).length;setStorePage(page=>Math.min(page,Math.max(0,Math.ceil(count/6)-1)));},[companies,storeQuery]);
    const [storeError, setStoreError] = useState('');
    const [refresh, setRefresh] = useState(0);
    useEffect(() => { const titles:Record<string,string>={'#privacy':'Privacy & your data','#terms':'Terms of the demo','#accessibility':'Accessibility','#help':'Help & contact','#two-step':'Two-step verification','#forgot-password':'Reset your password','#reset-password':'Reset your password','#verify-email':'Verify your email','#home':'Home','#collection':'The collection','#stores':'Our stores','#story':'Our story','#login':'Sign in','#register':'Create account','#account':'My account','#basket':'Your basket.','#checkout':'Checkout','#orders':'My orders','#admin':'Product management','#admin-orders':'Order management','#dashboard':'Dashboard','#owner-login':'Owner sign-in','#staff-login':'Employee sign-in','#staff-register':'Accept your employee invitation','#owner':'Business & employees','#staff-orders':'Orders & deliveries','#join-business':'Join with your business','#merchant':'My business dashboard','#marketplace-admin':'Marketplace management'}; document.title='Maramigo · '+t(titles[route] ?? (route.startsWith('#confirm-email-change/')?'Verify your email':route.startsWith('#reset-password/')?'Reset your password':route.startsWith('#verify-email/')?'Verify your email':route.startsWith('#staff-register/')?'Accept your employee invitation':route.startsWith('#store/')?'The store collection.':route.startsWith('#order/')?'Order details':'Page not found.')); },[route,language]);
    useEffect(() => { const change = () => setRoute(window.location.hash || '#home'); window.addEventListener('hashchange', change); return () => window.removeEventListener('hashchange', change); }, []);
    useEffect(() => { if(previousRoute.current!==route)document.getElementById('page-content')?.focus({preventScroll:true});previousRoute.current=route; if (route === '#collection' || route === '#story' || route === '#stores')
        requestAnimationFrame(() => document.getElementById(route.slice(1))?.scrollIntoView());
    else
        window.scrollTo(0, 0); }, [route]);
    useEffect(() => { const abort = new AbortController(); fetch('/api/companies', { signal: abort.signal }).then(async (r) => { if (!r.ok)
        throw new Error('Could not load stores.'); setCompanies(await r.json() as Company[]); setStoreError(''); }).catch(e => { if (!abort.signal.aborted)
        setStoreError(e.message); }); return () => abort.abort(); }, [refresh,route,session.user?.id]);
    const storeRoute = route.startsWith('#store/');
    const company = storeRoute ? companies.find(c => c.slug === route.slice(7)) : undefined;
    const staffToken=route.match(/^#staff-register\/([A-Za-z0-9_-]{43})$/)?.[1];
    const home = ['#home', '#collection', '#story', ''].includes(route);
    return <><a className="skip-link" href="#page-content" data-testid="skip-content" onClick={e => {e.preventDefault();document.getElementById('page-content')?.focus();}}>{t('Skip to content')}</a><div className="announcement" role="region" aria-label={t("A portfolio by Maram Abbas ")}>{t("A portfolio by Maram Abbas ")}<span>{t("CONSIDERED LIVING \u00B7 BEAUTIFULLY CONNECTED")}</span></div><header className="site-header"><a className="wordmark" href="#home" aria-label={t("Maramigo home")}>{t("maramigo")}<span>{t("\u00AE")}</span></a><nav aria-label={t("Main navigation")}><a href="#collection">{t("The collection")}</a><a href="#stores">{t("Our stores")}</a><a href="#story">{t("Our story")}</a></nav><div className="header-account"><LanguagePicker />{session.user?.role!=='EMPLOYEE'&&session.user?.role!=='MERCHANT'&&<a className="basket-link" href="#basket" data-testid="basket-link">{t("Basket (")}{t(basket.items.reduce((s, i) => s + i.quantity, 0))}{t(")")}</a>}<a className="join-business" href="#join-business">{t('Join with your business')}</a><AccountPanel /></div></header>
 {basket.storageWarning && <p className="basket-storage-warning" role="status">{t(basket.storageWarning)}</p>}{basket.message && <div className={'basket-feedback ' + (basket.message.kind === 'error' ? 'error' : '')} role={basket.message.kind === 'error' ? 'alert' : 'status'}><span>{t(basket.message.text)}</span>{route !== '#basket' && <a href="#basket">{t("View basket \u2197")}</a>}</div>}
 <div id="page-content" tabIndex={-1} className="page-content"><PageTools route={route}/>{!knownRoute(route) && <main className="simple-page"><h1>{t('Page not found.')}</h1><a className="gold-button" href="#home">{t('Return to the storefront')}</a></main>}{(home || route === '#stores') && <main id="home"><section className="hero"><div className="hero-copy"><p className="eyebrow">{t("SMALL RITUALS. EXTRAORDINARY DETAILS.")}</p><h1>{t("Everyday,")}<br /><em>{t("elevated.")}</em></h1><p>{t("Discover a world of considered stores.")}<br />{t("Beautiful essentials. One seamless experience.")}</p><a className="button gold-button" href="#collection">{t("Explore the collection \u2197")}</a><div className="hero-note">{t("THE MARAMIGO EDIT / 2026")}</div></div><div className="hero-art" role="img" aria-label={t("Sculptural golden silk")}><div className="orb"/><div className="silk silk-one"/><div className="silk silk-two"/><span className="art-label">{t("a brighter kind of everyday.")}</span></div></section><HomeDiscovery companies={companies}/><section id="stores" className="stores-section"><div className="section-top"><div><p className="eyebrow">{t("A WORLD OF GOOD TASTE")}</p><h2>{t("Find your kind of beautiful.")}</h2></div><p>{t('Independent stores. One destination.')}<br />{t("Fictional demonstration brands.")}</p></div>{storeError ? <div role="alert"><p>{t(storeError)}</p><button onClick={() => setRefresh(n => n + 1)}>{t("Try again")}</button></div> : <><label className="store-search">{t('Find a store')}<input type="search" value={storeQuery} onChange={e=>{setStoreQuery(e.target.value);setStorePage(0);}}/></label>{storeQuery.trim()&&!companies.some(c=>c.name.toLocaleLowerCase().includes(storeQuery.toLocaleLowerCase()))&&<p role="status">{t('No stores found. Try another search.')}</p>}<div className="store-grid">{companies.filter(c=>c.name.toLocaleLowerCase().includes(storeQuery.toLocaleLowerCase())).slice(storePage*6,storePage*6+6).map((c, i) => <a className={'store-card store-tone-' + i} key={c.id} href={'#store/' + c.slug}><span className="store-monogram" aria-hidden="true">{c.logoId?<img className="store-logo" src={'/api/media/'+c.logoId} alt=""/>:t(c.monogram)}</span><span className="demo-store-badge">{t(c.demo?"DEMO STORE":"INDEPENDENT STORE")}</span><h3>{t(c.name)}</h3><p>{t(c.description)}</p><span className="store-explore">{t("Explore store \u2197")}</span></a>)}</div><nav className="pagination"><button disabled={storePage===0} onClick={()=>setStorePage(n=>n-1)}>{t('Previous')}</button><span>{storePage+1}</span><button disabled={(storePage+1)*6>=companies.filter(c=>c.name.toLocaleLowerCase().includes(storeQuery.toLocaleLowerCase())).length} onClick={()=>setStorePage(n=>n+1)}>{t('Next')}</button></nav></>}</section><CatalogView key={'all-' + refresh} companies={companies}/><section id="story" className="story"><div className="story-mark" aria-hidden="true">{t("m.")}</div><div><p className="eyebrow">{t("THE MARAMIGO PHILOSOPHY")}</p><h2>{t("Made for the moments")}<br /><em>{t("in between.")}</em></h2><p>{t("Beautiful things deserve thoughtful experiences. Discover independent demonstration stores, build your basket and enjoy a simpler way to shop.")}</p><p className="signature">{t("Created by Maram Abbas")}</p></div></section></main>}
 {storeRoute && (company ? <main className="store-page"><section className="store-hero"><a href="#stores">{t("\u2190 All stores")}</a><p className="eyebrow">{t(company.demo?"DEMO STORE / MARAMIGO":"INDEPENDENT STORE")}</p><div className="store-hero-monogram" aria-hidden="true">{company.logoId?<img className="store-logo" src={'/api/media/'+company.logoId} alt=""/>:t(company.monogram)}</div><h1>{t(company.name)}</h1><p>{t(company.description)}</p>{company.demo&&<span>{t("Fictional demonstration brand. No affiliation with real companies.")}</span>}</section><CatalogView key={company.id} company={company} companies={companies}/></main> : <main className="simple-page"><h1>{t(companies.length ? 'Store not found.' : 'Loading stores…')}</h1>{storeError && <p role="alert">{t(storeError)}</p>}<a href="#stores">{t("All stores")}</a></main>)}
 {(route==='#forgot-password'||route==='#reset-password'||/^#reset-password\/[A-Za-z0-9_-]{43}$/.test(route))&&<AccountRecoveryPage key={route} kind="reset" onReset={session.refresh} token={route.startsWith('#reset-password/')?route.slice(16):undefined}/>}
 {(route==='#verify-email'||/^#verify-email\/[A-Za-z0-9_-]{43}$/.test(route))&&<AccountRecoveryPage key={route} kind="verify" token={route.startsWith('#verify-email/')?route.slice(14):undefined}/>}
 {['#privacy','#terms','#accessibility','#help'].includes(route)&&<PublicInfo page={route}/>} {route.startsWith('#confirm-email-change/')&&/^#confirm-email-change\/[A-Za-z0-9_-]{43}$/.test(route)&&<AccountRecoveryPage kind="email-change" token={route.slice(22)} onReset={session.refresh}/>} {route==='#two-step'&&<MfaChallenge/>}{route === '#join-business'&&<BusinessRegistration/>}{route === '#merchant'&&<MerchantDashboard key={session.user?.id}/>}{route === '#marketplace-admin'&&<MarketplaceAdmin/>}{route === '#login' && <AuthPage key="login"/>}{route === '#owner-login' && <AuthPage key="owner-login"/>}{route === '#staff-login' && <AuthPage key="staff-login"/>}{route === '#owner' && <OwnerWorkspace/>}{route === '#staff-orders' && <StaffWorkspace/>}{(route === '#staff-register'||staffToken)&&<StaffRegistrationPage key={staffToken??'manual'} token={staffToken}/>}{route === '#register' && <AuthPage key="register" register/>}{route === '#account' && <AccountPage />}{route === '#basket' && <CartPage />}{route === '#checkout' && <CheckoutPage />}{route === '#orders' && <OrdersPage />}{route === '#admin-orders' && <AdminOrders />}{route === '#dashboard' && <React.Suspense fallback={<main className="simple-page"><p>{t('Loading dashboard…')}</p></main>}><Dashboard /></React.Suspense>}{route.startsWith('#order/') && <OrdersPage number={route.slice(7)}/>}{route === '#admin' && <AdminProducts key={(session.user?.id ?? 'guest') + ':' + session.user?.role} onCatalogChanged={() => setRefresh(n => n + 1)}/>}
 </div><aside className="demo-note">{t("Demo marketplace. No real payments are accepted.")}</aside><footer><a className="wordmark" href="#home">{t("maramigo")}<span>{t("\u00AE")}</span></a><nav aria-label={t('Information')}><a href="#privacy">{t('Privacy & your data')}</a><a href="#terms">{t('Terms of the demo')}</a><a href="#accessibility">{t('Accessibility')}</a><a href="#help">{t('Help & contact')}</a></nav><p>{t("Designed & developed by Maram Abbas")}</p><span>{t("\u00A9 ")}{t(new Date().getFullYear())}{t(" Maramigo")}</span></footer></>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><I18nProvider><SessionProvider><CartProvider><App /></CartProvider></SessionProvider></I18nProvider></React.StrictMode>);








