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
  LEN_USER_ID,
  LEN_USER_TYPE,
  MSG_INVALID_KEY,
  focusField,
  functionKeyOf,
  returnRouteFor,
  severityClass
} from './user-admin-screen';

/**
 * Delete user screen, equivalent of BMS map COUSR3A (app/bms/COUSR03.bms):
 * User ID X(8) input; First Name X(20), Last Name X(20), User Type X(1) display-only;
 * 78-char message area. ENTER fetches, F5 deletes, F3 returns to the caller
 * (`from` query param = COUSR00C -> list, else admin menu), F12 returns to the admin menu,
 * F4 clears; any other function key is an unmapped AID (FR-S12-37..40).
 * A `userId` query param (CDEMO-CU03-USR-SELECTED) is fetched on entry.
 */
@Component({
  selector: 'app-user-delete',
  standalone: true,
  imports: [NgClass, SlicePipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './user-delete.component.html',
  styleUrl: './user-admin-screen.scss'
})
export class UserDeleteComponent implements OnInit {
  private readonly userAdminService = inject(UserAdminService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly lenName = LEN_NAME;
  readonly lenUserId = LEN_USER_ID;
  readonly lenUserType = LEN_USER_TYPE;
  readonly lenMessage = LEN_MESSAGE;
  readonly severityClass = severityClass;

  userId = '';
  firstName = '';
  lastName = '';
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
    this.userAdminService.fetchForDelete(this.userId).subscribe((response) => this.apply(response));
  }

  /** PF5: DELETE-USER-INFO. */
  remove(): void {
    this.userAdminService.delete(this.userId).subscribe((response) => this.apply(response));
  }

  /** PF3: RETURN-TO-PREV-SCREEN with CDEMO-FROM-PROGRAM (COADM01C when blank). */
  exit(): void {
    this.router.navigateByUrl(returnRouteFor(this.fromProgram));
  }

  /** PF12: RETURN-TO-PREV-SCREEN with COADM01C. */
  cancel(): void {
    this.router.navigateByUrl(ADMIN_MENU_ROUTE);
  }

  /** PF4: CLEAR-CURRENT-SCREEN. */
  clear(): void {
    this.clearFields();
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
        this.exit();
        break;
      case 'F4':
        this.clear();
        break;
      case 'F5':
        this.remove();
        break;
      case 'F12':
        this.cancel();
        break;
      default:
        this.message = MSG_INVALID_KEY;
        this.messageSeverity = 'error';
    }
  }

  private clearFields(): void {
    this.userId = '';
    this.firstName = '';
    this.lastName = '';
    this.userType = '';
  }

  private apply(response: UserAdminResponse): void {
    if (response.user) {
      this.userId = response.user.userId;
      this.firstName = response.user.firstName;
      this.lastName = response.user.lastName;
      this.userType = response.user.userType;
    } else if (response.outcome === 'success') {
      this.clearFields();
    }
    this.message = response.message ?? '';
    this.messageSeverity = response.severity;
    focusField(this.host, response.focusField);
  }
}
