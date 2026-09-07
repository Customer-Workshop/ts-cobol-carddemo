import { Component, ElementRef, HostListener, OnInit, ViewChild, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService, ScreenHeader } from '../../core/auth.service';

/**
 * COSGN00C sign-on screen — map COSGN0A of mapset COSGN00 (app/bms/COSGN00.bms), field for field.
 * USERID (POS 19,43, LENGTH=8, IC) and PASSWD (POS 20,43, LENGTH=8, DRK) are the only UNPROT
 * fields; everything else is display-only. ERRMSG (POS 23,1) shows the server's verbatim text.
 * Keys: ENTER = sign on, F3 / Esc = Exit (DV-03: no "invalid key" message for other keys).
 */
@Component({
  selector: 'app-signon',
  standalone: true,
  imports: [MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  templateUrl: './signon.component.html',
  styleUrl: './signon.component.scss',
})
export class SignonComponent implements OnInit {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  @ViewChild('userIdInput') userIdInput?: ElementRef<HTMLInputElement>;
  @ViewChild('passwordInput') passwordInput?: ElementRef<HTMLInputElement>;

  /** Static text of the map, verbatim (app/bms/COSGN00.bms). */
  readonly labels = {
    tran: 'Tran :',
    date: 'Date :',
    prog: 'Prog :',
    time: 'Time :',
    appId: 'AppID:',
    sysId: 'SysID:',
    intro: 'This is a Credit Card Demo Application for Mainframe Modernization',
    prompt: 'Type your User ID and Password, then press ENTER:',
    userId: 'User ID     :',
    password: 'Password    :',
    eightChar: '(8 Char)',
    footer: 'ENTER=Sign-on  F3=Exit',
  };

  /** Rows 7..15 of the map (app/bms/COSGN00.bms:100-144). */
  readonly banner = [
    '+========================================+',
    '|%%%%%%%  NATIONAL RESERVE NOTE  %%%%%%%%|',
    '|%(1)  THE UNITED STATES OF KICSLAND (1)%|',
    '|%$$              ___       ********  $$%|',
    '|%$    {x}       (o o)                 $%|',
    '|%$     ******  (  V  )      O N E     $%|',
    '|%(1)          ---m-m---             (1)%|',
    '|%%~~~~~~~~~~~ ONE DOLLAR ~~~~~~~~~~~~~%%|',
    '+========================================+',
  ];

  readonly bannerText = this.banner.join('\n');

  readonly maxLength = 8;

  header: ScreenHeader = {
    tranId: 'CC00',
    programName: 'COSGN00C',
    title01: '',
    title02: '',
    currentDate: 'mm/dd/yy',
    currentTime: 'hh:mm:ss',
    applId: '',
    sysId: '',
  };

  userId = '';
  password = '';
  errorMessage = '';
  submitting = false;
  signedOff = false;
  signOffMessage = '';

  ngOnInit(): void {
    this.auth.header().subscribe({
      next: (header) => (this.header = header),
      error: () => {
        /* header stays at its map defaults; the sign-on itself is unaffected */
      },
    });
  }

  /** FUNCTION UPPER-CASE on the input (COSGN00C.cbl:132-133), applied as the user types. */
  onUserIdInput(value: string): void {
    this.userId = value.toUpperCase().slice(0, this.maxLength);
  }

  onPasswordInput(value: string): void {
    this.password = value.slice(0, this.maxLength);
  }

  /** ENTER -> PROCESS-ENTER-KEY (COSGN00C.cbl:85-86). */
  signOn(): void {
    if (this.submitting || this.signedOff) {
      return;
    }
    this.errorMessage = '';
    this.submitting = true;
    this.auth.signOn(this.userId, this.password).subscribe({
      next: (response) => {
        this.submitting = false;
        void this.router.navigateByUrl(response.landingTarget);
      },
      error: (error: HttpErrorResponse) => {
        this.submitting = false;
        this.errorMessage = this.messageOf(error);
        this.focusAfterError();
      },
    });
  }

  /** PF3 -> SEND TEXT thank-you (COSGN00C.cbl:86-88, :162-172). */
  exit(): void {
    if (this.signedOff) {
      return;
    }
    this.auth.signOff().subscribe({
      next: (response) => {
        this.signedOff = true;
        this.signOffMessage = response.message.trim();
        this.errorMessage = '';
      },
      error: (error: HttpErrorResponse) => {
        this.errorMessage = this.messageOf(error);
      },
    });
  }

  /** Re-enter the program (EIBCALEN = 0, COSGN00C.cbl:80-84): a fresh, empty screen. */
  signOnAgain(): void {
    this.signedOff = false;
    this.signOffMessage = '';
    this.userId = '';
    this.password = '';
    this.errorMessage = '';
  }

  /** DV-03: only F3 and Escape are bound; any other key falls through untouched. */
  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'F3' || event.key === 'Escape') {
      event.preventDefault();
      this.exit();
    }
  }

  private messageOf(error: HttpErrorResponse): string {
    const body = error.error as { message?: string } | null;
    return body?.message ?? 'Unable to verify the User ...';
  }

  /**
   * Cursor placement of the COBOL: blank password (:124) and wrong password (:244) put the cursor
   * on PASSWD; every other message puts it on USERID (:119, :249, :254).
   */
  private focusAfterError(): void {
    if (
      this.errorMessage === 'Please enter Password ...' ||
      this.errorMessage === 'Wrong Password. Try again ...'
    ) {
      this.passwordInput?.nativeElement.focus();
    } else {
      this.userIdInput?.nativeElement.focus();
    }
  }
}
