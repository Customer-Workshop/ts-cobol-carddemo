namespace CardDemo.Batch.InterestCalc.Tests;

internal static class ParityPaths
{
    internal static string ParityRoot { get; } = Locate();

    private static string Locate()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            string candidate = Path.Combine(dir.FullName, "dotnet", "parity");
            if (Directory.Exists(candidate))
            {
                return candidate;
            }

            dir = dir.Parent;
        }

        throw new DirectoryNotFoundException("dotnet/parity not found above test directory.");
    }

    internal static string Golden(string scenario, string file) =>
        Path.Combine(ParityRoot, "golden", scenario, file);

    internal static string Fixture(string fixture, string file) =>
        Path.Combine(ParityRoot, "fixtures", fixture, file);
}
