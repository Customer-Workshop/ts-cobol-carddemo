import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ApiService } from './api.service';
import { ScreenHeader } from './auth.service';

/** One row of the static option table (app/cpy/COMEN02Y.cpy:19-90, B-0033). */
export interface MenuOption {
  number: number;
  name: string;
  program: string;
  endpoint: string | null;
  route: string | null;
  implemented: boolean;
  userType: string;
}

/** GET /api/menu — the initial SEND MAP of COMEN1A (COMEN01C.cbl:208-303). */
export interface MenuScreen {
  header: ScreenHeader;
  options: MenuOption[];
}

/** POST /api/menu/select — outcome of PROCESS-ENTER-KEY (COMEN01C.cbl:115-191). */
export interface MenuSelection {
  option: string;
  program: string | null;
  endpoint: string | null;
  route: string | null;
  implemented: boolean;
  message: string | null;
}

/** Client of the COMEN01C endpoints; the session cookie travels with every ApiService call. */
@Injectable({ providedIn: 'root' })
export class MenuService {
  private readonly api = inject(ApiService);

  menu(): Observable<MenuScreen> {
    return this.api.get<MenuScreen>('/menu');
  }

  select(option: string): Observable<MenuSelection> {
    return this.api.post<MenuSelection>('/menu/select', { option });
  }
}
