import { NgClass, SlicePipe } from '@angular/common';
import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import {
  UserAdminService,
  UserAdminSeverity,
  UserListAction,
  UserListResponse,
  UserListRow
} from './user-admin.service';
import {
  ADMIN_MENU_ROUTE,
  LEN_MESSAGE,
  LEN_SELECT,
  LEN_USER_ID,
  MSG_INVALID_KEY,
  PROGRAM_USER_DELETE,
  PROGRAM_USER_LIST,
  PROGRAM_USER_UPDATE,
  USER_DELETE_ROUTE,
  USER_UPDATE_ROUTE,
  functionKeyOf,
  severityClass
} from './user-admin-screen';

export const PAGE_SIZE = 10;

/** Messages after which COUSR00C re-sends the screen without touching the rows (guards and lookup failure). */
const RETAIN_ROWS_MESSAGES = new Set([
  'You are already at the top of the page...',
  'You are already at the bottom of the page...',
  'Unable to lookup User...'
]);

export interface UserListScreenRow extends UserListRow {
  select: string;
}

/**
 * User list screen, equivalent of BMS map COUSR0A (app/bms/COUSR00.bms):
 * search User ID X(8), 10 rows of SEL X(1) / User ID / First Name / Last Name / Type,
 * page number, 78-char message area. ENTER searches or dispatches the first selected row
 * (U -> COUSR02C, D -> COUSR03C), F7/F8 page, F3 returns to the admin menu, any other
 * function key is an unmapped AID (FR-S12-01..16).
 */
@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [NgClass, SlicePipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './user-list.component.html',
  styleUrl: './user-admin-screen.scss'
})
export class UserListComponent implements OnInit {
  private readonly userAdminService = inject(UserAdminService);
  private readonly router = inject(Router);

  readonly lenUserId = LEN_USER_ID;
  readonly lenSelect = LEN_SELECT;
  readonly lenMessage = LEN_MESSAGE;
  readonly severityClass = severityClass;

  searchUserId = '';
  rows: UserListScreenRow[] = [];
  pageNum = 0;
  nextPage = false;
  firstUserId: string | null = null;
  lastUserId: string | null = null;
  message = '';
  messageSeverity: UserAdminSeverity | null = null;

  ngOnInit(): void {
    this.send('enter');
  }

  submit(): void {
    this.send('enter');
  }

  pageForward(): void {
    this.send('pageForward');
  }

  pageBackward(): void {
    this.send('pageBackward');
  }

  exit(): void {
    this.router.navigateByUrl(ADMIN_MENU_ROUTE);
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const key = functionKeyOf(event);
    if (!key) {
      return;
    }
    event.preventDefault();
    switch (key) {
      case 'F3':
        this.exit();
        break;
      case 'F7':
        this.pageBackward();
        break;
      case 'F8':
        this.pageForward();
        break;
      default:
        this.message = MSG_INVALID_KEY;
        this.messageSeverity = 'error';
    }
  }

  private send(action: UserListAction): void {
    this.userAdminService
      .list({
        action,
        searchUserId: this.searchUserId,
        selections: this.rows.map((row) => ({ userId: row.userId, flag: row.select })),
        pageNum: this.pageNum,
        nextPage: this.nextPage,
        firstUserId: this.firstUserId,
        lastUserId: this.lastUserId
      })
      .subscribe((response) => this.apply(response));
  }

  private apply(response: UserListResponse): void {
    if (response.navigate) {
      const route = response.navigate.programKey === PROGRAM_USER_UPDATE ? USER_UPDATE_ROUTE
        : response.navigate.programKey === PROGRAM_USER_DELETE ? USER_DELETE_ROUTE
        : null;
      if (route) {
        this.router.navigate([route], { queryParams: { userId: response.navigate.userId, from: PROGRAM_USER_LIST } });
        return;
      }
    }

    this.message = response.message ?? '';
    this.messageSeverity = response.severity;
    this.pageNum = response.pageNum;
    this.nextPage = response.nextPage;
    this.firstUserId = response.firstUserId;
    this.lastUserId = response.lastUserId;

    if (response.rows.length === 0 && response.message && RETAIN_ROWS_MESSAGES.has(response.message)) {
      return;
    }
    this.rows = response.rows.map((row) => ({ ...row, select: '' }));
    this.searchUserId = response.searchUserId ?? '';
  }
}
