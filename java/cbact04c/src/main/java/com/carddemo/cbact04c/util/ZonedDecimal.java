package com.carddemo.cbact04c.util;
import java.math.*;
public final class ZonedDecimal {
  private ZonedDecimal() {}
  public static BigDecimal parse(String value) {
    if (value == null || value.isEmpty()) return BigDecimal.ZERO.setScale(2);
    char last=value.charAt(value.length()-1); int sign=1; int digit;
    if (last=='{') digit=0;
    else if (last>='A'&&last<='I') digit=last-'A'+1;
    else if (last=='}') { sign=-1; digit=0; }
    else if (last>='J'&&last<='R') { sign=-1; digit=last-'J'+1; }
    else if (Character.isDigit(last)) digit=last-'0';
    else throw new IllegalArgumentException("Invalid zoned decimal: "+value);
    String number=value.substring(0,value.length()-1)+digit;
    return new BigDecimal(number).movePointLeft(2).multiply(BigDecimal.valueOf(sign)).setScale(2);
  }
  public static String format(BigDecimal value,int digits) {
    BigDecimal v=value.setScale(2,RoundingMode.DOWN); boolean neg=v.signum()<0;
    String n=v.abs().movePointRight(2).toBigInteger().toString();
    n="0".repeat(Math.max(0,digits+2-n.length()))+n;
    int i=n.length()-1; int d=n.charAt(i)-'0';
    char over=(char)((neg?'}':'{')+d);
    return n.substring(0,i)+over;
  }
}
