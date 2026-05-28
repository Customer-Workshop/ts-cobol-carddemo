import { Component, inject, OnInit } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatSortModule, Sort } from '@angular/material/sort';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Card } from '../../../models/card.model';
import { CardService } from '../../../services/card.service';

@Component({
  selector: 'app-card-list',
  standalone: true,
  imports: [
    RouterLink,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatChipsModule,
    MatSortModule,
    MatTooltipModule,
  ],
  templateUrl: './card-list.component.html',
  styleUrl: './card-list.component.scss',
})
export class CardListComponent implements OnInit {
  private readonly cardService = inject(CardService);

  cards: Card[] = [];
  displayedColumns = [
    'cardNumber', 'embossedName', 'accountId',
    'expirationDate', 'activeStatus', 'actions',
  ];

  ngOnInit(): void {
    this.cardService.getCards().subscribe(cards => {
      this.cards = cards;
    });
  }

  maskCardNumber(num: string): string {
    if (num.length < 8) return num;
    return '****-****-****-' + num.slice(-4);
  }

  sortData(sort: Sort): void {
    if (!sort.active || sort.direction === '') return;
    this.cards = [...this.cards].sort((a, b) => {
      const isAsc = sort.direction === 'asc';
      const key = sort.active as keyof Card;
      return String(a[key]).localeCompare(String(b[key])) * (isAsc ? 1 : -1);
    });
  }
}
