package com.carddemo.cbtrn02c.repo;
import java.io.*;import java.nio.file.*;import java.util.*;import java.util.function.*;
public final class FixedFiles {
 private FixedFiles(){}
 public static <T> List<T> readSequential(Path p, Function<String,T> parser)throws IOException{if(!Files.exists(p))return new ArrayList<>();List<T> out=new ArrayList<>();try(BufferedReader b=Files.newBufferedReader(p)){String s;while((s=b.readLine())!=null){if(!s.isEmpty())out.add(parser.apply(s));}}return out;}
 public static <T> void writeSequential(Path p,List<T> data,Function<T,String> formatter)throws IOException{if(p.getParent()!=null)Files.createDirectories(p.getParent());try(BufferedWriter w=Files.newBufferedWriter(p)){for(T x:data){w.write(formatter.apply(x));w.newLine();}}}
 public static <T> TreeMap<String,T> readMap(Path p,Function<String,T> parser,Function<T,String> key)throws IOException{TreeMap<String,T> m=new TreeMap<>();for(T x:readSequential(p,parser))m.put(key.apply(x),x);return m;}
 public static <T> void writeMap(Path p,Map<String,T> m,Function<T,String> formatter)throws IOException{writeSequential(p,new ArrayList<>(m.values()),formatter);}
}
