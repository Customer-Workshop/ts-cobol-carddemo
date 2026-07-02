// Seed data faithful to the legacy CardDemo record layouts (app/cpy/*.cpy) and
// the ASCII sample datasets in app/data/ASCII.
// REQ-F-001..REQ-F-018 (DataStoreInitializationandLifecycle): idempotent
// delete-then-populate of every data store.
// REQ-F-023..REQ-F-026 (Security,Validation,andApplicationSetup): user security
// credential initialization with admin + standard users.

import { PrismaClient, Prisma, UserRole } from '@prisma/client';
import * as bcrypt from 'bcryptjs';

const prisma = new PrismaClient();

const D = (v: string | number) => new Prisma.Decimal(v);

// TRANTYPE reference data (app/data/ASCII/trantype.txt, copybook CVTRA04Y)
const TRAN_TYPES = [
  { code: '01', description: 'Purchase' },
  { code: '02', description: 'Payment' },
  { code: '03', description: 'Credit' },
  { code: '04', description: 'Authorization' },
  { code: '05', description: 'Refund' },
  { code: '06', description: 'Reversal' },
  { code: '07', description: 'Adjustment' },
];

// TRANCATG reference data (app/data/ASCII/trancatg.txt, copybook CVTRA03Y)
const TRAN_CATEGORIES = [
  { typeCode: '01', categoryCode: 1, description: 'Regular Sales Draft' },
  { typeCode: '01', categoryCode: 2, description: 'Regular Cash Advance' },
  { typeCode: '01', categoryCode: 3, description: 'Convenience Check Debit' },
  { typeCode: '01', categoryCode: 4, description: 'ATM Cash Advance' },
  { typeCode: '01', categoryCode: 5, description: 'Interest Amount' },
  { typeCode: '02', categoryCode: 1, description: 'Cash payment' },
  { typeCode: '02', categoryCode: 2, description: 'Electronic payment' },
  { typeCode: '02', categoryCode: 3, description: 'Check payment' },
  { typeCode: '03', categoryCode: 1, description: 'Credit to Account' },
  { typeCode: '03', categoryCode: 2, description: 'Credit to Purchase balance' },
  { typeCode: '03', categoryCode: 3, description: 'Credit to Cash balance' },
  { typeCode: '04', categoryCode: 1, description: 'Zero dollar authorization' },
  { typeCode: '04', categoryCode: 2, description: 'Online purchase authorization' },
  { typeCode: '04', categoryCode: 3, description: 'Travel booking authorization' },
  { typeCode: '05', categoryCode: 1, description: 'Refund credit' },
  { typeCode: '06', categoryCode: 1, description: 'Fraud reversal' },
  { typeCode: '06', categoryCode: 2, description: 'Non-fraud reversal' },
  { typeCode: '07', categoryCode: 1, description: 'Sales draft credit adjustment' },
];

const FIRST_NAMES = [
  'Immanuel', 'Enrico', 'Larry', 'Delbert', 'Treva', 'Aniya', 'Ward', 'Carter', 'Maci', 'Madeline',
  'April', 'Cody', 'Kaia', 'Manley', 'Bernita', 'Gladys', 'Virginie', 'Deshaun', 'Myrna', 'Esta',
];
const LAST_NAMES = [
  'Kessler', 'Rosenbaum', 'Homenick', 'Parisian', 'Schowalter', 'Von', 'Jones', 'Veum', 'Robel', 'Abshire',
  'Lowe', 'Nitzsche', 'Ernser', 'Roob', 'Gleason', 'Kertzmann', 'Schoen', 'Blanda', 'Legros', 'Gusikowski',
];
const CITIES = [
  'Altenwerthshire', 'West Bernita', 'New Gladys', 'Lake Virginie', 'Alvinaport',
  'North Enoshaven', 'Fidelshire', 'North Makenziemouth', 'South Lynn', 'East Eulahstad',
];
const STATES = ['NC', 'IN', 'GA', 'MI', 'TX', 'CA', 'NY', 'FL', 'OH', 'WA'];
const MERCHANTS = [
  'Abshire-Lowe', 'Nitzsche, Nicolas and Lowe', 'Ernser, Roob and Gleason', 'Guann LLC',
  'Kertzmann-Schoen', 'Blanda Group', 'Legros Plaza Stores', 'Deshaun Retail', 'Myrna Flats Market', 'Esta Parks Inc',
];

