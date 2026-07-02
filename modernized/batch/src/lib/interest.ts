// Interest computation — modernizes CBACT04C monthly interest formula.
// StatementandReportGeneration REQ-F-083: monthly interest =
// (transaction category balance × interest rate) / 1200.
import { Prisma } from '@prisma/client';

export function monthlyInterest(balance: Prisma.Decimal, annualRate: Prisma.Decimal): Prisma.Decimal {
  return balance.times(annualRate).dividedBy(1200).toDecimalPlaces(2);
}
