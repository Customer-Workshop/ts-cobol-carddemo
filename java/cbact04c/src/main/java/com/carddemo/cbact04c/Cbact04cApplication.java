package com.carddemo.cbact04c;
import java.nio.file.*;import org.springframework.boot.*;import org.springframework.boot.autoconfigure.*;import org.springframework.context.annotation.*;import com.carddemo.cbact04c.service.*;
@SpringBootApplication public class Cbact04cApplication {
 public static void main(String[] args){try{System.exit(SpringApplication.exit(SpringApplication.run(Cbact04cApplication.class,args)));}catch(AbendException e){System.err.println("ABENDING PROGRAM");System.exit(999);}}
 @Bean CommandLineRunner runner(){return a->{if(a.length<6)throw new AbendException("Usage: <tcatbal> <xref> <discgrp> <acctdata> <transact> <parm-date> [final-update-at-eof]");BatchResult r=new Cbact04cService(java.time.Clock.systemDefaultZone(),a.length>6&&Boolean.parseBoolean(a[6])).run(Paths.get(a[0]),Paths.get(a[1]),Paths.get(a[2]),Paths.get(a[3]),Paths.get(a[4]),a[5]);System.out.println("RECORD COUNT: "+r.recordCount());System.out.println("TRANSACTIONS GENERATED: "+r.transactionCount());};}
 @Bean ExitCodeGenerator exitCode(){return ()->0;}
}
