// CardDemo.Batch: host for ported batch programs. Wave sessions add
// one verb per ported program (e.g. `interest-calc`); wave 0 ships the
// skeleton only so parallel waves never edit shared files.
if (args.Length == 0)
{
    Console.Error.WriteLine("usage: carddemo-batch <job> [args...]  (no jobs ported yet - see dotnet/docs/migration/02-waves.md)");
    return 2;
}

Console.Error.WriteLine($"unknown job '{args[0]}'");
return 2;
