package com.carddemo.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import java.math.BigDecimal;

/**
 * CVTRA07Y 133-column fidelity port of CBTRN03C's report loop
 * (app/cbl/CBTRN03C.cbl:170-213): headers are emitted inside the first
 * in-range row's write, the page break fires whenever the written-record
 * counter is a multiple of 20 (headers, totals and rules all count), an
 * account total is written only on a card-number break — never for the
 * last card — and at EOF the stale last record's TRAN-AMT is added to the
 * page (and dead account) total once more before the page and grand
 * totals print (:198-203). An input with no in-range row writes nothing:
 * the out-of-range NEXT SENTENCE exits the sweep before any total.
 */
final class ReportLineAggregator implements org.springframework.batch.item.file.transform.LineAggregator<BatchJobService.ReportLine> {
    private static final int PAGE_SIZE = 20;
    private static final String RULE = "-".repeat(133);

    private final String nameHeader;
    private long lines;
    private boolean first = true;
    private String card;
    private BigDecimal page = BigDecimal.ZERO;
    private BigDecimal accountTotal = BigDecimal.ZERO;
    private BigDecimal grand = BigDecimal.ZERO;
    private BigDecimal lastAmount = BigDecimal.ZERO;

    ReportLineAggregator(String startDate, String endDate) {
        this.nameHeader = pad(pad("DALYREPT", 38)
                + pad("Daily Transaction Report", 41)
                + "Date Range: " + pad(startDate, 10) + " to " + pad(endDate, 10), 133);
    }

    @Override
    public String aggregate(BatchJobService.ReportLine report) {
        StringBuilder output = new StringBuilder();
        var transaction = report.transaction();
        if (!transaction.getTranCardNumber().equals(card)) {
            if (!first) {
                accountTotals(output);
            }
            card = transaction.getTranCardNumber();
        }
        if (first) {
            first = false;
            headers(output);
        }
        if (lines % PAGE_SIZE == 0) {
            pageTotals(output);
            headers(output);
        }
        BigDecimal amount = zero(transaction.getTranAmount());
        page = page.add(amount);
        accountTotal = accountTotal.add(amount);
        lastAmount = amount;
        emit(output, detail(report));
        return output.toString();
    }

    String footer() {
        if (first) {
            return "";
        }
        page = page.add(lastAmount);
        accountTotal = accountTotal.add(lastAmount);
        StringBuilder output = new StringBuilder(total("Page Total", 11, page));
        grand = grand.add(page);
        output.append('\n').append(RULE);
        output.append('\n').append(total("Grand Total", 11, grand));
        return output.toString();
    }

    private void headers(StringBuilder output) {
        emit(output, nameHeader);
        emit(output, pad("", 133));
        emit(output, pad(pad("Transaction ID", 17) + pad("Account ID", 12)
                + pad("Transaction Type", 19) + pad("Tran Category", 35)
                + pad("Tran Source", 14) + " " + pad("        Amount", 16), 133));
        emit(output, RULE);
    }

    private void pageTotals(StringBuilder output) {
        emit(output, total("Page Total", 11, page));
        grand = grand.add(page);
        page = BigDecimal.ZERO;
        emit(output, RULE);
    }

    private void accountTotals(StringBuilder output) {
        emit(output, total("Account Total", 13, accountTotal));
        accountTotal = BigDecimal.ZERO;
        emit(output, RULE);
    }

    private void emit(StringBuilder output, String record) {
        if (output.length() > 0) {
            output.append('\n');
        }
        output.append(record);
        lines++;
    }

    // TRANSACTION-DETAIL-REPORT (CVTRA07Y.cpy:14-31) padded to 133.
    private static String detail(BatchJobService.ReportLine report) {
        var transaction = report.transaction();
        return pad(pad(transaction.getTranId(), 16) + " "
                + pad(account(report.accountId()), 11) + " "
                + pad(transaction.getTranTypeCode(), 2) + "-"
                + pad(report.type(), 15) + " "
                + "%04d".formatted(transaction.getTranCategoryCode()) + "-"
                + pad(report.category(), 29) + " "
                + pad(transaction.getTranSource(), 10) + "    "
                + edit(transaction.getTranAmount(), false) + "  ", 133);
    }

    // XREF-ACCT-ID 9(11) moved to the X(11) report slot keeps its digits.
    private static String account(Long accountId) {
        return accountId == null ? "" : "%011d".formatted(accountId);
    }

    private static String total(String label, int labelWidth, BigDecimal value) {
        return pad(pad(label, labelWidth) + ".".repeat(97 - labelWidth)
                + edit(value, true), 133);
    }

    // COBOL edited move: PIC -ZZZ,ZZZ,ZZZ.ZZ (detail) or +ZZZ,ZZZ,ZZZ.ZZ
    // (totals) for an S9(09)V99 amount — a fixed sign column, leading-zero
    // suppression with commas suppressed inside the run, '.' always shown.
    private static String edit(BigDecimal value, boolean signAlways) {
        BigDecimal amount = value == null ? BigDecimal.ZERO : value;
        boolean negative = amount.signum() < 0;
        String digits = amount.abs().movePointRight(2)
                .setScale(0, java.math.RoundingMode.DOWN).toString();
        if (digits.length() > 11) {
            digits = digits.substring(digits.length() - 11);
        }
        digits = "0".repeat(11 - digits.length()) + digits;
        StringBuilder integer = new StringBuilder();
        boolean suppressed = true;
        String whole = digits.substring(0, 9);
        for (int group = 0; group < 3; group++) {
            String part = whole.substring(group * 3, group * 3 + 3);
            if (suppressed) {
                int zeros = 0;
                while (zeros < 3 && part.charAt(zeros) == '0') {
                    zeros++;
                }
                if (zeros == 3) {
                    integer.append("   ");
                } else {
                    integer.append(" ".repeat(zeros)).append(part.substring(zeros));
                    suppressed = false;
                }
            } else {
                integer.append(part);
            }
            if (group < 2) {
                integer.append(suppressed ? ' ' : ',');
            }
        }
        char sign = negative ? '-' : signAlways ? '+' : ' ';
        return sign + integer.toString() + '.' + digits.substring(9);
    }

    private static String pad(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() > width) {
            return value.substring(0, width);
        }
        return value + " ".repeat(width - value.length());
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

final class DualStatementWriter implements ItemStreamWriter<BatchJobService.CardStatement> {
    private final FlatFileItemWriter<BatchJobService.CardStatement> plain;
    private final FlatFileItemWriter<BatchJobService.CardStatement> html;

    DualStatementWriter(FlatFileItemWriter<BatchJobService.CardStatement> plain,
                        FlatFileItemWriter<BatchJobService.CardStatement> html) {
        this.plain = plain;
        this.html = html;
    }

    @Override
    public void write(Chunk<? extends BatchJobService.CardStatement> chunk) throws Exception {
        plain.write(chunk);
        html.write(chunk);
    }

    @Override
    public void open(ExecutionContext context) {
        plain.open(context);
        html.open(context);
    }

    @Override
    public void update(ExecutionContext context) {
        plain.update(context);
        html.update(context);
    }

    @Override
    public void close() {
        plain.close();
        html.close();
    }
}
