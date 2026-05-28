import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () =>
      import('./components/login/login.component').then(m => m.LoginComponent),
  },
  {
    path: '',
    loadComponent: () =>
      import('./components/layout/layout.component').then(m => m.LayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./components/dashboard/dashboard.component').then(m => m.DashboardComponent),
      },
      {
        path: 'accounts',
        loadComponent: () =>
          import('./components/accounts/account-list/account-list.component').then(
            m => m.AccountListComponent,
          ),
      },
      {
        path: 'accounts/:id',
        loadComponent: () =>
          import('./components/accounts/account-detail/account-detail.component').then(
            m => m.AccountDetailComponent,
          ),
      },
      {
        path: 'cards',
        loadComponent: () =>
          import('./components/cards/card-list/card-list.component').then(
            m => m.CardListComponent,
          ),
      },
      {
        path: 'cards/:cardNumber',
        loadComponent: () =>
          import('./components/cards/card-detail/card-detail.component').then(
            m => m.CardDetailComponent,
          ),
      },
      {
        path: 'transactions',
        loadComponent: () =>
          import('./components/transactions/transaction-list/transaction-list.component').then(
            m => m.TransactionListComponent,
          ),
      },
      {
        path: 'transactions/add',
        loadComponent: () =>
          import('./components/transactions/transaction-add/transaction-add.component').then(
            m => m.TransactionAddComponent,
          ),
      },
      {
        path: 'transactions/:id',
        loadComponent: () =>
          import('./components/transactions/transaction-detail/transaction-detail.component').then(
            m => m.TransactionDetailComponent,
          ),
      },
      {
        path: 'bill-payment',
        loadComponent: () =>
          import('./components/bill-payment/bill-payment.component').then(
            m => m.BillPaymentComponent,
          ),
      },
    ],
  },
  { path: '**', redirectTo: 'login' },
];
