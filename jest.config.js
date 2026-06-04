/** @type {import('jest').Config} */
module.exports = {
  preset: 'ts-jest',
  testEnvironment: 'node',
  roots: ['<rootDir>/tests'],
  testMatch: ['**/*.test.ts'],
  collectCoverageFrom: ['tests/models/**/*.ts', 'tests/validators/**/*.ts'],
  coverageDirectory: 'coverage',
  verbose: true,
};
