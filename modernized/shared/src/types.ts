// Domain types and enums mirroring modernized/backend/prisma/schema.prisma.
// Monetary amounts are serialized as decimal strings (e.g. "1234.56").

export enum UserRole {
  USER = 'USER',
  ADMIN = 'ADMIN',
}

export enum JobStatus {
  PENDING = 'PENDING',
  RUNNING = 'RUNNING',
  SUCCEEDED = 'SUCCEEDED',
  FAILED = 'FAILED',
}

export enum DailyTransactionStatus {
  PENDING = 'PENDING',
  POSTED = 'POSTED',
  REJECTED = 'REJECTED',
}

export interface User {
  id: string; // up to 9 chars, legacy SEC-USR-ID
  firstName: string;
  lastName: string;
  role: UserRole;
}

export interface Customer {
  id: string; // 9 digits, legacy CUST-ID
  firstName: string;
  middleName?: string | null;
  lastName: string;
  addressLine1: string;
  addressLine2?: string | null;
  addressLine3?: string | null;
  stateCode: string;
  countryCode: string;
  zipCode: string;
  phoneNumber1?: string | null;
  phoneNumber2?: string | null;
  ssn: string;
  governmentIssuedId?: string | null;
  dateOfBirth: string; // YYYY-MM-DD
  eftAccountId?: string | null;
  primaryCardHolder: boolean;
  ficoCreditScore: number;
}

export interface Account {
  id: string; // 11 digits, legacy ACCT-ID
  activeStatus: boolean;
  currentBalance: string;
  creditLimit: string;
  cashCreditLimit: string;
  openDate: string; // YYYY-MM-DD
  expirationDate: string;
  reissueDate?: string | null;
  currCycleCredit: string;
  currCycleDebit: string;
  addressZip?: string | null;
  groupId?: string | null;
}

export interface Card {
  cardNumber: string; // 16 chars
  accountId: string;
  cvv: string;
  embossedName: string;
  expiryDate: string; // YYYY-MM-DD
  activeStatus: boolean;
}

export interface CardXref {
  cardNumber: string;
  customerId: string;
  accountId: string;
}

export interface TransactionType {
  code: string; // 2 chars
  description: string;
}

export interface TransactionCategory {
  typeCode: string;
  categoryCode: number; // 4 digits
  description: string;
}

export interface Transaction {
  id: string; // 16 chars, legacy TRAN-ID
  typeCode: string;
  categoryCode: number;
  source: string;
  description: string;
  amount: string;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  cardNumber: string;
  originalTs: string; // ISO timestamp
  processedTs: string;
}

export interface TransactionCategoryBalance {
  accountId: string;
  typeCode: string;
  categoryCode: number;
  balance: string;
}

export interface DisclosureGroup {
  accountGroupId: string;
  typeCode: string;
  categoryCode: number;
  interestRate: string;
}

export interface DailyTransaction extends Omit<Transaction, 'processedTs'> {
  processedTs?: string | null;
  status: DailyTransactionStatus;
}

export interface DailyReject {
  id: number;
  dailyTransactionId: string;
  rejectReason: string;
  rejectedAt: string;
}

export interface Statement {
  id: number;
  accountId: string;
  version: number;
  periodStart: string;
  periodEnd: string;
  textContent: string;
  htmlContent: string;
  createdAt: string;
}

export interface Report {
  id: number;
  name: string;
  version: number;
  startDate: string;
  endDate: string;
  content?: string;
  createdAt: string;
}

export interface JobRun {
  id: number;
  jobName: string;
  status: JobStatus;
  startedAt: string;
  completedAt?: string | null;
  message?: string | null;
}
