import { Component, HostListener, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { AuthService } from '../auth/auth.service';
import { MenuOption, MenuService } from './menu.service';
import { MSG_INVALID_KEY, classifyAidKey } from '../shared/invalid-key';

/**
 * Main menu screen, equivalent of BMS map COMEN1A (app/bms/COMEN01.bms):
 * 11 numbered option rows from the route registry, 2-char option field,
 * 80-char message area, ENTER selects, Exit / F3 is the PF3 equivalent (FR-S01-10..16);
 * any other function key is an unmapped AID (FR-S01-20).
 */
@Component({
  selector: 'app-main-menu',
  standalone: true,
  imports: [FormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './menu-screen.html',
  styleUrl: './menu-screen.scss'
})
export class MainMenuComponent implements OnInit {
  private readonly menuService = inject(MenuService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly title = 'Main Menu';
  options: MenuOption[] = [];
  option = '';
  message = '';
  messageSeverity: 'error' | 'info' | null = null;

  ngOnInit(): void {
    this.menuService.getMenu('main').subscribe((response) => (this.options = response.options));
  }

  submit(): void {
    this.message = '';
    this.messageSeverity = null;
    this.menuService.select('main', this.option).subscribe((result) => {
      if (result.outcome === 'navigate' && result.target?.route) {
        this.router.navigateByUrl(result.target.route);
        return;
      }
      this.message = result.message ?? '';
      this.messageSeverity = result.severity;
    });
  }

  exit(): void {
    this.authService.signOut();
    this.router.navigateByUrl('/signin');
  }

  @HostListener('window:keydown', ['$event'])
  onKeydown(event: KeyboardEvent): void {
    const action = classifyAidKey(event);
    if (!action) {
      return;
    }
    event.preventDefault();
    if (action === 'exit') {
      this.exit();
    } else {
      this.message = MSG_INVALID_KEY;
      this.messageSeverity = 'error';
    }
  }
}
