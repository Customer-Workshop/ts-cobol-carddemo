import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CurrencyPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatDividerModule } from '@angular/material/divider';
import { Account } from '../../../models/account.model';
import { AccountService } from '../../../services/account.service';

@Component({
  selector: 'app-account-detail',
  standalone: true,
  imports: [
    RouterLink,
    CurrencyPipe,
    FormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatSnackBarModule,
    MatSelectModule,
    MatDividerModule,
  ],
  templateUrl: './account-detail.component.html',
  styleUrl: './account-detail.component.scss',
})
export class AccountDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly accountService = inject(AccountService);
  private readonly snackBar = inject(MatSnackBar);

  account: Account | null = null;
  editMode = false;
  editAccount: Account | null = null;

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.accountService.getAccountById(id).subscribe(account => {
        if (account) {
          this.account = account;
          this.editAccount = { ...account };
        } else {
          this.router.navigate(['/accounts']);
        }
      });
    }
  }

  toggleEdit(): void {
    this.editMode = !this.editMode;
    if (this.account) {
      this.editAccount = { ...this.account };
    }
  }

  saveChanges(): void {
    if (this.editAccount) {
      this.accountService.updateAccount(this.editAccount).subscribe(updated => {
        this.account = updated;
        this.editMode = false;
        this.snackBar.open('Account updated successfully', 'Close', { duration: 3000 });
      });
    }
  }
}
