import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';

describe('ApiService', () => {
  let service: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sends GET under /api with credentials', () => {
    service.get('/accounts/1').subscribe();
    const req = http.expectOne('/api/accounts/1');
    expect(req.request.method).toBe('GET');
    expect(req.request.withCredentials).toBeTrue();
    req.flush({});
  });

  it('sends POST under /api with credentials and body', () => {
    service.post('/auth/signon', { userId: 'USER0001' }).subscribe();
    const req = http.expectOne('/api/auth/signon');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBeTrue();
    expect(req.request.body).toEqual({ userId: 'USER0001' });
    req.flush({});
  });
});
