import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../auth/auth.service';

export const MSG_ENTER_USER_ID = 'Please enter User ID ...';
export const MSG_ENTER_PASSWORD = 'Please enter Password ...';
export const MSG_UNABLE_TO_VERIFY = 'Unable to verify the User ...';
export const MSG_THANK_YOU = 'Thank you for using CardDemo application...';

/**
 * Sign-on screen, equivalent of BMS map COSGN0A (app/bms/COSGN00.bms):
 * User ID X(8), dark Password X(8), 80-char error message area,
 * ENTER submits, Exit is the PF3 equivalent (farewell message).
 */
@Component({
  selector: 'app-sign-on',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './sign-on.component.html',
  styleUrl: './sign-on.component.scss'
})
export class SignOnComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  userId = '';
  password = '';
  errorMessage = '';
  exited = false;
  farewellMessage = MSG_THANK_YOU;

  submit(): void {
    this.errorMessage = '';
    if (!this.userId.trim()) {
      this.errorMessage = MSG_ENTER_USER_ID;
      return;
    }
    if (!this.password.trim()) {
      this.errorMessage = MSG_ENTER_PASSWORD;
      return;
    }

    this.authService.signIn(this.userId, this.password).subscribe({
      next: (response) => this.router.navigateByUrl(response.landingRoute),
      error: (error: HttpErrorResponse) => {
        this.errorMessage = error.error?.message ?? MSG_UNABLE_TO_VERIFY;
        this.password = '';
      }
    });
  }

  exit(): void {
    this.exited = true;
  }
}
