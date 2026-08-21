import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, tap } from 'rxjs';

export interface SignInResponse {
  token: string;
  userId: string;
  userType: 'A' | 'U';
  landingRoute: string;
}

const TOKEN_KEY = 'carddemo.token';
const USER_TYPE_KEY = 'carddemo.userType';
const USER_ID_KEY = 'carddemo.userId';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  signIn(userId: string, password: string): Observable<SignInResponse> {
    return this.http
      .post<SignInResponse>('/api/v1/auth/signin', { userId, password })
      .pipe(tap((response) => this.storeSession(response)));
  }

  get token(): string | null {
    return sessionStorage.getItem(TOKEN_KEY);
  }

  get userType(): string | null {
    return sessionStorage.getItem(USER_TYPE_KEY);
  }

  get userId(): string | null {
    return sessionStorage.getItem(USER_ID_KEY);
  }

  signOut(): void {
    sessionStorage.removeItem(TOKEN_KEY);
    sessionStorage.removeItem(USER_TYPE_KEY);
    sessionStorage.removeItem(USER_ID_KEY);
  }

  private storeSession(response: SignInResponse): void {
    sessionStorage.setItem(TOKEN_KEY, response.token);
    sessionStorage.setItem(USER_TYPE_KEY, response.userType);
    sessionStorage.setItem(USER_ID_KEY, response.userId);
  }
}
