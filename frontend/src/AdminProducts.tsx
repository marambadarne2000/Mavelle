import {formatMoney,formatDate} from './i18n';
import { t, useLanguage } from './i18n';
import { useEffect, useState, type FormEvent } from 'react';
import { ApiError, check, write } from './api';
import './admin.css';
type Product = {
    id: number;
    sku: string;
    name: string;
    description: string;
    category: string;
    price: number;
    stockQuantity: number;
    active: boolean;
    version: number;
    companyId: number;
};
type ProductPage = {
    items: Product[];
    total: number;
    page: number;
    size: number;
};
type Movement = {
    id: number;
    delta: number;
    reason: string;
    actorId: number;
    occurredAt: string;
};
type MovementPage = {
    items: Movement[];
    total: number;
};
type Fields = {
    sku: string;
    name: string;
    description: string;
    category: string;
    price: string;
    active: boolean;
    companyId: string;
};
const emptyFields: Fields = { sku: '', name: '', description: '', category: 'Living', price: '', active: true, companyId: '1' };
const money = { format: formatMoney };
const dates = { format: formatDate };
const fieldsFrom = (p: Product): Fields => ({ sku: p.sku, name: p.name, description: p.description, category: p.category, price: p.price.toFixed(2), active: p.active, companyId: String(p.companyId ?? 1) });
export function AdminProducts({ onCatalogChanged }: {
    onCatalogChanged: () => void;
}) {
    useLanguage();
    const [access, setAccess] = useState<'checking' | 'allowed' | 'denied'>('checking');
    const [data, setData] = useState<ProductPage | null>(null);
    const [query, setQuery] = useState('');
    const [page, setPage] = useState(0);
    const [refresh, setRefresh] = useState(0);
    const [loading, setLoading] = useState(false);
    const [working, setWorking] = useState(false);
    const [error, setError] = useState('');
    const [notice, setNotice] = useState('');
    const [selected, setSelected] = useState<Product | null>(null);
    const [editing, setEditing] = useState(false);
    const [fields, setFields] = useState<Fields>(emptyFields);
    const [initialStock, setInitialStock] = useState('0');
    const [delta, setDelta] = useState('');
    const [reason, setReason] = useState('');
    const [history, setHistory] = useState<MovementPage | null>(null);
    const [historyError, setHistoryError] = useState('');
    const [historyPage, setHistoryPage] = useState(0);
    const [companies, setCompanies] = useState<{
        id: number;
        name: string;
    }[]>([]);
    useEffect(() => {
        fetch('/api/companies').then(async (r) => {
            await check(r);
            setCompanies(await r.json() as {
                id: number;
                name: string;
            }[]);
        }).catch(fail);
    }, [refresh]);
    function fail(e: unknown) {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
            setAccess('denied');
            setData(null);
            setSelected(null);
            setEditing(false);
        }
        setError(e instanceof Error ? e.message : 'Could not connect. Reload before retrying a change.');
    }
    useEffect(() => {
        const abort = new AbortController();
        fetch('/api/auth/me', { signal: abort.signal }).then(async (response) => {
            await check(response);
            const user = await response.json() as {
                role: string;
            };
            setAccess(user.role === 'ADMIN' ? 'allowed' : 'denied');
        }).catch(e => {
            if (!abort.signal.aborted) {
                setAccess('denied');
                fail(e);
            }
        });
        return () => abort.abort();
    }, []);
    useEffect(() => {
        if (access !== 'allowed')
            return;
        const abort = new AbortController();
        setLoading(true);
        const timer = window.setTimeout(async () => {
            try {
                const response = await fetch(`/api/admin/products?search=${encodeURIComponent(query)}&page=${page}&size=12`, { signal: abort.signal });
                await check(response);
                setData(await response.json() as ProductPage);
            }
            catch (e) {
                if (!abort.signal.aborted)
                    fail(e);
            }
            finally {
                if (!abort.signal.aborted)
                    setLoading(false);
            }
        }, 250);
        return () => { window.clearTimeout(timer); abort.abort(); };
    }, [access, query, page, refresh]);
    useEffect(() => {
        if (!selected || access !== 'allowed')
            return;
        const abort = new AbortController();
        setHistory(null);
        setHistoryError('');
        fetch(`/api/admin/products/${selected.id}/inventory-movements?page=${historyPage}&size=6`, { signal: abort.signal })
            .then(async (response) => { await check(response); setHistory(await response.json() as MovementPage); })
            .catch(e => {
            if (!abort.signal.aborted) {
                setHistoryError(e instanceof Error ? e.message : 'Could not load history.');
                if (e instanceof ApiError && (e.status === 401 || e.status === 403))
                    fail(e);
            }
        });
        return () => abort.abort();
    }, [selected?.id, selected?.version, historyPage, access]);
    function show(product: Product) {
        setSelected(product);
        setFields(fieldsFrom(product));
        setEditing(true);
        setInitialStock('0');
        setDelta('');
        setReason('');
        setHistoryPage(0);
    }
    async function load(id: number) {
        setWorking(true);
        setError('');
        setNotice('');
        try {
            const response = await fetch(`/api/admin/products/${id}`, { signal: AbortSignal.timeout(15000) });
            await check(response);
            show(await response.json() as Product);
        }
        catch (e) {
            fail(e);
        }
        finally {
            setWorking(false);
        }
    }
    function changed(product: Product, message: string) {
        show(product);
        setRefresh(n => n + 1);
        onCatalogChanged();
        setNotice(message);
    }
    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setError('');
        setNotice('');
        setWorking(true);
        try {
            const payload = selected ? { product: fields, version: selected.version } : { product: fields, initialStock: Number(initialStock) };
            const response = await write(selected ? `/api/admin/products/${selected.id}` : '/api/admin/products', JSON.stringify(payload), 'application/json', selected ? 'PUT' : 'POST');
            await check(response);
            changed(await response.json() as Product, selected ? 'Product changes saved.' : 'Product created.');
        }
        catch (e) {
            fail(e);
        }
        finally {
            setWorking(false);
        }
    }
    async function toggle() {
        if (!selected)
            return;
        setWorking(true);
        setError('');
        setNotice('');
        try {
            const response = await write(`/api/admin/products/${selected.id}`, JSON.stringify({ product: { ...fieldsFrom(selected), active: !selected.active }, version: selected.version }), 'application/json', 'PUT');
            await check(response);
            changed(await response.json() as Product, selected.active ? 'Product deactivated. It is hidden from the storefront.' : 'Product reactivated.');
        }
        catch (e) {
            fail(e);
        }
        finally {
            setWorking(false);
        }
    }
    async function adjust(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!selected)
            return;
        setError('');
        setNotice('');
        if (!Number.isInteger(Number(delta)) || Number(delta) === 0) {
            setError('Enter a nonzero whole-number quantity change.');
            return;
        }
        setWorking(true);
        try {
            const response = await write(`/api/admin/products/${selected.id}/stock`, JSON.stringify({ delta: Number(delta), reason, version: selected.version }), 'application/json', 'PATCH');
            await check(response);
            changed(await response.json() as Product, 'Stock updated and recorded in the movement history.');
        }
        catch (e) {
            fail(e);
        }
        finally {
            setWorking(false);
        }
    }
    function field(key: keyof Fields, value: string | boolean) { setFields(old => ({ ...old, [key]: value })); }
    if (access === 'checking')
        return <main className="admin-shell"><p className="status">{t("Checking administrator access\u2026")}</p></main>;
    if (access === 'denied')
        return <main className="admin-shell"><h1>{t("Administrator access required.")}</h1><p>{t("Sign in with an administrator account using the account button above.")}</p>{error && <p role="alert">{t(error)}</p>}<a className="button" href="#home">{t("Return to the storefront")}</a></main>;
    return <main className="admin-shell">
    <div className="admin-heading"><div><p className="eyebrow">{t("MARAMIGO / CONTROL CENTER")}</p><h1>{t("Welcome back, ")}<em>{t("Maram Abbas.")}</em></h1><p>{t("Chief Executive Officer \u00B7 Products & inventory")}</p></div><a href="#collection" className="admin-back">{t("View storefront \u2197")}</a></div>
    <div className="admin-toolbar"><div><h2>{t("Product library")}</h2><p>{data?.total ?? 0}</p></div><button className="admin-primary" disabled={working} onClick={() => { setSelected(null); setFields(emptyFields); setEditing(true); setInitialStock('0'); setError(''); setNotice(''); }}>{t("+ Add product")}</button></div>
    {error && <div className="admin-alert" role="alert">{t(error)} {selected && <button disabled={working} onClick={() => load(selected.id)}>{t("Reload product (discard edits)")}</button>}</div>}
    {notice && <p className="admin-notice" role="status">{t(notice)}</p>}
    <div className="admin-columns"><section className="admin-card" aria-label={t("Product library")}>
      <label className="admin-label">{t("Search name or SKU")}<input type="search" maxLength={100} value={query} onChange={e => { setQuery(e.target.value); setPage(0); setError(''); }}/></label>
      <div aria-busy={loading}>
        {loading ? <p className="admin-empty" role="status">{t("Loading products\u2026")}</p> : data?.items.length === 0 ? <p className="admin-empty">{t("No products match this search.")}</p> : <div className="admin-product-list">{data?.items.map(p => <button key={p.id} className={`admin-product-row ${selected?.id === p.id ? 'selected' : ''}`} disabled={working} onClick={() => load(p.id)} data-testid="admin-product-row"><span><strong>{t(p.name)}</strong><small>{p.sku} · {t(p.category)}</small></span><span className="admin-product-meta"><strong>{money.format(p.price)}</strong><small>{p.stockQuantity}{t(" in stock · ")}{t(p.active ? 'Active' : 'Inactive')}</small></span></button>)}</div>}
      </div>
      {data && data.total > 12 && <div className="admin-pagination"><button disabled={loading || working || page === 0} onClick={() => setPage(n => n - 1)}>{t("Previous")}</button><span>{t("Page ")}{page + 1}{t(" of ")}{Math.ceil(data.total / 12)}</span><button disabled={loading || working || (page + 1) * 12 >= data.total} onClick={() => setPage(n => n + 1)}>{t("Next")}</button></div>}
      <button className="admin-link" disabled={working || loading} onClick={() => { setError(''); setRefresh(n => n + 1); }}>{t("Refresh library")}</button>
    </section>
    <section className="admin-card" aria-label={t("Product editor")}>
      {!editing ? <div className="admin-empty"><h2>{t("Considered details.")}</h2><p>{t("Select a product to edit, or add something new to the collection.")}</p></div> : <>
        <div className="admin-editor-title"><h2>{t(selected ? 'Edit product' : 'Add product')}</h2><button className="admin-link" disabled={working} onClick={() => { setEditing(false); setSelected(null); setNotice(''); setError(''); }}>{t("Close editor")}</button></div>
        <form data-testid="product-form" onSubmit={save} aria-busy={working}><fieldset disabled={working}>
          <label className="admin-label">{t("SKU")}<input required minLength={2} maxLength={80} pattern="[A-Za-z0-9][A-Za-z0-9_-]{1,79}" value={fields.sku} onChange={e => field('sku', e.target.value.toUpperCase())}/></label>
          <label className="admin-label">{t("Store")}<select required value={fields.companyId} onChange={e => field('companyId', e.target.value)}>{companies.map(c => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
          <label className="admin-label">{t("Product name")}<input required maxLength={160} value={fields.name} onChange={e => field('name', e.target.value)}/></label>
          <label className="admin-label">{t("Description")}<textarea required maxLength={4000} rows={3} value={fields.description} onChange={e => field('description', e.target.value)}/></label>
          <div className="admin-form-pair"><label className="admin-label">{t("Category")}<select value={fields.category} onChange={e => field('category', e.target.value)}>{['Living', 'Wear', 'Accessories', 'Wellness'].map(c => <option key={c} value={c}>{t(c)}</option>)}</select></label><label className="admin-label">{t("Price (ILS)")}<input type="number" required min="0.01" max="9999999999.99" step="0.01" value={fields.price} onChange={e => field('price', e.target.value)}/></label></div>
          {!selected && <label className="admin-label">{t("Initial stock")}<input required type="number" min="0" max="1000000" step="1" value={initialStock} onChange={e => setInitialStock(e.target.value)}/></label>}
          <label className="admin-checkbox"><input type="checkbox" checked={fields.active} onChange={e => field('active', e.target.checked)}/>{t(" Active in storefront")}</label>
          <button className="admin-primary" type="submit">{t(working ? 'Saving…' : selected ? 'Save changes' : 'Create product')}</button>
        </fieldset></form>
        {selected && <>
          <div className="admin-stock-summary"><strong>{selected.stockQuantity}{t(" units in stock")}</strong><button disabled={working} onClick={toggle}>{t(selected.active ? 'Deactivate product' : 'Activate product')}</button></div>
          <form data-testid="stock-adjustment-form" className="admin-stock-form" onSubmit={adjust}><h3>{t("Adjust stock")}</h3><p>{t("Enter a positive or negative change. Every adjustment is recorded.")}</p><fieldset disabled={working}><label className="admin-label">{t("Quantity change")}<input required type="number" min="-1000000" max="1000000" step="1" placeholder={t("e.g. 5 or -1")} value={delta} onChange={e => setDelta(e.target.value)}/></label><label className="admin-label">{t("Reason")}<input required maxLength={240} value={reason} onChange={e => setReason(e.target.value)}/></label><button type="submit" className="admin-secondary">{t("Apply stock change")}</button></fieldset></form>
          <section className="admin-history" aria-label={t("Inventory movement history")}><h3>{t("Movement history")}</h3><p className="admin-muted">{t("Times shown in Asia/Jerusalem. History shows recorded stock changes; starting demo quantities are not listed.")}</p>{historyError ? <p role="alert">{t(historyError)}</p> : !history ? <p>{t("Loading history\u2026")}</p> : history.items.length === 0 ? <p>{t("No inventory movements yet.")}</p> : <ul>{history.items.map(m => <li key={m.id}><strong>{m.delta > 0 ? '+' : ''}{m.delta}</strong><div><span>{m.reason}</span><small>{dates.format(new Date(m.occurredAt))}{t(" · Administrator #")}{m.actorId}</small></div></li>)}</ul>}
          {history && history.total > 6 && <div className="admin-pagination"><button disabled={working || historyPage === 0} onClick={() => setHistoryPage(n => n - 1)}>{t("Newer movements")}</button><span>{historyPage + 1} / {Math.ceil(history.total / 6)}</span><button disabled={working || (historyPage + 1) * 6 >= history.total} onClick={() => setHistoryPage(n => n + 1)}>{t("Older movements")}</button></div>}</section>
        </>}
      </>}
    </section></div>
  </main>;
}


