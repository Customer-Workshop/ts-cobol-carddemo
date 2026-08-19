namespace CardDemo.Persistence;

/// <summary>
/// Reader/writer for fixed-length record files (the coexistence seam:
/// keyed-order unloads and sequential batch outputs produced by the
/// parity oracle). Records are raw bytes; newline-delimited variants
/// cover GnuCOBOL LINE SEQUENTIAL unloads.
/// </summary>
public static class FixedRecordFile
{
    /// <summary>Reads fixed-length records from a record-sequential file (no delimiters).</summary>
    public static IEnumerable<byte[]> ReadFixed(string path, int recordLength)
    {
        using var stream = File.OpenRead(path);
        var buffer = new byte[recordLength];
        while (true)
        {
            int read = stream.ReadAtLeast(buffer, recordLength, throwOnEndOfStream: false);
            if (read == 0)
            {
                yield break;
            }

            if (read != recordLength)
            {
                throw new InvalidDataException($"Short record ({read} of {recordLength} bytes) in {path}.");
            }

            yield return (byte[])buffer.Clone();
        }
    }

    /// <summary>Reads fixed-length records from a LINE SEQUENTIAL unload (LF-delimited).</summary>
    public static IEnumerable<byte[]> ReadLineSequential(string path, int recordLength)
    {
        foreach (string line in File.ReadLines(path))
        {
            byte[] record = System.Text.Encoding.ASCII.GetBytes(line.PadRight(recordLength));
            if (record.Length != recordLength)
            {
                throw new InvalidDataException($"Record of {record.Length} bytes exceeds {recordLength} in {path}.");
            }

            yield return record;
        }
    }
}
