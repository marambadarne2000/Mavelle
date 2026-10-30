import test from 'node:test';import assert from 'node:assert/strict';import {JSDOM} from 'jsdom';import React,{act} from 'react';import {createRoot} from 'react-dom/client';
import {catalogSearch,knownRoute} from '../.test-build/route.js';
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://localhost/#login'});
for(const key of ['window','document','HTMLElement','HTMLInputElement','Event','MouseEvent','localStorage'])Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true});
globalThis.IS_REACT_ACT_ENVIRONMENT=true;localStorage.setItem('maramigo.language','en');
globalThis.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
const {AuthPage,SessionProvider,I18nProvider,FormFeedback,Dashboard,trapDialogTab,CustomerCenter,BusinessRegistration}=await import('../.test-build/ui.mjs');
let root;const host=document.getElementById('root');
test.afterEach(async()=>{if(root)await act(async()=>root.unmount());root=null;});
async function render(element){root=createRoot(host);await act(async()=>root.render(element));}
function shell(){return React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(AuthPage)));}
const response=(value,status=200)=>new Response(JSON.stringify(value),{status,headers:{'Content-Type':'application/json'}});
test('known application routes reject broken and unknown destinations',()=>{
 for(const route of ['#home','#dashboard','#admin-orders','#store/noir-atelier','#order/MG-abc123'])assert.ok(knownRoute(route));
 for(const route of ['#missing','#store/undefined/extra','#order/','#page-content'])assert.equal(knownRoute(route),false);
});
test('catalog search finds reviewed Hebrew and Arabic product names without changing custom content',()=>{
 assert.equal(catalogSearch('כרית'),'The Silk Pillow');assert.equal(catalogSearch('وسادة الحرير'),'The Silk Pillow');assert.equal(catalogSearch('חלוק חצות'),'Midnight Robe');assert.equal(catalogSearch('  SILK '),'SILK');assert.equal(catalogSearch('Custom demo name'),'Custom demo name');assert.equal(catalogSearch(''),'');
});
test('form failure is announced and receives keyboard focus',async()=>{
 await render(React.createElement(FormFeedback,{error:'Invalid email or password.'}));const error=host.querySelector('[role=alert]');assert.equal(error.textContent,'Invalid email or password.');assert.equal(document.activeElement,error);
});
test('dialog keyboard traversal wraps both directions and skips disabled controls',()=>{
 const dialog=document.createElement('dialog');dialog.innerHTML='<button>Close</button><button disabled>Add</button><a href="#basket">Basket</a>';document.body.append(dialog);
 const first=dialog.querySelector('button'),last=dialog.querySelector('a');let prevented=0;first.focus();
 trapDialogTab({key:'Tab',shiftKey:true,currentTarget:dialog,preventDefault:()=>prevented++});assert.equal(document.activeElement,last);
 trapDialogTab({key:'Tab',shiftKey:false,currentTarget:dialog,preventDefault:()=>prevented++});assert.equal(document.activeElement,first);assert.equal(prevented,2);dialog.remove();
});
test('sign-in fields have accessible labels and correct autocomplete',async()=>{
 globalThis.fetch=async()=>response({},401);await render(shell());
 const email=host.querySelector('[data-testid=auth-email]'),password=host.querySelector('[data-testid=auth-password]');
 assert.match(email.labels[0].textContent,/Email address/);assert.match(password.labels[0].textContent,/Password/);assert.equal(email.autocomplete,'username');assert.equal(password.autocomplete,'current-password');assert.equal(password.type,'password');
});

