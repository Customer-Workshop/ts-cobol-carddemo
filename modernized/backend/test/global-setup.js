// Prepares the e2e database: applies migrations and re-seeds (the seed is
// idempotent). Requires DATABASE_URL to point at a running PostgreSQL
// (docker compose locally, service container in CI).
const { execSync } = require('node:child_process');

module.exports = async () => {
  const cwd = __dirname + '/..';
  execSync('npx prisma migrate deploy', { cwd, stdio: 'inherit' });
  execSync('npx prisma db seed', { cwd, stdio: 'inherit' });
};
