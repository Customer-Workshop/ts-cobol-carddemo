using CardDemo.Application.LegacyData;

namespace CardDemo.Tests.Transactions;

internal static class LegacySeed
{
    public static LegacyDataSeedPaths Paths => new()
    {
        AccountPath = TestPaths.AsciiData("acctdata.txt"),
        CardPath = TestPaths.AsciiData("carddata.txt"),
        CardXrefPath = TestPaths.AsciiData("cardxref.txt"),
        CustomerPath = TestPaths.AsciiData("custdata.txt"),
        TransactionPath = TestPaths.AsciiData("dailytran.txt"),
        TransactionCategoryBalancePath = TestPaths.AsciiData("tcatbal.txt"),
        DisclosureGroupPath = TestPaths.AsciiData("discgrp.txt"),
        TransactionTypePath = TestPaths.AsciiData("trantype.txt"),
        TransactionCategoryPath = TestPaths.AsciiData("trancatg.txt")
    };
}
