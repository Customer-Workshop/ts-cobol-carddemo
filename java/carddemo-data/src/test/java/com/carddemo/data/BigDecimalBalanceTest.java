package com.carddemo.data;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;
class BigDecimalBalanceTest { @Test void preservesExactScale(){BigDecimal value=new BigDecimal("-123456789.01").setScale(2);assertThat(value).isEqualByComparingTo(new BigDecimal("-123456789.01"));assertThat(value.scale()).isEqualTo(2);} }
