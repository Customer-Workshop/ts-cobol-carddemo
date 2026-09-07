import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * B-0027: a screen behind the sign-on needs the session identity the COMMAREA used to carry. With
 * no session the backend answers 401 and the SPA shows the sign-on screen — the target form of the
 * {@code EIBCALEN = 0} refusal (COMEN01C.cbl:82-84, COACTVWC.cbl:120-126).
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth.session().pipe(
    map(() => true),
    catchError(() => {
      void router.navigateByUrl('/signon');
      return of(false);
    }),
  );
};
