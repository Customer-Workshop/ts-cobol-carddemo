namespace CardDemo.Application.Sessions;

public interface IJwtTokenIssuer
{
    string Issue(SessionContext session);
}
