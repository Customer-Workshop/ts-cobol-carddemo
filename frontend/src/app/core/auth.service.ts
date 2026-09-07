import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';

/** POST /api/auth/signon success body (COSGN00C.cbl:221-239; landingTarget = B-0009 routing point). */
export interface AuthResponse {
  userId: string;
  userType: 'A' | 'U';
  landingTarget: string;
}

/** POPULATE-HEADER-INFO fields of map COSGN0A (COSGN00C.cbl:175-204). */
export interface ScreenHeader {
  tranId: string;
  programName: string;
  title01: string;
  title02: string;
  currentDate: string;
  currentTime: string;
  applId: string;
  sysId: string;
}

export interface SignoffResponse {
  message: string;
}

/** Client of the COSGN00C endpoints; the session cookie travels with every ApiService call. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);

  header(): Observable<ScreenHeader> {
    return this.api.get<ScreenHeader>('/auth/header');
  }

  signOn(userId: string, password: string): Observable<AuthResponse> {
    return this.api.post<AuthResponse>('/auth/signon', { userId, password });
  }

  signOff(): Observable<SignoffResponse> {
    return this.api.post<SignoffResponse>('/auth/signoff', {});
  }
}
