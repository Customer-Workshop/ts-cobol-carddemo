package com.carddemo.cbact04c.io;
import java.io.*;import java.nio.file.*;import java.util.*;import com.carddemo.cbact04c.domain.Records.*;
public final class FileGateways {
 private FileGateways(){}
 public static List<String> lines(Path p)throws IOException{return Files.readAllLines(p);}
 public static Map<String,Xref> xrefs(Path p)throws IOException{Map<String,Xref> m=new HashMap<>();for(String l:lines(p)){Xref x=RecordCodecs.xref(l);m.put(x.acctId(),x);}return m;}
 public static Map<String,Account> accounts(Path p)throws IOException{Map<String,Account> m=new TreeMap<>();for(String l:lines(p)){Account a=RecordCodecs.account(l);m.put(a.id,a);}return m;}
 public static Map<DiscKey,DiscGroup> discs(Path p)throws IOException{Map<DiscKey,DiscGroup> m=new HashMap<>();for(String l:lines(p)){DiscGroup d=RecordCodecs.disc(l);m.put(d.key(),d);}return m;}
 public static void writeAccounts(Path p,Collection<Account> a)throws IOException{try(BufferedWriter w=Files.newBufferedWriter(p)){for(Account x:a){w.write(RecordCodecs.account(x));w.newLine();}}}
}
