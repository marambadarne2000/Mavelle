import test from 'node:test';import assert from 'node:assert/strict';import {JSDOM} from 'jsdom';import React,{act} from 'react';import {createRoot} from 'react-dom/client';
import {catalogSearch,knownRoute} from '../.test-build/route.js';
const dom=new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>',{url:'http://localhost/#login'});
for(const key of ['window','document','HTMLElement','HTMLInputElement','Event','MouseEvent','localStorage'])Object.defineProperty(globalThis,key,{value:dom.window[key],configurable:true});
globalThis.IS_REACT_ACT_ENVIRONMENT=true;localStorage.setItem('maramigo.language','en');
globalThis.ResizeObserver=class{observe(){}unobserve(){}disconnect(){}};
const {AuthPage,SessionProvider,I18nProvider,FormFeedback,Dashboard,trapDialogTab,CustomerCenter,BusinessRegistration,PageTools,HomeDiscovery,CartProvider,CartProbe,AccountRecoveryPage,ManufacturerDetails,ManufacturerPhoto,SupportPage,UpdatesPage,NotificationLink,CartPage,CartProductImage,OrderEmail}=await import('../.test-build/ui.mjs');
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
test('page back follows the actual internal trail including revisited routes and has a home fallback',async()=>{
 globalThis.fetch=async()=>response({},401);
 const view=route=>React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(PageTools,{route})));
 await render(view('#join-business'));await act(async()=>host.querySelector('[data-testid=page-back]').click());assert.equal(window.location.hash,'#home');
 for(const route of ['#home','#stores','#store/test','#stores']){window.location.hash=route;await act(async()=>root.render(view(route)));}
 await act(async()=>host.querySelector('[data-testid=page-back]').click());assert.equal(window.location.hash,'#store/test');
 await act(async()=>root.render(view('#store/test')));await act(async()=>host.querySelector('[data-testid=page-back]').click());assert.equal(window.location.hash,'#stores');
});

test('every signed-in role receives its own dashboard shortcut and guests receive sign-in',async()=>{
 for(const [role,target] of [['ADMIN','#marketplace-admin'],['MERCHANT','#merchant'],['CUSTOMER','#account'],['EMPLOYEE','#staff-orders'],[null,'#login']]){
  globalThis.fetch=async()=>role?response({id:1,email:'test@example.test',role}):response({},401);
  await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(PageTools,{route:'#join-business'}))));
  assert.equal(host.querySelector('[data-testid=personal-dashboard]').getAttribute('href'),target);
  await act(async()=>root.unmount());root=null;
 }
});

const demoCompanies=[{id:1,slug:'first-store',name:'First Store',description:'One',monogram:'FS',demo:true},{id:2,slug:'second-store',name:'Second Store',description:'Two',monogram:'SS',demo:true}];
function discoveryFetch(path){if(path==='/api/products?size=4')return response({items:[{id:1,name:'Test item',price:10,companyId:1}]});if(path==='/api/discovery/announcements')return response([]);throw Error('Unexpected URL '+path);}
function motionPreference(matches){window.matchMedia=()=>({matches,addEventListener(){},removeEventListener(){}});}
test('reduced motion keeps discovery manual with working store links and previous/next controls',async()=>{
 globalThis.fetch=async path=>discoveryFetch(path);motionPreference(true);
 await render(React.createElement(I18nProvider,null,React.createElement(HomeDiscovery,{companies:demoCompanies})));
 assert.equal(host.querySelector('[data-testid=home-discovery]').dataset.playing,'false');assert.equal(host.querySelector('[data-testid=discovery-pause]'),null);
 assert.match(host.querySelector('[data-testid=discovery-slide]').textContent,/First Store/);
 await act(async()=>host.querySelector('[data-testid=discovery-next]').click());assert.match(host.querySelector('[data-testid=discovery-slide]').textContent,/Second Store/);assert.equal(host.querySelector('[data-testid=discovery-slide] a').getAttribute('href'),'#store/second-store');
 await act(async()=>host.querySelector('[data-testid=discovery-previous]').click());assert.match(host.querySelector('[data-testid=discovery-slide]').textContent,/First Store/);
});
test('automatic discovery advances and can be paused, resumed and paused by keyboard focus',async()=>{
 globalThis.fetch=async path=>discoveryFetch(path);motionPreference(false);Object.defineProperty(document,'hidden',{value:false,configurable:true});const timers=new Map();let id=0;const originalSet=window.setInterval,originalClear=window.clearInterval;
 window.setInterval=(fn,delay)=>{const key=++id;timers.set(key,{fn,delay});return key;};window.clearInterval=key=>timers.delete(key);
 try{
  await render(React.createElement(I18nProvider,null,React.createElement(HomeDiscovery,{companies:demoCompanies})));
  const tick=()=>[...timers.values()].find(t=>t.delay===4500);
  assert.ok(tick());await act(async()=>tick().fn());assert.match(host.querySelector('[data-testid=discovery-slide]').textContent,/Second Store/);
  await act(async()=>host.querySelector('[data-testid=discovery-pause]').click());assert.equal(tick(),undefined);
  await act(async()=>host.querySelector('[data-testid=discovery-pause]').click());assert.ok(tick());
  const link=host.querySelector('[data-testid=discovery-slide] a');await act(async()=>link.dispatchEvent(new dom.window.FocusEvent('focusin',{bubbles:true})));assert.equal(tick(),undefined);
  await act(async()=>link.dispatchEvent(new dom.window.FocusEvent('focusout',{bubbles:true,relatedTarget:null})));assert.ok(tick());
  await act(async()=>root.unmount());root=null;
 }finally{window.setInterval=originalSet;window.clearInterval=originalClear;delete document.hidden;}
});

