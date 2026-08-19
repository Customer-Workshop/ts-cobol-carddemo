using System.Text;

namespace CardDemo.Legacy.Decoders;

/// <summary>Helpers for PIC X alphanumeric fields (space padded, ASCII).</summary>
public static class FieldCodec
{
    public static string DecodeText(ReadOnlySpan<byte> field) =>
        Encoding.ASCII.GetString(field);

    /// <summary>Encodes text into a PIC X(n) field, right-padded with spaces (group MOVE semantics).</summary>
    public static void EncodeText(string value, Span<byte> destination)
    {
        int written = Encoding.ASCII.GetBytes(value.AsSpan(0, Math.Min(value.Length, destination.Length)), destination);
        destination[written..].Fill((byte)' ');
    }
}
