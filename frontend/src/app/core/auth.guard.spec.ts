import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { firstValueFrom, isObservable, Observable } from 'rxjs';
import { authGuard } from './auth.guard';

/** B-0027: /menu and /accounts/view need the session identity; without it the SPA shows sign-on. */
describe('authGuard', () => {
  let http: HttpTestingController;
  let router: Router;

  const run = (): Promise<boolean> => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    return firstValueFrom(result as Observable<boolean>);
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
  });

  afterEach(() => http.verify());

  it('is an observable guard: the session is only probed once the router subscribes', () => {
    const result = TestBed.runInInjectionContext(() => authGuard({} as never, {} as never));
    expect(isObservable(result)).toBeTrue();
    http.expectNone('/api/auth/session');
  });

  it('allows the route when the session answers 200', async () => {
    const allowed = run();
    http.expectOne('/api/auth/session').flush({ userId: 'USER0001', userType: 'U' });
    expect(await allowed).toBeTrue();
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('blocks the route and routes to /signon on 401', async () => {
    const allowed = run();
    http.expectOne('/api/auth/session').flush(
      { message: 'Authentication required', status: 401, timestamp: '2026-09-07T00:00:00Z' },
      { status: 401, statusText: 'Unauthorized' },
    );
    expect(await allowed).toBeFalse();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/signon');
  });
});
