import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'accounts', pathMatch: 'full' },
  {
    path: 'accounts',
    loadComponent: () =>
      import('./accounts/account-list.component').then(
        (m) => m.AccountListComponent,
      ),
  },
];
