import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder route only — the real slice (COACTVWC / COACTVW — wave 3) is out of wave 1. */
@Component({
  selector: 'app-account-view',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-title>Account View</mat-card-title>
      <mat-card-content>Not yet available in this release.</mat-card-content>
    </mat-card>
  `,
})
export class AccountViewComponent {}
