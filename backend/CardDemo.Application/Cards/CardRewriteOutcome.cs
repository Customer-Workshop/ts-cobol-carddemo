namespace CardDemo.Application.Cards;

/// <summary>Result of the CICS READ UPDATE / REWRITE pair on CARDDAT.</summary>
public enum CardRewriteOutcome
{
    /// <summary>READ UPDATE did not return the record (lock failed).</summary>
    NotFound,

    /// <summary>Record locked but the caller declined to rewrite it.</summary>
    Skipped,

    Rewritten
}
