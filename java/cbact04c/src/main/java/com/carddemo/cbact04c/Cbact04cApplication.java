package com.carddemo.cbact04c;

import com.carddemo.cbact04c.config.Cbact04cProperties;
import com.carddemo.cbact04c.service.AbendException;
import com.carddemo.cbact04c.service.BatchJob;
import com.carddemo.cbact04c.service.BatchResult;
import com.carddemo.cbact04c.service.Cbact04cService;

import java.nio.file.Path;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ExitCodeExceptionMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Cbact04cApplication {

    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(Cbact04cApplication.class, args)));
    }

    @Bean
    public ApplicationRunner batchRunner(
            Cbact04cProperties properties,
            Cbact04cService service) {
        return arguments -> {
            BatchJob job = jobFrom(arguments, properties);
            BatchResult result = service.run(job);
            System.out.println("RECORD COUNT: " + result.recordCount());
            System.out.println("TRANSACTIONS GENERATED: " + result.transactionCount());
        };
    }

    @Bean
    public ExitCodeExceptionMapper exitCodeExceptionMapper() {
        return exception -> exception instanceof AbendException
                || exception.getCause() instanceof AbendException ? 999 : 1;
    }

    private static BatchJob jobFrom(
            ApplicationArguments arguments,
            Cbact04cProperties properties) {
        List<String> positional = arguments.getNonOptionArgs();
        if (positional.size() >= 6) {
            return new BatchJob(
                    Path.of(positional.get(0)),
                    Path.of(positional.get(1)),
                    Path.of(positional.get(2)),
                    Path.of(positional.get(3)),
                    Path.of(positional.get(4)),
                    positional.get(5),
                    positional.size() > 6
                            ? Boolean.parseBoolean(positional.get(6))
                            : properties.isFinalUpdateAtEof());
        }

        if (properties.getTcatbal() == null
                || properties.getXref() == null
                || properties.getDiscgrp() == null
                || properties.getAccount() == null
                || properties.getTransact() == null
                || properties.getParmDate() == null) {
            throw new AbendException(
                    "Usage: positional <tcatbal> <xref> <discgrp> <account> "
                            + "<transact> <parm-date> [final-update-at-eof], or "
                            + "cbact04c.* properties");
        }

        return new BatchJob(
                Path.of(properties.getTcatbal()),
                Path.of(properties.getXref()),
                Path.of(properties.getDiscgrp()),
                Path.of(properties.getAccount()),
                Path.of(properties.getTransact()),
                properties.getParmDate(),
                properties.isFinalUpdateAtEof());
    }
}
