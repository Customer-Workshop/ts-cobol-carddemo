package com.carddemo.cbtrn02c;

import com.carddemo.cbtrn02c.repo.BatchFiles;
import com.carddemo.cbtrn02c.service.TransactionPosterService;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Path;

@SpringBootApplication
public class Cbtrn02cApplication {
    public static void main(String[] args) throws Exception {
        Path dataDirectory = Path.of(
                args.length > 0 ? args[0] : "target/batch-data");
        BatchFiles files = new BatchFiles(dataDirectory);
        TransactionPosterService.Result result =
                new TransactionPosterService().run(files);
        files.save();

        System.out.println("TRANSACTIONS PROCESSED : " + result.processed());
        System.out.println("TRANSACTIONS REJECTED  : " + result.rejected());
        if (!Boolean.getBoolean("cbtrn02c.noExit")) {
            System.exit(result.exitCode());
        }
    }
}
