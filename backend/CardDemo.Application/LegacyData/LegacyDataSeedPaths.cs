namespace CardDemo.Application.LegacyData;

/// <summary>
/// Locations of the app/data/ASCII fixed-width exports, bound from the "Seed:LegacyData" configuration
/// section. A null/blank or non-existent path skips that dataset.
/// </summary>
public class LegacyDataSeedPaths
{
    public const string SectionName = "Seed:LegacyData";

    public string? AccountPath { get; set; }
    public string? CardPath { get; set; }
    public string? CardXrefPath { get; set; }
    public string? CustomerPath { get; set; }
    public string? TransactionPath { get; set; }
    public string? TransactionCategoryBalancePath { get; set; }
    public string? DisclosureGroupPath { get; set; }
    public string? TransactionTypePath { get; set; }
    public string? TransactionCategoryPath { get; set; }
}
