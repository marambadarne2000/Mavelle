/** Keep keyboard traversal inside an open product dialog, including Shift+Tab. */
export function trapDialogTab(event:{key:string;shiftKey:boolean;preventDefault:()=>void;currentTarget:HTMLDialogElement}):void{
 if(event.key!=='Tab')return;
 const dialog=event.currentTarget;
 const items=[...dialog.querySelectorAll<HTMLElement>('button:not(:disabled),a[href],input:not(:disabled),select:not(:disabled),textarea:not(:disabled),[tabindex]:not([tabindex="-1"])')].filter(el=>!el.closest('[hidden]'));
 const first=items[0],last=items.at(-1);
 if(!first){event.preventDefault();dialog.focus();return;}
 if(event.shiftKey&&(document.activeElement===first||!dialog.contains(document.activeElement))){event.preventDefault();last!.focus();}
 else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus();}
}
