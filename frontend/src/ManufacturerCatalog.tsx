import {useState} from 'react';
import {t} from './i18n';
type ReferenceProduct={name:string;sourceUrl?:string|null;referenceImageUrl?:string|null;specifications?:Record<string,string>;sourceCheckedOn?:string|null};
export function ManufacturerPhoto({src,name,detail=false}:{src:string;name:string;detail?:boolean}){
 const [failed,setFailed]=useState(false);
 return failed?<span className="reference-image-fallback">{name}</span>:<img className={detail?'reference-detail-photo':'catalog-photo reference-photo'} src={src} alt={detail?name:''} loading="lazy" onError={()=>setFailed(true)}/>;
}
export function ManufacturerDetails({product:p}:{product:ReferenceProduct}){
 if(!p.sourceUrl)return null;
 return <section className="manufacturer-details" aria-label={t('Manufacturer specifications')}>
  {p.referenceImageUrl&&<ManufacturerPhoto key={p.referenceImageUrl} src={p.referenceImageUrl} name={p.name} detail/>}
  <p className="manufacturer-notice">{t('Real products. Demonstration catalog, not an official store or partnership.')} {t('Demo price and stock')}</p>
  <h3>{t('Manufacturer specifications')}</h3>
  <dl className="reference-specs">{Object.entries(p.specifications??{}).map(([label,value])=><div key={label}><dt>{t(label)}</dt><dd dir="auto">{t(value)}</dd></div>)}</dl>
  <a className="reference-source" href={p.sourceUrl} target="_blank" rel="noreferrer">{t('Official product source ↗')}</a>
  {p.sourceCheckedOn&&<p className="reference-date">{t('Source checked:')} <time dateTime={p.sourceCheckedOn}>{p.sourceCheckedOn}</time></p>}
  <p className="reference-date">{t('Product images and trademarks belong to their respective owners.')}</p>
 </section>;
}
