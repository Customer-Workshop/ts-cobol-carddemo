package com.carddemo.cbtrn02c.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FixedWidth {
  private FixedWidth() {}
  public static String text(String s, int n) { s = s == null ? "" : s; return (s + " ".repeat(n)).substring(0, n); }
  public static String num(String s, int n) { s = s == null ? "" : s; String p="0".repeat(n)+s.trim(); return p.substring(Math.max(0,p.length()-n)); }
  public static String signed(BigDecimal value, int digits) {
    long cents = value.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
    boolean neg = cents < 0; String raw = Long.toString(Math.abs(cents));
    raw = ("0".repeat(digits) + raw); raw = raw.substring(Math.max(0,raw.length()-digits));
    char last = raw.charAt(digits-1); int d = last-'0';
    char over = neg ? (char)('J'+d) : (d == 0 ? '{' : (char)('A'+d-1));
    return raw.substring(0,digits-1) + over;
  }
  public static BigDecimal parseSigned(String s, int digits) {
    s = s.substring(0, digits); char c=s.charAt(digits-1); boolean neg=false; int d;
    if (c >= 'A' && c <= 'I') d=c-'A'+1; else if(c=='{') d=0; else if(c >= 'J' && c <= 'R'){neg=true; d=c-'J';} else if(c=='}'){neg=true; d=0;} else d=c-'0';
    String raw=s.substring(0,digits-1)+d; BigDecimal v=new BigDecimal(raw).movePointLeft(2);
    return neg ? v.negate() : v;
  }
  public static void require(String s, int n) { if (s.length()!=n) throw new IllegalArgumentException("Expected "+n+" bytes, got "+s.length()); }
}
