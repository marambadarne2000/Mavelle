import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { check } from '../api';
import { CART_KEY, addCartItem, isCartProduct, priceInAgorot, restoreCart, serializeCart, setCartQuantity, removePurchased, type CartItem, type CartProduct, type CartIssue } from './model';
import { useSession } from '../session';

type Message = { kind: 'success' | 'error'; text: string } | null;
type Basket = {
  items: CartItem[];
  adding: number[];
  message: Message;
  storageWarning: string;
  add: (productId: number) => Promise<void>;
  quantity: (productId: number, quantity: number, available: number) => boolean;
  remove: (productId: number) => void;
  clear: () => void;
  fulfill: (purchased:CartItem[])=>void;
  observePrices: (products: CartProduct[]) => number[];
};
const CartContext = createContext<Basket | null>(null);
const issueMessage = (issue: CartIssue) => issue === 'full' ? 'Your basket can contain up to 50 different items.'
  : issue === 'limit' ? 'That quantity exceeds the available stock or the 99-unit limit.'
  : issue === 'unavailable' ? 'This item is out of stock.' : 'Enter a valid whole-number quantity.';

function loadSavedBasket() {
  try {
    const restored = restoreCart(window.localStorage.getItem(CART_KEY));
    return { items: restored.items, warning: restored.repaired ? 'Some saved basket data could not be restored. Please review your items.' : '' };
  } catch { return { items: [] as CartItem[], warning: 'Your basket works in this tab, but this browser cannot save it after you close the page.' }; }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const session=useSession();
  const [saved] = useState(loadSavedBasket);
  const [items, setItems] = useState<CartItem[]>(saved.items);
  const current = useRef(items);
  const pending = useRef(new Map<number, symbol>());
  const knownPrices = useRef(new Map<number, number>());
  const [adding, setAdding] = useState<number[]>([]);
  const [message, setMessage] = useState<Message>(null);
  const [storageWarning, setStorageWarning] = useState(saved.warning);
  useEffect(()=>{if(session.user){const intent=session.takeIntent();if(intent?.kind==='add')void add(intent.productId);}},[session.user?.id]);

  const commit = useCallback((next: CartItem[]) => {
    const ids = new Set(next.map(item => item.productId));
    for (const id of knownPrices.current.keys()) if (!ids.has(id)) knownPrices.current.delete(id);
    current.current = next; setItems(next);
    try { window.localStorage.setItem(CART_KEY, serializeCart(next)); setStorageWarning(''); }
    catch { setStorageWarning('Your changes work in this tab, but this browser could not save the basket.'); }
  }, []);
  useEffect(() => {
    const synchronize = (event: StorageEvent) => {
      if (event.key !== CART_KEY && event.key !== null) return;
      const restored = restoreCart(event.newValue);
      current.current = restored.items; setItems(restored.items);
      const ids = new Set(restored.items.map(item => item.productId));
      for (const id of knownPrices.current.keys()) if (!ids.has(id)) knownPrices.current.delete(id);
      pending.current.clear(); setAdding([]);
      if (restored.repaired) setStorageWarning('Basket data from another tab could not be fully restored. Please review your items.');
      setMessage(null);
    };
    window.addEventListener('storage', synchronize);
    return () => window.removeEventListener('storage', synchronize);
  }, []);

  const observePrices = useCallback((products: CartProduct[]) => {
    const activeIds = new Set(current.current.map(item => item.productId));
    for (const id of knownPrices.current.keys()) if (!activeIds.has(id)) knownPrices.current.delete(id);
    const changed: number[] = [];
    for (const product of products) {
      const cents = priceInAgorot(product.price);
      const old = knownPrices.current.get(product.id);
      if (old !== undefined && old !== cents) changed.push(product.id);
      if (activeIds.has(product.id)) knownPrices.current.set(product.id, cents);
    }
    return changed;
  }, []);

  async function add(productId: number) {
    if((session.user?.role==='EMPLOYEE'||session.user?.role==='MERCHANT')){setMessage({kind:'error',text:'Use a customer account to shop.'});return;}
    if (pending.current.has(productId)) return;
    const operation = Symbol('basket-add');
    pending.current.set(productId, operation); setAdding([...pending.current.keys()]); setMessage(null);
    try {
      const user=await session.refresh();
      if(pending.current.get(productId)!==operation)return;
      if(!user){session.requireLogin({kind:'add',productId,returnTo:window.location.hash||'#collection'});return;}
      const response = await fetch(`/api/products/${productId}`, { signal: AbortSignal.timeout(15000) });
      if (response.status === 404) throw new Error('This item is no longer available. Please refresh the collection.');
      await check(response);
      const product: unknown = await response.json();
      if (!isCartProduct(product) || product.id !== productId) throw new Error('Could not verify this product. Please try again.');
      if (pending.current.get(productId) !== operation) return;
      const result = addCartItem(current.current, productId, product.stockQuantity);
      if (result.issue) throw new Error(issueMessage(result.issue));
      commit(result.items);
      if (!knownPrices.current.has(productId)) knownPrices.current.set(productId, priceInAgorot(product.price));
      setMessage({ kind: 'success', text: `${product.name} added to your basket.` });
    } catch (e) { if (pending.current.get(productId) === operation) setMessage({ kind: 'error', text: e instanceof Error ? e.message : 'Could not check availability. Your basket was not changed.' }); }
    finally { if (pending.current.get(productId) === operation) { pending.current.delete(productId); setAdding([...pending.current.keys()]); } }
  }
  function quantity(productId: number, next: number, available: number) {
    if(!session.user){session.requireLogin({kind:'return',returnTo:'#basket'});return false;}
    const result = setCartQuantity(current.current, productId, next, available);
    if (result.issue) { setMessage({ kind: 'error', text: issueMessage(result.issue) }); return false; }
    pending.current.delete(productId); setAdding([...pending.current.keys()]);
    commit(result.items); setMessage(null); return true;
  }
  function remove(productId: number) {
    pending.current.delete(productId); setAdding([...pending.current.keys()]);
    commit(current.current.filter(item => item.productId !== productId)); knownPrices.current.delete(productId);
    setMessage({ kind: 'success', text: 'Item removed from your basket.' });
  }
  function clear() { pending.current.clear(); setAdding([]); commit([]); knownPrices.current.clear(); setMessage({ kind: 'success', text: 'Your basket is now empty.' }); }
  function fulfill(purchased:CartItem[]){for(const item of purchased)pending.current.delete(item.productId);setAdding([...pending.current.keys()]);commit(removePurchased(current.current,purchased));setMessage(null);}

  return <CartContext.Provider value={{ items, adding, message, storageWarning, add, quantity, remove, clear, fulfill, observePrices }}>{children}</CartContext.Provider>;
}

export function useCart() {
  const basket = useContext(CartContext);
  if (!basket) throw new Error('Basket components must be inside CartProvider.');
  return basket;
}