test('basket rechecks the refreshed server role before adding after another session changes identity',async()=>{
 localStorage.clear();let meReads=0,productReads=0;
 globalThis.fetch=async path=>{if(path==='/api/auth/me'){meReads++;return response({id:meReads===1?1:2,email:'session@example.test',role:meReads===1?'CUSTOMER':'MERCHANT'});}if(path==='/api/products/1'){productReads++;return response({id:1,name:'Test',price:10,stockQuantity:5,active:true});}throw Error('Unexpected URL '+path);};
 await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(CartProvider,null,React.createElement(CartProbe)))));
 await act(async()=>host.querySelector('[data-testid=probe-add]').click());assert.equal(productReads,0);assert.equal(host.querySelector('[data-testid=probe-count]').textContent,'0');assert.equal(host.querySelector('[data-testid=probe-message]').textContent,'Use a customer account to shop.');
});


test('recovery request uses a generic response and explains local delivery',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push([String(url),options]);return response(String(url).endsWith('/options')?{deliveryMode:'file',verificationRequired:false}:String(url).endsWith('/csrf')?{token:'csrf',headerName:'X-CSRF-TOKEN'}:null,options?.method==='POST'?202:200);};
 await render(React.createElement(I18nProvider,null,React.createElement(AccountRecoveryPage,{kind:'reset'})));
 assert.match(host.textContent,/not sent to an email inbox/);
 await act(async()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
 assert.match(host.querySelector('[role=status]').textContent,/If this address belongs/);assert.ok(calls.some(([url,options])=>url==='/api/auth/forgot-password'&&options?.method==='POST'));
});

test('verification waits for explicit confirmation and removes the used link from the address',async()=>{
 const token='A'.repeat(43);window.history.replaceState(null,'','#verify-email/'+token);const calls=[];
 globalThis.fetch=async(url,options)=>{calls.push([String(url),options]);return options?.method==='POST'?new Response(null,{status:204}):response(String(url).endsWith('/csrf')?{token:'csrf',headerName:'X-CSRF-TOKEN'}:{deliveryMode:'file',verificationRequired:false});};
 await render(React.createElement(I18nProvider,null,React.createElement(AccountRecoveryPage,{kind:'verify',token})));
 assert.equal(calls.filter(([,options])=>options?.method==='POST').length,0);
 await act(async()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
 assert.match(host.textContent,/Email verified/);assert.equal(window.location.hash,'#verify-email');assert.equal(host.querySelector('form'),null);
});

test('weak reset passwords are rejected locally and link routes reject malformed tokens',async()=>{
 const calls=[];globalThis.fetch=async(url)=>{calls.push(String(url));return response({deliveryMode:'file',verificationRequired:false});};
 await render(React.createElement(I18nProvider,null,React.createElement(AccountRecoveryPage,{kind:'reset',token:'A'.repeat(43)})));
 await act(async()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));
 assert.match(host.querySelector('[role=alert]').textContent,/Passwords must match/);assert.equal(calls.includes('/api/auth/reset-password'),false);
 for(const kind of ['reset-password','verify-email']){assert.ok(knownRoute('#'+kind+'/'+'A'.repeat(43)));assert.equal(knownRoute('#'+kind+'/bad'),false);}
});


