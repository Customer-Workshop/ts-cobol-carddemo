// MSW handlers implementing modernized/shared/openapi.yaml against the in-memory db.
// Used in dev (VITE_USE_MOCKS=true) and in vitest via msw/node.

import { http, HttpResponse } from 'msw';
import { UserRole, type JobRun, type Transaction, type User } from '@carddemo/shared';
import { db, type MockUser } from './db';

const API = '*/api/v1';

function error(statusCode: number, message: string, details?: { field: string; message: string }[]) {
  const names: Record<number, string> = {
    400: 'Bad Request',
    401: 'Unauthorized',
    403: 'Forbidden',
    404: 'Not Found',
    409: 'Conflict',
  };
  return HttpResponse.json(
    { statusCode, error: names[statusCode] ?? 'Error', message, details },
    { status: statusCode },
  );
}

function authUser(request: Request): User | null {
  const header = request.headers.get('Authorization');
  if (!header?.startsWith('Bearer mock-jwt-')) return null;
  const userId = header.slice('Bearer mock-jwt-'.length);
  const user = db.users.find((u) => u.id === userId);
  if (!user) return null;
  return { id: user.id, firstName: user.firstName, lastName: user.lastName, role: user.role };
}

function paginate<T>(items: T[], url: URL) {
  const page = Math.max(1, Number(url.searchParams.get('page') ?? '1'));
  const pageSize = Math.max(1, Number(url.searchParams.get('pageSize') ?? '20'));
  const totalItems = items.length;
  const totalPages = Math.max(1, Math.ceil(totalItems / pageSize));
  return {
    page,
    pageSize,
    totalItems,
    totalPages,
    items: items.slice((page - 1) * pageSize, page * pageSize),
  };
}

