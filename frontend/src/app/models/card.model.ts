export interface Card {
  cardNumber: string;
  accountId: string;
  cvvCode: string;
  embossedName: string;
  expirationDate: string;
  activeStatus: string;
}

export interface CardCrossReference {
  cardNumber: string;
  customerId: string;
  accountId: string;
}
