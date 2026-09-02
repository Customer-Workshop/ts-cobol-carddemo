import { NgClass, SlicePipe } from '@angular/common';
import { Component, ElementRef, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { UserAdminResponse, UserAdminService, UserAdminSeverity } from './user-admin.service';
import {
  ADMIN_MENU_ROUTE,
  LEN_MESSAGE,
  LEN_NAME,
  LEN_PASSWORD,
  LEN_USER_ID,
  LEN_USER_TYPE,
  MSG_INVALID_KEY,
  focusField,
  functionKeyOf,
  returnRouteFor,
  severityClass
} from './user-admin-screen';

/**
 * Update user screen, equivalent of BMS map COUSR2A (app/bms/COUSR02.bms):
 * User ID X(8), First Name X(20), Last Name X(20), dark Password X(8), User Type X(1),
 * 78-char message area. ENTER fetches, F5 saves, F3 saves then returns to the caller
 * (`from` query param = COUSR00C -> list, else admin menu), F12 returns to the admin menu
 * without saving, F4 clears; any other function key is an unmapped AID (FR-S12-24..36).
 * A `userId` query param (CDEMO-CU02-USR-SELECTED) is fetched on entry.
 */
@Component({
  selector: 'app-user-update',
  standalone: true,
  imports: [NgClass, SlicePipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './user-update.component.html',
  styleUrl: './user-admin-screen.scss'
})
export class UserUpdateComponent implements OnInit {
  private readonly userAdminService = inject(UserAdminService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly lenName = LEN_NAME;
  readonly lenUserId = LEN_USER_ID;
  readonly lenPassword = LEN_PASSWORD;
  readonly lenUserType = LEN_USER_TYPE;
  readonly lenMessage = LEN_MESSAGE;
  readonly severityClass = severityClass;

  userId = '';
  firstName = '';
  lastName = '';
  password = '';
  userType = '';
  message = '';
  messageSeverity: UserAdminSeverity | null = null;
  fromProgram: string | null = null;

  ngOnInit(): void {
    const params = this.route.snapshot.queryParamMap;
    this.fromProgram = params.get('from');
    const selected = params.get('userId');
    if (selected && selected.trim()) {
      this.userId = selected;
      this.fetch();
    }
  }

  /** ENTER: PROCESS-ENTER-KEY. */
  fetch(): void {
    this.userAdminService.fetchForUpdate(this.userId).subscribe((response) => this.apply(response));
  }

  /** PF5: UPDATE-USER-INFO, stay on the screen. */
  save(): void {
    this.userAdminService.update(this.request()).subscribe((response) => this.apply(response));
  }

  /** PF3: UPDATE-USER-INFO then RETURN-TO-PREV-SCREEN regardless of the outcome. */
  saveAndExit(): void {
    this.userAdminService.update(this.request()).subscribe({
      next: () => this.returnToCaller(),
      error: () => this.returnToCaller()
    });
  }

  /** PF12: RETURN-TO-PREV-SCREEN with COADM01C. */
  cancel(): void {
    this.router.navigateByUrl(ADMIN_MENU_ROUTE);
  }

  /** PF4: CLEAR-CURRENT-SCREEN. */
  clear(): void {
    this.userId = '';
    this.firstName = '';
    this.lastName = '';
    this.password = '';
    this.userType = '';
    this.message = '';
    this.messageSeverity = null;
    focusField(this.host, 'userId');
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
        this.saveAndExit();
        break;
      case 'F4':
        this.clear();
        break;
      case 'F5':
        this.save();
        break;
      case 'F12':
        this.cancel();
        break;
      default:
        this.message = MSG_INVALID_KEY;
        this.messageSeverity = 'error';
    }
  }

  private request() {
    return {
      userId: this.userId,
      firstName: this.firstName,
      lastName: this.lastName,
      password: this.password,
      userType: this.userType
    };
  }

  private returnToCaller(): void {
    this.router.navigateByUrl(returnRouteFor(this.fromProgram));
  }

  private apply(response: UserAdminResponse): void {
    if (response.user) {
      this.userId = response.user.userId;
      this.firstName = response.user.firstName;
      this.lastName = response.user.lastName;
      this.userType = response.user.userType;
      this.password = '';
    }
    this.message = response.message ?? '';
    this.messageSeverity = response.severity;
    focusField(this.host, response.focusField);
  }
}
