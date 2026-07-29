package com.carddemo.cbtrn02c;
import com.carddemo.cbtrn02c.repo.BatchFiles;import com.carddemo.cbtrn02c.service.TransactionPosterService;import java.nio.file.*;import org.springframework.boot.*;import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication public class Cbtrn02cApplication {
 public static void main(String[] args)throws Exception{Path p=Path.of(args.length>0?args[0]:"target/batch-data");BatchFiles f=new BatchFiles(p);var r=new TransactionPosterService().run(f);f.save();System.out.println("TRANSACTIONS PROCESSED : "+r.processed());System.out.println("TRANSACTIONS REJECTED  : "+r.rejected());if(!Boolean.getBoolean("cbtrn02c.noExit"))System.exit(r.exitCode());}
}
