import {OrderAftercare} from './Portal';
import {DeliveryBreakdown} from './Marketplace';
import {formatMoney,formatDate} from './i18n';
import { t, useLanguage } from './i18n';
import { useEffect, useRef, useState, type FormEvent } from 'react';
import { useCart } from './cart/CartContext';
import { isCartProduct, resolveCart, type CartProduct, type CartItem } from './cart/model';
import { useSession } from './session';
import { check, ApiError } from './api';
type Shipping = {
    fullName: string;
    phone: string;
    address: string;
    city: string;
    postalCode: string;
    country: string;
};
type Order = {
    orderNumber: string;
    status: string;
    total: number;
    currency: string;
    createdAt: string;
    shipping: Shipping;
    items: {
        productId: number;
        sku: string;
        name: string;
        companyName: string;
        unitPrice: number;
        quantity: number;
        lineTotal: number;
    }[];
};
const money = { format: formatMoney };
const dates = { format: formatDate };
export function CheckoutPage() {
    useLanguage();
    const basket = useCart();
    const session = useSession();
    const [products, setProducts] = useState<CartProduct[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState('');
    const [paymentOutcome,setPaymentOutcome]=useState('SUCCESS');
    const [savedAddress,setSavedAddress]=useState<Partial<Shipping>|null>(null);
    useEffect(()=>{if(session.user?.role!=='CUSTOMER')return;const abort=new AbortController();fetch('/api/customer/profile',{signal:abort.signal}).then(async r=>{if(r.ok)setSavedAddress(await r.json() as Partial<Shipping>);}).catch(()=>{});return()=>abort.abort();},[session.user?.id,session.user?.role]);
    const [unknown, setUnknown] = useState(false);
    const [shipping, setShipping] = useState<Shipping>({ fullName: '', phone: '', address: '', city: '', postalCode: '', country: 'IL' });
    const frozen = useRef<{
        payload: unknown;
        key: string;
        items: CartItem[];
    } | null>(null);
    const submitting = useRef(false);
    const ids = basket.items.map(i => i.productId).join(',');
    useEffect(() => {
        if (!ids) {
            setLoading(false);
            return;
        }
        const abort = new AbortController();
        setLoading(true);
        fetch('/api/products/lookup?ids=' + ids, { signal: abort.signal }).then(async (r) => {
            await check(r);
            const data: unknown = await r.json();
            if (!Array.isArray(data) || !data.every(isCartProduct))
                throw new Error('Could not verify product prices.');
            setProducts(data);
        }).catch(e => {
            if (!abort.signal.aborted)
                setError(e.message);
        }).finally(() => {
            if (!abort.signal.aborted)
                setLoading(false);
        });
        return () => abort.abort();
    }, [ids]);
    const resolved = !loading ? resolveCart(basket.items, products) : null;
    async function send(retry = false) {
        if (submitting.current)
            return;
        submitting.current = true;
        setBusy(true);
        setError('');
        let orderSent = false;
        try {
            if (!retry) {
                if (!resolved || resolved.needsAttention)
                    throw new Error('Review your basket before checkout.');
                const payload = { items: basket.items.map(i => ({ productId: i.productId, quantity: i.quantity, expectedPrice: products.find(p => p.id === i.productId)!.price.toFixed(2) })), shipping,demoPaymentOutcome:paymentOutcome };
                const bytes = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(payload)));
                const digest = Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, '0')).join('');
                let key: string = crypto.randomUUID();
                try {
                    const old = JSON.parse(sessionStorage.getItem('maramigo.checkout') ?? 'null') as {
                        digest: string;
                        key: string;
                    } | null;
                    if (old?.digest === digest)
                        key = old.key;
                    sessionStorage.setItem('maramigo.checkout', JSON.stringify({ digest, key }));
                }
                catch { }
                frozen.current = { payload, key, items: basket.items.map(i => ({ ...i })) };
            }
            const attempt = frozen.current;
            if (!attempt)
                throw new Error('Review your basket before checkout.');
            const tokenResponse = await fetch('/api/auth/csrf');
            await check(tokenResponse);
            const csrf = await tokenResponse.json() as {
                headerName: string;
                token: string;
            };
            orderSent = true;
            const response = await fetch('/api/orders', { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': attempt.key, [csrf.headerName]: csrf.token }, body: JSON.stringify(attempt.payload), signal: AbortSignal.timeout(20000) });
            await check(response);
            const order = await response.json() as Order;
            if (!order.orderNumber?.startsWith('MG-') || !Array.isArray(order.items) || !order.items.length)
                throw new Error('The order response could not be verified.');
            try { sessionStorage.removeItem('maramigo.checkout'); } catch { }
            frozen.current = null;
            basket.fulfill(attempt.items);
            setUnknown(false);
            window.location.hash = '#order/' + encodeURIComponent(order.orderNumber);
        }
        catch (e) {
            if (e instanceof ApiError) {
                setUnknown(orderSent && e.status >= 500);
                if (e.status === 401)
                    session.requireLogin({ kind: 'return', returnTo: '#checkout' });
            }
            else
                setUnknown(orderSent);
            setError(e instanceof Error ? e.message : 'Could not confirm the order.');
        }
        finally {
            submitting.current = false;
            setBusy(false);
        }
    }
    function submit(e: FormEvent) { e.preventDefault(); void send(); }
    if (session.loading)
        return <main className="simple-page"><p>{t("Checking your account\u2026")}</p></main>;
    if (!session.user)
        return <main className="simple-page"><h1>{t("Sign in to checkout")}</h1><button className="gold-button" onClick={() => session.requireLogin({ kind: 'return', returnTo: '#checkout' })}>{t("Sign in")}</button></main>;
    if (!basket.items.length && !unknown)
        return <main className="simple-page"><h1>{t("Your basket is empty.")}</h1><a className="gold-button" href="#collection">{t("Explore the collection")}</a></main>;
    return <main className="checkout-page simple-page"><p className="eyebrow">{t("MARAMIGO / CHECKOUT")}</p><h1>{t("A little closer.")}</h1><ol className="flow-steps" aria-label={t('Checkout')}><li>1 · {t('Shipping details')}</li><li>2 · {t('Simulated payment')}</li><li>3 · {t('Order confirmation')}</li></ol><p>{t("Demo checkout only. No real payments are accepted.")}</p>{error && <div className="account-error" role="alert">{t(error)}</div>}{unknown && <section className="order-uncertain"><h2>{t("Order confirmation is uncertain.")}</h2><p>{t("Your basket has not been cleared. Check your orders or retry the same request safely.")}</p><button className="gold-button" disabled={busy} onClick={() => send(true)}>{t("Retry the same request")}</button><a className="outline-button" href="#orders">{t("My orders")}</a></section>}
 {loading ? <p>{t("Checking prices and availability\u2026")}</p> : resolved?.needsAttention ? <section><p>{t("Review your basket before checkout.")}</p><a className="gold-button" href="#basket">{t("Review basket")}</a></section> : <div className="checkout-columns"><form data-testid="checkout-form" className="checkout-form" onSubmit={submit}><h2>{t("Shipping details")}</h2>{savedAddress&&<button type="button" className="outline-button" onClick={()=>setShipping(old=>({...old,...Object.fromEntries(Object.entries(savedAddress).filter(([key])=>['fullName','phone','address','city'].includes(key)))}))}>{t('Use saved address')}</button>}<fieldset disabled={busy || unknown}>{(['fullName', 'phone', 'address', 'city', 'postalCode', 'country'] as const).map(key => <label key={key}>{t(({fullName: 'Full name', phone: 'Phone number', address: 'Street address', city: 'City', postalCode: 'Postal code', country: 'Country code'})[key])}<input data-testid={'shipping-' + key} type={key === 'phone' ? 'tel' : 'text'} required autoComplete={({ fullName: 'name', phone: 'tel', address: 'street-address', city: 'address-level2', postalCode: 'postal-code', country: 'country' })[key]} maxLength={key === 'country' ? 2 : key === 'address' ? 240 : key === 'fullName' ? 120 : key === 'city' ? 100 : key === 'phone' ? 40 : 20} value={shipping[key]} onChange={e => setShipping(old => ({ ...old, [key]: key === 'country' ? e.target.value.toUpperCase() : e.target.value }))}/></label>)}<label>{t('Choose a simulated payment result')}<select data-testid="payment-simulation" value={paymentOutcome} onChange={e=>setPaymentOutcome(e.target.value)}><option value="SUCCESS">{t('Simulated payment succeeds')}</option><option value="DECLINED">{t('Simulated payment is declined')}</option></select></label><button className="gold-button" data-testid="checkout-submit" type="submit">{t(busy ? 'Confirming…' : 'Confirm demo order')}</button></fieldset></form><aside className="checkout-review" aria-label={t("Order summary")}><h2>{t("Order summary")}</h2>{resolved?.rows.map(row => <p key={row.productId}><span>{t(row.product!.name)} × {row.quantity}</span><strong>{money.format(row.lineTotal! / 100)}</strong></p>)}<div className="checkout-total"><span>{t("Total")}</span><strong>{money.format((resolved?.subtotal ?? 0) / 100)}</strong></div><p>{t("No shipping fees or taxes are calculated in this demo.")}</p><a href="#basket">{t("Back to basket")}</a></aside></div>}
 </main>;
}
export function OrdersPage({ number }: {
    number?: string;
}) {
    useLanguage();
    const session = useSession();
    const [data, setData] = useState<Order[] | Order | null>(null);
    const [error, setError] = useState('');
    const [page, setPage] = useState(0);
    useEffect(() => {
        if (!session.user)
            return;
        const abort = new AbortController();
        setData(null);
        setError('');
        fetch(number ? '/api/orders/' + encodeURIComponent(number) : '/api/orders?page=' + page, { signal: abort.signal }).then(async (r) => { await check(r); setData(await r.json() as Order[] | Order); }).catch(e => {
            if (!abort.signal.aborted)
                setError(e.message);
        });
        return () => abort.abort();
    }, [number, page, session.user?.id]);
    if (!session.user)
        return <main className="simple-page"><h1>{t("Please sign in to continue.")}</h1><a className="gold-button" href="#login">{t("Sign in")}</a></main>;
    const order = data && !Array.isArray(data) ? data : null;
    return <main className="simple-page orders-page"><p className="eyebrow">{t("MARAMIGO / YOUR ORDERS")}</p><h1>{t(number ? 'Order details' : 'My orders')}</h1>{error && <p className="account-error" role="alert">{t(error)}</p>}{!data && !error && <p>{t("Loading orders\u2026")}</p>}{Array.isArray(data) && (data.length ? <><div className="order-list">{data.map(o => <a key={o.orderNumber} href={'#order/' + o.orderNumber}><span className="order-number" dir="ltr">{o.orderNumber}</span><span>{t(o.status)}</span><span>{dates.format(new Date(o.createdAt))}</span><strong>{money.format(o.total)}</strong><span>{t("View order \u2197")}</span></a>)}</div><div className="pagination"><button disabled={page === 0} onClick={() => setPage(p => p - 1)}>{t("Previous")}</button><span>{page + 1}</span><button disabled={data.length < 20} onClick={() => setPage(p => p + 1)}>{t("Next")}</button></div></> : <p>{t("No orders yet.")}</p>)}
 {order && <section className="order-confirmation"><div className="order-success-mark" aria-hidden="true">{t("\u2713")}</div><h2>{t(order.status === 'CANCELLED' ? 'Your order was cancelled.' : 'Your order is confirmed.')}</h2><p className="order-number" dir="ltr">{order.orderNumber}</p><p><span className="status-badge" data-status={order.status}>{t(order.status)}</span> · {dates.format(new Date(order.createdAt))}</p><div className="order-items">{order.items.map(item => <div key={item.productId}><span>{t(item.name)}<small>{item.companyName} · {item.sku} · {money.format(item.unitPrice)} × {item.quantity}</small></span><strong>{money.format(item.lineTotal)}</strong></div>)}</div><div className="checkout-total"><span>{t("Total")}</span><strong>{money.format(order.total)}</strong></div><h3>{t("Shipping details")}</h3><p>{order.shipping.fullName}<br />{order.shipping.address}<br />{order.shipping.city} {order.shipping.postalCode} · {order.shipping.country}</p><p className="demo-note">{t("Demo order confirmation. This is not a tax invoice. No payment was collected.")}</p><DeliveryBreakdown number={order.orderNumber}/>{session.user?.role==='CUSTOMER'&&<OrderAftercare key={order.orderNumber} number={order.orderNumber}/>}<div className="order-actions"><button className="outline-button" onClick={() => window.print()}>{t("Print confirmation")}</button><a className="gold-button" href="#orders">{t("My orders")}</a><a className="outline-button" href="#collection">{t("Continue shopping")}</a></div></section>}
 </main>;
}



