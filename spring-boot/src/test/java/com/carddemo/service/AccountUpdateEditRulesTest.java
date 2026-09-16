package com.carddemo.service;

import com.carddemo.service.AccountUpdateEditRules.Result;
import com.carddemo.service.AccountUpdateScreen.Flag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 1200-EDIT-MAP-INPUTS parity: every FR-S03 edit row maps to a named test,
 * with expectations taken from COACTUPC.cbl / CSUTLDPY.cpy / CSLKPCDY.cpy,
 * never from this implementation.
 */
class AccountUpdateEditRulesTest {

    private final AccountUpdateEditRules rules = new AccountUpdateEditRules();

    // ------------------------------------------------------- fixture

    private static final class Draft {
        String acctId = "00000000001", activeStatus = "Y",
                openYear = "2020", openMon = "01", openDay = "01",
                creditLimit = "2020.00",
                expYear = "2025", expMon = "01", expDay = "01",
                cashCreditLimit = "1020.00",
                risYear = "2025", risMon = "01", risDay = "01",
                currentBalance = "194.00", accountGroup = "02108",
                currentCycleCredit = "0.00", currentCycleDebit = "0.00",
                custId = "000000001",
                ssn1 = "123", ssn2 = "45", ssn3 = "6789",
                dobYear = "1950", dobMon = "12", dobDay = "10",
                ficoScore = "800",
                firstName = "Ada", middleName = "", lastName = "Lovelace",
                addrLine1 = "1 Main", addrLine2 = "", city = "Boston",
                state = "MA", zip = "10100", country = "USA",
                phone1a = "201", phone1b = "555", phone1c = "1212",
                phone2a = "", phone2b = "", phone2c = "",
                governmentId = "GOV", eftAccountId = "1234567890",
                priCardHolder = "Y";

        AccountUpdateForm form() {
            return new AccountUpdateForm(acctId, activeStatus,
                    openYear, openMon, openDay, creditLimit,
                    expYear, expMon, expDay, cashCreditLimit,
                    risYear, risMon, risDay, currentBalance, accountGroup,
                    currentCycleCredit, currentCycleDebit, custId,
                    ssn1, ssn2, ssn3, dobYear, dobMon, dobDay, ficoScore,
                    firstName, middleName, lastName,
                    addrLine1, addrLine2, city, state, zip, country,
                    phone1a, phone1b, phone1c, phone2a, phone2b, phone2c,
                    governmentId, eftAccountId, priCardHolder);
        }
    }

    private static AccountUpdateForm validForm() {
        return new Draft().form();
    }

