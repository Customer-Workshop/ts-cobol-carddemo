namespace CardDemo.Tests;

public static class TestPaths
{
    public static string UsrsecSeedJcl => Path.Combine(RepoRoot, "app", "jcl", "DUSRSECJ.jcl");

    public static string AsciiData(string fileName) => Path.Combine(RepoRoot, "app", "data", "ASCII", fileName);

    public static string RepoRoot
    {
        get
        {
            var dir = new DirectoryInfo(AppContext.BaseDirectory);
            while (dir is not null && !File.Exists(Path.Combine(dir.FullName, "app", "jcl", "DUSRSECJ.jcl")))
            {
                dir = dir.Parent!;
            }
            return dir?.FullName ?? throw new InvalidOperationException("Repo root with app/jcl/DUSRSECJ.jcl not found.");
        }
    }
}
