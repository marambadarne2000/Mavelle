import {formatMoney,formatTime} from '../i18n';
import { t, useLanguage } from '../i18n';
import { useEffect, useState, type FormEvent } from 'react';
import { check } from '../api';
import { useCart } from './CartContext';
import { isCartProduct, resolveCart, type CartItem, type CartProduct } from './model';
import './cart.css';
const money = { format: formatMoney };
const clock = { format: formatTime };
function QuantityEditor({ item, product }: {
    item: CartItem;
    product: CartProduct;
}) {
    useLanguage();
    const basket = useCart();
    const [draft, setDraft] = useState(String(item.quantity));
    useEffect(() => setDraft(String(item.quantity)), [item.quantity]);
    const maximum = Math.min(99, product.stockQuantity);
    const update = (quantity: number) => basket.quantity(item.productId, quantity, product.stockQuantity);
    function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); update(Number(draft)); }
    return <form className="basket-quantity" onSubmit={submit}>
    <label htmlFor={`quantity-${item.productId}`}>{t("Quantity")}</label>
    <div><button type="button" disabled={item.quantity <= 1 || item.quantity - 1 > maximum} aria-label={t(`Decrease quantity for ${product.name}`)} onClick={() => update(item.quantity - 1)}>{t("\u2212")}</button>
      <input id={`quantity-${item.productId}`} aria-label={t(`Quantity for ${product.name}`)} type="number" required min="1" max={Math.max(1, maximum)} step="1" value={draft} disabled={maximum === 0} onChange={e => setDraft(e.target.value)}/>
      <button type="button" disabled={item.quantity >= maximum} aria-label={t(`Increase quantity for ${product.name}`)} onClick={() => update(item.quantity + 1)}>{t("+")}</button>
      <button className="basket-update" type="submit" disabled={maximum === 0} aria-label={t(`Update quantity for ${product.name}`)}>{t("Update")}</button></div>
    {maximum > 0 && item.quantity > maximum && <button className="basket-text-button" type="button" onClick={() => update(maximum)}>{t("Use available quantity (")}{maximum}{t(")")}</button>}
  </form>;
}
export function CartPage() {
    useLanguage();
    const basket = useCart();
    const [products, setProducts] = useState<CartProduct[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const [refresh, setRefresh] = useState(0);
    const [checkedAt, setCheckedAt] = useState<Date | null>(null);
    const [loadedIds, setLoadedIds] = useState('');
    const [changedPrices, setChangedPrices] = useState<number[]>([]);
    const ids = basket.items.map(item => item.productId).join(',');
    const observePrices = basket.observePrices;
    useEffect(() => {
        const update = () => setRefresh(n => n + 1);
        window.addEventListener('focus', update);
        return () => window.removeEventListener('focus', update);
    }, []);
    useEffect(() => {
        const abort = new AbortController();
        if (!ids) {
            setProducts([]);
            setError('');
            setLoading(false);
            setChangedPrices([]);
            return;
        }
        setLoading(true);
        setError('');
        const timer = window.setTimeout(() => abort.abort(new Error('Availability check timed out. Please try again.')), 15000);
        fetch(`/api/products/lookup?ids=${ids}`, { signal: abort.signal }).then(async (response) => {
            await check(response);
            const data: unknown = await response.json();
            if (abort.signal.aborted)
                return;
            const requested = new Set(ids.split(',').map(Number));
            if (!Array.isArray(data) || !data.every(isCartProduct) || data.some(p => !requested.has(p.id)))
                throw new Error('The product information could not be verified.');
            const changed = observePrices(data);
            setChangedPrices(previous => [...new Set([...previous, ...changed])].filter(id => data.some(p => p.id === id)));
            setProducts(data);
            setLoadedIds(ids);
            setCheckedAt(new Date());
        }).catch(e => {
            if (!abort.signal.aborted || abort.signal.reason instanceof Error && abort.signal.reason.message.includes('timed out'))
                setError(e instanceof Error ? e.message : 'Could not check product availability.');
        })
            .finally(() => {
            window.clearTimeout(timer);
            if (!abort.signal.aborted || abort.signal.reason instanceof Error && abort.signal.reason.message.includes('timed out'))
                setLoading(false);
        });
        return () => { window.clearTimeout(timer); abort.abort(); };
    }, [ids, refresh, observePrices]);
    const checking = loading || !error && loadedIds !== ids;
    const checked = !checking && !error;
    const resolved = checked ? resolveCart(basket.items, products) : null;
    const unitCount = basket.items.reduce((sum, item) => sum + item.quantity, 0);
    return <main className="basket-page" id="basket">
    <div className="basket-heading"><div><p className="eyebrow">{t("MAVELLE / YOUR SELECTION")}</p><h1>{t("Your basket.")}</h1><p>{t("A few little things to make the everyday feel special.")}</p></div><a href="#collection">{t("Continue exploring \u2197")}</a></div>
    {basket.items.length === 0 ? <section className="basket-empty"><span aria-hidden="true">{t("\u25C7")}</span><h2>{t("Room for something lovely.")}</h2><p>{t("Your basket is empty. Find your next everyday essential.")}</p><a className="button" href="#collection">{t("Explore the collection \u2197")}</a></section> : <>
      <div className="basket-toolbar"><span>{basket.items.length} · {unitCount}</span><div><button data-testid="cart-refresh" disabled={loading} onClick={() => setRefresh(n => n + 1)}>{t("Refresh prices & availability")}</button><button data-testid="cart-clear" onClick={basket.clear}>{t("Clear basket")}</button></div></div>
      {checking && <p className="status" role="status">{t("Checking current prices and availability\u2026")}</p>}
      {error && <div className="basket-error" role="alert"><p>{t(error)}</p><p>{t("Your saved items are still in the basket. Prices have not been confirmed.")}</p><button onClick={() => setRefresh(n => n + 1)}>{t("Try again")}</button></div>}
      {checked && resolved && <div className="basket-columns"><section aria-label={t("Basket items")} className="basket-items">
        {resolved.rows.map(row => <article className="basket-item" key={row.productId} data-testid="basket-item">
          <div className={`basket-art art-${row.product?.category.toLowerCase() ?? 'unavailable'}`} aria-hidden="true"><span className="product-shape"/></div>
          <div className="basket-item-details"><p className="eyebrow">{t(row.product?.category ?? 'Unavailable')}</p><h2>{t(row.product?.name ?? 'Unavailable product')}</h2>
            {row.product && <p className="basket-unit-price">{money.format(row.product.price)}{t(" per unit")}</p>}
            {changedPrices.includes(row.productId) && <p className="basket-price-update">{t("Price updated. Please review.")}</p>}
            {row.issue && <p className="basket-item-issue">{t(row.issue === 'unavailable' ? 'This product is no longer available.' : row.issue === 'out-of-stock' ? 'This product is out of stock.' : 'Requested quantity exceeds current stock.')}</p>}
            {row.product ? <QuantityEditor item={row} product={row.product}/> : <p>{t("Requested quantity: ")}{row.quantity}</p>}
            <button data-testid="cart-remove" data-product-id={row.productId} className="basket-text-button" aria-label={`${t("Remove")}: ${t(row.product?.name ?? "unavailable product")}`} onClick={() => basket.remove(row.productId)}>{t("Remove")}</button>
          </div><div className="basket-line-total">{row.issue ? '—' : money.format(row.lineTotal! / 100)}</div>
        </article>)}
      </section><aside className="basket-summary" aria-label={t("Basket summary")}><p className="eyebrow">{t("THE LITTLE DETAILS")}</p><h2>{t("Your selection")}</h2><div className="basket-subtotal"><span>{t("Available items subtotal")}</span><strong data-testid="basket-subtotal">{money.format(resolved.subtotal / 100)}</strong></div>
        {resolved.needsAttention && <p className="basket-summary-warning">{t("Items needing attention are excluded from this subtotal. Review or remove them above.")}</p>}
        <p>{t("Items are not reserved in your basket. Prices and availability will be checked again at checkout.")}</p>
        {checkedAt && <p className="basket-checked">{t("Last checked ")}{clock.format(checkedAt)}{t(" \u00B7 Jerusalem time")}</p>}
        {!resolved.needsAttention && <a className="gold-button checkout-link" href="#checkout">{t("Proceed to checkout")}</a>}
        <div className="basket-demo">{t("Demo checkout only. No real payments are accepted.")}</div>
      </aside></div>}
    </>}
  </main>;
}



