import { Component, HostListener, OnInit, inject } from '@angular/core';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { AuthService, ScreenHeader } from '../../core/auth.service';
import { MenuOption, MenuScreen, MenuService } from '../../core/menu.service';

/**
 * COMEN01C main menu — map COMEN1A of mapset COMEN01 (app/bms/COMEN01.bms), field for field.
 * OPTION (POS 20,41, LENGTH=2, NUM, UNPROT, IC) is the only UNPROT field; the 11 option rows
 * OPTN001..OPTN011 and the header are display-only, ERRMSG (POS 23,1) shows the verbatim text.
 * Keys: ENTER = Continue, F3 / Esc = Exit (DV-03: no "invalid key" message for other keys).
 * Options 2..11 are rendered disabled and answer the Q-12 facade instead of an XCTL.
 */
@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatListModule],
  templateUrl: './menu.component.html',
  styleUrl: './menu.component.scss',
})
export class MenuComponent implements OnInit {
  private readonly menuService = inject(MenuService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Static text of the map, verbatim (app/bms/COMEN01.bms:29-162). */
  readonly labels = {
    tran: 'Tran:',
    date: 'Date:',
    prog: 'Prog:',
    time: 'Time:',
    title: 'Main Menu',
    prompt: 'Please select an option :',
    footer: 'ENTER=Continue  F3=Exit',
  };

  /** Target-only banner for an admin session (Q-01 / D-0024) — NEW TEXT, no legacy literal. */
  readonly adminBanner = 'Administration is not available in this release';

  readonly maxLength = 2;

  header: ScreenHeader = {
    tranId: 'CM00',
    programName: 'COMEN01C',
    title01: '',
    title02: '',
    currentDate: 'mm/dd/yy',
    currentTime: 'hh:mm:ss',
    applId: '',
    sysId: '',
  };

  options: MenuOption[] = [];
  option = '';
  errorMessage = '';
  submitting = false;
  isAdmin = false;

  ngOnInit(): void {
    this.menuService.menu().subscribe({
      next: (screen: MenuScreen) => {
        this.header = screen.header;
        this.options = screen.options;
      },
      error: (error: HttpErrorResponse) => this.handleError(error),
    });
    this.auth.session().subscribe({
      next: (session) => (this.isAdmin = session.userType === 'A'),
      error: () => {
        /* the menu request above owns the 401 handling */
      },
    });
  }

  /** OPTION is NUM with JUSTIFY=(RIGHT,ZERO) and LENGTH=2 (app/bms/COMEN01.bms:145-149). */
  onOptionInput(value: string): void {
    this.option = value.slice(0, this.maxLength);
  }

  /**
   * A click on a row is exactly typing its number and pressing ENTER: the option is put in the
   * field two digits wide, as the legacy screen shows it (COMEN01C.cbl:125).
   */
  choose(option: MenuOption): void {
    this.option = String(option.number).padStart(2, '0');
    this.submit();
  }

  /** ENTER -> PROCESS-ENTER-KEY (COMEN01C.cbl:93-95, :115-191). */
  submit(): void {
    if (this.submitting) {
      return;
    }
    this.errorMessage = '';
    this.submitting = true;
    this.menuService.select(this.option).subscribe({
      next: (selection) => {
        this.submitting = false;
        this.option = selection.option;
        if (selection.implemented && selection.route) {
          void this.router.navigateByUrl(selection.route);
          return;
        }
        this.errorMessage = selection.message ?? '';
      },
      error: (error: HttpErrorResponse) => {
        this.submitting = false;
        this.handleError(error);
      },
    });
  }

  /** PF3 -> XCTL 'COSGN00C' without COMMAREA (COMEN01C.cbl:96-98, :196-203): identity dropped. */
  exit(): void {
    this.auth.signOff().subscribe({
      next: () => void this.router.navigateByUrl('/signon'),
      error: () => void this.router.navigateByUrl('/signon'),
    });
  }

  /** DV-03: only F3 and Escape are bound; any other key falls through with no message. */
  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'F3' || event.key === 'Escape') {
      event.preventDefault();
      this.exit();
    }
  }

  /**
   * 401 is the target form of the {@code EIBCALEN = 0} refusal (:82-84): back to sign-on. Any other
   * failure keeps the menu on screen with the server's verbatim text; a 400 also restores the
   * normalised OPTIONO echo (:125).
   */
  private handleError(error: HttpErrorResponse): void {
    if (error.status === 401) {
      void this.router.navigateByUrl('/signon');
      return;
    }
    const body = error.error as { message?: string; option?: string } | null;
    if (body?.option !== undefined) {
      this.option = body.option;
    }
    this.errorMessage = body?.message ?? '';
  }
}
