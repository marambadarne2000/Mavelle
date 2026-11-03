import test from 'node:test';import assert from 'node:assert/strict';import {readFileSync} from 'node:fs';import ts from 'typescript';
import {translate} from '../.test-build/localization-core.js';import {translations} from '../.test-build/messages.js';
test('localized pages retain visible headings, action labels and dynamic expressions',()=>{
 for(const file of ['Dashboard.tsx','AdminOrders.tsx','main.tsx','AuthPages.tsx','AccountSecurity.tsx','Mfa.tsx','Portal.tsx','AdminProducts.tsx','OrderPages.tsx','cart/CartPage.tsx']){
  const source=readFileSync(new URL('../src/'+file,import.meta.url),'utf8');
  assert.doesNotMatch(source,/<(h[1-6]|button|option)\b[^>]*>\s*<\/\1>/,file+' contains an empty label');
  const tree=ts.createSourceFile(file,source,ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);
  const visit=n=>{if(ts.isJsxExpression(n))assert.ok(n.expression,file+' lost a JSX expression');ts.forEachChild(n,visit);};visit(tree);
 }
});
test('Hebrew, Arabic and English labels are explicit and user content remains unchanged',()=>{
 assert.equal(translate('Sign in','he'),'כניסה לחשבון');assert.equal(translate('Sign in','ar'),'تسجيل الدخول');assert.equal(translate('Sign in','en'),'Sign in');assert.equal(translate('Aurea Living','ar'),'Aurea Living');assert.equal(translate(null,'he'),'');assert.equal(translate(false,'ar'),'');
});
test('dynamic messages translate interpolation and product names safely',()=>{
 assert.equal(translate('The Silk Pillow added to your basket.','he'),'כרית המשי נוסף לסל שלך.');
 assert.equal(translate('Only 2 available. Please update the quantity.','ar'),'تتوفر 2 قطع فقط. يرجى تعديل الكمية.');
 assert.equal(translate('MG-abc-123','he'),'MG-abc-123');
});
test('all literal interface keys in deployed React pages have Hebrew and Arabic entries',()=>{
 const keys=new Set();const exempt=new Set(['m.','maramigo','Maramigo','Maram Abbas.']);
 const files=['Dashboard.tsx','AdminOrders.tsx','main.tsx','AuthPages.tsx','AccountSecurity.tsx','Mfa.tsx','Portal.tsx','AdminProducts.tsx','OrderPages.tsx','Workspaces.tsx','Marketplace.tsx','Discovery.tsx','cart/CartPage.tsx'];
 const gather=n=>{if(ts.isConditionalExpression(n)){gather(n.whenTrue);gather(n.whenFalse);return;}if(ts.isStringLiteral(n)&&/[A-Za-z]/.test(n.text))keys.add(n.text.trim());if(ts.isTemplateExpression(n)){let value=n.head.text;n.templateSpans.forEach((s,i)=>value+='{'+i+'}'+s.literal.text);keys.add(value.trim());return;}ts.forEachChild(n,gather);};
 for(const file of files){const tree=ts.createSourceFile(file,readFileSync(new URL('../src/'+file,import.meta.url),'utf8'),ts.ScriptTarget.Latest,true,ts.ScriptKind.TSX);const scan=n=>{if(ts.isCallExpression(n)&&n.expression.getText(tree)==='t'&&n.arguments[0])gather(n.arguments[0]);ts.forEachChild(n,scan);};scan(tree);}
 const missing=[...keys].filter(key=>key&&!exempt.has(key)&&(!translations.he[key]||!translations.ar[key]));assert.deepEqual(missing,[]);
});


