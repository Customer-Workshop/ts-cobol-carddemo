using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using CardDemo.Application.Sessions;
using Microsoft.Extensions.Options;
using Microsoft.IdentityModel.Tokens;

namespace CardDemo.Infrastructure.Security;

/// <summary>
/// COMMAREA-replacement plumbing (seam S01-B6): issues a JWT carrying the SessionContext
/// claims. Config-driven symmetric signing key for dev; no auth endpoint consumes it yet.
/// </summary>
public class JwtTokenIssuer(IOptions<JwtOptions> options) : IJwtTokenIssuer
{
    public const string UserIdClaim = "userId";
    public const string UserTypeClaim = "userType";
    public const string FromProgramClaim = "fromProgram";
    public const string ToProgramClaim = "toProgram";

    private readonly JwtOptions _options = options.Value;

    public string Issue(SessionContext session)
    {
        if (string.IsNullOrWhiteSpace(_options.SigningKey))
        {
            throw new InvalidOperationException("Jwt:SigningKey is not configured.");
        }

        var claims = new List<Claim>
        {
            new(UserIdClaim, session.UserId),
            new(UserTypeClaim, session.UserType.ToString())
        };
        if (session.FromProgram is not null)
        {
            claims.Add(new Claim(FromProgramClaim, session.FromProgram));
        }
        if (session.ToProgram is not null)
        {
            claims.Add(new Claim(ToProgramClaim, session.ToProgram));
        }

        var credentials = new SigningCredentials(
            new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_options.SigningKey)),
            SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: _options.Issuer,
            audience: _options.Audience,
            claims: claims,
            notBefore: DateTime.UtcNow,
            expires: DateTime.UtcNow.AddMinutes(_options.ExpiryMinutes),
            signingCredentials: credentials);

        return new JwtSecurityTokenHandler().WriteToken(token);
    }
}
