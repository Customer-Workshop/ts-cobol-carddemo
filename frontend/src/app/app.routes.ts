import { Routes } from '@angular/router';
import { SignOnComponent } from './sign-on/sign-on.component';
import { MainMenuComponent } from './menu/main-menu.component';
import { AdminMenuComponent } from './menu/admin-menu.component';
import { AccountUpdateComponent } from './account-update/account-update.component';
import { adminGuard, authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'signin', pathMatch: 'full' },
  { path: 'signin', component: SignOnComponent },
  { path: 'menu', component: MainMenuComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminMenuComponent, canActivate: [adminGuard] },
  { path: 'account-update', component: AccountUpdateComponent, canActivate: [authGuard] }
];
