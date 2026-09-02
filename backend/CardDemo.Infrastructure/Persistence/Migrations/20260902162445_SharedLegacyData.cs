using System;
using Microsoft.EntityFrameworkCore.Migrations;

#nullable disable

namespace CardDemo.Infrastructure.Persistence.Migrations
{
    /// <inheritdoc />
    public partial class SharedLegacyData : Migration
    {
        /// <inheritdoc />
        protected override void Up(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.CreateTable(
                name: "accounts",
                columns: table => new
                {
                    acct_id = table.Column<string>(type: "character varying(11)", maxLength: 11, nullable: false, collation: "C"),
                    acct_active_status = table.Column<string>(type: "character varying(1)", maxLength: 1, nullable: false),
                    acct_curr_bal = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    acct_credit_limit = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    acct_cash_credit_limit = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    acct_open_date = table.Column<DateOnly>(type: "date", nullable: true),
                    acct_expiration_date = table.Column<DateOnly>(type: "date", nullable: true),
                    acct_reissue_date = table.Column<DateOnly>(type: "date", nullable: true),
                    acct_curr_cyc_credit = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    acct_curr_cyc_debit = table.Column<decimal>(type: "numeric(12,2)", precision: 12, scale: 2, nullable: false),
                    acct_addr_zip = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false),
                    acct_group_id = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_accounts", x => x.acct_id);
                });

            migrationBuilder.CreateTable(
                name: "card_xref",
                columns: table => new
                {
                    xref_card_num = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false, collation: "C"),
                    xref_cust_id = table.Column<string>(type: "character varying(9)", maxLength: 9, nullable: false, collation: "C"),
                    xref_acct_id = table.Column<string>(type: "character varying(11)", maxLength: 11, nullable: false, collation: "C")
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_card_xref", x => x.xref_card_num);
                });

            migrationBuilder.CreateTable(
                name: "cards",
                columns: table => new
                {
                    card_num = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false, collation: "C"),
                    card_acct_id = table.Column<string>(type: "character varying(11)", maxLength: 11, nullable: false, collation: "C"),
                    card_cvv_cd = table.Column<string>(type: "character varying(3)", maxLength: 3, nullable: false),
                    card_embossed_name = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    card_expiration_date = table.Column<DateOnly>(type: "date", nullable: true),
                    card_active_status = table.Column<string>(type: "character varying(1)", maxLength: 1, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_cards", x => x.card_num);
                });

