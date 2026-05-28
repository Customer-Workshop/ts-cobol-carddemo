import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Customer } from '../models/customer.model';
import { MOCK_CUSTOMERS } from './mock-data';

@Injectable({ providedIn: 'root' })
export class CustomerService {
  private customers = [...MOCK_CUSTOMERS];

  getCustomers(): Observable<Customer[]> {
    return of(this.customers);
  }

  getCustomerById(customerId: string): Observable<Customer | undefined> {
    return of(this.customers.find(c => c.customerId === customerId));
  }
}
