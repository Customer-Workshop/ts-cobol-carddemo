using System.Globalization;
using CardDemo.Batch.InterestCalc;
using CardDemo.Legacy.Decoders.Records;
using CardDemo.Persistence;

if (args.Length != 7)
{
    Console.Error.WriteLine(
        "usage: CardDemo.Batch.InterestCalc <TCATBAL.unload> <ACCTFILE.unload> <DISCGRP.unload> <XREFFILE.unload> <parm-date> <frozen-ts yyyy-MM-dd HH:mm:ss> <out-dir>");
    return 2;
}

var input = new InterestCalcInput(
    CategoryBalances: FixedRecordFile.ReadLineSequential(args[0], TranCatBalRecord.Length)
        .Select(r => TranCatBalRecord.Decode(r)).ToList(),
    Accounts: FixedRecordFile.ReadLineSequential(args[1], AccountRecord.Length)
        .Select(r => AccountRecord.Decode(r)).ToList(),
    DisclosureGroups: FixedRecordFile.ReadLineSequential(args[2], DisclosureGroupRecord.Length)
        .Select(r => DisclosureGroupRecord.Decode(r)).ToList(),
    CardXrefs: FixedRecordFile.ReadLineSequential(args[3], CardXrefRecord.Length)
        .Select(r => CardXrefRecord.Decode(r)).ToList(),
    ParmDate: args[4],
    Clock: () => DateTime.ParseExact(args[5], "yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture));

var result = InterestCalcJob.Run(input);

string outDir = args[6];
Directory.CreateDirectory(outDir);
File.WriteAllBytes(Path.Combine(outDir, "TRANSACT.dat"), UnloadImage.TransactFile(result.Transactions));
File.WriteAllBytes(Path.Combine(outDir, "ACCTFILE.post.unload"), UnloadImage.AccountUnload(result.PostAccounts));
File.WriteAllBytes(Path.Combine(outDir, "TCATBAL.post.unload"), UnloadImage.TranCatBalUnload(result.PostCategoryBalances));
Console.WriteLine($"interest-calc: {result.Transactions.Count} transactions written to {outDir}");
return 0;
