import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, MatToolbarModule],
  template: `
    <mat-toolbar color="primary">
      <span>CardDemo &mdash; Modernized Frontend</span>
    </mat-toolbar>
    <router-outlet />
  `,
  styles: [`
    mat-toolbar { margin-bottom: 24px; }
  `],
})
export class AppComponent {}
