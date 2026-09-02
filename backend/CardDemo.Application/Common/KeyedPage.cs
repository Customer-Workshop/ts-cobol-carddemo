namespace CardDemo.Application.Common;

/// <summary>
/// One screen of a VSAM-style forward browse (STARTBR key GTEQ + READNEXT × page size):
/// records in key order starting at or after the requested key, plus whether READNEXT would find more.
/// </summary>
public record KeyedPage<T>(IReadOnlyList<T> Items, bool HasMore);
