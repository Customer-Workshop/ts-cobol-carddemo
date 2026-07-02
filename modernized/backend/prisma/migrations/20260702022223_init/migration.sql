-- CreateEnum
CREATE TYPE "UserRole" AS ENUM ('USER', 'ADMIN');

-- CreateEnum
CREATE TYPE "JobStatus" AS ENUM ('RUNNING', 'SUCCEEDED', 'FAILED');

-- CreateEnum
CREATE TYPE "DailyTransactionStatus" AS ENUM ('PENDING', 'POSTED', 'REJECTED');

-- CreateTable
CREATE TABLE "users" (
    "id" VARCHAR(9) NOT NULL,
    "firstName" VARCHAR(20) NOT NULL,
    "lastName" VARCHAR(20) NOT NULL,
    "password" TEXT NOT NULL,
    "role" "UserRole" NOT NULL DEFAULT 'USER',
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "users_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "customers" (
    "id" VARCHAR(9) NOT NULL,
    "firstName" VARCHAR(25) NOT NULL,
    "middleName" VARCHAR(25),
    "lastName" VARCHAR(25) NOT NULL,
    "addressLine1" VARCHAR(50) NOT NULL,
    "addressLine2" VARCHAR(50),
    "addressLine3" VARCHAR(50),
    "stateCode" VARCHAR(2) NOT NULL,
    "countryCode" VARCHAR(3) NOT NULL,
    "zipCode" VARCHAR(10) NOT NULL,
    "phoneNumber1" VARCHAR(15),
    "phoneNumber2" VARCHAR(15),
    "ssn" VARCHAR(9) NOT NULL,
    "governmentIssuedId" VARCHAR(20),
    "dateOfBirth" DATE NOT NULL,
    "eftAccountId" VARCHAR(10),
    "primaryCardHolder" BOOLEAN NOT NULL DEFAULT true,
    "ficoCreditScore" INTEGER NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "customers_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "accounts" (
    "id" VARCHAR(11) NOT NULL,
    "activeStatus" BOOLEAN NOT NULL DEFAULT true,
    "currentBalance" DECIMAL(12,2) NOT NULL,
    "creditLimit" DECIMAL(12,2) NOT NULL,
    "cashCreditLimit" DECIMAL(12,2) NOT NULL,
    "openDate" DATE NOT NULL,
    "expirationDate" DATE NOT NULL,
    "reissueDate" DATE,
    "currCycleCredit" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "currCycleDebit" DECIMAL(12,2) NOT NULL DEFAULT 0,
    "addressZip" VARCHAR(10),
    "groupId" VARCHAR(10),
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "accounts_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "cards" (
    "cardNumber" VARCHAR(16) NOT NULL,
    "accountId" VARCHAR(11) NOT NULL,
    "cvv" VARCHAR(3) NOT NULL,
    "embossedName" VARCHAR(50) NOT NULL,
    "expiryDate" DATE NOT NULL,
    "activeStatus" BOOLEAN NOT NULL DEFAULT true,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "updatedAt" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "cards_pkey" PRIMARY KEY ("cardNumber")
);

-- CreateTable
CREATE TABLE "card_xref" (
    "cardNumber" VARCHAR(16) NOT NULL,
    "customerId" VARCHAR(9) NOT NULL,
    "accountId" VARCHAR(11) NOT NULL,

    CONSTRAINT "card_xref_pkey" PRIMARY KEY ("cardNumber")
);

-- CreateTable
CREATE TABLE "transaction_types" (
    "code" VARCHAR(2) NOT NULL,
    "description" VARCHAR(50) NOT NULL,

    CONSTRAINT "transaction_types_pkey" PRIMARY KEY ("code")
);

-- CreateTable
CREATE TABLE "transaction_categories" (
    "typeCode" VARCHAR(2) NOT NULL,
    "categoryCode" INTEGER NOT NULL,
    "description" VARCHAR(50) NOT NULL,

    CONSTRAINT "transaction_categories_pkey" PRIMARY KEY ("typeCode","categoryCode")
);

-- CreateTable
CREATE TABLE "transactions" (
    "id" VARCHAR(16) NOT NULL,
    "typeCode" VARCHAR(2) NOT NULL,
    "categoryCode" INTEGER NOT NULL,
    "source" VARCHAR(10) NOT NULL,
    "description" VARCHAR(100) NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "merchantId" VARCHAR(9) NOT NULL,
    "merchantName" VARCHAR(50) NOT NULL,
    "merchantCity" VARCHAR(50) NOT NULL,
    "merchantZip" VARCHAR(10) NOT NULL,
    "cardNumber" VARCHAR(16) NOT NULL,
    "originalTs" TIMESTAMP(3) NOT NULL,
    "processedTs" TIMESTAMP(3) NOT NULL,

    CONSTRAINT "transactions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "transaction_category_balances" (
    "accountId" VARCHAR(11) NOT NULL,
    "typeCode" VARCHAR(2) NOT NULL,
    "categoryCode" INTEGER NOT NULL,
    "balance" DECIMAL(12,2) NOT NULL DEFAULT 0,

    CONSTRAINT "transaction_category_balances_pkey" PRIMARY KEY ("accountId","typeCode","categoryCode")
);

-- CreateTable
CREATE TABLE "disclosure_groups" (
    "accountGroupId" VARCHAR(10) NOT NULL,
    "typeCode" VARCHAR(2) NOT NULL,
    "categoryCode" INTEGER NOT NULL,
    "interestRate" DECIMAL(6,2) NOT NULL,

    CONSTRAINT "disclosure_groups_pkey" PRIMARY KEY ("accountGroupId","typeCode","categoryCode")
);

