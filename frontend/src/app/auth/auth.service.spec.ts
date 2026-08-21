import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpClient } from '@angular/common/http';
import { AuthService, SignInResponse } from './auth.service';
import { authInterceptor } from './auth.interceptor';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;
  let http: HttpClient;

  const successResponse: SignInResponse = {
    token: 'jwt-token',
    userId: 'ADMIN001',
    userType: 'A',
    landingRoute: '/admin'
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting()
      ]
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
    http = TestBed.inject(HttpClient);
    sessionStorage.clear();
  });

  afterEach(() => {
    httpMock.verify();
    sessionStorage.clear();
  });

  it('posts credentials to /api/v1/auth/signin and stores the session', () => {
    let result: SignInResponse | undefined;
    service.signIn('ADMIN001', 'PASSWORD').subscribe((r) => (result = r));

    const req = httpMock.expectOne('/api/v1/auth/signin');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ userId: 'ADMIN001', password: 'PASSWORD' });
    req.flush(successResponse);

    expect(result).toEqual(successResponse);
    expect(service.token).toBe('jwt-token');
    expect(service.userType).toBe('A');
    expect(service.userId).toBe('ADMIN001');
  });

  it('does not store a session on sign-in failure', () => {
    let error: unknown;
    service.signIn('USER0001', 'WRONGPWD').subscribe({ error: (e) => (error = e) });

    httpMock
      .expectOne('/api/v1/auth/signin')
      .flush({ message: 'Wrong Password. Try again ...' }, { status: 401, statusText: 'Unauthorized' });

    expect(error).toBeTruthy();
    expect(service.token).toBeNull();
  });

  it('signOut clears the stored session', () => {
    service.signIn('ADMIN001', 'PASSWORD').subscribe();
    httpMock.expectOne('/api/v1/auth/signin').flush(successResponse);

    service.signOut();

    expect(service.token).toBeNull();
    expect(service.userType).toBeNull();
    expect(service.userId).toBeNull();
  });

  it('interceptor attaches the JWT as a Bearer header once signed in', () => {
    service.signIn('ADMIN001', 'PASSWORD').subscribe();
    httpMock.expectOne('/api/v1/auth/signin').flush(successResponse);

    http.get('/api/v1/health').subscribe();
    const req = httpMock.expectOne('/api/v1/health');
    expect(req.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    req.flush({ status: 'ok' });
  });
});
