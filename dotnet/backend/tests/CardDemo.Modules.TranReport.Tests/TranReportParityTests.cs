using System.Text;

namespace CardDemo.Modules.TranReport.Tests;

/// <summary>
/// Wave 2 parity gate: the .NET CBTRN03C port must reproduce both
/// committed TRANREPT.rpt goldens byte-for-byte from the committed
/// oracle input fixtures (dotnet/parity/fixtures/tran-report-inputs).
/// </summary>
public class TranReportParityTests
{
    private const int LineWidth = 133;

    private static TranReportInputs Inputs(string tranFile) => new(
        TranFilePath: ParityPaths.Fixture(tranFile),
        CardXrefUnloadPath: ParityPaths.Fixture("CARDXREF.unload"),
        TranTypeUnloadPath: ParityPaths.Fixture("TRANTYPE.unload"),
        TranCategoryUnloadPath: ParityPaths.Fixture("TRANCATG.unload"),
        DateParmPath: ParityPaths.Fixture("DATEPARM.txt"));

    [Fact]
    public void TranReport_MatchesGolden_ByteExact()
    {
        byte[] golden = File.ReadAllBytes(ParityPaths.Golden("tran-report", "TRANREPT.rpt"));
        byte[] actual = TranReportJob.Run(Inputs("TRANFILE.dat"));

        AssertLinesEqual(golden, actual);
    }

    [Fact]
    public void TranReport_TrailingOutOfWindowRecord_SuppressesGrandTotal()
    {
        // SEM-B02 bug-for-bug: the out-of-window NEXT SENTENCE ends the
        // run, so no page/grand totals follow the last detail line.
        string[] lines = Lines(TranReportJob.Run(Inputs("TRANFILE.dat")));

        Assert.DoesNotContain(lines, l => l.StartsWith("Grand Total", StringComparison.Ordinal));
        Assert.StartsWith("TR", lines[^1], StringComparison.Ordinal);
    }

    [Fact]
    public void TranReportEof_MatchesGolden_ByteExact()
    {
        byte[] golden = File.ReadAllBytes(ParityPaths.Golden("tran-report-eof", "TRANREPT.rpt"));
        byte[] actual = TranReportJob.Run(Inputs("TRANFIL2.dat"));

        AssertLinesEqual(golden, actual);
    }

    [Fact]
    public void TranReportEof_CleanEof_WritesFinalPageAndGrandTotals()
    {
        // SEM-B03 bug-for-bug: clean EOF re-adds the stale last amount,
        // writes the final page total then the grand total, and never
        // flushes the last account total.
        string[] lines = Lines(TranReportJob.Run(Inputs("TRANFIL2.dat")));

        Assert.StartsWith("Grand Total", lines[^1], StringComparison.Ordinal);
        Assert.StartsWith("Page Total", lines[^3], StringComparison.Ordinal);
    }

    private static void AssertLinesEqual(byte[] golden, byte[] actual)
    {
        // Line-indexed comparison first for a readable failure, then
        // the byte-exact gate.
        string[] goldenLines = Lines(golden);
        string[] actualLines = Lines(actual);
        for (int i = 0; i < Math.Min(goldenLines.Length, actualLines.Length); i++)
        {
            Assert.True(
                goldenLines[i] == actualLines[i],
                $"Report line {i + 1} diverges from golden.\n golden: [{goldenLines[i]}]\n actual: [{actualLines[i]}]");
        }

        Assert.Equal(goldenLines.Length, actualLines.Length);
        Assert.Equal(golden, actual);
    }

    private static string[] Lines(byte[] report)
    {
        Assert.Equal(0, report.Length % LineWidth);
        return Enumerable.Range(0, report.Length / LineWidth)
            .Select(i => Encoding.ASCII.GetString(report, i * LineWidth, LineWidth))
            .ToArray();
    }
}
