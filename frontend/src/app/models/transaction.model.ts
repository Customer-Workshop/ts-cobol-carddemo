export interface Transaction {
  transactionId: string;
  typeCode: string;
  categoryCode: string;
  source: string;
  description: string;
  amount: number;
  merchantId: string;
  merchantName: string;
  merchantCity: string;
  merchantZip: string;
  cardNumber: string;
  originTimestamp: string;
  processedTimestamp: string;
}

export interface TransactionType {
  typeCode: string;
  description: string;
}

export interface TransactionCategory {
  typeCode: string;
  categoryCode: string;
  description: string;
}
