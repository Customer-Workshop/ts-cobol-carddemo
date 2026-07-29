package com.carddemo.cbact04c.io;
public final class FixedWidth {
  private FixedWidth(){}
  public static String pad(String s,int n){ s=s==null?"":s; return (s+" ".repeat(n)).substring(0,n); }
  public static String at(String s,int start,int length){ return pad(s,length+start).substring(start,start+length); }
}
