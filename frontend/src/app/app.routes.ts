import { Routes } from '@angular/router';
import { SignOnComponent } from './sign-on/sign-on.component';
import { MainMenuComponent } from './menu/main-menu.component';
import { AdminMenuComponent } from './menu/admin-menu.component';
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
  // S-12 User Admin (CU00..CU03); admin-only like the COADM01C-reachable source programs.
  { path: 'admin/users', component: UserListComponent, canActivate: [adminGuard] },
  { path: 'admin/users/add', component: UserAddComponent, canActivate: [adminGuard] },
  { path: 'admin/users/update', component: UserUpdateComponent, canActivate: [adminGuard] },
  { path: 'admin/users/delete', component: UserDeleteComponent, canActivate: [adminGuard] }
];
