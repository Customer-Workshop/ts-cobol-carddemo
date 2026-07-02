// End-to-end tests against a real PostgreSQL (migrated + seeded by
// test/global-setup.cjs). Covers auth, account view/update, card update,
// transaction create, bill pay, and admin user CRUD per openapi.yaml.

import { INestApplication } from '@nestjs/common';
import { Test } from '@nestjs/testing';
import request from 'supertest';
import { PrismaClient, Prisma } from '@prisma/client';
import { AppModule } from '../src/app.module';

describe('CardDemo API (e2e)', () => {
  let app: INestApplication;
  let server: unknown;
  let adminToken: string;
  let userToken: string;
  const prisma = new PrismaClient();

  const seededAccountId = '00000000001';
  const testUserId = 'E2ETEST1';

  beforeAll(async () => {
    const moduleRef = await Test.createTestingModule({ imports: [AppModule] }).compile();
    app = moduleRef.createNestApplication();
    app.setGlobalPrefix('api/v1');
    await app.init();
    server = app.getHttpServer();
    await prisma.user.deleteMany({ where: { id: testUserId } });
  });

  afterAll(async () => {
    await prisma.user.deleteMany({ where: { id: testUserId } });
    await prisma.$disconnect();
    await app.close();
  });

  const api = (): request.Agent => request(server as Parameters<typeof request>[0]);

  describe('auth', () => {
    it('signs in an admin and returns a JWT + user', async () => {
      const res = await api()
        .post('/api/v1/auth/signin')
        .send({ userId: 'admin0001', password: 'PASSWORD' })
        .expect(200);
      expect(res.body.token).toBeDefined();
      expect(res.body.user).toMatchObject({ id: 'ADMIN0001', role: 'ADMIN' });
      adminToken = res.body.token;
    });

    it('signs in a regular user', async () => {
      const res = await api()
        .post('/api/v1/auth/signin')
        .send({ userId: 'USER0001', password: 'PASSWORD' })
        .expect(200);
      expect(res.body.user.role).toBe('USER');
      userToken = res.body.token;
    });

    it('rejects unknown user with 401', async () => {
      const res = await api()
        .post('/api/v1/auth/signin')
        .send({ userId: 'NOBODY99', password: 'PASSWORD' })
        .expect(401);
      expect(res.body.message).toContain('User not found');
    });

    it('rejects wrong password with 401', async () => {
      const res = await api()
        .post('/api/v1/auth/signin')
        .send({ userId: 'USER0001', password: 'WRONGPWD' })
        .expect(401);
      expect(res.body.message).toContain('Wrong Password');
    });

    it('rejects blank credentials with 400', async () => {
      await api().post('/api/v1/auth/signin').send({ userId: '', password: '' }).expect(400);
    });

    it('guards routes: no token -> 401', async () => {
      await api().get(`/api/v1/accounts/${seededAccountId}`).expect(401);
    });
  });

  describe('accounts', () => {
    it('returns combined account + customer detail', async () => {
      const res = await api()
        .get(`/api/v1/accounts/${seededAccountId}`)
        .set('Authorization', `Bearer ${userToken}`)
        .expect(200);
      expect(res.body.account.id).toBe(seededAccountId);
      expect(res.body.account.currentBalance).toMatch(/^-?\d+\.\d{2}$/);
      expect(res.body.customer.ssn).toMatch(/^\d{9}$/);
      expect(res.body.customer.ficoCreditScore).toBeGreaterThanOrEqual(300);
    });

    it('404s for a missing account', async () => {
      await api()
        .get('/api/v1/accounts/99999999999')
        .set('Authorization', `Bearer ${userToken}`)
        .expect(404);
    });

    it('400s for a malformed account id', async () => {
      await api()
        .get('/api/v1/accounts/123')
        .set('Authorization', `Bearer ${userToken}`)
        .expect(400);
    });

    it('updates account and customer fields', async () => {
      const res = await api()
        .put(`/api/v1/accounts/${seededAccountId}`)
        .set('Authorization', `Bearer ${userToken}`)
        .send({
          creditLimit: '9999.99',
          customer: { ficoCreditScore: 777, firstName: 'Updated' },
        })
        .expect(200);
      expect(res.body.account.creditLimit).toBe('9999.99');
      expect(res.body.customer.ficoCreditScore).toBe(777);
      expect(res.body.customer.firstName).toBe('Updated');
    });

    it('rejects an unchanged update (change detection)', async () => {
      const res = await api()
        .put(`/api/v1/accounts/${seededAccountId}`)
        .set('Authorization', `Bearer ${userToken}`)
        .send({ creditLimit: '9999.99' })
        .expect(400);
      expect(res.body.message).toContain('modify');
    });

    it('rejects invalid FICO / SSN / state with field details', async () => {
      const res = await api()
        .put(`/api/v1/accounts/${seededAccountId}`)
        .set('Authorization', `Bearer ${userToken}`)
        .send({ customer: { ssn: '000123456', stateCode: 'ZZ' } })
        .expect(400);
      const fields = (res.body.details as { field: string }[]).map((d) => d.field);
      expect(fields).toEqual(expect.arrayContaining(['customer.ssn', 'customer.stateCode']));
    });

    it('rejects an invalid date (non-leap Feb 29)', async () => {
      await api()
        .put(`/api/v1/accounts/${seededAccountId}`)
        .set('Authorization', `Bearer ${userToken}`)
        .send({ expirationDate: '2025-02-29' })
        .expect(400);
    });
  });

  describe('cards', () => {
    let cardNumber: string;

    it('lists cards with pagination and account filter', async () => {
      const res = await api()
        .get(`/api/v1/cards?accountId=${seededAccountId}&page=1&pageSize=5`)
        .set('Authorization', `Bearer ${userToken}`)
        .expect(200);
      expect(res.body.page).toBe(1);
      expect(res.body.items.length).toBeGreaterThan(0);
      expect(res.body.items[0].accountId).toBe(seededAccountId);
      cardNumber = res.body.items[0].cardNumber;
    });

    it('gets a card by number', async () => {
      const res = await api()
        .get(`/api/v1/cards/${cardNumber}`)
        .set('Authorization', `Bearer ${userToken}`)
        .expect(200);
      expect(res.body.cardNumber).toBe(cardNumber);
    });

    it('updates a card (embossed name + status)', async () => {
      const res = await api()
        .put(`/api/v1/cards/${cardNumber}`)
        .set('Authorization', `Bearer ${userToken}`)
        .send({ embossedName: 'UPDATED CARDHOLDER', activeStatus: true })
        .expect(200);
      expect(res.body.embossedName).toBe('UPDATED CARDHOLDER');
    });

    it('rejects a non-alphabetic embossed name', async () => {
      await api()
        .put(`/api/v1/cards/${cardNumber}`)
        .set('Authorization', `Bearer ${userToken}`)
        .send({ embossedName: 'BAD NAME 123' })
        .expect(400);
    });

    it('404s for an unknown card', async () => {
      await api()
        .get('/api/v1/cards/9999999999999999')
        .set('Authorization', `Bearer ${userToken}`)
        .expect(404);
    });
  });

  describe('transactions', () => {
    let cardNumber: string;

    beforeAll(async () => {
      const xref = await prisma.cardXref.findFirstOrThrow({ where: { accountId: seededAccountId } });
      cardNumber = xref.cardNumber;
    });

    it('lists transactions paginated', async () => {
      const res = await api()
        .get('/api/v1/transactions?page=1&pageSize=10')
        .set('Authorization', `Bearer ${userToken}`)
        .expect(200);
      expect(res.body.totalItems).toBeGreaterThan(0);
      expect(res.body.items.length).toBeLessThanOrEqual(10);
    });

    it('creates a transaction and can fetch it by id', async () => {
      const res = await api()
        .post('/api/v1/transactions')
        .set('Authorization', `Bearer ${userToken}`)
        .send({
          typeCode: '01',
          categoryCode: 1,
          source: 'POS TERM',
          description: 'E2E TEST PURCHASE',
          amount: '42.42',
          merchantId: '123456789',
          merchantName: 'E2E MERCHANT',
          merchantCity: 'TESTVILLE',
          merchantZip: '90210',
          cardNumber,
          originalTs: new Date().toISOString(),
        })
        .expect(201);
      expect(res.body.id).toMatch(/^\d{16}$/);
      expect(res.body.amount).toBe('42.42');

      const fetched = await api()
        .get(`/api/v1/transactions/${res.body.id}`)
        .set('Authorization', `Bearer ${userToken}`)
        .expect(200);
      expect(fetched.body.description).toBe('E2E TEST PURCHASE');
    });

    it('rejects a transaction for an unknown card', async () => {
      await api()
        .post('/api/v1/transactions')
        .set('Authorization', `Bearer ${userToken}`)
        .send({
          typeCode: '01',
          categoryCode: 1,
          source: 'POS TERM',
          description: 'BAD CARD',
          amount: '1.00',
          merchantId: '123456789',
          merchantName: 'M',
          merchantCity: 'C',
          merchantZip: 'Z',
          cardNumber: '9999999999999999',
          originalTs: new Date().toISOString(),
        })
        .expect(400);
    });

    it('rejects a malformed amount', async () => {
      await api()
        .post('/api/v1/transactions')
        .set('Authorization', `Bearer ${userToken}`)
        .send({
          typeCode: '01',
          categoryCode: 1,
          source: 'POS TERM',
          description: 'BAD AMOUNT',
          amount: 'notmoney',
          merchantId: '123456789',
          merchantName: 'M',
          merchantCity: 'C',
          merchantZip: 'Z',
          cardNumber,
          originalTs: new Date().toISOString(),
        })
        .expect(400);
    });

    it('404s for an unknown transaction id', async () => {
      await api()
        .get('/api/v1/transactions/9999999999999999')
        .set('Authorization', `Bearer ${userToken}`)
        .expect(404);
    });
  });

  describe('billpay', () => {
    it('pays the full balance and zeroes the account', async () => {
      await prisma.account.update({
        where: { id: seededAccountId },
        data: { currentBalance: new Prisma.Decimal('150.75') },
      });
      const res = await api()
        .post('/api/v1/billpay')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ accountId: seededAccountId, confirm: true })
        .expect(201);
      expect(res.body.transaction.amount).toBe('150.75');
      expect(res.body.transaction.description).toBe('BILL PAYMENT - ONLINE');
      expect(res.body.account.currentBalance).toBe('0.00');
    });

    it('409s when there is nothing to pay', async () => {
      const res = await api()
        .post('/api/v1/billpay')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ accountId: seededAccountId, confirm: true })
        .expect(409);
      expect(res.body.message).toContain('nothing to pay');
    });

    it('400s when confirm is not true', async () => {
      await api()
        .post('/api/v1/billpay')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ accountId: seededAccountId, confirm: false })
        .expect(400);
    });

    it('404s for an unknown account', async () => {
      await api()
        .post('/api/v1/billpay')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ accountId: '99999999999', confirm: true })
        .expect(404);
    });
  });

  describe('users (admin)', () => {
    it('403s for non-admin callers', async () => {
      await api().get('/api/v1/users').set('Authorization', `Bearer ${userToken}`).expect(403);
    });

    it('lists users for admins', async () => {
      const res = await api()
        .get('/api/v1/users')
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(200);
      expect(res.body.items.some((u: { id: string }) => u.id === 'ADMIN0001')).toBe(true);
      expect(res.body.items[0].password).toBeUndefined();
    });

    it('creates, updates, and deletes a user', async () => {
      const created = await api()
        .post('/api/v1/users')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ id: testUserId, firstName: 'Ee', lastName: 'Tester', password: 'PASSWORD', role: 'USER' })
        .expect(201);
      expect(created.body).toMatchObject({ id: testUserId, role: 'USER' });

      // duplicate -> 409
      await api()
        .post('/api/v1/users')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ id: testUserId, firstName: 'Ee', lastName: 'Tester', password: 'PASSWORD', role: 'USER' })
        .expect(409);

      const updated = await api()
        .put(`/api/v1/users/${testUserId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ firstName: 'Renamed', role: 'ADMIN' })
        .expect(200);
      expect(updated.body).toMatchObject({ firstName: 'Renamed', role: 'ADMIN' });

      // unchanged update -> 400 'Please modify to update'
      await api()
        .put(`/api/v1/users/${testUserId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ firstName: 'Renamed' })
        .expect(400);

      await api()
        .delete(`/api/v1/users/${testUserId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .expect(204);

      await api()
        .put(`/api/v1/users/${testUserId}`)
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ firstName: 'X' })
        .expect(404);
    });

    it('400s on missing required create fields', async () => {
      await api()
        .post('/api/v1/users')
        .set('Authorization', `Bearer ${adminToken}`)
        .send({ id: 'BADUSER1', firstName: '', lastName: '', password: '', role: 'USER' })
        .expect(400);
    });
  });

  describe('reports', () => {
    it('records a report request as a PENDING job run', async () => {
      const res = await api()
        .post('/api/v1/reports')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ name: 'TRANREPT', startDate: '2024-01-01', endDate: '2024-01-31' })
        .expect(201);
      expect(res.body).toMatchObject({ jobName: 'transaction-report-request', status: 'PENDING' });
    });

    it('rejects an invalid date range', async () => {
      await api()
        .post('/api/v1/reports')
        .set('Authorization', `Bearer ${userToken}`)
        .send({ name: 'TRANREPT', startDate: '2024-02-01', endDate: '2024-01-01' })
        .expect(400);
    });

    it('lists reports paginated', async () => {
      const res = await api()
        .get('/api/v1/reports')
        .set('Authorization', `Bearer ${userToken}`)
        .expect(200);
      expect(res.body).toHaveProperty('items');
      expect(res.body).toHaveProperty('totalPages');
    });
  });
});
