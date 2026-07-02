// In-memory mock database with realistic CardDemo fixtures.
// Mirrors the seed data shape used by modernized/backend/prisma/seed.ts.

import { UserRole, type Account, type Card, type Customer, type Report, type Transaction, type User } from '@carddemo/shared';

export interface MockUser extends User {
  password: string;
}

export interface MockDb {
  users: MockUser[];
  customers: Customer[];
  accounts: Account[];
  accountCustomer: Record<string, string>; // accountId -> customerId
  cards: Card[];
  transactions: Transaction[];
  reports: Report[];
  nextReportId: number;
  nextJobRunId: number;
}

function seed(): MockDb {
  const customers: Customer[] = [
    {
      id: '100000001',
      firstName: 'JOHN',
      middleName: 'Q',
      lastName: 'DOE',
      addressLine1: '123 MAIN STREET',
      addressLine2: 'APT 4B',
      addressLine3: null,
      stateCode: 'NY',
      countryCode: 'USA',
      zipCode: '10001',
      phoneNumber1: '(212)555-0134',
      phoneNumber2: null,
      ssn: '123456789',
      governmentIssuedId: 'NY-DL-8891',
      dateOfBirth: '1980-05-15',
      eftAccountId: '1234567890',
      primaryCardHolder: true,
      ficoCreditScore: 720,
    },
    {
      id: '100000002',
      firstName: 'JANE',
      middleName: null,
      lastName: 'SMITH',
      addressLine1: '456 OAK AVENUE',
      addressLine2: null,
      addressLine3: null,
      stateCode: 'CA',
      countryCode: 'USA',
      zipCode: '94105',
      phoneNumber1: '(415)555-0188',
      phoneNumber2: null,
      ssn: '987654321',
      governmentIssuedId: null,
      dateOfBirth: '1975-11-30',
      eftAccountId: '9876543210',
      primaryCardHolder: true,
      ficoCreditScore: 680,
    },
  ];

  const accounts: Account[] = [
    {
      id: '00000000001',
      activeStatus: true,
      currentBalance: '1250.75',
      creditLimit: '5000.00',
      cashCreditLimit: '1000.00',
      openDate: '2020-01-15',
      expirationDate: '2027-01-31',
      reissueDate: '2024-01-15',
      currCycleCredit: '300.00',
      currCycleDebit: '1550.75',
      addressZip: '10001',
      groupId: 'DEFAULT',
    },
    {
      id: '00000000002',
      activeStatus: true,
      currentBalance: '0.00',
      creditLimit: '8000.00',
      cashCreditLimit: '2000.00',
      openDate: '2019-06-20',
      expirationDate: '2026-06-30',
      reissueDate: null,
      currCycleCredit: '0.00',
      currCycleDebit: '0.00',
      addressZip: '94105',
      groupId: 'GOLD',
    },
  ];

  const cards: Card[] = Array.from({ length: 9 }, (_, i) => ({
    cardNumber: `400000000000000${i + 1}`,
    accountId: i < 6 ? '00000000001' : '00000000002',
    cvv: '123',
    embossedName: i < 6 ? 'JOHN Q DOE' : 'JANE SMITH',
    expiryDate: '2027-01-31',
    activeStatus: i % 3 !== 2,
  }));

  const transactions: Transaction[] = Array.from({ length: 15 }, (_, i) => {
    const n = i + 1;
    return {
      id: String(n).padStart(16, '0'),
      typeCode: n % 2 === 0 ? '01' : '02',
      categoryCode: n % 2 === 0 ? 1 : 2,
      source: 'POS TERM',
      description: n % 2 === 0 ? `GROCERY PURCHASE #${n}` : `ONLINE PAYMENT #${n}`,
      amount: (25.5 * n).toFixed(2),
      merchantId: '111222333',
      merchantName: n % 2 === 0 ? 'WHOLE FOODS' : 'AMAZON.COM',
      merchantCity: 'NEW YORK',
      merchantZip: '10001',
      cardNumber: '4000000000000001',
      originalTs: `2026-06-${String((n % 28) + 1).padStart(2, '0')}T10:30:00.000Z`,
      processedTs: `2026-06-${String((n % 28) + 1).padStart(2, '0')}T22:00:00.000Z`,
    };
  });

  const reports: Report[] = [
    {
      id: 1,
      name: 'Monthly',
      version: 1,
      startDate: '2026-05-01',
      endDate: '2026-05-31',
      createdAt: '2026-06-01T02:00:00.000Z',
    },
  ];

  return {
    users: [
      { id: 'ADMIN001', firstName: 'ALICE', lastName: 'ADMIN', role: UserRole.ADMIN, password: 'PASSWORD' },
      { id: 'USER0001', firstName: 'UNA', lastName: 'USER', role: UserRole.USER, password: 'PASSWORD' },
    ],
    customers,
    accounts,
    accountCustomer: { '00000000001': '100000001', '00000000002': '100000002' },
    cards,
    transactions,
    reports,
    nextReportId: 2,
    nextJobRunId: 1,
  };
}

export let db: MockDb = seed();

export function resetDb(): void {
  db = seed();
}
