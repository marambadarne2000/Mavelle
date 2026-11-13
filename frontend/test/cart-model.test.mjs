import test from 'node:test';
import assert from 'node:assert/strict';
import { restoreCart, serializeCart, addCartItem, setCartQuantity, resolveCart, priceInAgorot, isCartProduct, MAX_LINES,removePurchased } from '../.test-build/cart/model.js';

const product = (id, price, stockQuantity) => ({ id, price, stockQuantity, sku: `TEST-${id}`, name: `Product ${id}`, description: 'Fixture', category: 'Living' });

test('empty, corrupt and unsupported saved baskets are safe', () => {
  assert.deepEqual(restoreCart(null), { items: [], repaired: false });
  for (const raw of ['broken', 'null', '{}', '{"version":2,"items":[]}', 'x'.repeat(32769)]) {
    assert.deepEqual(restoreCart(raw), { items: [], repaired: true });
  }
});
test('restore drops invalid IDs and quantities rather than truncating them', () => {
  const restored = restoreCart(JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 2 }, { productId: -1, quantity: 1 }, { productId: 2, quantity: 1.5 }, { productId: 3, quantity: 100 }, { productId: 4.5, quantity: 1 }] }));
  assert.deepEqual(restored, { items: [{ productId: 1, quantity: 2 }], repaired: true });
});
test('duplicate saved lines merge with a bounded quantity', () => {
  assert.deepEqual(restoreCart(JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 60 }, { productId: 1, quantity: 60 }] })).items, [{ productId: 1, quantity: 99 }]);
});
test('stored catalog prices and other fields are discarded', () => {
  const restored = restoreCart(JSON.stringify({ version: 1, items: [{ productId: 1, quantity: 2, price: 0, role: 'ADMIN', stockQuantity: 500 }] }));
  assert.equal(serializeCart(restored.items), '{"version":1,"items":[{"productId":1,"quantity":2}]}');
});
test('saved state and new adds are bounded to fifty distinct lines', () => {
  const items = Array.from({ length: MAX_LINES + 1 }, (_, i) => ({ productId: i + 1, quantity: 1 }));
  assert.equal(restoreCart(JSON.stringify({ version: 1, items })).items.length, MAX_LINES);
  assert.equal(addCartItem(items.slice(0, MAX_LINES), 200, 5).issue, 'full');
});
test('repeated adds merge and do not mutate the existing basket', () => {
  const before = [{ productId: 1, quantity: 1 }];
  assert.deepEqual(addCartItem(before, 1, 5).items, [{ productId: 1, quantity: 2 }]);
  assert.equal(before[0].quantity, 1);
});
test('the last available unit cannot be added twice', () => {
  const first = addCartItem([], 1, 1);
  assert.equal(first.issue, null);
  assert.equal(addCartItem(first.items, 1, 1).issue, 'limit');
  assert.equal(addCartItem([], 2, 0).issue, 'unavailable');
});
test('quantity edits reject fractional, zero and unavailable values', () => {
  const items = [{ productId: 1, quantity: 5 }];
  for (const quantity of [0, -1, 1.5, 100, NaN]) assert.notEqual(setCartQuantity(items, 1, quantity, 10).issue, null);
  assert.equal(setCartQuantity(items, 1, 3, 2).issue, 'limit');
  assert.deepEqual(setCartQuantity(items, 1, 2, 2).items, [{ productId: 1, quantity: 2 }]);
});
test('available totals use integer agorot without floating-point drift', () => {
  const resolved = resolveCart([{ productId: 1, quantity: 3 }, { productId: 2, quantity: 1 }], [product(1, 0.1, 10), product(2, 0.29, 10)]);
  assert.equal(resolved.subtotal, 59);
  assert.equal(resolved.needsAttention, false);
});
test('unavailable, out-of-stock and insufficient lines are visible but excluded from available subtotal', () => {
  const resolved = resolveCart([{ productId: 1, quantity: 1 }, { productId: 2, quantity: 1 }, { productId: 3, quantity: 4 }, { productId: 4, quantity: 1 }], [product(1, 5, 2), product(2, 10, 0), product(3, 20, 2)]);
  assert.equal(resolved.subtotal, 500);
  assert.deepEqual(resolved.rows.map(row => row.issue), [null, 'out-of-stock', 'insufficient-stock', 'unavailable']);
  assert.equal(resolved.needsAttention, true);
});
test('new server prices replace old display prices without changing saved quantities', () => {
  const items = [{ productId: 1, quantity: 2 }];
  assert.equal(resolveCart(items, [product(1, 10, 5)]).subtotal, 2000);
  assert.equal(resolveCart(items, [product(1, 12.5, 5)]).subtotal, 2500);
  assert.equal(items[0].quantity, 2);
});
test('invalid server prices fail instead of producing a free basket', () => {
  for (const price of [NaN, Infinity, -1, 0, 10000000000]) assert.throws(() => priceInAgorot(price));
});
test('the largest supported basket still has an exact safe-integer subtotal', () => {
  const items = Array.from({ length: 50 }, (_, i) => ({ productId: i + 1, quantity: 99 }));
  const products = items.map(item => product(item.productId, 9999999999.99, 99));
  const subtotal = resolveCart(items, products).subtotal;
  assert.equal(subtotal, 4949999999995050);
  assert.equal(Number.isSafeInteger(subtotal), true);
});
test('malformed server product information cannot be accepted as a valid basket price', () => {
  assert.equal(isCartProduct(product(1, 10, 3)), true);
  for (const bad of [{ ...product(1, 10, 3), price: 0 }, { ...product(1, 10, 3), id: '1' }, { ...product(1, 10, 3), stockQuantity: 1.5 }, { ...product(1, 10, 3), stockQuantity: -1 }, { id: 1, price: 10 }]) {
    assert.equal(isCartProduct(bad), false);
  }
});
test('confirmed orders remove only purchased quantities and preserve later additions',()=>{
 const basket=[{productId:1,quantity:4},{productId:2,quantity:1}];
 assert.deepEqual(removePurchased(basket,[{productId:1,quantity:2}]),[{productId:1,quantity:2},{productId:2,quantity:1}]);assert.equal(basket[0].quantity,4);
});
test('confirmed order consumption never creates a negative basket quantity',()=>{
 assert.deepEqual(removePurchased([{productId:1,quantity:1}],[{productId:1,quantity:2}]),[]);
});
