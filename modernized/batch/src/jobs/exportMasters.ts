// export-masters — modernizes the sequential master-file readers CBACT01C
// (accounts), CBACT02C (cards), CBACT03C (card xref), CBCUS01C (customers).
// CustomerandAccountDataManagement REQ-F-001..REQ-F-044: sequential dump of
// each master data store with every field output under a descriptive label,
// terminating cleanly at end of data (legacy result code 16).
import * as fs from 'fs';
import * as path from 'path';
import { prisma } from '../lib/prisma';
import { JobResult } from '../lib/jobRun';
import { Logger, OUTPUT_DIR } from '../lib/logger';

const iso = (d: Date | null): string => (d ? d.toISOString().slice(0, 10) : '');

function record(fields: [string, string][]): string {
  return (
    fields.map(([label, value]) => `${label.padEnd(22)}: ${value}`).join('\n') +
    '\n' +
    '-'.repeat(60) +
    '\n'
  );
}

export async function exportMasters(logger: Logger): Promise<JobResult> {
  const exportDir = path.join(OUTPUT_DIR, 'exports');
  fs.mkdirSync(exportDir, { recursive: true });

  // REQ-F-001..REQ-F-005, REQ-F-023..REQ-F-027: account master dump with labels.
  const accounts = await prisma.account.findMany({ orderBy: { id: 'asc' } });
  fs.writeFileSync(
    path.join(exportDir, 'ACCTDATA.txt'),
    accounts
      .map((a) =>
        record([
          ['ACCT-ID', a.id],
          ['ACCT-ACTIVE-STATUS', a.activeStatus ? 'Y' : 'N'],
          ['ACCT-CURR-BAL', a.currentBalance.toFixed(2)],
          ['ACCT-CREDIT-LIMIT', a.creditLimit.toFixed(2)],
          ['ACCT-CASH-CREDIT-LIMIT', a.cashCreditLimit.toFixed(2)],
          ['ACCT-OPEN-DATE', iso(a.openDate)],
          ['ACCT-EXPIRATION-DATE', iso(a.expirationDate)],
          ['ACCT-REISSUE-DATE', iso(a.reissueDate)],
          ['ACCT-CURR-CYC-CREDIT', a.currCycleCredit.toFixed(2)],
          ['ACCT-CURR-CYC-DEBIT', a.currCycleDebit.toFixed(2)],
          ['ACCT-GROUP-ID', a.groupId ?? ''],
        ]),
      )
      .join(''),
  );
  logger.info(`exported ${accounts.length} accounts`);

  // REQ-F-006..REQ-F-011, REQ-F-028..REQ-F-033: card master dump.
  const cards = await prisma.card.findMany({ orderBy: { cardNumber: 'asc' } });
  fs.writeFileSync(
    path.join(exportDir, 'CARDDATA.txt'),
    cards
      .map((c) =>
        record([
          ['CARD-NUM', c.cardNumber],
          ['CARD-ACCT-ID', c.accountId],
          ['CARD-EMBOSSED-NAME', c.embossedName],
          ['CARD-EXPIRY-DATE', iso(c.expiryDate)],
          ['CARD-ACTIVE-STATUS', c.activeStatus ? 'Y' : 'N'],
        ]),
      )
      .join(''),
  );
  logger.info(`exported ${cards.length} cards`);

  // REQ-F-012..REQ-F-016, REQ-F-040..REQ-F-044: cross-reference dump.
  const xrefs = await prisma.cardXref.findMany({ orderBy: { cardNumber: 'asc' } });
  fs.writeFileSync(
    path.join(exportDir, 'CARDXREF.txt'),
    xrefs
      .map((x) =>
        record([
          ['XREF-CARD-NUM', x.cardNumber],
          ['XREF-CUST-ID', x.customerId],
          ['XREF-ACCT-ID', x.accountId],
        ]),
      )
      .join(''),
  );
  logger.info(`exported ${xrefs.length} card xrefs`);

  // REQ-F-017..REQ-F-022, REQ-F-034..REQ-F-039: customer master dump.
  const customers = await prisma.customer.findMany({ orderBy: { id: 'asc' } });
  fs.writeFileSync(
    path.join(exportDir, 'CUSTDATA.txt'),
    customers
      .map((c) =>
        record([
          ['CUST-ID', c.id],
          ['CUST-FIRST-NAME', c.firstName],
          ['CUST-MIDDLE-NAME', c.middleName ?? ''],
          ['CUST-LAST-NAME', c.lastName],
          ['CUST-ADDR-LINE-1', c.addressLine1],
          ['CUST-ADDR-LINE-2', c.addressLine2 ?? ''],
          ['CUST-ADDR-LINE-3', c.addressLine3 ?? ''],
          ['CUST-ADDR-STATE-CD', c.stateCode],
          ['CUST-ADDR-COUNTRY-CD', c.countryCode],
          ['CUST-ADDR-ZIP', c.zipCode],
          ['CUST-PHONE-NUM-1', c.phoneNumber1 ?? ''],
          ['CUST-PHONE-NUM-2', c.phoneNumber2 ?? ''],
          ['CUST-SSN', c.ssn],
          ['CUST-GOVT-ISSUED-ID', c.governmentIssuedId ?? ''],
          ['CUST-DOB', iso(c.dateOfBirth)],
          ['CUST-EFT-ACCOUNT-ID', c.eftAccountId ?? ''],
          ['CUST-PRI-HOLDER-IND', c.primaryCardHolder ? 'Y' : 'N'],
          ['CUST-FICO-SCORE', String(c.ficoCreditScore)],
        ]),
      )
      .join(''),
  );
  logger.info(`exported ${customers.length} customers`);

  return {
    counts: {
      accounts: accounts.length,
      cards: cards.length,
      cardXrefs: xrefs.length,
      customers: customers.length,
    },
  };
}