            migrationBuilder.CreateTable(
                name: "customers",
                columns: table => new
                {
                    cust_id = table.Column<string>(type: "character varying(9)", maxLength: 9, nullable: false, collation: "C"),
                    cust_first_name = table.Column<string>(type: "character varying(25)", maxLength: 25, nullable: false),
                    cust_middle_name = table.Column<string>(type: "character varying(25)", maxLength: 25, nullable: false),
                    cust_last_name = table.Column<string>(type: "character varying(25)", maxLength: 25, nullable: false),
                    cust_addr_line_1 = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    cust_addr_line_2 = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    cust_addr_line_3 = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    cust_addr_state_cd = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false),
                    cust_addr_country_cd = table.Column<string>(type: "character varying(3)", maxLength: 3, nullable: false),
                    cust_addr_zip = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false),
                    cust_phone_num_1 = table.Column<string>(type: "character varying(15)", maxLength: 15, nullable: false),
                    cust_phone_num_2 = table.Column<string>(type: "character varying(15)", maxLength: 15, nullable: false),
                    cust_ssn = table.Column<string>(type: "character varying(9)", maxLength: 9, nullable: false),
                    cust_govt_issued_id = table.Column<string>(type: "character varying(20)", maxLength: 20, nullable: false),
                    cust_dob = table.Column<DateOnly>(type: "date", nullable: true),
                    cust_eft_account_id = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false),
                    cust_pri_card_holder_ind = table.Column<string>(type: "character varying(1)", maxLength: 1, nullable: false),
                    cust_fico_credit_score = table.Column<int>(type: "integer", nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_customers", x => x.cust_id);
                });

            migrationBuilder.CreateTable(
                name: "disclosure_groups",
                columns: table => new
                {
                    dis_acct_group_id = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false, collation: "C"),
                    dis_tran_type_cd = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false, collation: "C"),
                    dis_tran_cat_cd = table.Column<string>(type: "character varying(4)", maxLength: 4, nullable: false, collation: "C"),
                    dis_int_rate = table.Column<decimal>(type: "numeric(6,2)", precision: 6, scale: 2, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_disclosure_groups", x => new { x.dis_acct_group_id, x.dis_tran_type_cd, x.dis_tran_cat_cd });
                });

            migrationBuilder.CreateTable(
                name: "transaction_categories",
                columns: table => new
                {
                    tran_type_cd = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false, collation: "C"),
                    tran_cat_cd = table.Column<string>(type: "character varying(4)", maxLength: 4, nullable: false, collation: "C"),
                    tran_cat_type_desc = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_transaction_categories", x => new { x.tran_type_cd, x.tran_cat_cd });
                });

            migrationBuilder.CreateTable(
                name: "transaction_category_balances",
                columns: table => new
                {
                    trancat_acct_id = table.Column<string>(type: "character varying(11)", maxLength: 11, nullable: false, collation: "C"),
                    trancat_type_cd = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false, collation: "C"),
                    trancat_cd = table.Column<string>(type: "character varying(4)", maxLength: 4, nullable: false, collation: "C"),
                    tran_cat_bal = table.Column<decimal>(type: "numeric(11,2)", precision: 11, scale: 2, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_transaction_category_balances", x => new { x.trancat_acct_id, x.trancat_type_cd, x.trancat_cd });
                });

            migrationBuilder.CreateTable(
                name: "transaction_types",
                columns: table => new
                {
                    tran_type = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false, collation: "C"),
                    tran_type_desc = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_transaction_types", x => x.tran_type);
                });

            migrationBuilder.CreateTable(
                name: "transactions",
                columns: table => new
                {
                    tran_id = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false, collation: "C"),
                    tran_type_cd = table.Column<string>(type: "character varying(2)", maxLength: 2, nullable: false, collation: "C"),
                    tran_cat_cd = table.Column<string>(type: "character varying(4)", maxLength: 4, nullable: false, collation: "C"),
                    tran_source = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false),
                    tran_desc = table.Column<string>(type: "character varying(100)", maxLength: 100, nullable: false),
                    tran_amt = table.Column<decimal>(type: "numeric(11,2)", precision: 11, scale: 2, nullable: false),
                    tran_merchant_id = table.Column<string>(type: "character varying(9)", maxLength: 9, nullable: false),
                    tran_merchant_name = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    tran_merchant_city = table.Column<string>(type: "character varying(50)", maxLength: 50, nullable: false),
                    tran_merchant_zip = table.Column<string>(type: "character varying(10)", maxLength: 10, nullable: false),
                    tran_card_num = table.Column<string>(type: "character varying(16)", maxLength: 16, nullable: false, collation: "C"),
                    tran_orig_ts = table.Column<DateTime>(type: "timestamp without time zone", nullable: true),
                    tran_proc_ts = table.Column<DateTime>(type: "timestamp without time zone", nullable: true)
                },
                constraints: table =>
                {
                    table.PrimaryKey("PK_transactions", x => x.tran_id);
                });

            migrationBuilder.CreateIndex(
                name: "ix_card_xref_xref_acct_id",
                table: "card_xref",
                column: "xref_acct_id");

            migrationBuilder.CreateIndex(
                name: "ix_cards_card_acct_id",
                table: "cards",
                column: "card_acct_id");

            migrationBuilder.CreateIndex(
                name: "ix_transactions_tran_proc_ts",
                table: "transactions",
                column: "tran_proc_ts");
        }

        /// <inheritdoc />
        protected override void Down(MigrationBuilder migrationBuilder)
        {
            migrationBuilder.DropTable(
                name: "accounts");

            migrationBuilder.DropTable(
                name: "card_xref");

            migrationBuilder.DropTable(
                name: "cards");

            migrationBuilder.DropTable(
                name: "customers");

            migrationBuilder.DropTable(
                name: "disclosure_groups");

            migrationBuilder.DropTable(
                name: "transaction_categories");

            migrationBuilder.DropTable(
                name: "transaction_category_balances");

            migrationBuilder.DropTable(
                name: "transaction_types");

            migrationBuilder.DropTable(
                name: "transactions");
        }
    }
}
