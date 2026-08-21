import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type MenuKind = 'main' | 'admin';

export interface MenuOption {
  id: string;
  name: string;
  enabled: boolean;
}

export interface MenuResponse {
  menu: MenuKind;
  options: MenuOption[];
}

export interface MenuNavigationTarget {
  id: string;
  name: string;
  programKey: string;
  route: string;
}

export interface MenuSelectResponse {
  outcome: 'invalidOption' | 'adminOnly' | 'comingSoon' | 'notInstalled' | 'navigate';
  message: string | null;
  severity: 'error' | 'info' | null;
  target: MenuNavigationTarget | null;
}

@Injectable({ providedIn: 'root' })
export class MenuService {
  private readonly http = inject(HttpClient);

  getMenu(menu: MenuKind): Observable<MenuResponse> {
    return this.http.get<MenuResponse>(`/api/v1/menu?menu=${menu}`);
  }

  select(menu: MenuKind, option: string): Observable<MenuSelectResponse> {
    return this.http.post<MenuSelectResponse>('/api/v1/menu/select', { menu, option });
  }
}
