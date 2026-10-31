export function knownRoute(route:string):boolean{
 return ['#forgot-password','#reset-password','#verify-email','','#home','#collection','#stores','#story','#login','#register','#account','#basket','#checkout','#orders','#admin','#admin-orders','#dashboard','#owner-login','#staff-login','#staff-register','#owner','#staff-orders','#join-business','#merchant','#marketplace-admin'].includes(route)
  || /^#(?:reset-password|verify-email)\/[A-Za-z0-9_-]{43}$/.test(route)
  || /^#staff-register\/[A-Za-z0-9_-]{43}$/.test(route)
  || /^#store\/[a-z0-9-]+$/.test(route) || /^#order\/MG-[a-zA-Z0-9-]+$/.test(route);
}

// Demo products have reviewed translations; search their visible localized names too.
export function catalogSearch(query:string):string{
 const names:[string,string,string][]=[['The Silk Pillow','כרית המשי','وسادة الحرير'],['Midnight Robe','חלוק חצות','روب منتصف الليل'],['Golden Hour Scrunchie','גומיית שעת הזהב','ربطة الشعر الذهبية'],['The Sleep Mask','מסכת השינה','قناع النوم']];
 const q=query.trim();if(!q)return '';
 const matches=names.filter(row=>row.slice(1).some(name=>name.includes(q)));
 return matches.length===1?matches[0][0]:q;
}
