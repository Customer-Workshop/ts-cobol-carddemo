import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { adminGuard, authGuard } from './auth.guard';

describe('auth guards', () => {
  const route = {} as ActivatedRouteSnapshot;
  const state = {} as RouterStateSnapshot;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()]
    });
    sessionStorage.clear();
  });

  afterEach(() => sessionStorage.clear());

  function run(guard: typeof authGuard): boolean | UrlTree {
    return TestBed.runInInjectionContext(() => guard(route, state)) as boolean | UrlTree;
  }

  it('authGuard redirects to /signin without a token', () => {
    const result = run(authGuard);
    expect(result.toString()).toBe(TestBed.inject(Router).parseUrl('/signin').toString());
  });

  it('authGuard allows access with a token', () => {
    sessionStorage.setItem('carddemo.token', 't');
    expect(run(authGuard)).toBeTrue();
  });

  it('adminGuard redirects a regular user to /menu (userType claim gate)', () => {
    sessionStorage.setItem('carddemo.token', 't');
    sessionStorage.setItem('carddemo.userType', 'U');
    const result = run(adminGuard);
    expect(result.toString()).toBe(TestBed.inject(Router).parseUrl('/menu').toString());
  });

  it('adminGuard allows an admin user', () => {
    sessionStorage.setItem('carddemo.token', 't');
    sessionStorage.setItem('carddemo.userType', 'A');
    expect(run(adminGuard)).toBeTrue();
  });
});
