import { Routes } from '@angular/router';
import { SignOnComponent } from './sign-on/sign-on.component';
import { MainMenuComponent } from './menu/main-menu.component';
import { AdminMenuComponent } from './menu/admin-menu.component';
import { BillPaymentComponent } from './bill-payment/bill-payment.component';
import { adminGuard, authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'signin', pathMatch: 'full' },
  { path: 'signin', component: SignOnComponent },
  { path: 'menu', component: MainMenuComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminMenuComponent, canActivate: [adminGuard] },
  { path: 'bill-payment', component: BillPaymentComponent, canActivate: [authGuard] }
];