    private static AccountUpdateSnapshot fetched() {
        Draft d = new Draft();
        return new AccountUpdateSnapshot(
                1L, d.activeStatus,
                new BigDecimal("194.00"), new BigDecimal("2020.00"),
                new BigDecimal("1020.00"),
                LocalDate.of(2020, 1, 1), LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 1, 1),
                BigDecimal.ZERO, BigDecimal.ZERO, "02108     ",
                1L, 123456789L, LocalDate.of(1950, 12, 10), 800,
                "Ada", "", "Lovelace", "1 Main", "", "Boston",
                "MA", "10100     ", "USA",
                "(201)555-1212", "               ",
                "GOV", "1234567890", "Y");
    }

    @Test
    void unchangedOrCaseAndSpaceOnlyDifferencesAreNotChanges_frS0308() {
        // cbl:1681-1777 — text compares UPPER(TRIM), money numerically,
        // parts right-trimmed.
        assertThat(rules.changed(fetched(), validForm())).isFalse();
        Draft d = new Draft();
        d.lastName = "  lovelace   ";
        d.accountGroup = "02108";
        d.state = "ma";
        assertThat(rules.changed(fetched(), d.form())).isFalse();
        d.creditLimit = "$2,020.00";
        assertThat(rules.changed(fetched(), d.form())).isFalse();
        d.creditLimit = "2020.01";
        assertThat(rules.changed(fetched(), d.form())).isTrue();
        d = new Draft();
        d.ficoScore = "801";
        assertThat(rules.changed(fetched(), d.form())).isTrue();
        d = new Draft();
        d.firstName = "*";
        assertThat(rules.changed(fetched(), d.form())).isTrue();
    }

    @Test
    void firstMessageComesFromEarliestFailingEdit_frS0309() {
        // cbl:1469-1677 order — status bad + last name bad reports status.
        Draft d = new Draft();
        d.activeStatus = "X";
        d.lastName = "L0velace";
        d.phone1a = "12";
        Result result = rules.validate(d.form());
        assertThat(result.firstMessage()).isEqualTo("Account Status must be Y or N.");
        assertThat(result.flags()).containsKeys("acsttus", "acslnam", "acsph1a");
        assertThat(result.firstInvalidField()).isEqualTo("acsttus");
    }

    @Test
    void yesNoEditsTreatBlankStarSpacesAndZerosAsMissing_frS0310() {
        // cbl:1856-1894 — ZEROS counted as blank in the Y/N edit.
        for (String missing : new String[] {"", " ", "*", "0"}) {
            Draft d = new Draft();
            d.activeStatus = missing;
            Result r = rules.validate(d.form());
            assertThat(r.flags().get("acsttus")).isEqualTo(Flag.BLANK);
            assertThat(r.firstMessage()).isEqualTo("Account Status must be supplied.");
        }
        Draft d = new Draft();
        d.activeStatus = "X";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Account Status must be Y or N.");
        Draft p = new Draft();
        p.priCardHolder = "Q";
        assertThat(rules.validate(p.form()).flags()).containsKey("acspflg");
        assertThat(rules.validate(p.form()).firstMessage())
                .isEqualTo("Primary Card Holder must be Y or N.");
    }

    @Test
    void datePartEditsFollowCsutldpy_frS0311() {
        Draft d = new Draft();
        d.openYear = "";
        Result r = rules.validate(d.form());
        assertThat(r.flags().get("opnyear")).isEqualTo(Flag.BLANK);
        assertThat(r.firstMessage()).isEqualTo("Open Date : Year must be supplied.");

        d = new Draft();
        d.openYear = "202";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Open Date must be 4 digit number.");

        d = new Draft();
        d.openYear = "1800";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Open Date : Century is not valid.");

        d = new Draft();
        d.openMon = "13";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Open Date: Month must be a number between 1 and 12.");

        d = new Draft();
        d.openDay = "32";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Open Date:day must be a number between 1 and 31.");

        d = new Draft();
        d.expMon = "04";
        d.expDay = "31";
        r = rules.validate(d.form());
        assertThat(r.firstMessage())
                .isEqualTo("Expiry Date:Cannot have 31 days in this month.");
        assertThat(r.flags()).containsKeys("expday", "expmon");

        d = new Draft();
        d.risMon = "02";
        d.risDay = "30";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Reissue Date:Cannot have 30 days in this month.");

        d = new Draft();
        d.risYear = "2023";
        d.risMon = "02";
        d.risDay = "29";
        r = rules.validate(d.form());
        assertThat(r.firstMessage()).isEqualTo(
                "Reissue Date:Not a leap year.Cannot have 29 days in this month.");
        assertThat(r.flags()).containsKeys("risyear", "rismon", "risday");
    }

    @Test
    void dobNotStrictlyInThePastIsRejected_frS0312() {
        LocalDate today = LocalDate.now();
        Draft d = new Draft();
        d.dobYear = "%04d".formatted(today.getYear());
        d.dobMon = "%02d".formatted(today.getMonthValue());
        d.dobDay = "%02d".formatted(today.getDayOfMonth());
        Result r = rules.validate(d.form());
        assertThat(r.firstMessage())
                .isEqualTo("Date of Birth:cannot be in the future ");
        assertThat(r.flags()).containsKeys("dobyear", "dobmon", "dobday");
    }

    @Test
    void moneyEditsUseTestNumvalCForms_frS0313() {
        Draft d = new Draft();
        d.creditLimit = "";
        Result r = rules.validate(d.form());
        assertThat(r.flags().get("acrdlim")).isEqualTo(Flag.BLANK);
        assertThat(r.firstMessage()).isEqualTo("Credit Limit must be supplied.");

        d = new Draft();
        d.creditLimit = "abc";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Credit Limit is not valid");

        for (String good : new String[] {"+2,020.00", "$2020", "-50.25",
                "100.00CR", "100.00DB", ".5", "  1234.50  "}) {
            Draft ok = new Draft();
            ok.creditLimit = good;
            assertThat(rules.validate(ok.form()).flags())
                    .doesNotContainKey("acrdlim");
        }
        for (String bad : new String[] {"1-2", "1..2", "CR", "--5"}) {
            Draft nok = new Draft();
            nok.cashCreditLimit = bad;
            assertThat(rules.validate(nok.form()).flags())
                    .containsKey("acshlim");
        }
    }

    @Test
    void ssnPartsAreNumericRequiredPlusPartOneRanges_frS0314() {
        Draft d = new Draft();
        d.ssn1 = "";
        Result r = rules.validate(d.form());
        assertThat(r.firstMessage()).isEqualTo("SSN: First 3 chars must be supplied.");
        d = new Draft();
        d.ssn2 = "4x";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("SSN 4th & 5th chars must be all numeric.");
        d = new Draft();
        d.ssn3 = "0000";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("SSN Last 4 chars must not be zero.");
        for (String part1 : new String[] {"666", "900", "999"}) {
            Draft bad = new Draft();
            bad.ssn1 = part1;
            r = rules.validate(bad.form());
            assertThat(r.flags()).containsKey("actssn1");
            assertThat(r.firstMessage()).isEqualTo(
                    "SSN: First 3 chars: should not be 000, 666,"
                            + " or between 900 and 999");
        }
    }

    @Test
    void ficoIsNumericRequiredThenRanged_frS0315() {
        Draft d = new Draft();
        d.ficoScore = "";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("FICO Score must be supplied.");
        d.ficoScore = "12x";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("FICO Score must be all numeric.");
        d.ficoScore = "299";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("FICO Score: should be between 300 and 850");
        d.ficoScore = "851";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("FICO Score: should be between 300 and 850");
    }

    @Test
    void alphaEditsAllowLettersAndSpacesOnly_frS0316() {
        Draft d = new Draft();
        d.firstName = "Ada2";
        Result r = rules.validate(d.form());
        assertThat(r.flags().get("acsfnam")).isEqualTo(Flag.NOT_OK);
        assertThat(r.firstMessage())
                .isEqualTo("First Name can have alphabets only.");
        d = new Draft();
        d.firstName = "";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("First Name must be supplied.");
        // Middle name optional — blank passes, digits fail (cbl:1953-1975).
        d = new Draft();
        d.middleName = "";
        assertThat(rules.validate(d.form()).flags())
                .doesNotContainKey("acsmnam");
        d.middleName = "M1";
        assertThat(rules.validate(d.form()).flags()).containsKey("acsmnam");
    }

    @Test
    void addressLine1IsMandatoryAndLine2IsUnedited_frS0317() {
        Draft d = new Draft();
        d.addrLine1 = "";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Address Line 1 must be supplied.");
        d = new Draft();
        d.addrLine2 = "!!!!###";
        assertThat(rules.validate(d.form()).flags())
                .doesNotContainKey("acsadl2");
    }

    @Test
    void stateMustBeInUsListAfterAlphaCheck_frS0318() {
        Draft d = new Draft();
        d.state = "ZZ";
        Result r = rules.validate(d.form());
        assertThat(r.flags().get("acsstte")).isEqualTo(Flag.NOT_OK);
        assertThat(r.firstMessage()).isEqualTo("State: is not a valid state code");
        d.state = "M1";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("State can have alphabets only.");
    }

    @Test
    void zipIsFiveDigitNumericRequired_frS0319() {
        Draft d = new Draft();
        d.zip = "";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Zip must be supplied.");
        d.zip = "0210x";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Zip must be all numeric.");
        d.zip = "00000";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Zip must not be zero.");
    }

    @Test
    void stateAndZipCombinationMustPair_frS0320() {
        Draft d = new Draft();
        d.state = "MA";
        d.zip = "02108";   // MA10-MA27/MA55 only — MA02 is not in the table
        Result r = rules.validate(d.form());
        assertThat(r.firstMessage()).isEqualTo("Invalid zip code for state");
        assertThat(r.flags()).containsKeys("acsstte", "acszipc");
        d.zip = "10100";
        assertThat(rules.validate(d.form()).flags())
                .doesNotContainKeys("acsstte", "acszipc");
    }

    @Test
    void phoneEditsWalkAreaThenPrefixThenLine_frS0321() {
        // cbl:2230-2240 quirk — all-blank test re-reads NUMA, so area+prefix
        // blank passes regardless of the line part.
        Draft d = new Draft();
        d.phone1a = "";
        d.phone1b = "";
        d.phone1c = "9999";
        assertThat(rules.validate(d.form()).flags())
                .doesNotContainKeys("acsph1a", "acsph1b", "acsph1c");

        d = new Draft();
        d.phone1a = "20";
        Result r = rules.validate(d.form());
        assertThat(r.firstMessage())
                .isEqualTo("Phone Number 1: Area code must be A 3 digit number.");
        d.phone1a = "000";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Phone Number 1: Area code cannot be zero");
        d.phone1a = "999";
        assertThat(rules.validate(d.form()).firstMessage()).isEqualTo(
                "Phone Number 1: Not valid North America general purpose area code");
        d = new Draft();
        d.phone1b = "";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Phone Number 1: Prefix code must be supplied.");
        d = new Draft();
        d.phone1c = "12";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("Phone Number 1: Line number code must be A 4 digit number.");
        d = new Draft();
        d.phone2a = "999";
        assertThat(rules.validate(d.form()).flags()).containsKey("acsph2a");
    }

    @Test
    void eftAccountIdIsTenDigitNumericRequired_frS0322() {
        Draft d = new Draft();
        d.eftAccountId = "";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("EFT Account Id must be supplied.");
        d.eftAccountId = "12345";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("EFT Account Id must be all numeric.");
        d.eftAccountId = "0000000000";
        assertThat(rules.validate(d.form()).firstMessage())
                .isEqualTo("EFT Account Id must not be zero.");
    }

    @Test
    void validFormPassesEveryEdit_frS0309() {
        assertThat(rules.validate(validForm()).valid()).isTrue();
    }
}
