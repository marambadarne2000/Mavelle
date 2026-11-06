import {useState} from 'react';
import {t} from '../i18n';
import type {CartProduct} from './model';

/** Use the same authoritative image fields as the catalog, never a shared product illustration. */
export function CartProductImage({product}:{product?:CartProduct}){
 const uploaded=typeof product?.imageId==='string'&&/^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/i.test(product.imageId)?'/api/media/'+product.imageId:null;
 const reference=typeof product?.referenceImageUrl==='string'&&/^\/catalog\/[a-z0-9][a-z0-9._-]*\.(?:png|jpe?g|webp)$/i.test(product.referenceImageUrl)?product.referenceImageUrl:null;
 const source=uploaded??reference;
 return <div className="basket-art">{source?<Photo key={source} src={source} name={product!.name}/>:<Missing/>}</div>;
}
function Photo({src,name}:{src:string;name:string}){
 const [failed,setFailed]=useState(false);
 return failed?<Missing/>:<img className="basket-product-photo" src={src} alt={t(name)} loading="lazy" onError={()=>setFailed(true)}/>;
}
function Missing(){return <span className="basket-photo-missing"><span aria-hidden="true">◇</span><small>{t('No product photo')}</small></span>;}