test('password-only owner login enters the challenge without treating the response as a signed-in user',async()=>{
 window.location.hash='#login';globalThis.fetch=async(url,options)=>response(String(url).endsWith('/csrf')?{token:'csrf',headerName:'X-CSRF-TOKEN'}:String(url).endsWith('/login')?{mfaRequired:true}:null,String(url).endsWith('/me')?401:options?.method==='POST'?202:200);
 await render(shell());await act(async()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));assert.equal(window.location.hash,'#two-step');assert.equal(host.querySelector('[role=alert]'),null);
});

test('email change confirmation uses its own purpose-bound endpoint',async()=>{
 const calls=[];globalThis.fetch=async(url,options)=>{calls.push(String(url));return options?.method==='POST'?new Response(null,{status:204}):response(String(url).endsWith('/csrf')?{token:'csrf',headerName:'X-CSRF-TOKEN'}:{deliveryMode:'file',verificationRequired:false});};
 await render(React.createElement(I18nProvider,null,React.createElement(AccountRecoveryPage,{kind:'email-change',token:'A'.repeat(43)})));
 assert.equal(calls.includes('/api/auth/confirm-email-change'),false);await act(async()=>host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})));assert.ok(calls.includes('/api/auth/confirm-email-change'));
});


test('manufacturer details distinguish real specifications from demo offers and retain source links',async()=>{
 const product={name:'LEGO Orchid',sourceUrl:'https://www.lego.com/en-us/product/orchid-10311',referenceImageUrl:'/catalog/ref-le-10311.jpg',specifications:{Model:'10311',Pieces:'608'},sourceCheckedOn:'2026-11-05'};
 await render(React.createElement(I18nProvider,null,React.createElement(ManufacturerDetails,{product})));
 assert.match(host.textContent,/not an official store or partnership/);assert.match(host.textContent,/Demo price and stock/);
 assert.equal(host.querySelector('a').href,product.sourceUrl);assert.equal(host.querySelector('a').rel,'noreferrer');
 assert.equal(host.querySelectorAll('dl>div').length,2);assert.equal(host.querySelector('time').dateTime,'2026-11-05');
 assert.equal(host.querySelector('img').alt,'LEGO Orchid');
});

test('a failed manufacturer photo falls back to the product name without a broken image',async()=>{
 await render(React.createElement(ManufacturerPhoto,{src:'/catalog/missing.jpg',name:'Source product'}));
 await act(async()=>host.querySelector('img').dispatchEvent(new Event('error')));
 assert.equal(host.querySelector('img'),null);assert.equal(host.textContent,'Source product');
});


test('guest support uses the normal sign-in and support routes reject invalid IDs',async()=>{
 globalThis.fetch=async()=>response(null,401);window.location.hash='#support';
 await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(SupportPage))));
 assert.match(host.textContent,/Sign in to continue/);await act(async()=>host.querySelector('button').click());assert.equal(window.location.hash,'#login');
 for(const route of ['#updates','#support','#support/1','#support/12345'])assert.ok(knownRoute(route));
 for(const route of ['#support/0','#support/-1','#support/001','#support/bad','#support/1/extra'])assert.equal(knownRoute(route),false);
});

test('update acknowledgements include only the displayed unread keys',async()=>{
 const calls=[];let read=false;globalThis.fetch=async(url,options)=>{const p=String(url);if(p.endsWith('/me'))return response({id:51,email:'customer@example.test',role:'CUSTOMER'});if(p.endsWith('/csrf'))return response({token:'csrf',headerName:'X-CSRF-TOKEN'});if(p.endsWith('/read')){calls.push(JSON.parse(options.body));read=true;}return response({items:[{key:'reply:42',title:'New support message',reference:'SUP-9',target:'#support/9',occurredAt:'2026-11-05T10:00:00Z',state:null,read}],unread:read?0:1,retentionDays:90,limit:100});};
 await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(UpdatesPage))));
 const button=[...host.querySelectorAll('button')].find(b=>b.textContent==='Mark displayed updates as read');await act(async()=>button.click());
 assert.deepEqual(calls,[{keys:['reply:42']}]);assert.equal(host.querySelector('a[href="#support/9"]').textContent,'Open details');assert.equal(host.querySelectorAll('.notice.unread').length,0);
});

test('resolved private support keeps message content as text and offers explicit reopening',async()=>{
 globalThis.fetch=async(url)=>response(String(url).endsWith('/me')?{id:51,email:'customer@example.test',role:'CUSTOMER'}:{ticket:{id:9,subject:'Private help',category:'TECHNICAL',status:'RESOLVED',version:2,createdAt:'2026-11-05T10:00:00Z',updatedAt:'2026-11-05T10:00:00Z',requesterRole:'CUSTOMER',ownTicket:true},messages:[{id:1,body:'<img src=x onerror=alert(1)>',senderRole:'ADMIN',mine:false,createdAt:'2026-11-05T10:00:00Z'}],hasOlder:false});
 await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(SupportPage,{id:'9'}))));
 assert.equal(host.querySelector('img'),null);assert.match(host.textContent,/<img src=x/);assert.equal(host.querySelector('[data-testid=support-reply]'),null);assert.ok([...host.querySelectorAll('button')].some(b=>b.textContent==='Reopen request'));
});


