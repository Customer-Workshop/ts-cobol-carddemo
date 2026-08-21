using System.IdentityModel.Tokens.Jwt;
using CardDemo.Application.Sessions;
using CardDemo.Infrastructure.Security;
using FluentAssertions;
using Microsoft.Extensions.Options;

namespace CardDemo.Tests.Sessions;

public class SessionContextTests
{
    [Fact]
    public void JwtIssuer_EmbedsSessionContextClaims()
    {
        var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions
        {
            SigningKey = "unit-test-signing-key-with-enough-length-000000"
        }));
        var session = new SessionContext("ADMIN001", 'A', FromProgram: "COSGN00C", ToProgram: "COADM01C");

        var token = new JwtSecurityTokenHandler().ReadJwtToken(issuer.Issue(session));

        token.Claims.Single(c => c.Type == JwtTokenIssuer.UserIdClaim).Value.Should().Be("ADMIN001");
        token.Claims.Single(c => c.Type == JwtTokenIssuer.UserTypeClaim).Value.Should().Be("A");
        token.Claims.Single(c => c.Type == JwtTokenIssuer.FromProgramClaim).Value.Should().Be("COSGN00C");
        token.Claims.Single(c => c.Type == JwtTokenIssuer.ToProgramClaim).Value.Should().Be("COADM01C");
    }

    [Fact]
    public void JwtIssuer_ThrowsWhenSigningKeyMissing()
    {
        var issuer = new JwtTokenIssuer(Options.Create(new JwtOptions()));

        var act = () => issuer.Issue(new SessionContext("USER0001", 'U'));

        act.Should().Throw<InvalidOperationException>();
    }

    [Fact]
    public void SessionContext_AdminFlagMirrorsCdemoUsrtypAdmin()
    {
        new SessionContext("ADMIN001", 'A').IsAdmin.Should().BeTrue();
        new SessionContext("USER0001", 'U').IsAdmin.Should().BeFalse();
    }
}