-- CreateTable
CREATE TABLE "daily_transactions" (
    "id" VARCHAR(16) NOT NULL,
    "typeCode" VARCHAR(2) NOT NULL,
    "categoryCode" INTEGER NOT NULL,
    "source" VARCHAR(10) NOT NULL,
    "description" VARCHAR(100) NOT NULL,
    "amount" DECIMAL(12,2) NOT NULL,
    "merchantId" VARCHAR(9) NOT NULL,
    "merchantName" VARCHAR(50) NOT NULL,
    "merchantCity" VARCHAR(50) NOT NULL,
    "merchantZip" VARCHAR(10) NOT NULL,
    "cardNumber" VARCHAR(16) NOT NULL,
    "originalTs" TIMESTAMP(3) NOT NULL,
    "processedTs" TIMESTAMP(3),
    "status" "DailyTransactionStatus" NOT NULL DEFAULT 'PENDING',

    CONSTRAINT "daily_transactions_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "daily_rejects" (
    "id" SERIAL NOT NULL,
    "dailyTransactionId" VARCHAR(16) NOT NULL,
    "rejectReason" VARCHAR(80) NOT NULL,
    "rejectedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "daily_rejects_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "statements" (
    "id" SERIAL NOT NULL,
    "accountId" VARCHAR(11) NOT NULL,
    "version" INTEGER NOT NULL,
    "periodStart" DATE NOT NULL,
    "periodEnd" DATE NOT NULL,
    "textContent" TEXT NOT NULL,
    "htmlContent" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "statements_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "reports" (
    "id" SERIAL NOT NULL,
    "name" VARCHAR(50) NOT NULL,
    "version" INTEGER NOT NULL,
    "startDate" DATE NOT NULL,
    "endDate" DATE NOT NULL,
    "content" TEXT NOT NULL,
    "createdAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT "reports_pkey" PRIMARY KEY ("id")
);

-- CreateTable
CREATE TABLE "job_runs" (
    "id" SERIAL NOT NULL,
    "jobName" VARCHAR(50) NOT NULL,
    "status" "JobStatus" NOT NULL DEFAULT 'RUNNING',
    "startedAt" TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    "completedAt" TIMESTAMP(3),
    "message" TEXT,

    CONSTRAINT "job_runs_pkey" PRIMARY KEY ("id")
);

-- CreateIndex
CREATE INDEX "cards_accountId_idx" ON "cards"("accountId");

-- CreateIndex
CREATE INDEX "card_xref_customerId_idx" ON "card_xref"("customerId");

-- CreateIndex
CREATE INDEX "card_xref_accountId_idx" ON "card_xref"("accountId");

-- CreateIndex
CREATE INDEX "transactions_cardNumber_idx" ON "transactions"("cardNumber");

-- CreateIndex
CREATE INDEX "transactions_processedTs_idx" ON "transactions"("processedTs");

-- CreateIndex
CREATE INDEX "daily_transactions_status_idx" ON "daily_transactions"("status");

-- CreateIndex
CREATE UNIQUE INDEX "daily_rejects_dailyTransactionId_key" ON "daily_rejects"("dailyTransactionId");

-- CreateIndex
CREATE UNIQUE INDEX "statements_accountId_version_key" ON "statements"("accountId", "version");

-- CreateIndex
CREATE UNIQUE INDEX "reports_name_version_key" ON "reports"("name", "version");

-- CreateIndex
CREATE INDEX "job_runs_jobName_startedAt_idx" ON "job_runs"("jobName", "startedAt");

-- AddForeignKey
ALTER TABLE "cards" ADD CONSTRAINT "cards_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "accounts"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "card_xref" ADD CONSTRAINT "card_xref_cardNumber_fkey" FOREIGN KEY ("cardNumber") REFERENCES "cards"("cardNumber") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "card_xref" ADD CONSTRAINT "card_xref_customerId_fkey" FOREIGN KEY ("customerId") REFERENCES "customers"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "card_xref" ADD CONSTRAINT "card_xref_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "accounts"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transaction_categories" ADD CONSTRAINT "transaction_categories_typeCode_fkey" FOREIGN KEY ("typeCode") REFERENCES "transaction_types"("code") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transactions" ADD CONSTRAINT "transactions_typeCode_fkey" FOREIGN KEY ("typeCode") REFERENCES "transaction_types"("code") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transactions" ADD CONSTRAINT "transactions_typeCode_categoryCode_fkey" FOREIGN KEY ("typeCode", "categoryCode") REFERENCES "transaction_categories"("typeCode", "categoryCode") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transaction_category_balances" ADD CONSTRAINT "transaction_category_balances_accountId_fkey" FOREIGN KEY ("accountId") REFERENCES "accounts"("id") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "transaction_category_balances" ADD CONSTRAINT "transaction_category_balances_typeCode_categoryCode_fkey" FOREIGN KEY ("typeCode", "categoryCode") REFERENCES "transaction_categories"("typeCode", "categoryCode") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "disclosure_groups" ADD CONSTRAINT "disclosure_groups_typeCode_fkey" FOREIGN KEY ("typeCode") REFERENCES "transaction_types"("code") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "disclosure_groups" ADD CONSTRAINT "disclosure_groups_typeCode_categoryCode_fkey" FOREIGN KEY ("typeCode", "categoryCode") REFERENCES "transaction_categories"("typeCode", "categoryCode") ON DELETE RESTRICT ON UPDATE CASCADE;

-- AddForeignKey
ALTER TABLE "daily_rejects" ADD CONSTRAINT "daily_rejects_dailyTransactionId_fkey" FOREIGN KEY ("dailyTransactionId") REFERENCES "daily_transactions"("id") ON DELETE RESTRICT ON UPDATE CASCADE;
