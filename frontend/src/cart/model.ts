export type CartItem = { productId: number; quantity: number };
export type CartProduct = {
  companyId?: number;
  id: number;
  sku: string;
  name: string;
  description: string;
  category: string;
  price: number;
  stockQuantity: number;
};
export type CartIssue = 'invalid' | 'unavailable' | 'limit' | 'full' | null;
export type CartChange = { items: CartItem[]; issue: CartIssue };
export const CART_KEY = 'maramigo.cart.v1';
export const MAX_LINES = 50;
export const MAX_QUANTITY = 99;

const positiveInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value > 0;

export function restoreCart(raw: string | null): { items: CartItem[]; repaired: boolean } {
  if (raw === null) return { items: [], repaired: false };
  try {
    if (raw.length > 32768) throw new Error('Oversized saved basket');
    const saved: unknown = JSON.parse(raw);
    if (!saved || typeof saved !== 'object' || !('version' in saved) || saved.version !== 1 || !('items' in saved) || !Array.isArray(saved.items)) {
      throw new Error('Unsupported saved basket');
    }
    const items: CartItem[] = [];
    let repaired = false;
    for (const row of saved.items) {
      if (!row || typeof row !== 'object' || !positiveInteger(row.productId) || !positiveInteger(row.quantity) || row.quantity > MAX_QUANTITY) {
        repaired = true; continue;
      }
      const existing = items.find(item => item.productId === row.productId);
      if (existing) { existing.quantity = Math.min(MAX_QUANTITY, existing.quantity + row.quantity); repaired = true; }
      else if (items.length < MAX_LINES) items.push({ productId: row.productId, quantity: row.quantity });
      else repaired = true;
    }
    return { items, repaired };
  } catch { return { items: [], repaired: true }; }
}

export function serializeCart(items: CartItem[]): string {
  return JSON.stringify({ version: 1, items: items.map(({ productId, quantity }) => ({ productId, quantity })) });
}

export function addCartItem(items: CartItem[], productId: number, available: number): CartChange {
  if (!positiveInteger(productId) || !Number.isSafeInteger(available) || available < 0) return { items, issue: 'invalid' };
  if (available === 0) return { items, issue: 'unavailable' };
  const existing = items.find(item => item.productId === productId);
  if (!existing && items.length >= MAX_LINES) return { items, issue: 'full' };
  if ((existing?.quantity ?? 0) >= Math.min(MAX_QUANTITY, available)) return { items, issue: 'limit' };
  return {
    items: existing ? items.map(item => item.productId === productId ? { ...item, quantity: item.quantity + 1 } : item) : [...items, { productId, quantity: 1 }],
    issue: null,
  };
}

export function setCartQuantity(items: CartItem[], productId: number, quantity: number, available: number): CartChange {
  if (!positiveInteger(quantity) || quantity > MAX_QUANTITY || !Number.isSafeInteger(available) || available < 0 || !items.some(item => item.productId === productId)) return { items, issue: 'invalid' };
  if (quantity > available) return { items, issue: 'limit' };
  return { items: items.map(item => item.productId === productId ? { ...item, quantity } : item), issue: null };
}

export function priceInAgorot(price: number): number {
  if (!Number.isFinite(price) || price <= 0 || price > 9999999999.99) throw new Error('Invalid server price');
  return Math.round(price * 100);
}

export function isCartProduct(value: unknown): value is CartProduct {
  if (!value || typeof value !== 'object') return false;
  const p = value as Partial<CartProduct>;
  return positiveInteger(p.id) && typeof p.sku === 'string' && typeof p.name === 'string'
    && typeof p.description === 'string' && typeof p.category === 'string'
    && typeof p.price === 'number' && Number.isFinite(p.price) && p.price > 0 && p.price <= 9999999999.99
    && typeof p.stockQuantity === 'number' && Number.isSafeInteger(p.stockQuantity) && p.stockQuantity >= 0;
}

export function resolveCart(items: CartItem[], products: CartProduct[]) {
  const byId = new Map(products.map(product => [product.id, product]));
  const rows = items.map(item => {
    const product = byId.get(item.productId);
    const issue = !product ? 'unavailable' : product.stockQuantity === 0 ? 'out-of-stock' : item.quantity > product.stockQuantity ? 'insufficient-stock' : null;
    return { ...item, product, issue, lineTotal: product ? priceInAgorot(product.price) * item.quantity : null };
  });
  const subtotal = rows.reduce((total, row) => total + (row.issue ? 0 : row.lineTotal ?? 0), 0);
  return { rows, subtotal, needsAttention: rows.some(row => row.issue !== null) };
}
export function removePurchased(current:CartItem[],purchased:CartItem[]):CartItem[]{
  const quantities=new Map(purchased.filter(i=>positiveInteger(i.productId)&&positiveInteger(i.quantity)).map(i=>[i.productId,i.quantity]));
  return current.flatMap(item=>{const remaining=item.quantity-(quantities.get(item.productId)??0);return remaining>0?[{...item,quantity:remaining}]:[];});
}
