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
public class DisclosureGroupId implements Serializable {
    private String acctGroupId;
    private String tranTypeCd;
    private Integer tranCatCd;
}
