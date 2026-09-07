import { Routes } from '@angular/router';
import { SignonComponent } from './features/signon/signon.component';
import { MenuComponent } from './features/menu/menu.component';
import { AccountViewComponent } from './features/account-view/account-view.component';

/** Business-name routes (target state §4 ONLINE): /signon, /menu, /accounts/view. */
export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'signon' },
  { path: 'signon', component: SignonComponent },
  { path: 'menu', component: MenuComponent },
  { path: 'accounts/view', component: AccountViewComponent },
  { path: '**', redirectTo: 'signon' },
];
