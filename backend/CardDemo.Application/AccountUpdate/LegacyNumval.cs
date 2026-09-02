using System.Globalization;
using System.Text.RegularExpressions;

namespace CardDemo.Application.AccountUpdate;

/// <summary>
/// FUNCTION TEST-NUMVAL-C / NUMVAL-C acceptance used by 1100-RECEIVE-MAP and 1250-EDIT-SIGNED-9V2
/// (app/cbl/COACTUPC.cbl:1073-1160, :2180-2221): optional leading or trailing sign (or trailing CR/DB),
/// optional currency symbol, digits with optional thousands separators and decimal point.
/// The result lands in PIC S9(10)V99, so extra decimals and high-order digits are truncated as COMPUTE does.
/// </summary>
public static partial class LegacyNumval
{
    [GeneratedRegex(@"^\s*(?:(?<lsign>[+-])\s*)?(?:\$\s*)?(?<int>\d{1,3}(?:,\d{3})+|\d+)?(?:\.(?<frac>\d*))?\s*(?:(?<tsign>[+-])|(?<crdb>CR|DB))?\s*$", RegexOptions.IgnoreCase)]
    private static partial Regex NumvalC();

    private const decimal IntegerModulus = 10_000_000_000m;

    public static bool TryParseSigned9V2(string text, out decimal value)
    {
        value = 0m;
        var match = NumvalC().Match(text);
        if (!match.Success)
        {
            return false;
        }
        var integerPart = match.Groups["int"].Value.Replace(",", "");
        var fraction = match.Groups["frac"].Value;
        if (integerPart.Length == 0 && fraction.Length == 0)
        {
            return false;
        }
        if (match.Groups["lsign"].Success && (match.Groups["tsign"].Success || match.Groups["crdb"].Success))
        {
            return false;
        }

        var magnitude = decimal.Parse(
            (integerPart.Length == 0 ? "0" : integerPart) + "." + (fraction.Length == 0 ? "0" : fraction),
            CultureInfo.InvariantCulture);
        var negative = match.Groups["lsign"].Value == "-"
            || match.Groups["tsign"].Value == "-"
            || match.Groups["crdb"].Success;

        var truncated = decimal.Truncate(magnitude * 100m) / 100m;
        var integerDigits = decimal.Truncate(truncated);
        truncated = (integerDigits % IntegerModulus) + (truncated - integerDigits);
        value = negative ? -truncated : truncated;
        return true;
    }
}
