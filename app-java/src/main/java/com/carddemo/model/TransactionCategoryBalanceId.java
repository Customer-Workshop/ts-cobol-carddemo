package com.carddemo.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TransactionCategoryBalanceId implements Serializable {
    private Long acctId;
    private String tranTypeCd;
    private Integer tranCatCd;
}
