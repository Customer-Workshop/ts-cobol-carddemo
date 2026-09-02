import { NgClass, SlicePipe } from '@angular/common';
import { Component, ElementRef, HostListener, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
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
  severityClass
} from './user-admin-screen';

/**
 * Add user screen, equivalent of BMS map COUSR1A (app/bms/COUSR01.bms):
 * First Name X(20), Last Name X(20), User ID X(8), dark Password X(8), User Type X(1),
 * 78-char message area. ENTER adds, F3 returns to the admin menu, F4 clears the form;
 * every other function key (F12 included, despite the footer) is an unmapped AID (FR-S12-17..24).
 */
@Component({
  selector: 'app-user-add',
  standalone: true,
  imports: [NgClass, SlicePipe, FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './user-add.component.html',
  styleUrl: './user-admin-screen.scss'
})
export class UserAddComponent {
  private readonly userAdminService = inject(UserAdminService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly lenName = LEN_NAME;
  readonly lenUserId = LEN_USER_ID;
  readonly lenPassword = LEN_PASSWORD;
  readonly lenUserType = LEN_USER_TYPE;
  readonly lenMessage = LEN_MESSAGE;
  readonly severityClass = severityClass;

  firstName = '';
  lastName = '';
  userId = '';
  password = '';
  userType = '';
  message = '';
  messageSeverity: UserAdminSeverity | null = null;

  submit(): void {
    this.userAdminService
      .add({
        firstName: this.firstName,
        lastName: this.lastName,
        userId: this.userId,
        password: this.password,
        userType: this.userType
      })
      .subscribe((response) => this.apply(response));
  }

  exit(): void {
    this.router.navigateByUrl(ADMIN_MENU_ROUTE);
  }

  clear(): void {
    this.firstName = '';
    this.lastName = '';
    this.userId = '';
    this.password = '';
    this.userType = '';
    this.message = '';
    this.messageSeverity = null;
    focusField(this.host, 'firstName');
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
      default:
        this.message = MSG_INVALID_KEY;
        this.messageSeverity = 'error';
    }
  }

  private apply(response: UserAdminResponse): void {
    if (response.outcome === 'success') {
      this.firstName = '';
      this.lastName = '';
      this.userId = '';
      this.password = '';
      this.userType = '';
    }
    this.message = response.message ?? '';
    this.messageSeverity = response.severity;
    focusField(this.host, response.focusField);
  }
}
