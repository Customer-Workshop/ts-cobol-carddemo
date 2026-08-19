namespace CardDemo.Parity.Tests;

internal static class GoldenPaths
{
    internal static string Root { get; } = Locate();

    private static string Locate()
    {
        var dir = new DirectoryInfo(AppContext.BaseDirectory);
        while (dir is not null)
        {
            string candidate = Path.Combine(dir.FullName, "dotnet", "parity", "golden");
            if (Directory.Exists(candidate))
            {
                return candidate;
            }

            dir = dir.Parent;
        }

        throw new DirectoryNotFoundException("dotnet/parity/golden not found above test directory.");
    }

    internal static string Scenario(string scenario, string file) =>
        Path.Combine(Root, scenario, file);
}
