import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

/** ERRMSG attribute colour: DFHRED / DFHGREEN / DFHNEUTR. */
export type UserAdminSeverity = 'error' | 'success' | 'neutral';

export type UserAdminOutcome = 'success' | 'validationError' | 'notFound' | 'duplicate' | 'noChange' | 'storeError';

export interface UserAdminDetails {
  userId: string;
  firstName: string;
  lastName: string;
  userType: string;
}

export interface UserAdminResponse {
  outcome: UserAdminOutcome;
  message: string | null;
  severity: UserAdminSeverity | null;
  focusField: string | null;
  user: UserAdminDetails | null;
}

export interface UserAddRequest {
  firstName: string;
  lastName: string;
  userId: string;
  password: string;
  userType: string;
}

export interface UserUpdateRequest {
  userId: string;
  firstName: string;
  lastName: string;
  password: string;
  userType: string;
}

export type UserListAction = 'enter' | 'pageForward' | 'pageBackward';

export interface UserListSelection {
  userId: string;
  flag: string;
}

/** Screen input plus the CDEMO-CU00-INFO COMMAREA state kept by the list screen. */
export interface UserListRequest {
  action: UserListAction;
  searchUserId: string;
  selections: UserListSelection[];
  pageNum: number;
  nextPage: boolean;
  firstUserId: string | null;
  lastUserId: string | null;
}

export interface UserListRow {
  userId: string;
  firstName: string;
  lastName: string;
  userType: string;
}

export interface UserListNavigation {
  programKey: 'COUSR02C' | 'COUSR03C' | string;
  userId: string;
}

export interface UserListResponse {
  rows: UserListRow[];
  pageNum: number;
  nextPage: boolean;
  firstUserId: string | null;
  lastUserId: string | null;
  searchUserId: string | null;
  message: string | null;
  severity: UserAdminSeverity | null;
  navigate: UserListNavigation | null;
}

/** CU00..CU03 over /api/v1/admin/users (admin-only, S12-B5). */
@Injectable({ providedIn: 'root' })
export class UserAdminService {
  private readonly http = inject(HttpClient);

  list(request: UserListRequest): Observable<UserListResponse> {
    return this.http.post<UserListResponse>('/api/v1/admin/users/list', request);
  }

  add(request: UserAddRequest): Observable<UserAdminResponse> {
    return this.http.post<UserAdminResponse>('/api/v1/admin/users/add', request);
  }

  fetchForUpdate(userId: string): Observable<UserAdminResponse> {
    return this.http.post<UserAdminResponse>('/api/v1/admin/users/update/fetch', { userId });
  }

  update(request: UserUpdateRequest): Observable<UserAdminResponse> {
    return this.http.post<UserAdminResponse>('/api/v1/admin/users/update', request);
  }

  fetchForDelete(userId: string): Observable<UserAdminResponse> {
    return this.http.post<UserAdminResponse>('/api/v1/admin/users/delete/fetch', { userId });
  }

  delete(userId: string): Observable<UserAdminResponse> {
    return this.http.post<UserAdminResponse>('/api/v1/admin/users/delete', { userId });
  }
}