test('unified sign-in routes all four server roles without sending a client-selected portal',async()=>{
 for(const [role,destination] of [['CUSTOMER','#account'],['MERCHANT','#merchant'],['ADMIN','#marketplace-admin'],['EMPLOYEE','#staff-orders']]){
  let payload;globalThis.fetch=async(path,options)=>path==='/api/auth/me'?response({},401):path==='/api/auth/csrf'?response({headerName:'X-CSRF-TOKEN',token:'token'}):(payload=options.body,response({id:7,email:'test@example.test',role}));
  window.location.hash='#login';await render(shell());assert.equal(host.querySelector('.portal-switch'),null);
  await act(async()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
  assert.equal(window.location.hash,destination);assert.equal(new URLSearchParams(payload).has('portal'),false);
  await act(async()=>root.unmount());root=null;
 }
});

test('business onboarding requests contact details and confirmation without raw card bank or national ID fields',async()=>{
 globalThis.fetch=async()=>response({monthlyFee:99,commissionPercent:5,status:'DRAFT',chargesEnabled:false});
 await render(React.createElement(I18nProvider,null,React.createElement(BusinessRegistration)));
 for(const name of ['ownerName','businessName','email','phone','password','confirmPassword'])assert.ok(host.querySelector('[data-testid=join-'+name+']'));
 assert.ok(host.querySelector('input[type=checkbox][required]'));assert.match(host.textContent,/Draft launch plan/);assert.match(host.textContent,/99.00/);assert.match(host.textContent,/5%/);
 assert.doesNotMatch(host.innerHTML,/name="(?:cardNumber|cvv|bankAccount|nationalId)"/);assert.equal(host.querySelector('[data-testid=join-confirmPassword]').autocomplete,'new-password');
});

test('customer favorites delete uses the personal endpoint and refreshes the empty state',async()=>{
 let removed=false;globalThis.fetch=async(path,options)=>{
  if(path==='/api/auth/me')return response({id:9,email:'buyer@example.test',role:'CUSTOMER'});
  if(path==='/api/customer/profile')return response({fullName:'',phone:'',address:'',city:''});
  if(path==='/api/auth/csrf')return response({headerName:'X-CSRF-TOKEN',token:'token'});
  if(path==='/api/customer/favorites/11'){assert.equal(options.method,'DELETE');removed=true;return new Response(null,{status:200});}
  if(path==='/api/customer/favorites')return response(removed?[]:[{id:11,name:'Synthetic favorite',price:10,slug:'shop-test',company_name:'Test',active:true,status:'APPROVED'}]);
  throw Error('Unexpected URL '+path);
 };
 await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(CustomerCenter))));
 await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent==='Favorites').click());assert.match(host.textContent,/Synthetic favorite/);
 await act(async()=>[...host.querySelectorAll('button')].find(b=>b.textContent==='Remove').click());assert.equal(removed,true);assert.match(host.textContent,/Your favorites list is empty/);
});
test('pending sign-in sends once, disables fields and recovers with a focused server error',async()=>{
 let complete,loginCalls=0,csrfCalls=0;
 globalThis.fetch=async(path)=>{
  if(path==='/api/auth/me')return response({},401);
  if(path==='/api/auth/csrf'){csrfCalls++;return response({headerName:'X-CSRF-TOKEN',token:'test-token'});}
  if(path==='/api/auth/login'){loginCalls++;return new Promise(resolve=>{complete=resolve;});}
  throw Error('Unexpected URL '+path);
 };
 await render(shell());const form=host.querySelector('form');
 await act(async()=>{form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));form.dispatchEvent(new Event('submit',{bubbles:true,cancelable:true}));});
 assert.equal(loginCalls,1);assert.equal(csrfCalls,1);assert.equal(host.querySelector('fieldset').disabled,true);assert.equal(form.getAttribute('aria-busy'),'true');
 await act(async()=>complete(response({detail:'Invalid email or password.'},401)));
 assert.equal(host.querySelector('fieldset').disabled,false);const error=host.querySelector('[role=alert]');assert.equal(error.textContent,'Invalid email or password.');assert.equal(document.activeElement,error);
});
test('dashboard retains the last metrics and warns when a subsequent refresh fails',async()=>{
 let reads=0;
 globalThis.fetch=async(path)=>{
  if(path==='/api/auth/me')return response({id:1,email:'admin@example.test',role:'ADMIN'});
  if(String(path).startsWith('/api/admin/dashboard')){
   reads++;return reads===1?response({from:'2026-10-01',to:'2026-10-27',timezone:'Asia/Jerusalem',currency:'ILS',threshold:5,orders:2,cancelled:1,amount:129,lowStockCount:0,lowStock:[],days:[],generatedAt:'2026-10-27T10:00:00Z'}):response({detail:'Temporary service failure'},503);
  }throw Error('Unexpected URL '+path);
 };
 await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(Dashboard))));
 assert.equal(host.querySelector('[data-testid=dashboard-orders]').textContent,'2');
 const refresh=[...host.querySelectorAll('button')].find(b=>b.textContent==='Refresh now');
 await act(async()=>refresh.dispatchEvent(new MouseEvent('click',{bubbles:true})));
 assert.equal(reads,2);assert.equal(host.querySelector('[data-testid=dashboard-orders]').textContent,'2');assert.match(host.querySelector('[role=alert]').textContent,/Values may be outdated/);
});
