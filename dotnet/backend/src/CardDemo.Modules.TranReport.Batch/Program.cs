// TRANREPT job host (CBTRN03C port). Reads the same DD-name
// environment mapping the parity harness uses (see
// dotnet/parity/scripts/common.sh), with the lookup DDs pointing at
// keyed-order unloads rather than ISAM files.
using CardDemo.Modules.TranReport;

static string Dd(string name) =>
    Environment.GetEnvironmentVariable(name)
    ?? throw new InvalidOperationException($"Missing DD environment variable {name}.");

try
{
    var inputs = new TranReportInputs(
        TranFilePath: Dd("DD_TRANFILE"),
        CardXrefUnloadPath: Dd("DD_CARDXREF"),
        TranTypeUnloadPath: Dd("DD_TRANTYPE"),
        TranCategoryUnloadPath: Dd("DD_TRANCATG"),
        DateParmPath: Dd("DD_DATEPARM"));
    byte[] report = TranReportJob.Run(inputs);
    File.WriteAllBytes(Dd("DD_TRANREPT"), report);
    Console.Error.WriteLine($"TRANREPT: wrote {report.Length / 133} report records");
    return 0;
}
catch (Exception ex) when (ex is IOException or InvalidDataException or InvalidOperationException)
{
    Console.Error.WriteLine($"TRANREPT ABEND: {ex.Message}");
    return 99;
}
