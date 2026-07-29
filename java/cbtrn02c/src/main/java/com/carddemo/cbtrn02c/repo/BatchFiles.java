package com.carddemo.cbtrn02c.repo;
import com.carddemo.cbtrn02c.domain.*;import java.io.*;import java.nio.file.*;import java.util.*;
public class BatchFiles {
 public final List<DalyTranRecord> daily; public final TreeMap<String,TranRecord> transact; public final TreeMap<String,CardXrefRecord> xref; public final List<String> rejects; public final TreeMap<String,AccountRecord> accounts; public final TreeMap<String,TranCatBalRecord> tcatbal;
 private final Path dir;
 public BatchFiles(Path dir)throws IOException{this.dir=dir;daily=FixedFiles.readSequential(dir.resolve("DALYTRAN"),DalyTranRecord::parse);transact=FixedFiles.readMap(dir.resolve("TRANSACT"),TranRecord::parse,x->x.id);xref=FixedFiles.readMap(dir.resolve("XREF"),CardXrefRecord::parse,x->x.cardNum);rejects=Files.exists(dir.resolve("DALYREJS"))?Files.readAllLines(dir.resolve("DALYREJS")):new ArrayList<>();accounts=FixedFiles.readMap(dir.resolve("ACCOUNT"),AccountRecord::parse,x->x.acctId);tcatbal=FixedFiles.readMap(dir.resolve("TCATBAL"),TranCatBalRecord::parse,TranCatBalRecord::key);}
 public void save()throws IOException{Files.createDirectories(dir);FixedFiles.writeMap(dir.resolve("TRANSACT"),transact,x->x.format());FixedFiles.writeMap(dir.resolve("XREF"),xref,x->x.format());FixedFiles.writeSequential(dir.resolve("DALYREJS"),rejects,x->x);FixedFiles.writeMap(dir.resolve("ACCOUNT"),accounts,x->x.format());FixedFiles.writeMap(dir.resolve("TCATBAL"),tcatbal,x->x.format());}
}
