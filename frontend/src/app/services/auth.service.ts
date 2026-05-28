import { Injectable, signal, computed } from '@angular/core';

export interface User {
  userId: string;
  displayName: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUser = signal<User | null>(null);

  readonly user = this.currentUser.asReadonly();
  readonly isLoggedIn = computed(() => this.currentUser() !== null);

  login(userId: string, password: string): boolean {
    if (userId && password && password.length >= 1) {
      this.currentUser.set({ userId, displayName: userId.toUpperCase() });
      return true;
    }
    return false;
  }

  logout(): void {
    this.currentUser.set(null);
  }
}