export const handlers = [
  http.post(`${API}/auth/signin`, async ({ request }) => {
    const body = (await request.json()) as { userId?: string; password?: string };
    const userId = (body.userId ?? '').toUpperCase();
    const password = (body.password ?? '').toUpperCase();
    const user = db.users.find((u) => u.id === userId);
    if (!user) return error(401, 'User not found. Try again ...');
    if (user.password !== password) return error(401, 'Wrong Password. Try again ...');
    return HttpResponse.json({
      token: `mock-jwt-${user.id}`,
      user: { id: user.id, firstName: user.firstName, lastName: user.lastName, role: user.role },
    });
  }),

  http.get(`${API}/accounts/:accountId`, ({ request, params }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const account = db.accounts.find((a) => a.id === params.accountId);
    if (!account) return error(404, 'Account ID NOT found...');
    const customer = db.customers.find((c) => c.id === db.accountCustomer[account.id]);
    if (!customer) return error(404, 'Customer NOT found...');
    return HttpResponse.json({ account, customer });
  }),

  http.put(`${API}/accounts/:accountId`, async ({ request, params }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const account = db.accounts.find((a) => a.id === params.accountId);
    if (!account) return error(404, 'Account ID NOT found...');
    const customer = db.customers.find((c) => c.id === db.accountCustomer[account.id]);
    if (!customer) return error(404, 'Customer NOT found...');
    const body = (await request.json()) as Record<string, unknown>;
    const { customer: custPatch, ...acctPatch } = body as {
      customer?: Record<string, unknown>;
    } & Record<string, unknown>;
    Object.assign(account, acctPatch);
    if (custPatch) Object.assign(customer, custPatch);
    return HttpResponse.json({ account, customer });
  }),

  http.get(`${API}/cards`, ({ request }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const url = new URL(request.url);
    const accountId = url.searchParams.get('accountId');
    const filtered = accountId ? db.cards.filter((c) => c.accountId === accountId) : db.cards;
    return HttpResponse.json(paginate(filtered, url));
  }),

  http.get(`${API}/cards/:cardNumber`, ({ request, params }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const card = db.cards.find((c) => c.cardNumber === params.cardNumber);
    if (!card) return error(404, 'Did not find cards for this search condition');
    return HttpResponse.json(card);
  }),

  http.put(`${API}/cards/:cardNumber`, async ({ request, params }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const card = db.cards.find((c) => c.cardNumber === params.cardNumber);
    if (!card) return error(404, 'Did not find cards for this search condition');
    const body = (await request.json()) as Record<string, unknown>;
    Object.assign(card, body);
    return HttpResponse.json(card);
  }),

  http.get(`${API}/transactions`, ({ request }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const url = new URL(request.url);
    const cardNumber = url.searchParams.get('cardNumber');
    const accountId = url.searchParams.get('accountId');
    let filtered = db.transactions;
    if (cardNumber) filtered = filtered.filter((t) => t.cardNumber === cardNumber);
    if (accountId) {
      const cardNumbers = new Set(db.cards.filter((c) => c.accountId === accountId).map((c) => c.cardNumber));
      filtered = filtered.filter((t) => cardNumbers.has(t.cardNumber));
    }
    return HttpResponse.json(paginate(filtered, url));
  }),

  http.post(`${API}/transactions`, async ({ request }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const body = (await request.json()) as Omit<Transaction, 'id' | 'processedTs'>;
    const maxId = db.transactions.reduce((m, t) => Math.max(m, Number(t.id)), 0);
    const transaction: Transaction = {
      ...body,
      id: String(maxId + 1).padStart(16, '0'),
      processedTs: new Date().toISOString(),
    };
    db.transactions.push(transaction);
    return HttpResponse.json(transaction, { status: 201 });
  }),

  http.get(`${API}/transactions/:transactionId`, ({ request, params }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const transaction = db.transactions.find((t) => t.id === params.transactionId);
    if (!transaction) return error(404, 'Transaction ID NOT found...');
    return HttpResponse.json(transaction);
  }),

  http.post(`${API}/billpay`, async ({ request }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const body = (await request.json()) as { accountId: string; confirm: boolean };
    if (!body.confirm) return error(400, 'Confirm to make a bill payment...');
    const account = db.accounts.find((a) => a.id === body.accountId);
    if (!account) return error(404, 'Account ID NOT found...');
    if (Number(account.currentBalance) <= 0) return error(409, 'You have nothing to pay...');
    const card = db.cards.find((c) => c.accountId === account.id);
    const maxId = db.transactions.reduce((m, t) => Math.max(m, Number(t.id)), 0);
    const now = new Date().toISOString();
    const transaction: Transaction = {
      id: String(maxId + 1).padStart(16, '0'),
      typeCode: '02',
      categoryCode: 2,
      source: 'POS TERM',
      description: 'BILL PAYMENT - ONLINE',
      amount: account.currentBalance,
      merchantId: '999999999',
      merchantName: 'BILL PAYMENT',
      merchantCity: 'N/A',
      merchantZip: 'N/A',
      cardNumber: card?.cardNumber ?? '0000000000000000',
      originalTs: now,
      processedTs: now,
    };
    db.transactions.push(transaction);
    account.currentBalance = '0.00';
    return HttpResponse.json({ transaction, account }, { status: 201 });
  }),

  http.get(`${API}/reports`, ({ request }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    return HttpResponse.json(paginate(db.reports, new URL(request.url)));
  }),

  http.post(`${API}/reports`, async ({ request }) => {
    if (!authUser(request)) return error(401, 'Missing or invalid JWT');
    const body = (await request.json()) as { name: string; startDate: string; endDate: string };
    if (body.startDate > body.endDate) return error(400, 'startDate must not be after endDate');
    db.reports.push({
      id: db.nextReportId++,
      name: body.name,
      version: 1,
      startDate: body.startDate,
      endDate: body.endDate,
      createdAt: new Date().toISOString(),
    });
    const jobRun: JobRun = {
      id: db.nextJobRunId++,
      jobName: 'transaction-report',
      status: 'RUNNING' as JobRun['status'],
      startedAt: new Date().toISOString(),
      completedAt: null,
      message: null,
    };
    return HttpResponse.json(jobRun, { status: 202 });
  }),

  http.get(`${API}/users`, ({ request }) => {
    const caller = authUser(request);
    if (!caller) return error(401, 'Missing or invalid JWT');
    if (caller.role !== UserRole.ADMIN) return error(403, 'Caller lacks the required role');
    const users = db.users.map(({ id, firstName, lastName, role }) => ({ id, firstName, lastName, role }));
    return HttpResponse.json(paginate(users, new URL(request.url)));
  }),

  http.post(`${API}/users`, async ({ request }) => {
    const caller = authUser(request);
    if (!caller) return error(401, 'Missing or invalid JWT');
    if (caller.role !== UserRole.ADMIN) return error(403, 'Caller lacks the required role');
    const body = (await request.json()) as {
      id: string;
      firstName: string;
      lastName: string;
      password: string;
      role: UserRole;
    };
    if (db.users.some((u) => u.id === body.id.toUpperCase())) {
      return error(409, 'User ID already exist...');
    }
    const user = { ...body, id: body.id.toUpperCase() };
    db.users.push(user);
    return HttpResponse.json(
      { id: user.id, firstName: user.firstName, lastName: user.lastName, role: user.role },
      { status: 201 },
    );
  }),

  http.put(`${API}/users/:userId`, async ({ request, params }) => {
    const caller = authUser(request);
    if (!caller) return error(401, 'Missing or invalid JWT');
    if (caller.role !== UserRole.ADMIN) return error(403, 'Caller lacks the required role');
    const user = db.users.find((u) => u.id === params.userId);
    if (!user) return error(404, 'User ID NOT found...');
    const body = (await request.json()) as Partial<Pick<MockUser, 'firstName' | 'lastName' | 'password' | 'role'>>;
    if (body.firstName !== undefined) user.firstName = body.firstName;
    if (body.lastName !== undefined) user.lastName = body.lastName;
    if (body.password !== undefined) user.password = body.password;
    if (body.role !== undefined) user.role = body.role;
    return HttpResponse.json({ id: user.id, firstName: user.firstName, lastName: user.lastName, role: user.role });
  }),

  http.delete(`${API}/users/:userId`, ({ request, params }) => {
    const caller = authUser(request);
    if (!caller) return error(401, 'Missing or invalid JWT');
    if (caller.role !== UserRole.ADMIN) return error(403, 'Caller lacks the required role');
    const idx = db.users.findIndex((u) => u.id === params.userId);
    if (idx === -1) return error(404, 'User ID NOT found...');
    db.users.splice(idx, 1);
    return new HttpResponse(null, { status: 204 });
  }),
];
