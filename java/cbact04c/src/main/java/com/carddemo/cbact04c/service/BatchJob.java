package com.carddemo.cbact04c.service;

import java.nio.file.Path;

public record BatchJob(
        Path tcatbal,
        Path xref,
        Path discgrp,
        Path account,
        Path transact,
        String parmDate,
        boolean finalUpdateAtEof) {
}
