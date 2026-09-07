import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder route only — the real slice (COMEN01C / COMEN01 — wave 2) is out of wave 1. */
@Component({
  selector: 'app-menu',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-title>Main Menu</mat-card-title>
      <mat-card-content>Not yet available in this release.</mat-card-content>
    </mat-card>
  `,
})
export class MenuComponent {}
