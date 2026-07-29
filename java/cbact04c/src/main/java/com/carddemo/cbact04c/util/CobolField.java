package com.carddemo.cbact04c.util;
import java.math.BigDecimal;
public final class CobolField {
  private CobolField(){}
  public static String text(String value,int length){ value=value==null?"":value; return (value+" ".repeat(length)).substring(0,length); }
  public static String digits(String value,int length){ value=value==null?"":value; if(value.length()>length)value=value.substring(value.length()-length); return "0".repeat(length-value.length())+value; }
  public static BigDecimal decimal(String s){ return ZonedDecimal.parse(s); }
}