test('basket rows display each matched product photo even when lookup order differs from basket order',async()=>{
 localStorage.setItem('maramigo.cart.v1',JSON.stringify({version:1,items:[{productId:101,quantity:1},{productId:102,quantity:1},{productId:103,quantity:1},{productId:104,quantity:1}]}));
 const products=[{id:103,name:'Sony headphones',referenceImageUrl:'/catalog/ref-so-wh-ch720n.jpg'},{id:101,name:'Apple iPad',referenceImageUrl:'/catalog/ref-ap-ipad10.png'},{id:104,name:'Product without photo'},{id:102,name:'Apple AirPods',referenceImageUrl:'/catalog/ref-ap-airpods2.png'}].map(p=>({...p,sku:'PHOTO-'+p.id,description:'Synthetic fixture',category:'Electronics',price:10,stockQuantity:5}));
 globalThis.fetch=async(url)=>response(String(url).includes('/products/lookup')?products:{id:51,email:'customer@example.test',role:'CUSTOMER'});
 try{
  await render(React.createElement(I18nProvider,null,React.createElement(SessionProvider,null,React.createElement(CartProvider,null,React.createElement(CartPage)))));
  const rows=[...host.querySelectorAll('[data-testid=basket-item]')];assert.equal(rows.length,4);
  assert.equal(rows[0].querySelector('img').getAttribute('src'),'/catalog/ref-ap-ipad10.png');
  assert.equal(rows[1].querySelector('img').getAttribute('src'),'/catalog/ref-ap-airpods2.png');
  assert.equal(rows[2].querySelector('img').getAttribute('src'),'/catalog/ref-so-wh-ch720n.jpg');
  assert.equal(rows[3].querySelector('img'),null);assert.match(rows[3].textContent,/No product photo/);
  assert.equal(host.querySelectorAll('.basket-art .product-shape').length,0);
 }finally{localStorage.removeItem('maramigo.cart.v1');}
});

test('uploaded basket photos take precedence, failed photos fall back and a new source can load',async()=>{
 const product={id:1,sku:'PHOTO-1',name:'Uploaded product',description:'',category:'Living',price:10,stockQuantity:1,imageId:'12345678-1234-1234-1234-123456789abc',referenceImageUrl:'/catalog/reference.png'};
 await render(React.createElement(CartProductImage,{product}));assert.equal(host.querySelector('img').getAttribute('src'),'/api/media/'+product.imageId);
 await act(async()=>host.querySelector('img').dispatchEvent(new Event('error')));assert.equal(host.querySelector('img'),null);assert.match(host.textContent,/No product photo/);
 await act(async()=>root.render(React.createElement(CartProductImage,{product:{...product,imageId:null}})));assert.equal(host.querySelector('img').getAttribute('src'),'/catalog/reference.png');
});

test('original demo products retain their distinct catalog illustrations while photos still take priority',async()=>{
 for(const [sku,category] of [['MG-001','living'],['MG-002','wear'],['MG-003','accessories'],['MG-004','wellness']]){
  const product={id:1,sku,name:'Original demo product',description:'',category,price:10,stockQuantity:1};
  await render(React.createElement(CartProductImage,{product}));
  assert.ok(host.querySelector('.art-'+category+' .product-shape[role="img"]'));
  assert.equal(host.querySelector('.basket-photo-missing'),null);
  await act(async()=>root.render(React.createElement(CartProductImage,{product:{...product,referenceImageUrl:'/catalog/reference.png'}})));
  assert.equal(host.querySelector('img').getAttribute('src'),'/catalog/reference.png');
  assert.equal(host.querySelector('.product-shape'),null);
 }
});

test('order mail status distinguishes local files, provider acceptance and failure',async()=>{
 for(const [status,deliveryMode,text] of [['COMPLETED','file','saved locally'],['COMPLETED','smtp','accepted by the email provider'],['FAILED',null,'email failed']]){
  globalThis.fetch=async()=>response({status,deliveryMode});
  await render(React.createElement(I18nProvider,null,React.createElement(OrderEmail,{number:'MG-test'})));
  assert.match(host.querySelector('[data-testid=order-email-status]').textContent,new RegExp(text));
  await act(async()=>root.unmount());root=null;
 }
});
