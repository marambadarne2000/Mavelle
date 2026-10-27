import {createContext,useContext,useEffect,useState,type ReactNode} from 'react';
import {translate} from './localization-core';
export type Language='he'|'ar'|'en';
function saved():Language{try{const value=localStorage.getItem('maramigo.language');if(value==='en'||value==='ar'||value==='he')return value;}catch{}return 'he';}
let active:Language=saved();
const Locale=createContext<{language:Language;setLanguage:(value:Language)=>void}>({language:active,setLanguage:()=>{}});
export function t(value:unknown):string{return translate(value,active);}
const locale=()=>active==='he'?'he-IL':active==='ar'?'ar-IL':'en-IL';
export const formatMoney=(value:number)=>new Intl.NumberFormat(locale(),{style:'currency',currency:'ILS'}).format(value);
export const formatDate=(value:Date)=>new Intl.DateTimeFormat(locale(),{dateStyle:'medium',timeStyle:'short',timeZone:'Asia/Jerusalem'}).format(value);
export const formatTime=(value:Date)=>new Intl.DateTimeFormat(locale(),{timeStyle:'medium',timeZone:'Asia/Jerusalem'}).format(value);
export function I18nProvider({children}:{children:ReactNode}){
 const [language,set]=useState<Language>(active);
 const setLanguage=(value:Language)=>{active=value;set(value);try{localStorage.setItem('maramigo.language',value);}catch{}};
 useEffect(()=>{document.documentElement.lang=language;document.documentElement.dir=language==='en'?'ltr':'rtl';},[language]);
 return <Locale.Provider value={{language,setLanguage}}>{children}</Locale.Provider>;
}
export function useLanguage(){return useContext(Locale);}
export function LanguagePicker(){const {language,setLanguage}=useLanguage();return <label className="language-picker"><span className="sr-only">{t('Language')}</span><select aria-label={t('Language')} value={language} onChange={e=>setLanguage(e.target.value as Language)}><option value="he">עברית</option><option value="ar">العربية</option><option value="en">English</option></select></label>;}
