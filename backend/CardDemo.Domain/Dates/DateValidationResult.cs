using System.Globalization;

namespace CardDemo.Domain.Dates;

/// <summary>
/// Port of CSUTLDTC's 80-byte LS-RESULT (app/cbl/CSUTLDTC.cbl:42-57): the CEEDAYS feedback
/// severity and message number plus the 15-character verdict. <see cref="Severity"/> is also the
/// program's RETURN-CODE (CSUTLDTC.cbl:98).
/// </summary>
public record DateValidationResult(
    int Severity,
    int MessageNumber,
    string Verdict,
    string DateText,
    string Mask,
    DateOnly? Date)
{
    public const int ValidMessage = 0;
    public const int InsufficientDataMessage = 2507;
    public const int BadDateValueMessage = 2508;
    public const int InvalidEraMessage = 2509;
    public const int UnsupportedRangeMessage = 2513;
    public const int InvalidMonthMessage = 2517;
    public const int BadPictureStringMessage = 2518;
    public const int NonNumericDataMessage = 2520;
    public const int YearInEraZeroMessage = 2521;

    /// <summary>Severity '0000' — the only condition COTRN02C treats as a clean pass (COTRN02C.cbl:397).</summary>
    public bool IsValid => Severity == 0;

    public int ReturnCode => Severity;

    /// <summary>The 80-byte WS-MESSAGE image: severity, 'Mesg Code:', number, verdict, 'TstDate:', date, 'Mask used:', mask.</summary>
    public string ResultText =>
        Severity.ToString("0000", CultureInfo.InvariantCulture)
        + "Mesg Code: "
        + MessageNumber.ToString("0000", CultureInfo.InvariantCulture)
        + " "
        + Verdict.PadRight(15)[..15]
        + " "
        + "TstDate: "
        + DateText.PadRight(10)[..10]
        + " "
        + "Mask used:"
        + Mask.PadRight(10)[..10]
        + "    ";
}
