package com.carddemo.api;

/**
 * The account half of map {@code CACTVWA} (COACTVWC.cbl:471-490), one field per BMS field and in
 * screen order. Money fields carry the {@code +ZZZ,ZZZ,ZZZ.99} text of the {@code PICOUT} clause and
 * the date fields the stored 10-character text (Q-10), so the component renders without reformatting.
 */
public record AccountBlock(
        String activeStatus,
        String openDate,
        String creditLimit,
        String expirationDate,
        String cashCreditLimit,
        String reissueDate,
        String currentBalance,
        String currentCycleCredit,
        String groupId,
        String currentCycleDebit) {
}
