package com.carddemo.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * JPA entity mapped from COBOL copybook CVTRA02Y.cpy (DIS-GROUP-RECORD, RECLN 50).
 * Composite key: (acctGroupId, tranTypeCd, tranCatCd).
 */
@Entity
@Table(name = "disclosure_groups")
@IdClass(DisclosureGroupId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DisclosureGroup {

    @Id
    @Column(name = "acct_group_id", length = 10)
    private String acctGroupId;

    @Id
    @Column(name = "tran_type_cd", length = 2)
    private String tranTypeCd;

    @Id
    @Column(name = "tran_cat_cd")
    private Integer tranCatCd;

    @Column(name = "int_rate", precision = 6, scale = 2)
    private BigDecimal intRate;
}