const pad = (n: number, width: number) => String(n).padStart(width, '0');
const customerId = (n: number) => pad(n, 9);
const accountId = (n: number) => pad(n, 11);
const tranId = (n: number) => pad(n, 16);
const pick = <T>(arr: T[], i: number): T => arr[i % arr.length] as T;

function cardNumber(i: number): string {
  return `4${pad(500024453765 + i * 137, 15)}`;
}

async function main(): Promise<void> {
  // Delete in dependency order (mirrors legacy IDCAMS delete-define-repro lifecycle,
  // REQ-F-001..REQ-F-018, REQ-N-001: full re-initialization, idempotent by design).
  await prisma.dailyReject.deleteMany();
  await prisma.dailyTransaction.deleteMany();
  await prisma.transaction.deleteMany();
  await prisma.transactionCategoryBalance.deleteMany();
  await prisma.disclosureGroup.deleteMany();
  await prisma.cardXref.deleteMany();
  await prisma.card.deleteMany();
  await prisma.transactionCategory.deleteMany();
  await prisma.transactionType.deleteMany();
  await prisma.statement.deleteMany();
  await prisma.report.deleteMany();
  await prisma.jobRun.deleteMany();
  await prisma.account.deleteMany();
  await prisma.customer.deleteMany();
  await prisma.user.deleteMany();

  // Users (CSUSR01Y / USRSEC, REQ-F-023..REQ-F-026)
  const passwordHash = await bcrypt.hash('PASSWORD', 10);
  await prisma.user.createMany({
    data: [
      { id: 'ADMIN0001', firstName: 'Admin', lastName: 'User', password: passwordHash, role: UserRole.ADMIN },
      { id: 'USER0001', firstName: 'Standard', lastName: 'User', password: passwordHash, role: UserRole.USER },
    ],
  });

  // Reference data
  await prisma.transactionType.createMany({ data: TRAN_TYPES });
  await prisma.transactionCategory.createMany({ data: TRAN_CATEGORIES });

  // DISCGRP (CVTRA02Y): interest rates per account group + type + category
  const discRows: Prisma.DisclosureGroupCreateManyInput[] = [];
  const groups = ['A000000000', 'DEFAULT', 'ZEROAPR'];
  const rates: Record<string, string> = { A000000000: '15.00', DEFAULT: '25.00', ZEROAPR: '0.00' };
  for (const g of groups) {
    for (const cat of TRAN_CATEGORIES.filter((c) => c.typeCode === '01')) {
      discRows.push({
        accountGroupId: g,
        typeCode: cat.typeCode,
        categoryCode: cat.categoryCode,
        interestRate: D(rates[g] ?? '0.00'),
      });
    }
  }
  await prisma.disclosureGroup.createMany({ data: discRows });

  // Customers (CVCUS01Y, ~20)
  const customers: Prisma.CustomerCreateManyInput[] = [];
  for (let i = 1; i <= 20; i++) {
    customers.push({
      id: customerId(i),
      firstName: pick(FIRST_NAMES, i - 1),
      middleName: pick(FIRST_NAMES, i + 6),
      lastName: pick(LAST_NAMES, i - 1),
      addressLine1: `${100 + i * 7} ${pick(LAST_NAMES, i + 2)} Route`,
      addressLine2: `Apt. ${pad(100 + i * 13, 3)}`,
      addressLine3: pick(CITIES, i - 1),
      stateCode: pick(STATES, i - 1),
      countryCode: 'USA',
      zipCode: `${pad(10000 + i * 731, 5)}`,
      phoneNumber1: `(${pad(200 + i, 3)})555-${pad(1000 + i * 17, 4)}`,
      phoneNumber2: `(${pad(300 + i, 3)})555-${pad(2000 + i * 23, 4)}`,
      ssn: pad(20973888 + i * 104729, 9),
      governmentIssuedId: pad(493684371 + i * 12345, 20),
      dateOfBirth: new Date(Date.UTC(1955 + (i % 40), i % 12, 1 + (i % 27))),
      eftAccountId: pad(5358175 + i * 991, 10),
      primaryCardHolder: true,
      ficoCreditScore: 300 + ((i * 137) % 551),
    });
  }
  await prisma.customer.createMany({ data: customers });

  // Accounts (CVACT01Y, ~20) — 1:1 with customers here
  const accounts: Prisma.AccountCreateManyInput[] = [];
  for (let i = 1; i <= 20; i++) {
    const creditLimit = 1000 + (i % 10) * 500;
    accounts.push({
      id: accountId(i),
      activeStatus: i !== 19, // one inactive account
      currentBalance: D((i * 97.13).toFixed(2)),
      creditLimit: D(creditLimit.toFixed(2)),
      cashCreditLimit: D((creditLimit / 2).toFixed(2)),
      openDate: new Date(Date.UTC(2012 + (i % 4), i % 12, 1 + (i % 27))),
      expirationDate: new Date(Date.UTC(2027 + (i % 3), i % 12, 1 + (i % 27))),
      reissueDate: new Date(Date.UTC(2025, i % 12, 1 + (i % 27))),
      currCycleCredit: D('0.00'),
      currCycleDebit: D((i * 11.5).toFixed(2)),
      addressZip: pad(10000 + i * 731, 5),
      groupId: i % 3 === 0 ? 'DEFAULT' : 'A000000000',
    });
  }
  await prisma.account.createMany({ data: accounts });

  // Cards + xref (CVACT02Y / CVACT03Y, ~25: accounts 1-5 get a second card)
  const cards: Prisma.CardCreateManyInput[] = [];
  const xrefs: Prisma.CardXrefCreateManyInput[] = [];
  let cardIdx = 0;
  for (let i = 1; i <= 20; i++) {
    const holders = i <= 5 ? 2 : 1;
    for (let h = 0; h < holders; h++) {
      const num = cardNumber(cardIdx++);
      cards.push({
        cardNumber: num,
        accountId: accountId(i),
        cvv: pad((cardIdx * 379) % 1000, 3),
        embossedName: `${pick(FIRST_NAMES, i + h)} ${pick(LAST_NAMES, i + h)}`.toUpperCase(),
        expiryDate: new Date(Date.UTC(2027 + (i % 3), i % 12, 1 + (i % 27))),
        activeStatus: cardIdx % 9 !== 0,
      });
      xrefs.push({ cardNumber: num, customerId: customerId(i), accountId: accountId(i) });
    }
  }
  await prisma.card.createMany({ data: cards });
  await prisma.cardXref.createMany({ data: xrefs });

  // Posted transactions (CVTRA05Y, ~100)
  const purchaseCategories = TRAN_CATEGORIES.filter((c) => c.typeCode === '01' && c.categoryCode <= 4);
  const transactions: Prisma.TransactionCreateManyInput[] = [];
  for (let i = 1; i <= 100; i++) {
    const card = pick(cards, i * 3);
    const cat = pick(purchaseCategories, i);
    const merchant = pick(MERCHANTS, i);
    const orig = new Date(Date.UTC(2025, (i % 6), 1 + (i % 27), i % 24, (i * 7) % 60, (i * 13) % 60));
    transactions.push({
      id: tranId(i),
      typeCode: cat.typeCode,
      categoryCode: cat.categoryCode,
      source: i % 4 === 0 ? 'OPERATOR' : 'POS TERM',
      description: `Purchase at ${merchant}`,
      amount: D(((i * 31.37) % 500 + 1).toFixed(2)),
      merchantId: pad(100000000 + i * 8933, 9),
      merchantName: merchant,
      merchantCity: pick(CITIES, i),
      merchantZip: pad(50000 + i * 487, 5),
      cardNumber: card.cardNumber,
      originalTs: orig,
      processedTs: new Date(orig.getTime() + 86_400_000),
    });
  }
  await prisma.transaction.createMany({ data: transactions });

  // TCATBALF (CVTRA01Y): per-account category balances derived from transactions
  const balanceMap = new Map<string, Prisma.Decimal>();
  const cardToAccount = new Map(cards.map((c) => [c.cardNumber, c.accountId]));
  for (const t of transactions) {
    const acct = cardToAccount.get(t.cardNumber);
    if (!acct) continue;
    const key = `${acct}|${t.typeCode}|${t.categoryCode}`;
    const prev = balanceMap.get(key) ?? D(0);
    balanceMap.set(key, prev.add(t.amount as Prisma.Decimal));
  }
  const balances: Prisma.TransactionCategoryBalanceCreateManyInput[] = [];
  for (const [key, balance] of balanceMap) {
    const parts = key.split('|');
    balances.push({
      accountId: parts[0] as string,
      typeCode: parts[1] as string,
      categoryCode: Number(parts[2]),
      balance,
    });
  }
  await prisma.transactionCategoryBalance.createMany({ data: balances });

  // Pending daily transaction batch (CVTRA06Y) — includes records destined for rejection
  const daily: Prisma.DailyTransactionCreateManyInput[] = [];
  for (let i = 1; i <= 12; i++) {
    const valid = i <= 8;
    const card = pick(cards, i * 5);
    const cat = pick(purchaseCategories, i + 2);
    const merchant = pick(MERCHANTS, i + 3);
    daily.push({
      id: tranId(9000 + i),
      typeCode: valid ? cat.typeCode : i % 2 === 0 ? '99' : cat.typeCode, // invalid type code
      categoryCode: valid ? cat.categoryCode : i % 2 === 0 ? cat.categoryCode : 9999, // invalid category
      source: 'POS TERM',
      description: `Purchase at ${merchant}`,
      amount: D(((i * 53.21) % 400 + 1).toFixed(2)),
      merchantId: pad(200000000 + i * 7817, 9),
      merchantName: merchant,
      merchantCity: pick(CITIES, i + 1),
      merchantZip: pad(60000 + i * 311, 5),
      // 2 of the invalid ones also reference a card that does not exist
      cardNumber: valid || i % 3 !== 0 ? card.cardNumber : '9999999999999999',
      originalTs: new Date(Date.UTC(2025, 5, 10, 19, 27, 53)),
      status: 'PENDING',
    });
  }
  await prisma.dailyTransaction.createMany({ data: daily });

  const counts = {
    users: await prisma.user.count(),
    customers: await prisma.customer.count(),
    accounts: await prisma.account.count(),
    cards: await prisma.card.count(),
    cardXref: await prisma.cardXref.count(),
    transactionTypes: await prisma.transactionType.count(),
    transactionCategories: await prisma.transactionCategory.count(),
    disclosureGroups: await prisma.disclosureGroup.count(),
    transactions: await prisma.transaction.count(),
    categoryBalances: await prisma.transactionCategoryBalance.count(),
    dailyTransactions: await prisma.dailyTransaction.count(),
  };
  console.log('Seed complete:', counts);
}

main()
  .then(async () => {
    await prisma.$disconnect();
  })
  .catch(async (e) => {
    console.error(e);
    await prisma.$disconnect();
    process.exit(1);
  });
