using System.Text;
using CardDemo.Domain;
using CardDemo.Legacy.Decoders.Records;
using CardDemo.Modules.TranReport.Records;
using CardDemo.Persistence;

namespace CardDemo.Modules.TranReport;

/// <summary>
/// Port of CBTRN03C: the daily transaction detail report (TRANREPT).
/// Produces the 133-byte-record report byte-for-byte, including two
/// preserved estate quirks (see the semantics contract):
/// SEM-B02 - an out-of-window transaction ends the run immediately
/// (COBOL NEXT SENTENCE jumps past the whole loop body), so a trailing
/// out-of-window record suppresses the final page/grand totals; and
/// SEM-B03 - the clean-EOF path re-accumulates the stale last record's
/// amount before writing the final page and grand totals, and never
/// flushes the last account total.
/// </summary>
public static class TranReportJob
{
    private const int ReportRecordLength = 133;
    private const int DateParmRecordLength = 80;
    private const int PageSize = 20;

    public static byte[] Run(TranReportInputs inputs)
    {
        ArgumentNullException.ThrowIfNull(inputs);

        (string startDate, string endDate) = ReadDateWindow(inputs.DateParmPath);
        Dictionary<string, CardXrefRecord> xrefByCard = FixedRecordFile
            .ReadLineSequential(inputs.CardXrefUnloadPath, CardXrefRecord.Length)
            .Select(r => CardXrefRecord.Decode(r))
            .ToDictionary(r => r.CardNumber);
        Dictionary<string, TranTypeRecord> typeByCode = FixedRecordFile
            .ReadLineSequential(inputs.TranTypeUnloadPath, TranTypeRecord.Length)
            .Select(r => TranTypeRecord.Decode(r))
            .ToDictionary(r => r.TypeCode);
        Dictionary<(string TypeCode, int CategoryCode), TranCategoryRecord> categoryByKey = FixedRecordFile
            .ReadLineSequential(inputs.TranCategoryUnloadPath, TranCategoryRecord.Length)
            .Select(r => TranCategoryRecord.Decode(r))
            .ToDictionary(r => (r.TypeCode, r.CategoryCode));

        var report = new MemoryStream();
        var state = new ReportState(new string(' ', 16));

        using IEnumerator<byte[]> transactions = FixedRecordFile
            .ReadFixed(inputs.TranFilePath, TransactionRecord.Length)
            .GetEnumerator();

        // The record area keeps its previous content when READ hits
        // EOF; CBTRN03C's EOF branch reads that stale record (SEM-B03).
        TransactionRecord? current = null;
        bool endOfFile = false;
        while (!endOfFile)
        {
            if (transactions.MoveNext())
            {
                current = TransactionRecord.Decode(transactions.Current);
            }
            else
            {
                endOfFile = true;
            }

            string processDate = current is null ? new string(' ', 10) : current.ProcessTimestamp[..10];
            if (LegacyDate.Compare(processDate, startDate) < 0 || LegacyDate.Compare(processDate, endDate) > 0)
            {
                // SEM-B02: NEXT SENTENCE leaves the read loop entirely,
                // suppressing all remaining totals.
                break;
            }

            if (!endOfFile)
            {
                WriteDetailLine(report, state, current!, startDate, endDate, xrefByCard, typeByCode, categoryByKey);
            }
            else
            {
                state.PageTotal += current!.Amount;
                state.AccountTotal += current.Amount;
                WritePageTotals(report, state);
                Write(report, ReportLayout.GrandTotal(state.GrandTotal));
            }
        }

        return report.ToArray();
    }

    private static void WriteDetailLine(
        MemoryStream report,
        ReportState state,
        TransactionRecord tran,
        string startDate,
        string endDate,
        Dictionary<string, CardXrefRecord> xrefByCard,
        Dictionary<string, TranTypeRecord> typeByCode,
        Dictionary<(string TypeCode, int CategoryCode), TranCategoryRecord> categoryByKey)
    {
        if (state.CurrentCardNumber != tran.CardNumber)
        {
            if (!state.FirstTime)
            {
                Write(report, ReportLayout.AccountTotal(state.AccountTotal));
                state.AccountTotal = 0m;
                state.LineCounter++;
                Write(report, ReportLayout.DashedLine);
                state.LineCounter++;
            }

            state.CurrentCardNumber = tran.CardNumber;
            state.CurrentXref = xrefByCard.TryGetValue(tran.CardNumber, out CardXrefRecord? xref)
                ? xref
                : throw new InvalidDataException($"INVALID CARD NUMBER (xref lookup): {tran.CardNumber}");
        }

        if (!typeByCode.TryGetValue(tran.TypeCode, out TranTypeRecord? tranType))
        {
            throw new InvalidDataException($"INVALID TRANSACTION TYPE : {tran.TypeCode}");
        }

        if (!categoryByKey.TryGetValue((tran.TypeCode, tran.CategoryCode), out TranCategoryRecord? category))
        {
            throw new InvalidDataException($"INVALID TRAN CATG KEY : {tran.TypeCode}{tran.CategoryCode:D4}");
        }

        if (state.FirstTime)
        {
            state.FirstTime = false;
            WriteHeaders(report, state, startDate, endDate);
        }

        if (state.LineCounter % PageSize == 0)
        {
            WritePageTotals(report, state);
            WriteHeaders(report, state, startDate, endDate);
        }

        state.PageTotal += tran.Amount;
        state.AccountTotal += tran.Amount;
        Write(report, ReportLayout.Detail(
            tran.TranId,
            state.CurrentXref!.AccountId,
            tran.TypeCode,
            tranType.Description,
            tran.CategoryCode,
            category.Description,
            tran.Source,
            tran.Amount));
        state.LineCounter++;
    }

    private static void WriteHeaders(MemoryStream report, ReportState state, string startDate, string endDate)
    {
        Write(report, ReportLayout.NameHeader(startDate, endDate));
        state.LineCounter++;
        Write(report, string.Empty);
        state.LineCounter++;
        Write(report, ReportLayout.ColumnHeader);
        state.LineCounter++;
        Write(report, ReportLayout.DashedLine);
        state.LineCounter++;
    }

    private static void WritePageTotals(MemoryStream report, ReportState state)
    {
        Write(report, ReportLayout.PageTotal(state.PageTotal));
        state.GrandTotal += state.PageTotal;
        state.PageTotal = 0m;
        state.LineCounter++;
        Write(report, ReportLayout.DashedLine);
        state.LineCounter++;
    }

    private static void Write(MemoryStream report, string line) =>
        report.Write(Encoding.ASCII.GetBytes(line.PadRight(ReportRecordLength)));

    private static (string StartDate, string EndDate) ReadDateWindow(string dateParmPath)
    {
        byte[] record = FixedRecordFile.ReadFixed(dateParmPath, DateParmRecordLength).First();
        string text = Encoding.ASCII.GetString(record);
        return (text[..10], text.Substring(11, 10));
    }

    private sealed class ReportState(string currentCardNumber)
    {
        public string CurrentCardNumber { get; set; } = currentCardNumber;
        public CardXrefRecord? CurrentXref { get; set; }
        public bool FirstTime { get; set; } = true;
        public long LineCounter { get; set; }
        public decimal PageTotal { get; set; }
        public decimal AccountTotal { get; set; }
        public decimal GrandTotal { get; set; }
    }
}
