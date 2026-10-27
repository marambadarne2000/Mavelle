import {translations} from './messages.js';
export type Language='he'|'ar'|'en';
const escape=(value:string)=>value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
export function translate(value:unknown,language:Language):string{
 if(value===null||value===undefined||typeof value==='boolean')return '';
 const text=String(value);if(language==='en')return text;const key=text.trim();const dictionary=translations[language];let translated=dictionary[key];
 if(translated===undefined)for(const [pattern,result] of Object.entries(dictionary)){
  if(!pattern.includes('{0}'))continue;const match=key.match(new RegExp('^'+pattern.split(/\{\d+\}/).map(escape).join('(.+?)')+'$'));
  if(match){translated=result.replace(/\{(\d+)\}/g,(_,index)=>translate(match[Number(index)+1],language));break;}
 }
 return translated===undefined?text:text.slice(0,text.length-text.trimStart().length)+translated+text.slice(text.trimEnd().length);
}
