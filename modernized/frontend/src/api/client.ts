// Typed API client hand-written against modernized/shared/openapi.yaml.
// All requests target `${VITE_API_URL}/api/v1` and carry the JWT bearer token.

import type {
  Account,
  Card,
  Customer,
  JobRun,
  Report,
  Transaction,
  User,
  UserRole,
} from '@carddemo/shared';

export interface PageMeta {
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
}

export interface Paged<T> extends PageMeta {
  items: T[];
}

export interface FieldError {
  field: string;
  message: string;
}

export interface ErrorBody {
  statusCode: number;
  error: string;
  message: string;
  details?: FieldError[];
}

export class ApiError extends Error {
  statusCode: number;
  details?: FieldError[];
  constructor(body: ErrorBody) {
    super(body.message);
    this.statusCode = body.statusCode;
    this.details = body.details;
  }
}

export interface SignInResponse {
  token: string;
  user: User;
}

export interface AccountDetail {
  account: Account;
  customer: Customer;
}

export interface AccountUpdateRequest {
  activeStatus?: boolean;
  creditLimit?: string;
  cashCreditLimit?: string;
  expirationDate?: string;
  reissueDate?: string;
  groupId?: string;
  customer?: Partial<Omit<Customer, 'id'>>;
}

export interface CardUpdateRequest {
  embossedName?: string;
  expiryDate?: string;
  activeStatus?: boolean;
}

export interface TransactionCreateRequest {
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
  originalTs: string;
}

export interface BillPayResponse {
  transaction: Transaction;
  account: Account;
}

export interface ReportRequest {
  name: string;
  startDate: string;
  endDate: string;
}

export interface UserCreateRequest {
  id: string;
  firstName: string;
  lastName: string;
  password: string;
  role: UserRole;
}

export interface UserUpdateRequest {
  firstName?: string;
  lastName?: string;
  password?: string;
  role?: UserRole;
}

const TOKEN_KEY = 'carddemo.token';
const USER_KEY = 'carddemo.user';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as User;
  } catch {
    return null;
  }
}

export function storeSession(token: string, user: User): void {
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, JSON.stringify(user));
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

function baseUrl(): string {
  return `${import.meta.env.VITE_API_URL ?? 'http://localhost:3000'}/api/v1`;
}

async function request<T>(
  method: string,
  path: string,
  options: { body?: unknown; query?: Record<string, string | number | undefined>; auth?: boolean } = {},
): Promise<T> {
  const { body, query, auth = true } = options;
  const url = new URL(baseUrl() + path, window.location.origin);
  if (query) {
    for (const [k, v] of Object.entries(query)) {
      if (v !== undefined && v !== '') url.searchParams.set(k, String(v));
    }
  }
  const headers: Record<string, string> = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth) {
    const token = getToken();
    if (token) headers['Authorization'] = `Bearer ${token}`;
  }
  const res = await fetch(url.toString(), {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return undefined as T;
  const json: unknown = await res.json().catch(() => undefined);
  if (!res.ok) {
    const errBody =
      json && typeof json === 'object' && 'message' in json
        ? (json as ErrorBody)
        : { statusCode: res.status, error: res.statusText, message: `Request failed (${res.status})` };
    throw new ApiError(errBody);
  }
  return json as T;
}

export const api = {
  signIn: (userId: string, password: string) =>
    request<SignInResponse>('POST', '/auth/signin', { body: { userId, password }, auth: false }),

  getAccount: (accountId: string) => request<AccountDetail>('GET', `/accounts/${accountId}`),
  updateAccount: (accountId: string, body: AccountUpdateRequest) =>
    request<AccountDetail>('PUT', `/accounts/${accountId}`, { body }),

  listCards: (params: { accountId?: string; page?: number; pageSize?: number }) =>
    request<Paged<Card>>('GET', '/cards', { query: params }),
  getCard: (cardNumber: string) => request<Card>('GET', `/cards/${cardNumber}`),
  updateCard: (cardNumber: string, body: CardUpdateRequest) =>
    request<Card>('PUT', `/cards/${cardNumber}`, { body }),

  listTransactions: (params: { cardNumber?: string; accountId?: string; page?: number; pageSize?: number }) =>
    request<Paged<Transaction>>('GET', '/transactions', { query: params }),
  getTransaction: (transactionId: string) => request<Transaction>('GET', `/transactions/${transactionId}`),
  createTransaction: (body: TransactionCreateRequest) =>
    request<Transaction>('POST', '/transactions', { body }),

  payBill: (accountId: string) =>
    request<BillPayResponse>('POST', '/billpay', { body: { accountId, confirm: true } }),

  listReports: (params: { page?: number; pageSize?: number }) =>
    request<Paged<Report>>('GET', '/reports', { query: params }),
  createReport: (body: ReportRequest) => request<JobRun>('POST', '/reports', { body }),

  listUsers: (params: { page?: number; pageSize?: number }) =>
    request<Paged<User>>('GET', '/users', { query: params }),
  createUser: (body: UserCreateRequest) => request<User>('POST', '/users', { body }),
  updateUser: (userId: string, body: UserUpdateRequest) =>
    request<User>('PUT', `/users/${userId}`, { body }),
  deleteUser: (userId: string) => request<void>('DELETE', `/users/${userId}`),
};
