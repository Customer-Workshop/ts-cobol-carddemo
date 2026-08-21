import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return auth.token ? true : router.parseUrl('/signin');
};

/** Admin menu is reachable only with the JWT userType 'A' claim (COADM01C routing rule). */
export const adminGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!auth.token) {
    return router.parseUrl('/signin');
  }
  return auth.userType === 'A' ? true : router.parseUrl('/menu');
};
