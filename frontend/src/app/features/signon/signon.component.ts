import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

/** Placeholder route only — the real slice (COSGN00C / COSGN00 — wave 2) is out of wave 1. */
@Component({
  selector: 'app-signon',
  standalone: true,
  imports: [MatCardModule],
  template: `
    <mat-card>
      <mat-card-title>Sign On</mat-card-title>
      <mat-card-content>Not yet available in this release.</mat-card-content>
    </mat-card>
  `,
})
export class SignonComponent {}
