using CardDemo.Domain.Users;

namespace CardDemo.Application.Users;

/// <summary>
/// Idempotent USRSEC seed importer (seam S01-B5): upserts users keyed by SEC-USR-ID,
/// hashing the seed password. Re-runnable; existing rows are refreshed, not duplicated.
/// </summary>
public class UsrsecImportService(IUserRepository userRepository, IPasswordHashingService passwordHashingService)
{
    public async Task<int> ImportAsync(IEnumerable<string> records, CancellationToken cancellationToken = default)
    {
        var imported = 0;
        foreach (var line in records)
        {
            var record = UsrsecRecordParser.Parse(line);
            var user = new User
            {
                UserId = record.UserId,
                FirstName = record.FirstName,
                LastName = record.LastName,
                PasswordHash = passwordHashingService.Hash(record.Password),
                UserType = UserTypeCodes.FromCode(record.UserType)
            };
            await userRepository.UpsertAsync(user, cancellationToken);
            imported++;
        }
        return imported;
    }
}
