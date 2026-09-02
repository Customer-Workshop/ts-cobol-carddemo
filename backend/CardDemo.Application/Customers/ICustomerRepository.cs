using CardDemo.Domain.Customers;

namespace CardDemo.Application.Customers;

/// <summary>Keyed read parity with CUSTDAT KSDS (KEYS(9 0) = CUST-ID).</summary>
public interface ICustomerRepository
{
    Task<Customer?> GetByIdAsync(string customerId, CancellationToken cancellationToken = default);
}
