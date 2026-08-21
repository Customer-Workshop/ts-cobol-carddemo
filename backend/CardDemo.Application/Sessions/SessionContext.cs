namespace CardDemo.Application.Sessions;

/// <summary>
/// Port of the CARDDEMO-COMMAREA general-info section (app/cpy/COCOM01Y.cpy), seam S01-B6:
/// user identity/type plus program navigation context. Consumed as JWT claims + route state
/// by later waves; no endpoint issues it yet.
/// </summary>
public record SessionContext(
    string UserId,
    char UserType,
    string? FromProgram = null,
    string? ToProgram = null)
{
    public bool IsAdmin => UserType == 'A';
}
