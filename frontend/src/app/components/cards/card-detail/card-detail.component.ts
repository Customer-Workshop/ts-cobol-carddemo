import { Component, inject, OnInit } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
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
import { Card } from '../../../models/card.model';
import { CardService } from '../../../services/card.service';

@Component({
  selector: 'app-card-detail',
  standalone: true,
  imports: [
    RouterLink,
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
  templateUrl: './card-detail.component.html',
  styleUrl: './card-detail.component.scss',
})
export class CardDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly cardService = inject(CardService);
  private readonly snackBar = inject(MatSnackBar);

  card: Card | null = null;
  editMode = false;
  editCard: Card | null = null;

  ngOnInit(): void {
    const cardNumber = this.route.snapshot.paramMap.get('cardNumber');
    if (cardNumber) {
      this.cardService.getCardByNumber(cardNumber).subscribe(card => {
        if (card) {
          this.card = card;
          this.editCard = { ...card };
        } else {
          this.router.navigate(['/cards']);
        }
      });
    }
  }

  maskCardNumber(num: string): string {
    if (num.length < 8) return num;
    return '****-****-****-' + num.slice(-4);
  }

  toggleEdit(): void {
    this.editMode = !this.editMode;
    if (this.card) {
      this.editCard = { ...this.card };
    }
  }

  saveChanges(): void {
    if (this.editCard) {
      this.cardService.updateCard(this.editCard).subscribe(updated => {
        this.card = updated;
        this.editMode = false;
        this.snackBar.open('Card updated successfully', 'Close', { duration: 3000 });
      });
    }
  }
}
