using CardDemo.Application.Users;
using FluentAssertions;

namespace CardDemo.Tests.Users;

public class UsrsecRecordParserTests
{
    [Fact]
    public void Parse_SlicesFixedWidthFieldsAndTrimsTrailingSpaces()
    {
        var line = "ADMIN001MARGARET            GOLD                PASSWORDA";

        var record = UsrsecRecordParser.Parse(line);

        record.Should().Be(new UsrsecRecord("ADMIN001", "MARGARET", "GOLD", "PASSWORD", 'A'));
    }

    [Fact]
    public void Parse_ReadsUserTypeU()
    {
        var line = "USER0001LAWRENCE            THOMAS              PASSWORDU";

        UsrsecRecordParser.Parse(line).UserType.Should().Be('U');
    }

    [Fact]
    public void Parse_RejectsRecordsLongerThan80Bytes()
    {
        var act = () => UsrsecRecordParser.Parse(new string('X', 81));

        act.Should().Throw<FormatException>();
    }

    [Fact]
    public void ExtractJclInstreamRecords_ReadsAllSeedRecordsFromDusrsecj()
    {
        var records = UsrsecSeedSource.ReadRecords(TestPaths.UsrsecSeedJcl);

        records.Should().HaveCount(10);
        records.Select(r => UsrsecRecordParser.Parse(r).UserId)
            .Should().OnlyHaveUniqueItems()
            .And.Contain(["ADMIN001", "USER0001", "USER0005"]);
        records.Select(r => UsrsecRecordParser.Parse(r).UserType)
            .Should().OnlyContain(t => t == 'A' || t == 'U');
    }
}
