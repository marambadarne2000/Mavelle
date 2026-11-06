export {AuthPage} from '../src/AuthPages';
export {SessionProvider} from '../src/session';
export {I18nProvider,LanguagePicker} from '../src/i18n';
export {FormFeedback} from '../src/FormFeedback';
export {Dashboard} from '../src/Dashboard';
export {trapDialogTab} from '../src/focus';
export {CustomerCenter,BusinessRegistration} from '../src/Marketplace';

export {PageTools,HomeDiscovery,dashboardRoute} from '../src/Discovery';
import {useCart} from '../src/cart/CartContext';
export {CartProvider} from '../src/cart/CartContext';
export function CartProbe(){const cart=useCart();return <div><button data-testid="probe-add" onClick={()=>void cart.add(1)}>Add</button><span data-testid="probe-count">{cart.items.length}</span><p data-testid="probe-message">{cart.message?.text}</p></div>;}

export {AccountRecoveryPage} from '../src/AccountSecurity';

export {ManufacturerDetails,ManufacturerPhoto} from '../src/ManufacturerCatalog';

export {SupportPage,UpdatesPage,NotificationLink} from '../src/Engagement';
