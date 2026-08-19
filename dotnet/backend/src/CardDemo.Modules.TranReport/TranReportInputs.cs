namespace CardDemo.Modules.TranReport;

/// <summary>
/// File inputs of the TRANREPT job (JCL DD names in parentheses):
/// 350-byte record-sequential transactions (TRANFILE), keyed-order
/// unloads of the three lookup KSDS files (CARDXREF, TRANTYPE,
/// TRANCATG - the coexistence seam, never raw ISAM bytes), and the
/// 80-byte date window parm record (DATEPARM).
/// </summary>
public sealed record TranReportInputs(
    string TranFilePath,
    string CardXrefUnloadPath,
    string TranTypeUnloadPath,
    string TranCategoryUnloadPath,
    string DateParmPath);
