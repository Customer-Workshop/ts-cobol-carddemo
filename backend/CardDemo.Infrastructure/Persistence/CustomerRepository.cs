using CardDemo.Application.Customers;
using CardDemo.Domain.Customers;
using Microsoft.EntityFrameworkCore;

namespace CardDemo.Infrastructure.Persistence;

public class CustomerRepository(CardDemoDbContext dbContext) : ICustomerRepository
{
    public Task<Customer?> GetByIdAsync(string customerId, CancellationToken cancellationToken = default) =>
        dbContext.Customers.AsNoTracking().SingleOrDefaultAsync(c => c.CustomerId == customerId, cancellationToken);
}
