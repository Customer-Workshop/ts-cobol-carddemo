import { Routes } from '@angular/router';
import { SignOnComponent } from './sign-on/sign-on.component';
import { MainMenuComponent } from './menu/main-menu.component';
import { AdminMenuComponent } from './menu/admin-menu.component';
import { AccountViewComponent } from './account-view/account-view.component';
import { AccountUpdateComponent } from './account-update/account-update.component';
import { CardListComponent } from './cards/card-list.component';
import { CardViewComponent } from './cards/card-view.component';
import { CardUpdateComponent } from './cards/card-update.component';
import { TransactionListComponent } from './transactions/transaction-list.component';
import { TransactionViewComponent } from './transactions/transaction-view.component';
import { TranAddComponent } from './transactions/tran-add.component';
import { BillPaymentComponent } from './bill-payment/bill-payment.component';
import { UserListComponent } from './user-admin/user-list.component';
import { UserAddComponent } from './user-admin/user-add.component';
import { UserUpdateComponent } from './user-admin/user-update.component';
import { UserDeleteComponent } from './user-admin/user-delete.component';
import { adminGuard, authGuard } from './auth/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'signin', pathMatch: 'full' },
  { path: 'signin', component: SignOnComponent },
  { path: 'menu', component: MainMenuComponent, canActivate: [authGuard] },
  { path: 'admin', component: AdminMenuComponent, canActivate: [adminGuard] },
  { path: 'accounts/view', component: AccountViewComponent, canActivate: [authGuard] },
  { path: 'account-update', component: AccountUpdateComponent, canActivate: [authGuard] },
  { path: 'cards/list', component: CardListComponent, canActivate: [authGuard] },
  { path: 'cards/view', component: CardViewComponent, canActivate: [authGuard] },
  { path: 'cards/update', component: CardUpdateComponent, canActivate: [authGuard] },
  { path: 'transactions/list', component: TransactionListComponent, canActivate: [authGuard] },
  { path: 'transactions/view', component: TransactionViewComponent, canActivate: [authGuard] },
  { path: 'transactions/add', component: TranAddComponent, canActivate: [authGuard] },
  { path: 'bill-payment', component: BillPaymentComponent, canActivate: [authGuard] },
  // S-12 User Admin (CU00..CU03); admin-only like the COADM01C-reachable source programs.,
  { path: 'admin/users', component: UserListComponent, canActivate: [adminGuard] },
  { path: 'admin/users/add', component: UserAddComponent, canActivate: [adminGuard] },
  { path: 'admin/users/update', component: UserUpdateComponent, canActivate: [adminGuard] },
  { path: 'admin/users/delete', component: UserDeleteComponent, canActivate: [adminGuard] }
];
