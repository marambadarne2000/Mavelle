import {useEffect,useRef} from 'react';
import {t,useLanguage} from './i18n';
export function FormFeedback({error}:{error:string}){
 useLanguage();const target=useRef<HTMLParagraphElement>(null);
 useEffect(()=>{if(error)target.current?.focus();},[error]);
 return error?<p ref={target} tabIndex={-1} className="account-error" role="alert" data-testid="form-error">{t(error)}</p>:null;
}
