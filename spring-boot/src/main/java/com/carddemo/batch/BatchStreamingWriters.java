package com.carddemo.batch;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemStreamWriter;
import org.springframework.batch.item.file.FlatFileItemWriter;

import java.math.BigDecimal;

final class ReportLineAggregator implements org.springframework.batch.item.file.transform.LineAggregator<BatchJobService.ReportLine> {
    private long lines;
    private Long account;
    private BigDecimal page = BigDecimal.ZERO;
    private BigDecimal accountTotal = BigDecimal.ZERO;
    private BigDecimal grand = BigDecimal.ZERO;

    @Override
    public String aggregate(BatchJobService.ReportLine line) {
        StringBuilder output = new StringBuilder();
        if (account != null && !account.equals(line.accountId())) {
            output.append(total("Account Total", accountTotal)).append('\n');
            accountTotal = BigDecimal.ZERO;
            output.append(columns()).append('\n');
        }
        if (lines == 0) {
            output.append(columns()).append('\n');
        }
        account = line.accountId();
        var transaction = line.transaction();
        output.append("%-16s %-11s %2s-%-15s %04d-%-29s %-10s    %15s".formatted(
                transaction.getTranId(), line.accountId(), transaction.getTranTypeCode(), line.type(),
                transaction.getTranCategoryCode(), line.category(), transaction.getTranSource(),
                amount(transaction.getTranAmount())));
        lines++;
        BigDecimal value = zero(transaction.getTranAmount());
        page = page.add(value);
        accountTotal = accountTotal.add(value);
        grand = grand.add(value);
        if (lines % 20 == 0) {
            output.append('\n').append(total("Page Total", page));
            page = BigDecimal.ZERO;
            output.append('\n').append(columns());
        }
        return output.toString();
    }

    String footer() {
        StringBuilder output = new StringBuilder();
        if (lines > 0) {
            if (page.signum() != 0) {
                output.append(total("Page Total", page)).append('\n');
            }
            output.append(total("Account Total", accountTotal)).append('\n');
        }
        return output.append(total("Grand Total", grand)).toString();
    }

    private static String columns() {
        return "Transaction ID  Account ID           Transaction Type    Tran Category       Tran Source          Amount\n"
                + "-".repeat(133);
    }

    private static String total(String label, BigDecimal value) {
        return "%-13s%s%+15.2f".formatted(label, ".".repeat(86), value);
    }

    private static String amount(BigDecimal value) {
        return "%,.2f".formatted(zero(value));
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
