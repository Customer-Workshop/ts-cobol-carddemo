namespace CardDemo.Modules.TranReport.Tests;

internal static class ParityPaths
{
    internal static string Root { get; } = Locate();

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
        Path.Combine(Root, "golden", scenario, file);

    internal static string Fixture(string file) =>
        Path.Combine(Root, "fixtures", "tran-report-inputs", file);
}
