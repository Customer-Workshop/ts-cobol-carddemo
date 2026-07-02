# Interactive Navigation and Menu Control — Requirements

## Navigation Context
Users navigate the Interactive Navigation and Menu Control function through a terminal-based, screen-driven interface where function keys (primarily PF3 for exit) and menu option selections drive transitions between programs. Each program maintains a shared communication area (COCOM01Y) that carries navigation context, including the originating program or transaction and the intended destination, allowing the system to route users back to either the calling program or the main menu depending on how the current session was initiated. Control transfers between programs are executed via CICS XCTL calls, meaning navigation is stateless from the program perspective — each program receives context through the communication area rather than maintaining its own session state. The administrative menu (COADM01C) acts as a hub that validates user option selections and dispatches control to subordinate programs, while account-level programs (COACTUPC, COACTVWC) handle their own exit routing back up the navigation hierarchy.

## Global Preconditions
- A valid CICS terminal session must be active and the user must be signed on before any program in this function can be invoked
- The shared communication area (COCOM01Y) must be properly initialized and passed between programs; an absent or malformed communication area causes the program to default to the signon screen (COADM01C behavior)
- The user must have a valid, authenticated user profile accessible via CSUSR01Y copybook structures; unauthorized or unrecognized users are not permitted to navigate to protected screens
- The VSAM data stores (ACCTDATA, CARDXREF, CUSTDATA) must be available and accessible; programs that perform account or customer lookups as part of navigation context resolution depend on these files being open and reachable
- Terminal attention identifier (AID) values must be mappable to supported internal function-key codes via DFHAID and CSSTRPFY; unrecognized or unsupported key inputs are treated as invalid and do not trigger navigation transitions
- The target program or transaction specified in the communication area's destination fields must exist and be enabled within the CICS region before an XCTL transfer is attempted
- All copybooks and included members (e.g., COTTL01Y, CSMSG01Y, CSMSG02Y, CSSETATY) must be compiled into the program load modules; missing copybook data structures will cause abnormal termination

## 1. Account Update Navigation and Exit
As an interactive user, I want to exit the account update screen and return to my previous context so that I can navigate the application without losing my session state.

### Requirements

REQ-F-001: [Event-driven] When the user presses PF3 (exit), the system shall determine the destination by using the originating program and transaction from the session context if available, or defaulting to the main menu program and transaction if not; populate the session context with the current program's identity, user type, initial-entry marker, and screen names; then transfer control to the destination program.

REQ-F-002: [Event-driven] When a user presses a terminal key, the system shall map the terminal attention identifier to an internal function key code (ENTER, CLEAR, PA1, PA2, or PF1–PF12); function keys 13–24 shall be remapped to their PF1–PF12 equivalents.

---

## 2. Account Update Function Key Validation and Transaction Routing
As an interactive user, I want the system to accept only valid function keys for the current transaction state so that I cannot trigger actions that are inappropriate at each step of the workflow.

### Requirements

REQ-F-003: [Complex] While the program is processing user input and a function key has been mapped, when the user presses a key, the system shall accept ENTER, PF3, PF5 (only when changes are pending confirmation), and PF12 (only when account details are available) as valid; if the pressed key is not valid for the current state, the system shall override it to ENTER.

REQ-F-004: [Complex] While the program is in a valid transaction state with a validated function key, when the user presses a function key, the system shall route as follows: transfer control on PF3 (exit); display the account search screen on initial state or PF12; process input validation and change detection on ENTER; write updates to master files on PF5 (confirmed changes); reset to the search screen after a completed or failed update; and terminate with abend code 9999 on any unexpected state.

---

## 3. Account Update Screen Input and Change Detection
As an account update operator, I want the system to receive my input, detect whether I have made any changes, and prompt me to confirm before saving so that accidental or unchanged submissions are not written to the master files.

### Requirements

REQ-F-005: [Event-driven] When the user submits the account update screen, the system shall receive all account and customer fields; for each field, if the user entered an asterisk or spaces the system shall store a low-value sentinel, otherwise copy the entered value to working storage; for numeric fields (credit limit, cash credit limit, current balance, current cycle credit, current cycle debit) the system shall also attempt numeric conversion using NUMVAL-C.

REQ-F-006: [Event-driven] When account data comparison is performed, the system shall compare all account master fields (account ID, active status, current balance, credit limit, cash credit limit, open date, expiration date, reissue date, current cycle credit, current cycle debit, group ID) between the new user-entered values and the old values retrieved at fetch time; if all fields match the system shall set the no-changes-detected flag, otherwise set the change-has-occurred flag.

REQ-F-007: [Event-driven] When customer data comparison is performed, the system shall compare all customer master fields (customer ID, first name, middle name, last name, address lines 1–3, state code, country code, zip code, phone numbers 1 and 2 area codes/prefixes/line numbers, SSN, government-issued ID, date of birth, EFT account ID, primary holder indicator, FICO score) between new and old values using case-insensitive and trimmed comparison; if all fields match the system shall set the no-changes flag, otherwise set the change-detected flag.

REQ-F-008: [Event-driven] When the user submits the account update form, the system shall validate the account ID if in search state, compare old and new data, and apply the following outcomes: if no changes are detected or validation errors exist, clear all field-level error flags and display the appropriate message; if changes are detected and validation passes, set the changes-ok-not-confirmed flag; if validation fails, set the changes-not-ok flag.

---

## 4. Account Update Input Validation
As an account update operator, I want all account and customer fields validated before changes are saved so that only correct and complete data is written to the master files.

### Requirements

REQ-F-009: [State-driven] While all input fields have been received from the screen, the system shall validate all account and customer fields in sequence; if no errors are detected the system shall set the changes-not-confirmed flag; if any validation fails the system shall leave the changes-not-ok flag set.

REQ-F-010: [Event-driven] When the account identifier field is validated, the system shall verify that the account identifier is supplied, numeric, and non-zero; if blank the system shall set the input error flag and display a prompt to supply an account number; if non-numeric or zero the system shall set the input error flag and display a message that the account number must be an 11-digit non-zero number; if both checks pass the system shall mark the account filter as valid.

REQ-F-011: [Event-driven] When the account status field is validated, the system shall validate that the account active status contains either 'Y' or 'N'.

REQ-F-012: [Event-driven] When the primary card holder indicator field is validated, the system shall validate that the primary card holder indicator contains either 'Y' or 'N'.

REQ-F-013: [Event-driven] When a yes/no field is validated, the system shall validate that the field is supplied and contains either 'Y' or 'N'; if blank the system shall set the input error flag and display a message that the field must be supplied; if any other value the system shall set the input error flag and display a message that the field must be Y or N.

REQ-F-014: [Event-driven] When the credit limit field is validated, the system shall validate that the credit limit is a valid signed numeric value with two decimal places.

REQ-F-015: [Event-driven] When the cash credit limit field is validated, the system shall validate that the cash credit limit is a valid signed numeric value with two decimal places.

REQ-F-016: [Event-driven] When the current balance field is validated, the system shall validate that the current balance is a valid signed numeric value with two decimal places.

REQ-F-017: [Event-driven] When the current cycle credit field is validated, the system shall validate that the current cycle credit is a valid signed numeric value with two decimal places.

REQ-F-018: [Event-driven] When the current cycle debit field is validated, the system shall validate that the current cycle debit is a valid signed numeric value with two decimal places.

REQ-F-019: [Event-driven] When a signed numeric field is validated, the system shall validate that the field, if supplied, is a valid signed numeric value with optional sign and two decimal places; if blank the system shall display a message that the field must be supplied; if supplied but not a valid signed numeric value the system shall set the input error flag and display a message that the field is not valid.

REQ-F-020: [Event-driven] When user enters a signed numeric field with two decimal places, the system shall validate that the field is either blank or contains a valid signed numeric value; if blank the system shall mark it as blank without setting an error; if non-numeric the system shall set the invalid flag and display an error message; if valid the system shall set the field-valid flag.

REQ-F-021: [Event-driven] When the open date field is validated, the system shall validate the open date for format, month/day/year range, and leap year correctness.

REQ-F-022: [Event-driven] When the expiration date field is validated, the system shall validate the expiration date for format, month/day/year range, and leap year correctness.

REQ-F-023: [Event-driven] When the reissue date field is validated, the system shall validate the reissue date for format, month/day/year range, and leap year correctness.

REQ-F-024: [Event-driven] When a date in CCYYMMDD format is validated, the system shall validate that the month is between 1 and 12, the century is 19 or 20, and the day is between 1 and 31; if any component fails the system shall set the input error flag and display an appropriate error message.

REQ-F-025: [Event-driven] When date day-month-year consistency is validated, the system shall validate that the day is valid for the given month and year; for February 29 the system shall determine leap year by dividing the year by 4 (or 400 if the year ends in 00) and checking for zero remainder; if the combination is invalid the system shall set the input error flag and display an error message.

REQ-F-026: [Event-driven] When a date is validated using the external date validation service, the system shall pass the date in CCYYMMDD format and the format string 'YYYYMMDD' to the service; if the service returns a non-zero severity code the system shall set the input error flag, mark all date components as not-ok, and display a message containing the severity code and message code from the service.

REQ-F-027: [Event-driven] When user enters a date in CCYYMMDD format, the system shall validate year (century 19 or 20), month (1–12), and day (1–31 with month-specific rules), then delegate to the date validation service for final validation; if any component or the service validation fails the system shall set the error flag and display a specific message.

REQ-F-028: [Event-driven] When the date of birth field is validated, the system shall validate the date of birth for format, month/day/year range, and leap year correctness; if the date passes format validation the system shall also verify that the date of birth is not in the future.

REQ-F-029: [Event-driven] When the date of birth reasonableness is validated, the system shall compare the date of birth to the current system date; if the date of birth is greater than or equal to the current date the system shall set the input error flag and display a message that the date of birth cannot be in the future.

REQ-F-030: [Event-driven] When user enters a date of birth, the system shall validate that the date of birth is not later than the current date; if the date of birth is in the future the system shall set the error flag and display a message.

REQ-F-031: [Event-driven] When the first name field is validated, the system shall validate that the first name is supplied and contains only alphabetic characters.

REQ-F-032: [Event-driven] When the last name field is validated, the system shall validate that the last name is supplied and contains only alphabetic characters.

REQ-F-033: [Event-driven] When the middle name field is validated, the system shall validate that the middle name, if supplied, contains only alphabetic characters; a blank middle name shall pass validation.

REQ-F-034: [Event-driven] When the city field is validated, the system shall validate that the city is supplied and contains only alphabetic characters.

REQ-F-035: [Event-driven] When the address line 1 field is validated, the system shall validate that address line 1 is supplied.

REQ-F-036: [Event-driven] When the country code field is validated, the system shall validate that the country code is supplied and contains only alphabetic characters.

REQ-F-037: [Event-driven] When the state code field is validated, the system shall validate that the state code is supplied, contains only alphabetic characters, and is a valid two-character US state or territory postal abbreviation.

REQ-F-038: [Event-driven] When the US state code is validated, the system shall validate that the state code is one of the valid US state or territory postal abbreviations (AL, AK, AZ, AR, CA, CO, CT, DE, FL, GA, HI, ID, IL, IN, IA, KS, KY, LA, ME, MD, MA, MI, MN, MS, MO, MT, NE, NV, NH, NJ, NM, NY, NC, ND, OH, OK, OR, PA, RI, SC, SD, TN, TX, UT, VT, VA, WA, WV, WI, WY, DC, AS, GU, MP, PR, VI); if invalid the system shall set the error flag, mark the field as invalid, and display a message that the state code is not valid.

REQ-F-039: [Event-driven] When the zip code field is validated, the system shall validate that the zip code is supplied, numeric, and non-zero.

REQ-F-040: [Complex] While both state code and zip code have passed individual validation, when state and zip code consistency is checked, the system shall construct a four-character value from the state code and the first two digits of the zip code and validate it against the list of valid state-zip combinations; if the combination is not valid the system shall set the input error flag and display a message that the zip code is invalid for the state.

REQ-F-041: [Event-driven] When user enters a state code and zip code combination, the system shall validate that the state-zip code combination is valid according to USPS data; if invalid the system shall set the error flag and display a message.

REQ-F-042: [Event-driven] When the EFT account ID field is validated, the system shall validate that the EFT account ID is supplied, numeric, and non-zero.

REQ-F-043: [Event-driven] When the FICO score field is validated, the system shall validate that the FICO score is numeric and non-zero; if the FICO score passes numeric validation the system shall then verify that the score is between 300 and 850.

REQ-F-044: [Event-driven] When the FICO score range is validated, the system shall validate that the FICO score is between 300 and 850; if outside this range the system shall set the input error flag and display a message that the FICO score should be between 300 and 850.

REQ-F-045: [Event-driven] When user enters a FICO credit score, the system shall validate that the FICO score is numeric and within the range 300 to 850; if invalid the system shall set the error flag and display a range message.

REQ-F-046: [Event-driven] When the Social Security Number is validated, the system shall validate each of the three SSN parts: part 1 (3 digits) must be numeric, non-zero, and not 000, 666, or in the range 900–999; part 2 (2 digits) must be numeric and non-zero; part 3 (4 digits) must be numeric and non-zero.

REQ-F-047: [Event-driven] When the first part of the Social Security Number is validated, the system shall validate that the first part is numeric and non-zero, and is not 000, 666, or between 900 and 999; if invalid the system shall set the input error flag and display a message.

REQ-F-048: [Event-driven] When the second part of the Social Security Number is validated, the system shall validate that the second part is numeric and non-zero.

REQ-F-049: [Event-driven] When the third part of the Social Security Number is validated, the system shall validate that the third part is numeric and non-zero.

REQ-F-050: [Event-driven] When user enters a Social Security Number in three parts, the system shall validate that each part is numeric and non-zero; part 1 must not be 000, 666, or 900–999; part 2 must be 01–99; part 3 must be 0001–9999; if any part fails the system shall set the error flag and display a specific message.

REQ-F-051: [Event-driven] When the phone number 1 field is validated, the system shall validate that phone number 1, if supplied, has all three parts (area code, prefix, line number) supplied, numeric, non-zero, and with a valid North American area code; if all three parts are blank the validation shall pass.

REQ-F-052: [Event-driven] When the phone number 2 field is validated, the system shall validate that phone number 2, if supplied, has all three parts (area code, prefix, line number) supplied, numeric, non-zero, and with a valid North American area code; if all three parts are blank the validation shall pass.

REQ-F-053: [Event-driven] When a US phone number is validated, the system shall validate that the phone number is either completely blank or has all three parts (area code, prefix, line number) supplied, numeric, non-zero, and with a valid North American area code.

REQ-F-054: [Event-driven] When the phone area code is validated, the system shall validate that the area code is supplied, numeric, non-zero, and is a valid North American area code; if blank the system shall set the error flag and display a message that the area code must be supplied; if non-numeric the system shall display a message that the area code must be a 3-digit number; if zero the system shall display a message that the area code cannot be zero; if not a valid North American area code the system shall display a message that it is not a valid North America general purpose area code.

REQ-F-055: [Event-driven] When the phone prefix is validated, the system shall validate that the prefix is supplied, numeric, and non-zero; if blank the system shall set the error flag and display a message that the prefix code must be supplied; if non-numeric the system shall display a message that the prefix code must be a 3-digit number; if zero the system shall display a message that the prefix code cannot be zero.

REQ-F-056: [Event-driven] When the phone line number is validated, the system shall validate that the line number is supplied, numeric, and non-zero; if blank the system shall set the error flag and display a message that the line number code must be supplied; if non-numeric the system shall display a message that the line number code must be a 4-digit number; if zero the system shall display a message that the line number code cannot be zero.

REQ-F-057: [Event-driven] When a required numeric field is validated, the system shall validate that the field is supplied, numeric, and non-zero; if blank the system shall set the input error flag and display a message that the field must be supplied; if non-numeric the system shall set the input error flag and display a message that the field must be all numeric; if zero the system shall set the input error flag and display a message that the field must not be zero.

REQ-F-058: [Event-driven] When user enters a mandatory numeric field (zip code, EFT account ID, FICO score), the system shall validate that the field is supplied, numeric, and non-zero; if blank the system shall set the blank flag and error; if non-numeric the system shall set the invalid flag and error; if zero the system shall set the invalid flag and error; if valid the system shall set the field-valid flag.

REQ-F-059: [Event-driven] When a mandatory field is validated, the system shall validate that the field is supplied; if blank the system shall set the input error flag and display a message that the field must be supplied.

REQ-F-060: [Event-driven] When a required alphabetic field is validated, the system shall validate that the field is supplied and contains only alphabetic characters; if blank the system shall set the input error flag, mark the field as blank, and display a message that the field must be supplied; if non-alphabetic the system shall set the input error flag, mark the field as not-ok, and display a message that the field can have alphabets only.

REQ-F-061: [Event-driven] When an optional alphabetic field is validated, the system shall validate that the field, if supplied, contains only alphabetic characters; if empty the validation shall pass; if supplied but non-alphabetic the system shall set the input error flag and display a message that the field can have alphabets only.

REQ-F-062: [Event-driven] When user enters an optional alphabetic field (middle name), the system shall validate that if the field is supplied it contains only alphabetic characters; blank shall be acceptable; if non-alphabetic the system shall set the invalid flag and error; if valid the system shall set the field-valid flag.

REQ-F-063: [Event-driven] When user enters a mandatory alphabetic field (first name, last name, address), the system shall validate that the field is supplied and contains only alphabetic characters; if blank the system shall set the blank flag and error; if non-alphabetic the system shall set the invalid flag and error; if valid the system shall set the field-valid flag.

---

## 5. Account and Customer Record Retrieval
As an account update operator, I want the system to retrieve the current account and customer records when I search by account ID so that I can review and update the correct data.

### Requirements

REQ-F-064: [Event-driven] When the user searches for an account by account ID, the system shall read the card cross-reference file (legacy: CARDXREF) using the account ID to obtain the customer ID; read the account master file (Acctdata data store, legacy: AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS) using the account ID; read the customer master file (Custdata data store, legacy: AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS) using the customer ID; if any read fails the system shall set the error flag and display a message; if all reads succeed the system shall store all fetched data in the old-details area.

REQ-F-065: [Event-driven] When the system needs to retrieve the customer ID for a given account ID, the system shall read the card cross-reference file using the account ID; if successful the system shall extract and store the customer ID; if not found the system shall set the account-filter-not-ok flag and display a not-found message; if any other error the system shall set the account-filter-not-ok flag and store the error details (operation name, file name, response code, reason code).

REQ-F-066: [Event-driven] When the system needs to retrieve account data for a given account ID, the system shall read the Acctdata data store using the account ID; if successful the system shall set the found-acct-in-master flag; if not found the system shall set the account-filter-not-ok flag and display a not-found message; if any other error the system shall set the account-filter-not-ok flag and store the error details.

REQ-F-067: [Event-driven] When the system needs to retrieve customer data for a given customer ID, the system shall read the Custdata data store using the customer ID; if successful the system shall set the found-cust-in-master flag; if not found the system shall store the error details without setting a fatal error flag; if any other error the system shall store the error details.

REQ-F-068: [Event-driven] When account and customer master records have been successfully read, the system shall store all fetched account fields (active status, current balance, credit limit, cash credit limit, current cycle credit, current cycle debit, open date, expiration date, reissue date, group ID) and all fetched customer fields (customer ID, first name, middle name, last name, address lines 1–3, state code, country code, zip code, phone numbers 1 and 2, SSN, government-issued ID, date of birth, EFT account ID, primary cardholder indicator, FICO score) in the old-details area.

---

## 6. Account and Customer Record Update
As an account update operator, I want confirmed changes written back to the account and customer master files so that the records reflect the latest approved data.

### Requirements

REQ-F-069: [Event-driven] When the system is ready to write account updates, the system shall assemble the account update record by moving all new account field values (account ID, active status, current balance, credit limit, cash credit limit, open date, expiration date, reissue date, current cycle credit, current cycle debit, group ID) with date fields constructed from year, month, and day components.

REQ-F-070: [Event-driven] When the system is ready to write customer updates, the system shall assemble the customer update record by moving all new customer field values (customer ID, first name, middle name, last name, address lines 1–3, state code, country code, zip code, phone numbers 1 and 2 constructed from area code/exchange/line number components, SSN, government-issued ID, date of birth constructed from year/month/day components, EFT account ID, primary cardholder indicator, FICO score).

REQ-F-071: [Event-driven] When the system is ready to update the account record, the system shall read the Acctdata data store with an update lock; if the lock succeeds the record is held for update; if the lock fails the system shall set the could-not-lock-acct-for-update flag.

REQ-F-072: [Event-driven] When the system is about to update the customer master record, the system shall compare current customer record values (read with update lock) with the old values stored at fetch time; if any field differs the system shall set the data-was-changed-before-update flag to reject the update.

REQ-F-073: [Event-driven] When the system is ready to update the customer record, the system shall read the Custdata data store with an update lock; if the lock succeeds the record is held for update; if the lock fails the system shall set the could-not-lock-cust-for-update flag.

REQ-F-074: [Event-driven] When the account update record is assembled and ready to write, the system shall write the account update record to the Acctdata data store using a rewrite operation; if successful the record is updated; if the write fails the system shall set the locked-but-update-failed flag.

REQ-F-075: [Event-driven] When the customer update record is assembled and ready to write, the system shall write the customer update record to the Custdata data store using a rewrite operation; if successful the record is updated; if the write fails the system shall set the locked-but-update-failed flag and issue a rollback to undo the account update.

REQ-F-076: [Event-driven] When account and customer update writes have completed, the system shall evaluate the write results and concurrent modification check and set the outcome flag as follows: changes-okayed-and-done if both writes succeeded and no concurrent modification was detected; changes-okayed-lock-error if the account record could not be locked; changes-okayed-but-failed if the customer record could not be locked or either write failed; show-details if concurrent modification was detected.

---

## 7. Account Update Screen Display
As an account update operator, I want the screen to show the correct data and status messages at each step so that I know what action to take next.

### Requirements

REQ-F-077: [State-driven] While the program is in a state where account and customer data should be displayed, the system shall populate the screen output buffer with account and customer data based on transaction state: blank fields for initial entry, original values for fetched details, or updated values for user changes; numeric fields (balances, limits) shall be formatted with currency format including thousands separators and two decimal places.

REQ-F-078: [State-driven] While the program is in a state where an information message should be displayed, the system shall set the information message based on transaction state: a search prompt for initial entry; an update prompt when account details have been fetched; a confirmation prompt (press F5 to save) when changes have been validated; a success message when changes have been committed; or a failure message when the update was unsuccessful.

REQ-F-079: [Ubiquitous] The system shall set all screen field attributes to protected to prevent user input, then selectively unprotect specific fields based on the current transaction state to allow editing only of the appropriate fields.

REQ-F-080: [Event-driven] When user enters an account number to search, the system shall validate that the account ID is supplied, numeric, and non-zero; if blank the system shall set the error flag and display a prompt to supply an account number; if non-numeric or zero the system shall set the error flag and display a message that the account number must be an 11-digit non-zero number; if valid the system shall set the account-filter-valid flag.

---

## 8. Unexpected Condition Handling
As an operations team, I want the system to terminate cleanly on unrecoverable conditions so that the transaction does not remain in an indeterminate state.

### Requirements

REQ-F-081: [Unwanted] If an unexpected or unrecoverable condition is detected, the system shall terminate the transaction with abend code 9999.

---

## 9. Sign-on Screen Display and User Authentication (COSGN00C)
As an unauthenticated user, I want to provide my credentials on the sign-on screen so that I can be authenticated and routed to the appropriate portal.

### Requirements

REQ-F-082: [Ubiquitous] The system shall clear the error flag and the error message output field to spaces at the start of sign-on processing.

REQ-F-083: [Event-driven] When the user submits the sign-on form by pressing ENTER, the system shall receive the screen input, validate that both user ID and password are provided; if the user ID is blank the system shall set the error flag, display a message to enter the user ID, and redisplay the sign-on screen; if the password is blank the system shall set the error flag, display a message to enter the password, and redisplay the sign-on screen; if both are provided the system shall convert them to uppercase and store them in working storage and the session context.

REQ-F-084: [Ubiquitous] The system shall convert the user ID and password to uppercase and store them in working storage and the session context.

REQ-F-085: [Complex] While no input validation errors have been detected, when the ENTER key is pressed with valid user ID and password input, the system shall read the user security file (Usrsec data store, legacy: AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS) to retrieve and validate the stored password against the entered password.

REQ-F-086: [Event-driven] When the user credentials are submitted for authentication, the system shall retrieve the user record from the user security file, verify the password matches, populate the session context with transaction ID, program name, user ID, and user type, and transfer control to the administrator portal if the user type is 'A' or to the customer portal otherwise.

REQ-F-087: [Unwanted] If the user security file read fails with an unexpected error, the system shall display 'Unable to verify the User ...' and redisplay the sign-on screen.

REQ-F-088: [Event-driven] When the user ID is not found in the user security file, the system shall display 'User not found. Try again ...' and redisplay the sign-on screen.

REQ-F-089: [Event-driven] When the user security file read succeeds, the system shall compare the stored password with the entered password; if they do not match the system shall display 'Wrong Password. Try again ...' and redisplay the sign-on screen.

REQ-F-090: [Event-driven] When an invalid key is pressed on the sign-on screen, the system shall display the invalid key error message.

REQ-F-091: [Event-driven] When the program is invoked with no session context, the system shall transfer control to the sign-on screen.

REQ-F-092: [Event-driven] When the user presses a key on the sign-on screen, the system shall route to the credential validation process when ENTER is pressed, or handle the alternative action when PF3 is pressed.

---

## 10. Main Menu Display and Navigation (COMEN01C)
As an authenticated user, I want to see the main menu and select an option so that I can navigate to the desired function.

### Requirements

REQ-F-093: [Ubiquitous] The system shall clear the error flag and message area at the start of menu processing.

REQ-F-094: [Event-driven] When the communication area length is greater than zero, the system shall copy the caller's communication area into the local session context, establishing the originating transaction, originating program, and program re-entry state.

REQ-F-095: [Complex] While the session context contains data from a prior transaction, when the user presses a key on the menu screen, the system shall on first entry display the initial menu; on re-entry with ENTER validate and process the selected option; on re-entry with PF3 transfer to the exit handler; on re-entry with any other key display an invalid-key error and redisplay the menu.

REQ-F-096: [Event-driven] When the user submits a menu option selection, the system shall validate that the option is numeric, within the range 1 to the menu option count (10), and not zero; if validation fails the system shall set the error flag.

REQ-F-097: [Event-driven] When a standard user selects a menu option that requires administrator authorization, the system shall set the error flag to deny access.

REQ-F-098: [Event-driven] When the user enters a menu option selection and presses ENTER, the system shall validate that the option is numeric, within range (1–10), and not zero; if invalid the system shall display a validation error and redisplay the menu; if valid the system shall check user access permissions; if access is denied the system shall display an access-denied error and redisplay the menu; if access is granted the system shall display a confirmation message with the selected option name.

REQ-F-099: [Ubiquitous] The system shall trim trailing spaces from the menu option input, replace any remaining spaces with zeros, and convert the result to a numeric option code.

REQ-F-100: [State-driven] While menu options are available in the menu configuration table, the system shall iterate through each menu option, format each as 'number. name', and populate the corresponding screen display field.

REQ-F-101: [Ubiquitous] The system shall retrieve the current system date and time and populate the menu screen header with the application title, transaction identifier, program name, and formatted date and time.

REQ-F-102: [Event-driven] When the user presses a terminal key on the menu screen, the system shall route to the appropriate handler: process the menu option selection when ENTER is pressed, or handle the exit request when PF3 is pressed.

REQ-F-103: [Event-driven] When the program is invoked with no session context, the system shall transfer control to the sign-on screen.

REQ-F-104: [Ubiquitous] The system shall validate the destination program field before transferring control; if the destination program field is empty the system shall default it to the sign-on screen program, then transfer control to the destination program.

REQ-F-105: [Event-driven] When the user presses PF3 on the menu screen, the system shall set the destination to the sign-on screen and transfer control.

---

## 11. Administrative Menu Display and Navigation (COADM01C)
As an administrator, I want to see the administrative menu and select an option so that I can access administrative functions.

### Requirements

REQ-F-106: [Ubiquitous] The system shall clear the error flag and reset the message area to spaces at program initialization.

REQ-F-107: [Ubiquitous] The system shall receive the communication area from the caller when present and copy it into the local session context.

REQ-F-108: [Complex] While the program is executing within a session, when a communication area is passed to the program, the system shall route to initial menu display when first entered; on re-entry with ENTER delegate to option processing; on re-entry with PF3 handle that key; on re-entry with any other key display an invalid-key error and redisplay the menu.

REQ-F-109: [State-driven] While the program is being re-entered after a previous interaction, the system shall receive the administrative menu screen to capture the user-selected option.

REQ-F-110: [Event-driven] When the user enters a menu option on the administrative menu screen, the system shall extract and validate the option; if the option is non-numeric, exceeds the maximum option count (4), or is zero the system shall set the error flag.

REQ-F-111: [Event-driven] When the user presses the ENTER key or PF3 key on the administrative menu screen, the system shall route to the appropriate handler based on the attention key pressed.

REQ-F-112: [State-driven] While menu options are available in the administrative menu configuration, the system shall iterate through each administrative menu option, format each as 'number. name', and populate the corresponding screen output field (option001 through option010).

REQ-F-113: [Ubiquitous] The system shall retrieve the current system date and time, format the date as MM/DD/YY and time as HH:MM:SS, and populate the administrative menu screen header with the application titles, transaction identifier, program name, current date, and current time.

REQ-F-114: [Ubiquitous] The system shall populate the administrative menu screen header with system information, build the menu options, place any pending message in the error message field, and send the complete administrative menu screen to the user.

REQ-F-115: [Event-driven] When the program is invoked with or without a communication area, the system shall transfer control to the sign-on screen when no communication area is present; when a communication area is present and the program is being re-entered the system shall evaluate the user's key press to determine the next action.

REQ-F-116: [Ubiquitous] The system shall validate the destination program field and default it to the sign-on screen program if empty, then transfer control to the destination program.

REQ-F-117: [Event-driven] When the user presses the PF3 function key on the administrative menu, the system shall set the destination program to the sign-on screen program and transfer control to the sign-on screen.

### Non-Functional Requirements

REQ-N-001: [Unwanted] If the customer update write fails after the account update write has already succeeded, the system shall issue a rollback to undo the account update so that the account master file and customer master file remain consistent.

## 12. Card Activity Screen Navigation and Control Transfer
As an interactive session user, I want the card activity screen to correctly interpret my key presses and route me to the appropriate destination so that I can navigate the application reliably.

### Requirements

REQ-F-118: [Event-driven] When a user presses a terminal key, the system shall translate the terminal attention identifier into the corresponding function-key indicator, mapping ENTER, CLEAR, PA1, PA2, and PF1–PF12 directly, and remapping extended keys PF13–PF24 to their base equivalents PF1–PF12 respectively.

REQ-F-119: [Event-driven] When a function key is mapped, the system shall accept ENTER and PF3 as valid inputs; for all other keys, the system shall mark the input as invalid and force the effective input to ENTER so that the current screen is redisplayed.

REQ-F-120: [Event-driven] When the user presses PF3 to exit, the system shall determine the destination: if the calling transaction identifier is empty or spaces, route to the main menu; otherwise route back to the calling transaction and program. The system shall then populate the navigation context with the source transaction identifier (`CAVW`), source program name (`COACTVWC`), user type as regular user, program context as initial entry, and the current screen map and mapset, then transfer control to the destination program.

---

## 13. Credit Card Account Inquiry Screen Display
As an interactive session user, I want to search for a credit card account by account ID and view the associated account and customer details so that I can review account information accurately.

### Requirements

REQ-F-121: [Event-driven] When the user presses an attention key on the terminal, the system shall map the attention identifier to a standardized function-key code (mapping extended keys PF13–PF24 to their base equivalents PF1–PF12), validate whether the key is permitted (ENTER and PF3 are valid), and default the effective action to ENTER if an invalid key was pressed.

REQ-F-122: [Complex] While the program is in an active transaction context, when the program is entered for the first time, the system shall display the account inquiry screen to prompt the user to enter an account ID.

REQ-F-123: [Complex] While the program is in an active transaction context, when the program is reentered after the user submits input, the system shall validate the input; if validation fails, the system shall redisplay the account inquiry screen with the applicable error message; if validation succeeds, the system shall retrieve account data and display the populated account inquiry screen.

REQ-F-124: [Ubiquitous] The system shall set the informational message to prompt the user to enter or update the account ID when the session context is empty (first entry) or when no informational message is currently set.

REQ-F-125: [Event-driven] When the user submits input from the account inquiry screen, the system shall receive the user's input (including the account ID value) from the account inquiry screen into the input buffer and capture the response and reason codes.

REQ-F-126: [Event-driven] When the user submits input from the screen, the system shall normalize the account ID by clearing it to low-values if the user entered an asterisk or spaces, otherwise copy the input value; if the account ID is blank after normalization, the system shall set an error flag and record a message indicating no search criteria were received.

REQ-F-127: [Event-driven] When the account ID field is validated, the system shall reject the account ID with an error flag and a 'Account number not provided' message if the account ID is blank or low-values; reject the account ID with an error flag and an 'Account Filter must be a non-zero 11 digit number' message if the account ID is not numeric or equals zero; or accept and mark the account ID as valid and copy it to the session context.

REQ-F-128: [Event-driven] When the account ID is validated and ready for lookup, the system shall retrieve the cross-reference record (from the Cardxref data store, legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH) by account ID via the alternate index path; if found, extract and store the customer ID in the session context; if not found, set an error flag and record a not-found message containing the account ID, response code, and reason code; if any other read error occurs, set an error flag and record the operation name, file name, response code, and reason code in the error message.

REQ-F-129: [Event-driven] When the customer ID is retrieved from the cross-reference record, the system shall retrieve the account master record (from the Acctdata data store, legacy: AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS) by account ID; if found, populate the account data; if not found, set an error flag and record a not-found message containing the account ID, response code, and reason code; if any other read error occurs, set an error flag and record the operation name, file name, response code, and reason code in the error message.

REQ-F-130: [Event-driven] When the customer ID is extracted from the cross-reference record, the system shall retrieve the customer master record (from the Custdata data store, legacy: AWS.M2.CARDDEMO.CUSTDATA.VSAM.KSDS) by customer ID.

REQ-F-131: [Unwanted] If input validation encounters an error, the system shall display the account inquiry screen to present the validation error message to the user.

## 14. Administrative Menu Option Selection and Program Transfer
As an administrative user, I want to select options from the administrative menu and be routed to the correct administrative function so that I can perform administrative tasks efficiently.

### Requirements

REQ-F-132: [Ubiquitous] The system shall clear the error flag and reset the message area to spaces at program entry to establish a clean processing state.

REQ-F-133: [Ubiquitous] The system shall receive the communication area from the caller when a communication area is present (indicated by a non-zero communication area length) and copy it into the local session context for processing.

REQ-F-134: [State-driven] While the program is being re-entered after a previous interaction, the system shall receive the administrative menu screen to capture the user-selected option.

REQ-F-135: [Event-driven] When the user presses the ENTER key on the administrative menu screen, the system shall route to the menu option processing handler.

REQ-F-136: [Event-driven] When the user enters a menu option on the administrative menu screen, the system shall extract and validate the option, and set the error flag if the option is non-numeric, exceeds the maximum option count of 4, or is zero.

REQ-F-137: [Event-driven] When the user presses ENTER after entering a menu option and the option is invalid, the system shall display the error message 'Please enter a valid option number...' and redisplay the administrative menu.

REQ-F-138: [Event-driven] When the user presses ENTER after entering a menu option and the option is valid, the system shall display the confirmation message 'This option [option name] is coming soon ...' and redisplay the administrative menu.

---

## 15. Administrative Menu Screen Display and Input Handling
As an administrative user, I want the administrative menu to display current system information and available options so that I can make an informed selection.

### Requirements

REQ-F-139: [Complex] While the program is executing within an interactive session, when a communication area is passed to the program and the program is being entered for the first time (reentry flag not set), the system shall display the administrative menu screen with cleared output fields.

REQ-F-140: [Complex] While the program is executing within an interactive session, when a communication area is passed to the program and the program is being re-entered (reentry flag set) and the user presses any key other than ENTER or PF3, the system shall display an invalid-key error message and redisplay the administrative menu.

REQ-F-141: [Complex] While the program is executing within an interactive session, when no communication area is present (communication area length equals zero), the system shall take the initial-entry path and display the administrative menu screen.

REQ-F-142: [Ubiquitous] The system shall retrieve the current system date and time, format the date as MM/DD/YY and the time as HH:MM:SS, and populate the menu screen header with the application titles, transaction identifier, program name, current date, and current time.

REQ-F-143: [State-driven] While menu options are available in the administrative menu configuration, the system shall iterate through each administrative menu option (up to the option count of 4), format each option as 'number. name', and populate the corresponding screen output field (option001 through option010).

REQ-F-144: [Ubiquitous] The system shall place any pending error or confirmation message in the error message output field and send the complete administrative menu screen to the user, erasing the previous screen content.

REQ-F-145: [Ubiquitous] The system shall receive the user's input from the administrative menu screen and capture the response and reason codes for error handling.

---

## 16. Signon Screen Navigation and Program Transfer
As an administrative user, I want the system to route me to the correct screen based on my key press and session context so that navigation is consistent and predictable.

### Requirements

REQ-F-146: [Event-driven] When the program is invoked with no communication area (communication area length is zero), the system shall immediately transfer control to the signon screen.

REQ-F-147: [Event-driven] When the program is invoked with a communication area present, the system shall copy the communication area into the local session context and, when the program is being re-entered (reentry flag set), evaluate the key pressed to determine the next action.

REQ-F-148: [Event-driven] When the user presses the PF3 function key, the system shall set the destination program to the signon screen program and transfer control to the signon screen.

REQ-F-149: [Ubiquitous] The system shall validate the destination program field before transferring control; if the destination program field is empty (contains low-values or spaces), the system shall default it to the signon screen program name, then transfer control to the destination program.

### Open Questions

OQ-001: Rule 2a868c8e (pre-classified not_applicable) describes populating the session context with navigation context (transaction ID, originating program name, and program context reset) before transferring control to the selected administrative program, and treating placeholder ('DUMMY') targets as no-ops. This rule was pre-classified as platform mechanics, but the navigation context population and placeholder-skip logic appear to be business behavior. Should these be reinstated as requirements? — Owner: modernization architect

## 17. Bill Payment Transaction Processing and Screen Management
As a cardholder-services operator, I want to process bill payments interactively so that account balances are reduced by the payment amount and a corresponding transaction record is persisted.

### Requirements

REQ-F-150: [Ubiquitous] The system shall retrieve the current system date and time, format the date as YYYYMMDD with hyphen separator and the time as HH:MM:SS with colon separator, and construct a 26-character timestamp by concatenating the formatted date (positions 1–10), a space (position 11), the formatted time (positions 12–19), and six microsecond digits set to zero (positions 20–25).

REQ-F-151: [Ubiquitous] The system shall populate the bill payment screen header with the titles "AWS Mainframe Modernization" and "CardDemo", the program name "COBIL00C", the current date formatted as MM/DD/YY, and the current time formatted as HH:MM:SS.

REQ-F-152: [Event-driven] When the user submits the bill payment screen, the system shall validate that the account identifier input is not empty; if empty, the system shall reject the submission with the error message "Acct ID can NOT be empty..." and re-display the screen.

REQ-F-153: [Event-driven] When the account identifier is not empty, the system shall validate that the confirmation input is one of Y, y, N, n, or empty; if any other value is entered, the system shall reject the submission with the error message "Invalid value. Valid values are (Y/N)..." and re-display the screen.

REQ-F-154: [Event-driven] When the account identifier passes validation, the system shall retrieve the account record from the credit card account master file (legacy: AWS.M2.CARDDEMO.ACCTDATA.VSAM.KSDS) using the account identifier as the key; if the account is not found, the system shall display the error message "Account ID NOT found..." and re-display the screen; if a read error occurs, the system shall display the error message "Unable to lookup Account..." and re-display the screen.

REQ-F-155: [Event-driven] When the account record is successfully retrieved, the system shall display the account's current balance on the bill payment screen.

REQ-F-156: [Event-driven] When the account balance has been retrieved and is zero or negative, the system shall reject the payment with the message "You have nothing to pay..." and re-display the screen.

REQ-F-157: [Event-driven] When the user has confirmed the bill payment (confirmation input is Y or y), the system shall retrieve the card cross-reference record from the card cross-reference data store (legacy: AWS.M2.CARDDEMO.CARDXREF.VSAM.AIX.PATH) using the account identifier as the key to obtain the associated card number; if the account is not found in the cross-reference, the system shall display the error message "Account ID NOT found..." and re-display the screen; if a read error occurs, the system shall display the error message "Unable to lookup XREF AIX file..." and re-display the screen.

REQ-F-158: [Event-driven] When the card cross-reference record is successfully retrieved, the system shall browse the transaction master data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) in reverse order starting from the highest key position to find the highest existing transaction ID; if the transaction ID is not found at the start position, the system shall display the error message "Transaction ID NOT found..." and re-display the screen; if a browse error occurs, the system shall display the error message "Unable to lookup Transaction..." and re-display the screen.

REQ-F-159: [Event-driven] When the reverse browse of the transaction master data store reaches the previous record successfully, the system shall use that record's transaction ID as the highest existing transaction ID; if the end of file is reached with no records found, the system shall set the transaction ID to zeros.

REQ-F-160: [Event-driven] When a read error occurs during the reverse browse of the transaction master data store, the system shall display the error message "Unable to lookup Transaction..." and re-display the screen.

REQ-F-161: [Event-driven] When the highest existing transaction ID has been determined, the system shall generate a new transaction ID by incrementing the highest existing transaction ID by one (16-character alphanumeric field).

REQ-F-162: [Event-driven] When the new transaction ID has been generated, the system shall construct a transaction record in the transaction master data store with: transaction ID (the newly generated 16-character ID), transaction type code "02", transaction category code 2, transaction source "POS TERM", transaction description "BILL PAYMENT - ONLINE", transaction amount equal to the account's current balance, card number from the cross-reference record, merchant ID 999999999, merchant name "BILL PAYMENT", merchant city "N/A", merchant ZIP "N/A", and the current system timestamp stored in both the original timestamp and processed timestamp fields.

REQ-F-163: [Event-driven] When the transaction record has been constructed, the system shall write it to the transaction master data store using the transaction ID as the key; if the transaction ID already exists, the system shall display the error message "Tran ID already exist..." and re-display the screen; if a write error occurs, the system shall display the error message "Unable to Add Bill pay Transaction..." and re-display the screen.

REQ-F-164: [Event-driven] When the transaction record is successfully written, the system shall compute the new account balance by subtracting the transaction amount from the current balance and update the account record in the credit card account master file; if the account is not found during update, the system shall display the error message "Account ID NOT found..." and re-display the screen; if an update error occurs, the system shall display the error message "Unable to Update Account..." and re-display the screen.

REQ-F-165: [Event-driven] When the account record is successfully updated, the system shall display a success message containing the transaction ID on the bill payment screen.

REQ-F-166: [Event-driven] When the confirmation input is N or n, the system shall clear all screen input and display fields and re-display the bill payment screen.

REQ-F-167: [State-driven] While the confirmation flag has not been set to confirmed (confirmation input is empty or absent), the system shall display the message "Confirm to make a bill payment..." and position the cursor on the confirmation field.

### Non-Functional Requirements

REQ-N-002: [Event-driven] When a confirmed bill payment is processed, the system shall ensure that writing the transaction record to the transaction master data store and updating the account balance in the credit card account master file are treated as a single atomic operation that either both succeed or neither is persisted.

### Open Questions

OQ-002: REQ-F-161 specifies that the new transaction ID is generated by incrementing the highest existing transaction ID by one. The transaction ID field is 16-character alphanumeric. It is unclear whether the increment is numeric (treating the field as a zero-padded number) or uses another scheme, and what the overflow behavior is when the maximum value is reached. — Owner: data architecture team

OQ-003: REQ-F-156 rejects payments when the account balance is zero or negative. It is unclear whether a zero balance should be treated identically to a negative balance (i.e., both are ineligible), or whether zero is a distinct edge case with different handling. — Owner: business rules team

---

## 18. Navigation Control Transfer Between Programs
As an interactive session user, I want the system to route me to the correct screen based on my navigation actions so that I can move between functions without losing context.

### Requirements

REQ-F-168: [Event-driven] When the program is invoked without a communication area (communication area length is zero), the system shall set the destination program to the sign-on screen and transfer control immediately.

REQ-F-169: [Event-driven] When the program is invoked with a communication area present, the system shall copy the communication area into working storage for further processing.

REQ-F-170: [Ubiquitous] The system shall validate the destination program before transferring control; if the destination program field is empty or contains low-values, the system shall default it to the sign-on screen.

REQ-F-171: [Ubiquitous] The system shall populate the navigation session context with the current transaction identifier, the current program name, and a reset program context flag, then transfer control to the destination program passing the prepared session context.

REQ-F-172: [Event-driven] When the PF3 function key is pressed during program re-entry, the system shall evaluate the calling program recorded in the session context; if the calling program is empty or low-values, the system shall set the destination to the menu screen; otherwise, the system shall set the destination to the recorded calling program and transfer control.

## 19. Credit Card List Display and Navigation
As a credit card operations user, I want to browse a paginated list of credit cards filtered by account and card number criteria so that I can locate and select individual cards for viewing or updating.

### Requirements

REQ-F-173: [Event-driven] When the program is re-entered from itself with a non-zero session context, the system shall receive the user's input from the card list screen, including the account filter, card filter, and selection codes for each of the seven displayed rows, and transfer the received values into working storage for validation and processing.

REQ-F-174: [Ubiquitous] The system shall map the terminal attention identifier to a named function key flag, covering ENTER, CLEAR, PA1, PA2, and PF1–PF12; extended function keys PF13–PF24 shall be remapped to their base equivalents PF1–PF12 respectively.

REQ-F-175: [Ubiquitous] The system shall validate that the function key pressed is one of ENTER, PF3, PF7, or PF8; if any other key is pressed, the system shall default the action to ENTER.

REQ-F-176: [Ubiquitous] The system shall initialize the input validation state by setting the input validation flag to success and the selection row protection flag to not protected before validating user input.

REQ-F-177: [Ubiquitous] The system shall validate the account filter input: if the account filter is blank, low-values, or zero, the system shall mark it as blank and clear the account ID in the session context; if the account filter is not numeric, the system shall mark it as invalid, set an error message, protect selection rows, and clear the account ID in the session context; if the account filter is valid and numeric, the system shall move it to the session context and mark it as valid.

REQ-F-178: [Event-driven] When the account identifier is not numeric, the system shall set the input error flag to indicate validation failure.

REQ-F-179: [Ubiquitous] The system shall validate the card filter input: if the card filter is blank, low-values, or zero, the system shall mark it as blank and clear the card number in the session context; if the card filter is not numeric, the system shall mark it as invalid, set an error message (if not already set), protect selection rows, and clear the card number in the session context; if the card filter is valid and numeric, the system shall move it to the session context and mark it as valid.

REQ-F-180: [Ubiquitous] The system shall validate the selection codes for each of the seven screen rows: count rows marked with 'S' (view) or 'U' (update); if more than one row is selected, the system shall set the input error flag and mark error flags for all selected rows; for each row, if the selection code is valid ('S', 'U', or blank), the system shall record the row index if selected; if the selection code is invalid (not 'S', 'U', or blank), the system shall set the input error flag and mark an error flag for that row.

REQ-F-181: [Event-driven] When the user input contains validation errors, the system shall record the program name in the session context and, if both account and card filters are valid, initiate a forward read to retrieve matching records; the system shall then display the screen with error messages to allow the user to correct the input.

REQ-F-182: [Ubiquitous] The system shall evaluate whether a card record matches the active filter criteria: if an account filter is active (valid), the system shall include the record only if its account ID matches the filter value; if a card filter is active (valid), the system shall include the record only if its card number matches the filter value; a record shall be excluded if it fails either filter criterion.

REQ-F-183: [Ubiquitous] The system shall initiate a forward browse on the card file starting from the specified card key, retrieve up to 7 card records matching the filter criteria, detect whether a next page of records exists, and close the browse.

REQ-F-184: [Ubiquitous] The system shall initiate a backward browse on the card file starting from the specified card key using GTEQ positioning, retrieve up to 7 card records in reverse order matching the filter criteria, and close the browse.

REQ-F-185: [Event-driven] When the user presses PF7 while not on the first page, the system shall set the card key to the first card number stored in the session context, decrement the screen page number, initiate a backward read to retrieve the previous page of records, and display the screen.

REQ-F-186: [Event-driven] When the user presses PF7 while already on the first page, the system shall set the card key to the first card number stored in the session context and initiate a forward read to retrieve the first page of records, then display the screen.

REQ-F-187: [Event-driven] When the user presses PF8 and a next page of records exists, the system shall set the card key to the last card number from the current page, increment the screen page number, initiate a forward read to retrieve the next page of records, and display the screen.

REQ-F-188: [Event-driven] When the user does not press PF8, the system shall reset the last-page-displayed flag to indicate that the last page has not been shown.

REQ-F-189: [Ubiquitous] The system shall configure error and informational messages based on the current state: if the account or card filter is in error, the system shall display the corresponding error message; if the user presses PF7 while on the first page, the system shall display an error message indicating no previous pages are available; if the user presses PF8 with no next page available, the system shall display an error message indicating no more pages are available; if no informational message is set and a next page exists, the system shall display the instruction message; otherwise, no informational message shall be displayed.

REQ-F-190: [Ubiquitous] The system shall clear the screen output area, then populate the screen header with the application title, transaction identifier, program name, current date, current time, and page number; the informational message area shall be cleared.

REQ-F-191: [Ubiquitous] For each of the seven screen rows, if card data exists for that row, the system shall populate the selection code, account number (11-digit numeric), card number (16-character alphanumeric), and card active status from the session context; if no card data exists for a row, the system shall leave that row empty.

REQ-F-192: [Event-driven] When the user input does not match any explicitly handled action, the system shall set the card key to the first card number, initiate a forward read to retrieve the first page of records, and display the screen.

REQ-F-193: [Event-driven] When the originating program is not this program, the system shall discard any prior state, reset the program context to initial entry, and activate the first page indicator.

REQ-F-194: [Event-driven] When the user presses PF3 and the program is re-entered from itself, the system shall transfer control to the main menu program.

---

## 20. Card Detail Inquiry Navigation
As a credit card operations user, I want to select a card from the list and view its full details so that I can review card information without making changes.

### Requirements

REQ-F-195: [Event-driven] When the program is re-entered from itself with screen input, the system shall receive the screen input containing the account identifier (11-digit numeric), card number (16-digit numeric), and seven row-level selection codes, and extract each field into working storage.

REQ-F-196: [Ubiquitous] The system shall map the terminal attention identifier to a named function key flag; extended function keys PF13–PF24 shall be remapped to their base equivalents PF1–PF12.

REQ-F-197: [Ubiquitous] The system shall validate that the pressed function key is one of ENTER, PF3, PF7, or PF8; if any other key is pressed, the system shall default the action to ENTER.

REQ-F-198: [Event-driven] When the card number field is received from user input, the system shall validate it: if the field is empty (blank, spaces, or numeric zero), the system shall set the session context card number to zero; if the field is not numeric, the system shall set the session context card number to zero; if the field is numeric, the system shall move the card number to the session context.

REQ-F-199: [Event-driven] When the account ID field is received from user input, the system shall validate it: if the field is empty (blank, spaces, or numeric zero), the system shall set the session context account ID to zero; if the field is not numeric, the system shall set the session context account ID to zero; if the field is numeric, the system shall move the account ID to the session context.

REQ-F-200: [Event-driven] When the user presses ENTER and has selected a card row marked for view ('S' selection code), the system shall populate the session context with the selected card's account number and card number, set the user type to regular user, mark the entry as initial, record the current screen context (mapset and map name), designate the card detail program as the next target, and transfer control to the card detail program.

REQ-F-201: [Event-driven] When the user presses a keyboard key, the system shall map the keyboard input to the corresponding function key identifier; validate whether the key is valid for the current screen (ENTER, PF3, PF7, and PF8 are valid; all others default to ENTER); when PF3 is pressed and the program is being called from itself, the system shall populate the session context with the current program context (transaction identifier, program name, user type, program context, mapset, and map name) and transfer control to the main menu program.

---

## 21. Card Detail Inquiry Search and Retrieval (COCRDSLC)
As a credit card operations user, I want to search for a specific card by account number and card number so that I can view its details.

### Requirements

REQ-F-202: [Event-driven] When the terminal sends an attention identifier, the system shall evaluate it and set the corresponding function key flag; extended function keys PF13–PF24 shall be remapped to their base equivalents PF1–PF12.

REQ-F-203: [Event-driven] When a function key is mapped, the system shall mark all keys as invalid initially; if ENTER or PF3 is pressed, the system shall mark the key as valid; if the key remains invalid, the system shall force the action to ENTER.

REQ-F-204: [Ubiquitous] The system shall extract account and card numbers from the screen input; if the user entered an asterisk or spaces in the account field, the system shall clear the account number to indicate no input; if the user entered an asterisk or spaces in the card field, the system shall clear the card number to indicate no input.

REQ-F-205: [Ubiquitous] The system shall validate the account number input: if blank (low-values, spaces, or zero), the system shall set an input error flag, mark the account filter as blank, and if no error message is already set, display 'Account number not provided', clearing the account ID in the session context to zero; if not numeric, the system shall set an input error flag, mark the account filter as invalid, and if no error message is already set, display 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER', clearing the account ID to zero; if valid and numeric, the system shall store the account number in the session context and mark the account filter as valid.

REQ-F-206: [Ubiquitous] The system shall validate the card number input: if blank (low-values, spaces, or zero), the system shall set an input error flag, mark the card filter as blank, and if no error message is already set, display 'Card number not provided', clearing the card number in the session context to zero; if not numeric, the system shall set an input error flag, mark the card filter as invalid, and if no error message is already set, display 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER', clearing the card number to zero; if valid and numeric, the system shall store the card number in the session context and mark the card filter as valid.

REQ-F-207: [Ubiquitous] The system shall validate that at least one search criterion (account number or card number) is provided; if both are blank, the system shall set the return message to 'No input received'.

REQ-F-208: [Event-driven] When the program needs to retrieve a card record, the system shall read the card record from the credit card master data store (legacy: CARDDAT) using the card number as the lookup key; if successful, the system shall set a found flag; if not found, the system shall mark both the account and card filters as invalid and set the return message to 'Did not find cards for this search condition'; if any other error occurs, the system shall mark the account filter as invalid and record the error details (operation name, file name, response code, and reason code) in the return message.

REQ-F-209: [Ubiquitous] The system shall populate the screen with the account and card number search criteria, display card details when a record is found (cardholder name, card active status, and expiration date month and year), display any informational or error messages, and display the input prompt when no informational message is set.

REQ-F-210: [Complex] While the program is active and awaiting user action, when the user presses ENTER, PF3, or another key, the system shall: if PF3 is pressed, exit the program; if entering from the credit card list program with ENTER, retrieve and display the card record; if entering from another context with ENTER, display the search screen; if re-entering after input submission, validate the input and either redisplay with errors or retrieve and display the card record; for unexpected scenarios, display an error message; if an input error flag remains set after all branches, redisplay the search screen.

REQ-F-211: [Event-driven] When the user presses PF3 (exit), the system shall determine the destination: if the caller's transaction identifier is empty or spaces, the system shall use the main menu transaction identifier; otherwise, the system shall use the caller's transaction identifier; if the caller's program name is empty or spaces, the system shall use the main menu program; otherwise, the system shall use the caller's program name; the system shall then update the session context to record the current program and transaction as the originating source, set the user type to 'U', mark the context as first entry, record the current screen map and mapset, and transfer control to the destination program.

---

## 22. Card Update Navigation and Processing (COCRDUPC)
As a credit card operations user, I want to select a card from the list for update and modify its details so that card information is kept accurate.

### Requirements

REQ-F-212: [Event-driven] When a user key press is received, the system shall map the attention identifier to the corresponding function-key code by comparing against known key constants (ENTER, CLEAR, PA1, PA2, PF1–PF12, and extended PF13–PF24) and setting the matching flag; extended function keys PF13–PF24 shall be mapped to their base equivalents PF1–PF12.

REQ-F-213: [Complex] While the card update session is active, when a user key press is received, the system shall determine whether the pressed key is valid in the current context: if the key is ENTER, PF3, PF5 (with pending changes), or PF12 (with fetched details), the system shall mark it as valid; otherwise, the system shall force the action to ENTER to redisplay the screen.

REQ-F-214: [Event-driven] When the user submits the card update screen, the system shall receive the map input from the terminal and parse each field (account ID, card number, cardholder name, card active status, and expiration date fields); if the user enters an asterisk or leaves a field blank, the system shall store a low-value sentinel for that field; otherwise, the system shall store the user's input.

REQ-F-215: [Event-driven] When the program is validating search criteria during initial entry, the system shall validate the account ID: if blank, low-value, or zero, the system shall set an error flag and display a prompt asking for an account number; if not numeric, the system shall set an error flag and display a message stating the account ID must be an 11-digit number; if valid and numeric, the system shall mark the account filter as valid.

REQ-F-216: [Event-driven] When the program is validating search criteria during initial entry, the system shall validate the card number: if blank, low-value, or zero, the system shall set an error flag, display a prompt asking for a card number, and clear the card number fields; if not numeric, the system shall set an error flag, display a message stating the card number must be a 16-digit number, and clear the card number fields; if valid and numeric, the system shall mark the card filter as valid and store the card number.

REQ-F-217: [Event-driven] When the program is validating card details after retrieval, the system shall validate the cardholder name: if blank, low-value, or zero, the system shall set an error flag and display a prompt asking for a card name; if it contains non-alphabetic characters other than spaces, the system shall set an error flag and display a message stating the card name can only contain alphabets and spaces; if valid, the system shall mark the name field as valid.

REQ-F-218: [Event-driven] When the program is validating card details after retrieval, the system shall validate the card active status: if blank, low-value, or zero, the system shall set an error flag and display a message stating the card active status must be Y or N; if not 'Y' or 'N', the system shall set an error flag and display the same message; if valid, the system shall mark the status field as valid.

REQ-F-219: [Event-driven] When the program is validating card details after retrieval, the system shall validate the card expiration month: if blank, low-value, or zero, the system shall set an error flag and display a message stating the card expiry month must be between 1 and 12; if not in the range 1–12, the system shall set an error flag and display the same message; if valid, the system shall mark the month field as valid.

REQ-F-220: [Event-driven] When the program is validating card details after retrieval, the system shall validate the card expiration year: if blank, low-value, or zero, the system shall set an error flag and display a message stating the card expiry year is invalid; if not in the range 1950–2099, the system shall set an error flag and display the same message; if valid, the system shall mark the year field as valid.

REQ-F-221: [Event-driven] When the program needs to retrieve card details for display or update, the system shall read the card record from the credit card master data store using the card number as the primary key; if successful, the system shall extract and store the card's CVV code, cardholder name, expiration date (year, month, day), and active status; if not found, the system shall set error flags indicating the account-card combination was not found and display an appropriate message; if a technical error occurs, the system shall log the error details (operation name, file name, response code, reason code) and display an error message.

REQ-F-222: [Event-driven] When the user has confirmed changes and requested to save them, the system shall lock the card record in the credit card master data store for update using the card number as the primary key; if the lock cannot be acquired, the system shall set an error flag indicating the record could not be locked for update.

REQ-F-223: [Event-driven] When the program has locked the card record for update, the system shall compare the current database record with the previously retrieved record across CVV code, cardholder name, expiration date (year, month, day), and active status; if all fields match, the update shall proceed; if any field differs, the system shall set an error flag indicating the data was changed before the update and update the stored old-details with the current database values.

REQ-F-224: [Ubiquitous] The system shall assemble the updated card record from the new values entered by the user, populating the card number, account ID, CVV code, cardholder name, expiration date (formatted as YYYY-MM-DD), and active status.

REQ-F-225: [Event-driven] When the updated card record has been assembled, the system shall write the updated card record to the credit card master data store; if the write fails, the system shall set an error flag indicating the update failed.

REQ-F-226: [Complex] While the transaction is active and awaiting a decision, when the user has submitted input or the program has completed a processing step, the system shall evaluate the current state and user input: if details have not been fetched and the user presses PF12 with valid search criteria, the system shall read the card data and mark it for display; if details are being shown and the user has made valid changes, the system shall mark the changes as confirmed and awaiting user confirmation; if the user presses PF5 while awaiting confirmation, the system shall attempt to write the changes; after the write attempt, if the record could not be locked, the system shall mark the update as failed due to lock error; if the record was locked but the update failed, the system shall mark the update as failed; if the record was changed by another user, the system shall mark the data as changed and redisplay the details; otherwise, the system shall mark the update as complete; if the transaction reaches an unexpected state, the system shall abend with code 9999.

REQ-F-227: [State-driven] While the transaction is active and awaiting a user action, the system shall evaluate the current state and user action to route to exit, data retrieval, input processing, or screen display: if the user presses PF3, the system shall exit; if card details have been successfully updated and confirmed, or if the update failed, the system shall reset and return to the initial search screen; if details have not yet been fetched and the user is entering from the card list program or pressing PF12, the system shall read the card data and display it; if the user is entering from the menu program on initial entry, the system shall display a blank search screen; for all other cases, the system shall process the user's input, decide on the next action, and display the appropriate screen.

REQ-F-228: [Complex] While the card update session is active and a valid key has been pressed, when the user presses PF3, or the card update is complete or failed and the prior screen was the card list, the system shall determine the destination: if the originating transaction identifier is empty, the system shall route to the main menu transaction; otherwise, the system shall route to the originating transaction; if the originating program is empty, the system shall route to the main menu program; otherwise, the system shall route to the originating program; the system shall record the current program and transaction as the source, set the user type to user, set the program context to initial entry, record the current map and mapset; if the prior screen was the card list, the system shall clear the account ID and card number; the system shall then transfer control to the destination program.

REQ-F-229: [State-driven] While the screen detail fields have been populated, the system shall set the informational message based on the current transaction state: when details have not been fetched, display a prompt to enter search criteria; when details have been retrieved, display a message indicating card details are shown; when changes failed validation, display a prompt to update card details; when valid changes are awaiting confirmation, display a prompt to press PF5 to save; when the update completed successfully, display a confirmation message; when the update failed, display a failure message.

REQ-F-230: [State-driven] While the informational message has been set, the system shall set field protection attributes based on the current transaction state: when details have not been fetched, the account ID and card number fields shall be editable and the detail fields shall be protected; when details are being shown or changes have failed validation, the account ID and card number fields shall be protected and the detail fields shall be editable; when the user is awaiting confirmation or the update is complete, all fields shall be protected.

REQ-F-231: [State-driven] While the screen header has been initialized, the system shall populate the card detail fields based on the current transaction state: when entering for the first time, the detail fields shall be left blank; when details have been retrieved, the system shall display the retrieved card data (cardholder name, card active status, expiration date); when the user has made changes, the system shall display the new values entered by the user; if the account ID or card number is zero, the system shall display them as low-values; otherwise, the system shall display them as entered.

### Open Questions

OQ-004: Rule REQ-F-226 references abend code 9999 for unexpected transaction states. Should the modernized system raise a structured exception or return a specific error response instead of an abend? — Owner: architecture team

OQ-005: The card detail inquiry (COCRDSLC) displays the transaction identifier as 'CCDL' and the program name as 'COCRDSLC' in the screen header (rule ac329916). Should these identifiers be preserved as-is in the modernized system, or replaced with modernized equivalents? — Owner: product owner

## 23. Keyboard Input Mapping and Screen Input Collection
As an interactive session user, I want my keyboard inputs and screen field entries captured and interpreted correctly so that the system routes me to the right function and validates the data I have entered.

### Requirements

REQ-F-232: [Event-driven] When the user presses a keyboard key and the attention identifier is captured, the system shall evaluate the attention identifier and set the corresponding function key flag; PF keys 13 through 24 shall be mapped to their PF1 through PF12 equivalents respectively.

REQ-F-233: [Event-driven] When the user submits the credit card inquiry screen, the system shall receive the screen input containing the account ID and card number fields and move the received values into working storage for subsequent validation.

## 24. Function Key Mapping and Navigation Control
As an interactive user, I want my key presses to be correctly recognized and routed so that the system responds to my intended navigation actions consistently.

### Requirements

REQ-F-234: [Ubiquitous] The system shall translate the user's attention identifier to a named function key by evaluating it against all supported keys (ENTER, CLEAR, PA1, PA2, PF1–PF12) and setting the corresponding function key flag.

REQ-F-235: [Event-driven] When the attention identifier matches PF13, the system shall remap it to PF1 by setting the PF1 function key flag to true.

REQ-F-236: [Event-driven] When the attention identifier matches PF14, the system shall remap it to PF2 by setting the PF2 function key flag to true.

REQ-F-237: [Event-driven] When the attention identifier matches PF15, the system shall remap it to PF3 by setting the PF3 function key flag to true.

REQ-F-238: [Event-driven] When the attention identifier matches PF16, the system shall remap it to PF4 by setting the PF4 function key flag to true.

REQ-F-239: [Event-driven] When the attention identifier matches PF17, the system shall remap it to PF5 by setting the PF5 function key flag to true.

REQ-F-240: [Event-driven] When the attention identifier matches PF18, the system shall remap it to PF6 by setting the PF6 function key flag to true.

REQ-F-241: [Event-driven] When the attention identifier matches PF19, the system shall remap it to PF7 by setting the PF7 function key flag to true.

REQ-F-242: [Event-driven] When the attention identifier matches PF20, the system shall remap it to PF8 by setting the PF8 function key flag to true.

REQ-F-243: [Event-driven] When the attention identifier matches PF21, the system shall remap it to PF9 by setting the PF9 function key flag to true.

REQ-F-244: [Event-driven] When the attention identifier matches PF22, the system shall remap it to PF10 by setting the PF10 function key flag to true.

REQ-F-245: [Event-driven] When the attention identifier matches PF23, the system shall remap it to PF11 by setting the PF11 function key flag to true.

REQ-F-246: [Event-driven] When the attention identifier matches PF24, the system shall remap it to PF12 by setting the PF12 function key flag to true.

REQ-F-247: [Event-driven] When a function key has been mapped, the system shall mark all function keys as invalid initially, then mark the key as valid if it is ENTER or PF3; if the key remains invalid, the system shall force it to ENTER.

REQ-F-248: [Event-driven] When the user presses PF3 (exit), the system shall determine the destination: if the caller's transaction identifier is empty or spaces, use the main menu transaction identifier; otherwise, use the caller's transaction identifier; and if the caller's program name is empty or spaces, use the main menu program; otherwise, use the caller's program name.

REQ-F-249: [Event-driven] When the user presses PF3 (exit), the system shall update the session context to record the current program as the originating program and transaction, set the user type to 'U', mark the context as first-entry, record the current screen and screen set, and transfer control to the destination program passing the updated session context.

REQ-F-250: [Event-driven] When the user presses ENTER, or when an invalid key is forced to ENTER, the system shall process the ENTER key action.

---

## 25. Credit Card Details Lookup and Display
As an interactive user, I want to search for credit card details by account number and card number so that I can view card information including cardholder name, status, and expiration date.

### Requirements

REQ-F-251: [Event-driven] When the terminal sends an attention identifier, the system shall evaluate it against all supported keys (ENTER, CLEAR, PA1, PA2, PF1–PF12, PF13–PF24) and set the corresponding function key flag, remapping extended keys PF13–PF24 to their base equivalents PF1–PF12.

REQ-F-252: [Event-driven] When the user presses a terminal key, the system shall validate that the key is ENTER or PF3 and default to ENTER if an invalid key is pressed.

REQ-F-253: [Complex] While the program is active and awaiting user action, when the user presses PF3, the system shall exit the program.

REQ-F-254: [Complex] While entering from the credit card list program with ENTER pressed, the system shall retrieve the card record using the account and card number passed from the list program and display the results.

REQ-F-255: [Complex] While entering from a context other than the credit card list program with ENTER pressed, the system shall display the search screen to gather input criteria.

REQ-F-256: [Complex] While re-entering after the user has submitted search criteria, when input validation fails, the system shall redisplay the search screen with error messages.

REQ-F-257: [Complex] While re-entering after the user has submitted search criteria, when input validation succeeds, the system shall retrieve the card record and display it.

REQ-F-258: [Unwanted] If an unexpected scenario occurs, the system shall display an error message.

REQ-F-259: [Event-driven] When the user submits the screen by pressing ENTER, the system shall receive the screen input buffer and capture the response and reason codes.

REQ-F-260: [Ubiquitous] The system shall extract the account number and card number from screen input; if either field contains an asterisk or spaces, the system shall clear that field to indicate no input; otherwise, the system shall store the entered value.

REQ-F-261: [Ubiquitous] The system shall validate the account number: if blank, set an input error flag and display the message 'Account number not provided' and clear the account identifier in the session context; if non-numeric, set an input error flag and display the message 'ACCOUNT FILTER,IF SUPPLIED MUST BE A 11 DIGIT NUMBER' and clear the account identifier; if valid (numeric and non-blank), store the account number in the session context and mark the account filter as valid.

REQ-F-262: [Ubiquitous] The system shall validate the card number: if blank, set an input error flag and display the message 'Card number not provided' and clear the card number in the session context; if non-numeric, set an input error flag and display the message 'CARD ID FILTER,IF SUPPLIED MUST BE A 16 DIGIT NUMBER' and clear the card number; if valid (numeric and non-blank), store the card number in the session context and mark the card filter as valid.

REQ-F-263: [Ubiquitous] The system shall validate that at least one search criterion (account number or card number) is provided; if both are blank, the system shall set the return message to 'No input received'.

REQ-F-264: [Event-driven] When the program needs to retrieve a card record, the system shall read the card record from the credit card master file (legacy: CARDDAT) using the card number as the lookup key; if successful, set a found flag; if not found, mark the account and card filters as invalid and set the return message to 'Did not find cards for this search condition'; if any other error occurs, mark the account filter as invalid and record the operation name, file name, response code, and reason code in the return message.

REQ-F-265: [Ubiquitous] The system shall prepare the screen output by clearing the output buffer and populating the screen with the transaction identifier 'CCDL', the program name 'COCRDSLC', and the current date formatted as MM/DD/YY and current time formatted as HH:MM:SS.

REQ-F-266: [Ubiquitous] The system shall populate the search criteria fields on the screen with the account number and card number from the session context (or clear them if zero); when a card record has been found, display the cardholder name, card active status, and expiration date (month and year); display any informational or error messages, and if no informational message is set, display the input prompt.

REQ-F-267: [Ubiquitous] The system shall preserve the caller's session context when re-entering from another program; if no session context is passed or the caller is the main menu on first entry, the system shall clear the session context.

REQ-F-268: [State-driven] While returning from the credit card list program, the system shall protect the account and card number input fields; while in normal entry mode, the system shall leave those fields unprotected.

## 26. Card Update Session Entry and Navigation Control
As a card operations user, I want to navigate the card update workflow using function keys so that I can search for, review, and update credit card records efficiently.

### Requirements

REQ-F-269: [Event-driven] When a user key press is received, the system shall map the attention identifier to the corresponding internal function-key code by comparing it against all known key constants (ENTER, CLEAR, PA1, PA2, PF1–PF12, and extended PF13–PF24) and setting the matching flag.

REQ-F-270: [Event-driven] When the attention identifier matches the ENTER key, the system shall set the ENTER function-key flag to true.

REQ-F-271: [Event-driven] When the attention identifier matches the CLEAR key, the system shall set the CLEAR function-key flag to true.

REQ-F-272: [Event-driven] When the attention identifier matches the PA1 key, the system shall set the PA1 function-key flag to true.

REQ-F-273: [Event-driven] When the attention identifier matches the PA2 key, the system shall set the PA2 function-key flag to true.

REQ-F-274: [Event-driven] When the attention identifier matches the PF1 key, the system shall set the PF1 function-key flag to true.

REQ-F-275: [Event-driven] When the attention identifier matches the PF2 key, the system shall set the PF2 function-key flag to true.

REQ-F-276: [Event-driven] When the attention identifier matches the PF3 key, the system shall set the PF3 function-key flag to true.

REQ-F-277: [Event-driven] When the attention identifier matches the PF4 key, the system shall set the PF4 function-key flag to true.

REQ-F-278: [Event-driven] When the attention identifier matches the PF5 key, the system shall set the PF5 function-key flag to true.

REQ-F-279: [Event-driven] When the attention identifier matches the PF6 key, the system shall set the PF6 function-key flag to true.

REQ-F-280: [Event-driven] When the attention identifier matches the PF7 key, the system shall set the PF7 function-key flag to true.

REQ-F-281: [Event-driven] When the attention identifier matches the PF8 key, the system shall set the PF8 function-key flag to true.

REQ-F-282: [Event-driven] When the attention identifier matches the PF9 key, the system shall set the PF9 function-key flag to true.

REQ-F-283: [Event-driven] When the attention identifier matches the PF10 key, the system shall set the PF10 function-key flag to true.

REQ-F-284: [Event-driven] When the attention identifier matches the PF11 key, the system shall set the PF11 function-key flag to true.

REQ-F-285: [Event-driven] When the attention identifier matches the PF12 key, the system shall set the PF12 function-key flag to true.

REQ-F-286: [Event-driven] When the attention identifier matches the PF13 key, the system shall map PF13 to PF1 and set the PF1 function-key flag to true.

REQ-F-287: [Event-driven] When the attention identifier matches the PF14 key, the system shall map PF14 to PF2 and set the PF2 function-key flag to true.

REQ-F-288: [Event-driven] When the attention identifier matches the PF15 key, the system shall map PF15 to PF3 and set the PF3 function-key flag to true.

REQ-F-289: [Event-driven] When the attention identifier matches the PF16 key, the system shall map PF16 to PF4 and set the PF4 function-key flag to true.

REQ-F-290: [Event-driven] When the attention identifier matches the PF17 key, the system shall map PF17 to PF5 and set the PF5 function-key flag to true.

REQ-F-291: [Event-driven] When the attention identifier matches the PF18 key, the system shall map PF18 to PF6 and set the PF6 function-key flag to true.

REQ-F-292: [Event-driven] When the attention identifier matches the PF19 key, the system shall map PF19 to PF7 and set the PF7 function-key flag to true.

REQ-F-293: [Event-driven] When the attention identifier matches the PF20 key, the system shall map PF20 to PF8 and set the PF8 function-key flag to true.

REQ-F-294: [Event-driven] When the attention identifier matches the PF21 key, the system shall map PF21 to PF9 and set the PF9 function-key flag to true.

REQ-F-295: [Event-driven] When the attention identifier matches the PF22 key, the system shall map PF22 to PF10 and set the PF10 function-key flag to true.

REQ-F-296: [Event-driven] When the attention identifier matches the PF23 key, the system shall map PF23 to PF11 and set the PF11 function-key flag to true.

REQ-F-297: [Event-driven] When the attention identifier matches the PF24 key, the system shall map PF24 to PF12 and set the PF12 function-key flag to true.

REQ-F-298: [Complex] While the card update session is active, when a user key press is received, the system shall determine whether the pressed key is valid in the current context: the key is valid if it is ENTER, PF3 (exit), PF5 (when changes are pending confirmation), or PF12 (when card details have been fetched); if the key is not valid in the current context, the system shall force the action to ENTER to redisplay the screen.

REQ-F-299: [Complex] While the card update session is active and a valid key has been pressed, when the user presses PF3, or when the card update is complete or failed and the prior screen was the card list, the system shall determine the destination: if the originating transaction is empty or spaces, route to the main menu transaction; otherwise, route to the originating transaction; if the originating program is empty or spaces, route to the main menu program; otherwise, route to the originating program. The system shall record the current program and transaction as the source, set the user type to user, set the program context to initial entry, record the current screen and screen set, clear the account ID and card number if the prior screen was the card list, and transfer control to the destination program passing the session context.

---

## 27. Credit Card Update Transaction Processing
As a card operations user, I want to search for a credit card record, review its details, make changes, and confirm the update so that card information is kept accurate and changes are applied safely.

### Requirements

REQ-F-300: [Event-driven] When the card update program is invoked, the system shall initialize the session context; if no prior context exists or the call is a fresh entry from the menu, reset the card update state to details-not-fetched; otherwise, restore the prior session state from the input parameters.

REQ-F-301: [Event-driven] When a function key is pressed by the user, the system shall determine if the key is valid for the current transaction state; valid keys are ENTER, PF3 (exit), PF5 (confirm changes when awaiting confirmation), and PF12 (when details have already been fetched); if the pressed key is not valid, the system shall force the action to ENTER to re-display the current screen.

REQ-F-302: [State-driven] While the transaction is active and awaiting a user action, the system shall evaluate the current state and the user's action to route to exit, data retrieval, input processing, or screen display as follows: if the user presses PF3, exit; if card details have been successfully updated and confirmed or the update failed, reset and return to the initial search screen; if details have not yet been fetched and the user is entering from the card list or pressing PF12, read and display card data; if the user is entering from the menu on initial entry, display a blank search screen; for all other cases, process user input, decide the next action, and display the appropriate screen.

REQ-F-303: [Event-driven] When the user submits the card update screen, the system shall receive the input and parse each field (account ID, card number, cardholder name, card status code, and expiration date fields), converting asterisks and blanks to low-value sentinels; the expiration day field shall be extracted directly without the asterisk/blank conversion.

REQ-F-304: [Event-driven] When the program is validating search criteria during initial entry and the account ID is blank, low-value, or zero, the system shall set an error flag and display a prompt asking the user to provide an account number.

REQ-F-305: [Event-driven] When the program is validating search criteria during initial entry and the account ID is supplied but is not numeric, the system shall set an error flag and display a message stating that the account ID must be an 11-digit number.

REQ-F-306: [Event-driven] When the program is validating search criteria during initial entry and the account ID is numeric and non-zero, the system shall mark the account filter as valid.

REQ-F-307: [Event-driven] When the program is validating search criteria during initial entry and the card number is blank, low-value, or zero, the system shall set an error flag, display a prompt asking for a card number, and clear the card number fields.

REQ-F-308: [Event-driven] When the program is validating search criteria during initial entry and the card number is supplied but is not numeric, the system shall set an error flag, display a message stating that the card number must be a 16-digit number, and clear the card number fields.

REQ-F-309: [Event-driven] When the program is validating search criteria during initial entry and the card number is numeric and non-zero, the system shall mark the card filter as valid and store the card number.

REQ-F-310: [Complex] While the user has submitted input from the screen, when input validation is required in the initial search state, the system shall validate the account ID and card number search criteria; if both search criteria are blank, the system shall set an error message indicating no search criteria were received.

REQ-F-311: [Complex] While the user has submitted input from the screen, when input validation is required in the edit state, the system shall compare the new card data with the previously retrieved card data; if they are identical (case-insensitive), the system shall set a message indicating no changes were detected and skip further validation; otherwise, the system shall validate the cardholder name, card status, expiration month, and expiration year fields, mark changes as confirmed and awaiting user confirmation if all validations pass, or mark changes as not acceptable if any validation fails.

REQ-F-312: [Event-driven] When the program is validating card details after retrieval and the cardholder name is blank, low-value, or zero, the system shall set an error flag and display a prompt asking for a card name.

REQ-F-313: [Event-driven] When the program is validating card details after retrieval and the cardholder name is supplied but contains non-alphabetic characters other than spaces, the system shall set an error flag and display a message stating that the card name can only contain alphabets and spaces.

REQ-F-314: [Event-driven] When the program is validating card details after retrieval and the cardholder name contains only alphabetic characters and spaces, the system shall mark the name field as valid.

REQ-F-315: [Event-driven] When the program is validating card details after retrieval and the card active status is blank, low-value, or zero, the system shall set an error flag and display a message stating that the card active status must be Y or N.

REQ-F-316: [Event-driven] When the program is validating card details after retrieval and the card active status is supplied but is not 'Y' or 'N', the system shall set an error flag and display a message stating that the card active status must be Y or N.

REQ-F-317: [Event-driven] When the program is validating card details after retrieval and the card active status is 'Y' or 'N', the system shall mark the status field as valid.

REQ-F-318: [Event-driven] When the program is validating card details after retrieval and the card expiration month is blank, low-value, or zero, the system shall set an error flag and display a message stating that the card expiry month must be between 1 and 12.

REQ-F-319: [Event-driven] When the program is validating card details after retrieval and the card expiration month is supplied but is not in the range 1–12, the system shall set an error flag and display a message stating that the card expiry month must be between 1 and 12.

REQ-F-320: [Event-driven] When the program is validating card details after retrieval and the card expiration month is in the range 1–12, the system shall mark the month field as valid.

REQ-F-321: [Event-driven] When the program is validating card details after retrieval and the card expiration year is blank, low-value, or zero, the system shall set an error flag and display a message stating that the card expiry year is invalid.

REQ-F-322: [Event-driven] When the program is validating card details after retrieval and the card expiration year is supplied but is not in the range 1950–2099, the system shall set an error flag and display a message stating that the card expiry year is invalid.

REQ-F-323: [Event-driven] When the program is validating card details after retrieval and the card expiration year is in the range 1950–2099, the system shall mark the year field as valid.

REQ-F-324: [Event-driven] When the program needs to retrieve card details for display or update, the system shall read the card record from the primary credit card master file (CARD-FILE) keyed by the 16-character card number; on success, the system shall extract and store the CVV code, cardholder name, expiration date (year, month, day), and active status in the old-details area for later comparison.

REQ-F-325: [Unwanted] If the card record is not found in the primary credit card master file during retrieval, the system shall set error flags indicating that the account-card combination was not found and display an appropriate message.

REQ-F-326: [Unwanted] If a technical error occurs during the card record read operation, the system shall log the error details (operation name, file name, response code, reason code) and display an error message.

REQ-F-327: [Complex] While the transaction is active and awaiting a decision, when the user has submitted input or the program has completed a processing step, the system shall evaluate the current state and user input to determine the next action: if details have not been fetched and the user presses PF12 with valid search criteria, read the card data and mark it for display; if details are being shown and the user has made valid changes, mark the changes as confirmed and awaiting user confirmation; if the user presses PF5 while awaiting confirmation, attempt to write the changes to the database; after the write attempt, if the record could not be locked, mark the update as failed due to lock error; if the record was locked but the update failed, mark the update as failed; if the record was changed by another user, mark the data as changed and redisplay the details; otherwise, mark the update as complete and successful; if the transaction reaches an unexpected state, terminate with error code 9999.

REQ-F-328: [Event-driven] When the user has confirmed changes and requested to save them, the system shall lock the card record in the primary credit card master file for update using the card number as the primary key; if the lock cannot be acquired, the system shall set an error flag indicating that the record could not be locked for update.

REQ-F-329: [Event-driven] When the program has locked the card record for update, the system shall compare the CVV code, cardholder name, expiration date (year, month, day), and active status of the currently locked record against the previously retrieved values; if all fields match, the update shall proceed; if any field differs, the system shall set an error flag indicating that the data was changed before the update and update the old-details area with the current database values.

REQ-F-330: [Ubiquitous] The system shall assemble the updated card record from the new values entered by the user, populating the card number, account ID, CVV code (converted from alphanumeric to numeric), cardholder name, expiration date (formatted as YYYY-MM-DD), and active status.

REQ-F-331: [Event-driven] When the updated card record has been assembled, the system shall write the updated card record to the primary credit card master file; if the write fails, the system shall set an error flag indicating that the update failed.

REQ-F-332: [State-driven] While the screen header has been initialized, the system shall populate the card detail fields based on the current transaction state: leave detail fields blank on first entry; display retrieved card data (cardholder name, status, expiration date) when details have been retrieved; display new values entered by the user when changes have been made; display account ID and card number as low-values if their stored values are zero, otherwise display as entered.

REQ-F-333: [State-driven] While the screen detail fields have been populated, the system shall set the informational message based on the current transaction state: prompt for search criteria on first entry or when details have not been fetched; indicate that card details are shown when details have been retrieved; prompt to update card details when changes failed validation; prompt to press PF5 to save when valid changes are awaiting confirmation; display a confirmation message when the update has completed successfully; display a failure message when the update has failed.

REQ-F-334: [State-driven] While the informational message has been set, the system shall set field protection attributes based on the current transaction state: when details have not been fetched, the account ID and card number fields shall be editable and the detail fields shall be protected; when details are being shown or changes have failed validation, the account ID and card number fields shall be protected and the detail fields shall be editable; when the user is awaiting confirmation or the update is complete, all fields shall be protected.

### Non-Functional Requirements

REQ-N-003: [Event-driven] When the user has confirmed changes and the system has acquired an exclusive lock on the card record, the system shall ensure that the lock, concurrent-modification check, record assembly, and write to the primary credit card master file are performed as a single atomic operation that either succeeds completely or is fully rolled back.

## 28. Main Menu Display and Navigation
As an authenticated user, I want to see the application main menu with current date and time so that I can select a function to perform.

### Requirements

REQ-F-335: [Event-driven] When the program is first entered with no session context (communication area length is zero), the system shall immediately transfer control to the signon screen.

REQ-F-336: [Complex] While session context is present in the communication area, when the program is re-entered with the program context flag set, the system shall receive the user's menu option selection and key press from the screen, capturing response codes for error diagnostics.

REQ-F-337: [Event-driven] When the program is re-entered with the program context flag set and the user presses ENTER, the system shall validate and process the selected menu option.

REQ-F-338: [Event-driven] When the program is re-entered with the program context flag set and the user presses PF3, the system shall set the destination to the signon screen and transfer control.

REQ-F-339: [Event-driven] When the program is re-entered with the program context flag set and the user presses any key other than ENTER or PF3, the system shall display an invalid-key error message and redisplay the menu.

REQ-F-340: [Complex] While session context is present but the program context flag does not indicate re-entry, when the program is entered, the system shall display the initial menu screen.

REQ-F-341: [Ubiquitous] The system shall clear the error flag and message area at the start of menu processing to ensure no stale error state is carried forward.

REQ-F-342: [Ubiquitous] The system shall retrieve the current system date and time and populate the menu screen header with the application title, transaction identifier, program name, current date formatted as MM/DD/YY, and current time formatted as HH:MM:SS.

REQ-F-343: [State-driven] While menu options are available in the menu configuration table, the system shall iterate through each option (up to 12 entries), format each as 'number. name', and populate the corresponding screen display field.

REQ-F-344: [Ubiquitous] The system shall place any pending message in the error message field and send the complete menu screen to the user, erasing prior screen content.

REQ-F-345: [Event-driven] When the user submits a menu option selection, the system shall trim trailing spaces from the option input, replace any remaining spaces with zeros, and convert the result to a numeric option code.

REQ-F-346: [Event-driven] When the user submits a menu option selection, the system shall validate that the option is numeric, is within the range 1 to the maximum menu option count (10), and is not zero; if any validation check fails, the system shall set the error flag and display a validation error message before redisplaying the menu.

REQ-F-347: [Event-driven] When a standard user selects a menu option that requires administrator authorization (option user type is 'A'), the system shall set the error flag, display an access-denied message, and redisplay the menu.

REQ-F-348: [Event-driven] When a user selects a valid, authorized menu option, the system shall display the selected option number in the option echo field, set the message color to green, construct a confirmation message naming the selected option, and redisplay the menu.

### Open Questions

OQ-006: Rule 4617d06a is pre-classified as not applicable (CICS/BMS platform mechanics), yet it describes recording the current transaction ID and program name in the session context, resetting the program context flag to zero, and transferring control to the target program. The business behavior of updating session context before transfer may be a functional requirement independent of the XCTL mechanism. — Owner: modernization architect

## 29. Session Context and Communication Area Handling
As an interactive session manager, I want session context and communication area data correctly loaded and validated on program entry so that navigation state is preserved across screen transitions.

### Requirements

REQ-F-349: [Event-driven] When the program is invoked with a communication area length greater than zero, the system shall copy the caller's communication area into the local communication area structure.

REQ-F-350: [Event-driven] When the program is invoked with session context present, the system shall load the session context from the communication area.

REQ-F-351: [Ubiquitous] The system shall validate that the destination program is set before transferring control; if the destination program is empty, the system shall default the destination to the signon screen program, then transfer control to the destination program.

## 30. Program Navigation and Control Transfer
As a session manager, I want the system to route users to the correct destination screen based on session state and key presses so that unauthenticated users always reach the sign-on screen and authenticated users are directed appropriately.

### Requirements

REQ-F-352: [Event-driven] When the program is invoked without a session context (communication area), the system shall set the destination to the sign-on screen and transfer control to it.

REQ-F-353: [Complex] While the program is being re-entered after a previous interaction, when the user presses PF3, the system shall set the destination to the menu screen.

REQ-F-354: [Ubiquitous] The system shall validate the destination program before transferring control; if the destination is empty or blank, the system shall default it to the sign-on screen.

REQ-F-355: [Ubiquitous] The system shall populate the session context with the current transaction identifier, current program name, and a cleared program context flag before transferring control to the destination program.

---

## 31. Transaction Report Submission and Validation
As a reporting user, I want to select a report type, provide date parameters, and submit a transaction report job so that the correct report is generated for the selected period.

### Requirements

REQ-F-356: [Ubiquitous] The system shall populate the screen header with the current system date formatted as MM/DD/YY, the current system time formatted as HH:MM:SS, the transaction identifier, the program name, and the application branding titles "AWS Mainframe Modernization" and "CardDemo".

REQ-F-357: [Ubiquitous] The system shall receive user input from the transaction report screen and capture the response code and reason code for subsequent processing.

REQ-F-358: [Event-driven] When the user presses Enter and selects the monthly report type, the system shall set the report name to "Monthly", set the start date to the first day of the current month, and set the end date to the last day of the current month.

REQ-F-359: [Event-driven] When the user presses Enter and selects the yearly report type, the system shall set the report name to "Yearly", set the start date to January 1st of the current year, and set the end date to December 31st of the current year.

REQ-F-360: [Event-driven] When the user presses Enter and selects the custom date range report type, the system shall validate that all six date component fields (start month, start day, start year, end month, end day, end year) are not empty; if any field is empty, the system shall display an appropriate error message and redisplay the screen.

REQ-F-361: [Event-driven] When the custom date range option is selected and all six date component fields are present, the system shall convert each component to numeric form and validate that month values are numeric and not greater than 12, day values are numeric and not greater than 31, and year values are numeric; for each validation failure, the system shall display an appropriate error message and redisplay the screen.

REQ-F-362: [Event-driven] When the custom date range fields pass numeric validation, the system shall invoke the date validation service with the start date in YYYY-MM-DD format; if the service returns a failure code other than '2513', the system shall display an appropriate error message and redisplay the screen.

REQ-F-363: [Event-driven] When the start date passes validation, the system shall invoke the date validation service with the end date in YYYY-MM-DD format; if the service returns a failure code other than '2513', the system shall display an appropriate error message and redisplay the screen.

REQ-F-364: [Event-driven] When the user presses Enter and no report type is selected, the system shall display an appropriate error message and redisplay the screen.

REQ-F-365: [Complex] While the user has selected a report type and provided date parameters, when the program is ready to submit the job, the system shall validate that the user has provided a confirmation value (Y/N); if no confirmation value is provided, the system shall display a confirmation prompt and redisplay the screen.

REQ-F-366: [Complex] While awaiting job submission confirmation, when the user enters 'N' or 'n', the system shall initialize all input fields, set the error flag, and redisplay the screen.

REQ-F-367: [Complex] While awaiting job submission confirmation, when the user enters any value other than 'Y', 'y', 'N', or 'n', the system shall display an error message, set the error flag, and redisplay the screen.

REQ-F-368: [Complex] While awaiting job submission confirmation, when the user enters 'Y' or 'y' and the error flag is not set, the system shall iterate through the job data lines and write each line to the job submission queue until an end-of-file marker, a blank line, or an error condition is encountered.

REQ-F-369: [Event-driven] When a JCL record is written to the job submission queue and the write fails, the system shall set the error flag to 'Y' and display the error message "Unable to Write TDQ (JOBS)..." and redisplay the screen.

REQ-F-370: [Event-driven] When all job lines are successfully submitted and the error flag is not set, the system shall initialize all input fields, set the error message color to green, display a success message indicating the report has been submitted for printing, and redisplay the screen.

---

## 32. Date Validation Service
As a report submission handler, I want date inputs validated against a format mask so that only well-formed dates are accepted for report parameters.

### Requirements

REQ-F-371: [Ubiquitous] The date validation service shall accept an input date string, a date format mask, and a result buffer from the caller.

REQ-F-372: [Event-driven] When the date and format mask are prepared, the date validation service shall invoke the date conversion service to convert the date to Lillian format and capture the feedback code, severity level, and message number from the response.

REQ-F-373: [Event-driven] When the feedback code is returned from the date conversion service, the date validation service shall map the feedback code to a human-readable result message: 'Date is valid' for a valid date, 'Insufficient' for insufficient data, 'Datevalue error' for an invalid date value, 'Invalid Era' for an invalid era, 'Unsupp. Range' for an unsupported range, 'Invalid month' for an invalid month, 'Bad Pic String' for an invalid format string, 'Nonnumeric data' for non-numeric input, 'YearInEra is 0' for a zero year in era, and 'Date is invalid' for all other conditions.

REQ-F-374: [Ubiquitous] The date validation service shall return the complete diagnostic record containing the validation result, severity, message number, date, and format mask to the caller's result buffer.

## 33. Sign-on Screen Display and User Authentication
As an end user, I want to be presented with a sign-on screen that collects my credentials and authenticates me so that I can access the appropriate portal for my user type.

### Requirements

REQ-F-375: [Event-driven] When the sign-on screen is to be displayed, the system shall retrieve the current system date and time, format them as MM/DD/YY and HH:MM:SS respectively, populate the screen with application branding, transaction identifier, program name, application identifier, system identifier, formatted date, and formatted time.

REQ-F-376: [Ubiquitous] The system shall clear the error message field to spaces before displaying the sign-on screen, removing any stale messages from prior sessions.

REQ-F-377: [Ubiquitous] The system shall clear the error flag to indicate no error condition at the start of sign-on processing.

REQ-F-378: [Event-driven] When no prior session context exists, the system shall display the sign-on screen to collect user credentials.

REQ-F-379: [Event-driven] When the user submits the sign-on screen, the system shall validate that the user ID field is not blank; if the user ID is absent, the system shall set the error flag and redisplay the sign-on screen with the message "Please enter User ID ...".

REQ-F-380: [Event-driven] When the user submits the sign-on screen and the user ID is present, the system shall validate that the password field is not blank; if the password is absent, the system shall set the error flag and redisplay the sign-on screen with the message "Please enter Password ...".

REQ-F-381: [Complex] While no input validation errors have been detected, when the user submits the sign-on screen with both user ID and password present, the system shall convert the user ID and password to uppercase, store them in session working storage and the shared session context, then look up the user record in the user security data store (AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS) by user ID (up to 8 alphanumeric characters).

REQ-F-382: [Event-driven] When the user security data store lookup succeeds, the system shall compare the stored password (up to 8 alphanumeric characters) against the entered password; if the passwords do not match, the system shall redisplay the sign-on screen with the message "Wrong Password. Try again ...".

REQ-F-383: [Event-driven] When the user security data store lookup returns a record-not-found response, the system shall redisplay the sign-on screen with the message "User not found. Try again ...".

REQ-F-384: [Unwanted] If the user security data store read fails with an unexpected error, the system shall redisplay the sign-on screen with the message "Unable to verify the User ...".

REQ-F-385: [Event-driven] When authentication succeeds and the stored password matches the entered password, the system shall populate the shared session context with the transaction identifier, program name, user ID, user type, and a program context flag set to zero indicating successful authentication.

REQ-F-386: [Event-driven] When authentication succeeds and the user type is "A" (administrator), the system shall transfer control to the administrator portal.

REQ-F-387: [Event-driven] When authentication succeeds and the user type is not "A", the system shall transfer control to the customer portal.

REQ-F-388: [Event-driven] When the user presses an unrecognized key on the sign-on screen, the system shall redisplay the sign-on screen with the message "Invalid key pressed. Please see below ...".

## 34. Transaction List Display and Pagination
As a CardDemo user, I want to view a paginated list of transactions and navigate forward and backward through pages so that I can find and select a specific transaction for further processing.

### Requirements

REQ-F-389: [Event-driven] When the program is invoked with no communication area, the system shall set the destination program to the sign-on screen and transfer control to it.

REQ-F-390: [Event-driven] When the program is re-entered with a communication area, the system shall copy the incoming communication area into the local working record, preserving the navigation context (originating transaction ID, calling program name, and program re-entry flag).

REQ-F-391: [Complex] While the program is re-entered with an existing session context, when the user presses a function key, the system shall receive the screen input and dispatch as follows: process Enter for transaction selection, process PF7 for backward pagination, process PF8 for forward pagination, or display an invalid-key error message and redisplay the screen for any other key.

REQ-F-392: [Event-driven] When the program context flag indicates first-time entry, the system shall set the re-entry flag and display the transaction list screen.

REQ-F-393: [Ubiquitous] The system shall retrieve the current system date and time, reformat the date to MM/DD/YY and the time to HH:MM:SS, and populate the screen header with the title lines, transaction name, program name, formatted date, and formatted time.

REQ-F-394: [Ubiquitous] The system shall clear all 10 transaction row display fields (transaction ID, date, description, and amount) before populating a new page of transactions.

REQ-F-395: [Ubiquitous] The system shall format each retrieved transaction record for display by reformatting the transaction date from YYYY-MM-DD to MM/DD/YY, and populate the corresponding row (1–10) on the screen with the transaction ID, reformatted date, description, and formatted amount. For row 1, the system shall also store the transaction ID in the session context as the first transaction ID on the page. For row 10, the system shall also store the transaction ID in the session context as the last transaction ID on the page.

REQ-F-396: [Event-driven] When forward pagination is initiated and the transaction data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS) browse position is not found, the system shall display the message 'You are at the top of the page...' and mark the transaction ID input field as being in error state.

REQ-F-397: [Event-driven] When forward pagination is initiated and the transaction data store returns an error other than not-found, the system shall display the message 'Unable to lookup transaction...' and mark the transaction ID input field as being in error state.

REQ-F-398: [Event-driven] When reading the next transaction record in forward sequence and the end of the transaction data store is reached, the system shall display the message 'You have reached the bottom of the page...' and mark the transaction ID input field as being in error state.

REQ-F-399: [Event-driven] When reading the next transaction record in forward sequence and an error other than end-of-file occurs, the system shall display the message 'Unable to lookup transaction...' and mark the transaction ID input field as being in error state.

REQ-F-400: [Event-driven] When reading the previous transaction record in reverse sequence and the beginning of the transaction data store is reached, the system shall display the message 'You have reached the top of the page...' and mark the transaction ID input field as being in error state.

REQ-F-401: [Event-driven] When reading the previous transaction record in reverse sequence and an error other than end-of-file occurs, the system shall display the message 'Unable to lookup transaction...' and mark the transaction ID input field as being in error state.

REQ-F-402: [Event-driven] When the user presses PF7 and the current page number is 1, the system shall display the message 'You are already at the top of the page...' and redisplay the screen without clearing it.

REQ-F-403: [Event-driven] When the user presses PF8 and the next-page flag is not set, the system shall display the message 'You are already at the bottom of the page...' and redisplay the screen without clearing it.

---

## 35. Transaction Selection and Navigation Routing
As a CardDemo user, I want to select a transaction from the list and be routed to the transaction detail screen so that I can view the full details of the selected transaction.

### Requirements

REQ-F-404: [Event-driven] When the user presses Enter, the system shall evaluate each of the 10 transaction row selection indicators; for each row (1–10) where the selection indicator is not empty and not low-values, the system shall capture the selection flag and the corresponding transaction ID into the session context.

REQ-F-405: [Event-driven] When no transaction option is selected (all selection indicators are empty or low-values), the system shall clear both the transaction selection flag and the selected transaction ID in the session context.

REQ-F-406: [Event-driven] When the user presses Enter and the transaction ID search field is empty or low-values, the system shall set the transaction ID lookup value to low-values to retrieve all transactions.

REQ-F-407: [Event-driven] When the user presses Enter and the transaction ID search field contains non-numeric characters, the system shall display the error message 'Tran ID must be Numeric ...', mark the transaction ID input field as being in error state, and redisplay the screen without further processing.

REQ-F-408: [Event-driven] When the user presses Enter and the transaction ID search field contains a numeric value, the system shall move the value to the lookup field, reset the page number to 0, initiate forward pagination to retrieve the first matching page, and clear the transaction ID search field on the screen if no error occurred.

REQ-F-409: [Event-driven] When a valid transaction selection is present in the session context (selection flag is 'S' or 's' and transaction ID is not empty), the system shall set the destination program to the transaction detail program, record the current transaction ID and program name as the originating context, reset the program context flag to zero, and transfer control to the destination program.

---

## 36. Transaction Navigation and Screen Control
As a CardDemo user, I want consistent navigation between screens so that I can return to the menu or sign-on screen at any point during my session.

### Requirements

REQ-F-410: [Event-driven] When the user presses PF3, the system shall set the destination program to the menu screen and transfer control to it.

REQ-F-411: [Ubiquitous] The system shall populate the session context with the current transaction ID and program name as the originating context, and reset the program context flag to zero before transferring control to any destination program.

REQ-F-412: [Unwanted] If the destination program field is empty or contains only spaces or low-values before a control transfer, the system shall set the destination program to the sign-on screen.

REQ-F-413: [Ubiquitous] The system shall transfer control to the destination program, passing the complete session context.

---

## 37. Transaction Detail View (Invoked Program)
As a CardDemo user, I want to view the full details of a selected transaction so that I can review transaction, merchant, and card information.

### Requirements

REQ-F-414: [Event-driven] When the transaction detail program is invoked with no communication area, the system shall set the destination program to the sign-on program and transfer control to it.

REQ-F-415: [Event-driven] When the transaction detail program receives a communication area from the caller, the system shall copy the incoming communication area into the local data structure.

REQ-F-416: [Event-driven] When the user presses Enter on the transaction detail screen and the transaction ID field is empty, the system shall set the error flag, display an error message, and redisplay the screen.

REQ-F-417: [Event-driven] When the user presses Enter on the transaction detail screen and the transaction ID is not empty, the system shall move the transaction ID to the lookup key and retrieve the matching transaction record from the transaction data store (legacy: AWS.M2.CARDDEMO.TRANSACT.VSAM.KSDS).

REQ-F-418: [State-driven] While the transaction record is successfully retrieved (error flag is off), the system shall clear all transaction detail display fields and populate them with the retrieved data (transaction ID, card number, transaction type code, category code, source, amount, description, original timestamp, processed timestamp, merchant ID, merchant name, merchant city, and merchant ZIP code), then send the populated screen.

REQ-F-419: [Event-driven] When the transaction data store read returns a not-found response, the system shall set the error flag and display the error message 'Transaction ID NOT found...'.

REQ-F-420: [Event-driven] When the transaction data store read returns an error response other than not-found, the system shall set the error flag and display the error message 'Unable to lookup Transaction...'.

REQ-F-421: [Event-driven] When the user presses PF3 on the transaction detail screen and an originating program is recorded in the session context, the system shall route back to the originating program; if no originating program is recorded, the system shall route to the menu program, then transfer control.

REQ-F-422: [Event-driven] When the user presses PF4 on the transaction detail screen, the system shall clear all transaction detail fields to spaces and send the cleared screen.

REQ-F-423: [Event-driven] When the user presses PF5 on the transaction detail screen, the system shall set the destination program to the main transaction program and transfer control to it.

REQ-F-424: [Event-driven] When the user presses any key other than Enter, PF3, PF4, or PF5 on the transaction detail screen, the system shall display an invalid-key error message and redisplay the screen.

REQ-F-425: [Ubiquitous] The system shall capture the current system date and time and populate the transaction detail screen header with the title lines, transaction ID, program name, date formatted as MM/DD/YY, and time formatted as HH:MM:SS.

REQ-F-426: [Unwanted] If the destination program field is empty or contains low-values before a control transfer in the transaction detail program, the system shall default the destination to the sign-on program.

## 38. Program Navigation and Control Transfer
As a user of the card demo transaction system, I want the application to route me to the correct screen based on my navigation actions so that I can move between functions without losing context.

### Requirements

REQ-F-427: [Event-driven] When the program is invoked with no session context (no communication area), the system shall set the destination to the sign-on program and transfer control to it.

REQ-F-428: [Event-driven] When the program receives a communication area from the caller, the system shall copy the incoming communication area into the local session context, preserving the originating program, originating transaction ID, and program re-entry flag.

REQ-F-429: [Event-driven] When the user presses PF5 during a re-entry, the system shall set the destination to the main transaction program and transfer control to it.

REQ-F-430: [Event-driven] When the user presses PF3 during a re-entry, the system shall check whether an originating program is recorded in the session context; if the originating program field is empty or contains no value, the system shall route to the menu program; otherwise the system shall route back to the recorded originating program.

REQ-F-431: [Ubiquitous] Before transferring control to any destination, the system shall validate the destination program field; if the destination is empty or contains no value, the system shall default it to the sign-on program.

REQ-F-432: [Ubiquitous] Before transferring control to any destination, the system shall record the current transaction ID and current program name as the originating context, clear the program re-entry flag, and transfer control to the destination program passing the updated session context.

---

## 39. Transaction Inquiry Screen — Input Handling and Navigation
As a card demo user, I want to enter a transaction ID and retrieve its details so that I can review transaction and merchant information.

### Requirements

REQ-F-433: [Event-driven] When the session context length is greater than zero, the system shall receive the communication area from the caller and store it as the local session context.

REQ-F-434: [Event-driven] When the program is re-entering with user input from the screen, the system shall receive user input and dispatch based on the key pressed: if ENTER, validate the transaction ID; if PF4, clear all transaction detail fields and re-send the screen; if any other key, display an invalid-key error message and re-send the screen.

REQ-F-435: [Event-driven] When the user presses ENTER, the system shall validate that the transaction ID input is not empty; if the transaction ID is empty (spaces or no value), the system shall set the error flag, store an error message, and re-send the screen.

REQ-F-436: [State-driven] While the transaction ID validation succeeds (error flag is off), the system shall clear all transaction detail display fields — including transaction ID, card number, type code, category code, source, amount, description, original timestamp, processed timestamp, merchant ID, merchant name, merchant city, and merchant ZIP — to spaces.

REQ-F-437: [State-driven] While the transaction ID is validated and detail fields are cleared, the system shall move the user-entered transaction ID to the lookup key and retrieve the matching transaction record from the transaction master data store (primary VSAM KSDS storing all credit card transaction records, keyed by transaction ID).

REQ-F-438: [State-driven] While the transaction record is successfully retrieved (error flag is off), the system shall populate all transaction detail display fields with the retrieved data — transaction ID (16-character alphanumeric), card number (16-character alphanumeric), transaction type code (2-character alphanumeric), category code (4-digit numeric), source (10-character alphanumeric), amount (11-digit decimal), description (100-character alphanumeric), original timestamp (26-character alphanumeric), processed timestamp (26-character alphanumeric), merchant ID (9-digit numeric), merchant name (50-character alphanumeric), merchant city (50-character alphanumeric), and merchant ZIP (10-character alphanumeric) — and send the populated screen to the user.

REQ-F-439: [Unwanted] If the transaction data store read returns a not-found response, the system shall set the error flag and display the error message 'Transaction ID NOT found...' and re-send the screen.

REQ-F-440: [Unwanted] If the transaction data store read returns an error response other than not-found, the system shall set the error flag and display the error message 'Unable to lookup Transaction...' and re-send the screen.

REQ-F-441: [Ubiquitous] The system shall capture the current system date and time and populate the screen header with the transaction ID, program name, current date formatted as MM/DD/YY, and current time formatted as HH:MM:SS before sending the screen to the user.

REQ-F-442: [Ubiquitous] The system shall receive user input from the transaction inquiry screen and capture the response codes from the receive operation.

REQ-F-443: [Event-driven] When the program is invoked for the first time (not a re-entry) and a pre-selected transaction ID exists in the session context, the system shall process that transaction ID immediately; otherwise the system shall send the blank transaction inquiry screen.

---

## 40. Transaction List — Pagination, Selection, and Navigation (Subroutine)
As a card demo user, I want to browse a paginated list of transactions, search by transaction ID, and select a transaction for detail inquiry so that I can locate and review specific transactions.

### Requirements

REQ-F-444: [Event-driven] When the program is invoked with no communication area, the system shall set the destination to the sign-on screen and transfer control to it.

REQ-F-445: [Event-driven] When the program is invoked with a communication area, the system shall restore the session context from the caller's communication area, preserving the originating transaction ID, calling program name, and any prior transaction selections.

REQ-F-446: [Complex] While the program is re-entered with an existing session context, when a function key is pressed, the system shall receive the screen input and dispatch: ENTER processes transaction selection and search; PF7 initiates backward pagination; PF8 initiates forward pagination; any other key displays an invalid-key error message and redisplays the screen.

REQ-F-447: [Event-driven] When the user presses PF3, the system shall set the destination to the menu screen and transfer control to it.

REQ-F-448: [Event-driven] When the user presses ENTER, the system shall evaluate which of the ten transaction rows (options 1–10) the user selected; for each row where the selection indicator is not empty and not a no-value, the system shall capture the selection flag and the corresponding transaction ID into the session context.

REQ-F-449: [Event-driven] When no transaction option is selected on the screen, the system shall clear both the transaction selection flag and the selected transaction ID in the session context.

REQ-F-450: [Event-driven] When the user presses ENTER and the transaction ID search field is empty or contains no value, the system shall set the search key to no-value to retrieve all transactions.

REQ-F-451: [Event-driven] When the user presses ENTER and the transaction ID search field contains non-numeric characters, the system shall display the error message 'Tran ID must be Numeric ...', set the transaction ID input field to error state, and redisplay the screen without further processing.

REQ-F-452: [Event-driven] When the user presses ENTER and the transaction ID search field is numeric, the system shall move the value to the lookup field, reset the page number to 0, initiate forward pagination to retrieve the first matching page, and clear the search field on the screen if no error occurred.

REQ-F-453: [Event-driven] When a valid transaction selection is confirmed with selection flag 'S' or 's', the system shall set the target program to the transaction detail inquiry program, record the current transaction ID and program name as the originating context, reset the program context flag to zero, and transfer control to the target program via the session context.

REQ-F-454: [Unwanted] If the destination program field is empty or contains only spaces or no value before a transfer, the system shall set the destination to the sign-on screen.

REQ-F-455: [Ubiquitous] Before transferring control to any destination, the system shall populate the session context with the current program's transaction ID ('CT00'), current program name ('COTRN00C'), and reset the program context flag to zero.

REQ-F-456: [Ubiquitous] The system shall transfer control to the destination program specified in the session context, passing the complete session context.

REQ-F-457: [Ubiquitous] The system shall retrieve the current system date and time and populate the transaction list screen header with the title lines, transaction name, program name, current date formatted as MM/DD/YY, and current time formatted as HH:MM:SS.

REQ-F-458: [Ubiquitous] The system shall receive the transaction list screen input from the user and capture the response code and reason code from the receive operation.

REQ-F-459: [Ubiquitous] Before populating a new page of transactions, the system shall clear all 10 transaction row display fields — transaction ID, date, description, and amount — to spaces for each row.

REQ-F-460: [Ubiquitous] When a transaction record is successfully retrieved for display, the system shall format the transaction amount for display, reformat the transaction date from YYYY-MM-DD to MM/DD/YY, and populate the corresponding row (1–10) on the screen with the transaction ID, reformatted date, description, and formatted amount; for row 1, the system shall also store the transaction ID as the first transaction ID on the page; for row 10, the system shall also store the transaction ID as the last transaction ID on the page.

REQ-F-461: [Event-driven] When the program context flag indicates first-time entry, the system shall set the re-entry flag and display the transaction list screen.

REQ-F-462: [Unwanted] If forward pagination positioning against the transaction master data store returns a not-found response, the system shall set the end-of-file flag, display the message 'You are at the top of the page...', set the transaction ID input field to error state, and redisplay the screen.

REQ-F-463: [Unwanted] If forward pagination positioning against the transaction master data store returns an error response other than not-found, the system shall set the error flag, display the message 'Unable to lookup transaction...', set the transaction ID input field to error state, and redisplay the screen.

REQ-F-464: [Unwanted] If a sequential forward read of the transaction master data store reaches end-of-file, the system shall set the end-of-file flag, display the message 'You have reached the bottom of the page...', set the transaction ID input field to error state, and redisplay the screen.

REQ-F-465: [Unwanted] If a sequential forward read of the transaction master data store returns an error response other than end-of-file, the system shall set the error flag, display the message 'Unable to lookup transaction...', set the transaction ID input field to error state, and redisplay the screen.

REQ-F-466: [Unwanted] If a sequential backward read of the transaction master data store reaches end-of-file, the system shall set the end-of-file flag, display the message 'You have reached the top of the page...', set the transaction ID input field to error state, and redisplay the screen.

REQ-F-467: [Unwanted] If a sequential backward read of the transaction master data store returns an error response other than end-of-file, the system shall set the error flag, display the message 'Unable to lookup transaction...', set the transaction ID input field to error state, and redisplay the screen.

### Open Questions

OQ-007: Rule 44163b82 describes first-time entry behavior that includes both screen initialization (noise) and a conditional branch — if a pre-selected transaction ID exists, process it immediately; otherwise send the blank screen. The condition for what constitutes a "pre-selected transaction ID" in the session context is not fully specified. Owner: application team.

OQ-008: Rules for backward pagination (9f5fde90) and forward pagination (a64c9ab5) were classified as platform mechanics in the subroutine's not_applicable list, yet they contain business-meaningful boundary messages ('You are already at the top of the page...', 'You are already at the bottom of the page...'). Confirm whether these boundary-detection messages and the associated page-number guard conditions should be expressed as functional requirements. Owner: product owner.

## 41. Card Demo Transaction Navigation
As a user of the CardDemo application, I want the system to correctly route me to the appropriate screen based on my session context and key presses so that I can navigate the application efficiently.

### Requirements

REQ-F-468: [Event-driven] When the program is invoked without a communication area, the system shall set the destination to the signon screen and transfer control to it.

REQ-F-469: [Event-driven] When a communication area is provided by the caller, the system shall copy the caller's communication area into the local session context for processing.

REQ-F-470: [Complex] While the program is being re-entered after a previous interaction, when the user presses PF3 and a calling program is recorded in the session context, the system shall set the destination to that calling program.

REQ-F-471: [Complex] While the program is being re-entered after a previous interaction, when the user presses PF3 and no calling program is recorded in the session context, the system shall set the destination to the menu screen.

REQ-F-472: [Ubiquitous] The system shall validate the destination program before transferring control; if the destination program field is empty, the system shall default the destination to the signon screen.

REQ-F-473: [Ubiquitous] The system shall record the current transaction identifier as the originating transaction, record the current program name as the originating program, reset the program context flag to zero, and transfer control to the destination program with the updated session context.

---

## 42. Transaction Entry and Validation Workflow
As a CardDemo user, I want to add new credit card transactions with validated account, card, and transaction details so that only accurate and complete transactions are recorded.

### Requirements

REQ-F-474: [Event-driven] When the user submits the transaction entry screen, the system shall receive all field values entered by the user and store the response code and reason code from the receive operation.

REQ-F-475: [Ubiquitous] The system shall populate the screen header with the current system date formatted as MM/DD/YY, the current system time formatted as HH:MM:SS, the screen titles, and the current program name before displaying the transaction entry screen.

REQ-F-476: [Ubiquitous] The system shall move the current message text to the error message output field and display the transaction entry screen to the user.

REQ-F-477: [Event-driven] When the user provides an account identifier for lookup, the system shall retrieve the associated card number from the card-to-account cross-reference file (legacy: CXACAIX) using the account identifier as the key.

REQ-F-478: [Unwanted] If the account identifier lookup returns a not-found result, the system shall set an error flag and display an account identifier not found error message.

REQ-F-479: [Unwanted] If the account identifier lookup fails for any reason other than not found, the system shall set an error flag and display a lookup error message.

REQ-F-480: [Event-driven] When the user provides a card number for lookup, the system shall retrieve the associated account identifier from the card cross-reference file (legacy: CCXREF) using the card number as the key.

REQ-F-481: [Unwanted] If the card number lookup returns a not-found result, the system shall set an error flag and display a card number not found error message.

REQ-F-482: [Unwanted] If the card number lookup fails for any reason other than not found, the system shall set an error flag and display a lookup error message.

REQ-F-483: [Event-driven] When the user presses PF5 to copy the last transaction, the system shall validate the key fields to establish the account or card context, position to the end of the transaction master file (legacy: TRANSACT), and read the previous record to retrieve the most recent transaction.

REQ-F-484: [Event-driven] When retrieval of the most recent transaction succeeds, the system shall copy the transaction type code, category code, source, description, amount, original date, process date, merchant identifier, merchant name, merchant city, and merchant ZIP from the retrieved record to the corresponding screen input fields, then process the Enter key to allow the user to confirm or modify the pre-populated data.

REQ-F-485: [Event-driven] When the program needs to retrieve the most recent transaction record, the system shall read the previous transaction record from the browse cursor; if the read fails with end of file, the system shall set the transaction identifier to zeros; if the read fails for any other reason, the system shall set an error flag and display a lookup error message.

REQ-F-486: [Event-driven] When the program needs to locate the highest transaction identifier, the system shall position a browse cursor to the end of the transaction master file using the maximum key value; if positioning fails with not found, the system shall set an error flag and display a transaction identifier not found error message; if positioning fails for any other reason, the system shall set an error flag and display a lookup error message.

REQ-F-487: [Event-driven] When the user submits the transaction entry form, the system shall validate that the account identifier, when provided, is numeric and that the card number, when provided, is numeric; if either field is non-numeric, the system shall display an error message and highlight the offending field; if neither field is provided, the system shall display an error message requiring at least one field to be entered.

REQ-F-488: [Event-driven] When the user submits the transaction entry form with data fields, the system shall validate that transaction type code, category code, source, description, amount, original date, process date, merchant identifier, merchant name, merchant city, and merchant ZIP are not empty; validate that transaction type code and category code are numeric; validate that transaction amount conforms to the format -99999999.99; validate that original date and process date conform to the format YYYY-MM-DD; validate that merchant identifier is numeric; and for each validation failure, display an error message and highlight the offending field.

REQ-F-489: [Event-driven] When the transaction original date or process date requires validation, the system shall invoke the date validation utility with the date in YYYY-MM-DD format; if the utility returns severity code '0000', the date is valid; if the utility returns a non-zero severity code and the message number is not '2513', the system shall display an error message and highlight the date field.

REQ-F-490: [Event-driven] When the user enters a confirmation value, the system shall evaluate the confirmation field: if the value is 'Y' or 'y', proceed to add the transaction; if the value is 'N', 'n', spaces, or empty, display a confirmation prompt and highlight the confirmation field; if the value is any other character, display an error message indicating that valid values are Y or N and highlight the confirmation field.

REQ-F-491: [Event-driven] When the user confirms the transaction addition, the system shall position to the end of the transaction master file and read the previous record to obtain the highest transaction identifier, increment the transaction identifier by one to generate a new unique identifier (16-character alphanumeric), initialize a new transaction record, populate all fields from the screen input (transaction identifier, type code, category code, source, description, amount, card number, merchant identifier, merchant name, merchant city, merchant ZIP, original date, and process date), and write the assembled record to the transaction master file.

REQ-F-492: [Event-driven] When the transaction record write completes normally, the system shall initialize all screen fields, set the error message color to green, construct a success message containing the new transaction identifier, and display the screen.

REQ-F-493: [Unwanted] If the transaction record write fails with a duplicate key or duplicate record condition, the system shall display a duplicate transaction identifier error message and highlight the account identifier field.

REQ-F-494: [Unwanted] If the transaction record write fails for any other reason, the system shall display a generic write error message and highlight the account identifier field.

REQ-F-495: [Complex] While the program is invoked as an interactive transaction, when the program is first entered, the system shall display the transaction entry screen with the cursor positioned at the account identifier field.

REQ-F-496: [Complex] While the program is invoked as an interactive transaction, when the program is re-entered, the system shall dispatch to the appropriate handler based on the key pressed: Enter for transaction processing, PF3 for exit, PF4 for screen clear, PF5 for copy last transaction, or display an invalid key message for any other key.

REQ-F-497: [Event-driven] When the user presses PF4 to clear the screen, the system shall initialize all input fields to spaces, reset the cursor position to the account identifier field, and display the cleared screen.

## 43. Program Navigation and Screen Transition Control
As a session manager, I want the application to route users to the correct screen based on session context and function-key input so that navigation is consistent and users without an active session are always directed to authentication.

### Requirements

REQ-F-498: [Event-driven] When the program is invoked without a communication area, the system shall set the destination program to the signon screen and transfer control to it.

REQ-F-499: [Event-driven] When the program is re-entered with a communication area, the system shall copy the communication area into local working storage to establish the navigation context.

REQ-F-500: [Ubiquitous] The system shall initialize the next-page flag to indicate no additional pages are available at program entry.

REQ-F-501: [Complex] While the program is re-entered after a previous interaction, when the user presses PF3, the system shall set the destination program to the administration program and transfer control.

REQ-F-502: [Ubiquitous] Before transferring control to any destination program, the system shall populate the communication area with the current transaction ID, the current program name, and reset the program-context flag to zero to indicate first entry for the destination program.

REQ-F-503: [Unwanted] If the destination program name is empty or spaces before a control transfer, the system shall set the destination program to the signon screen.

REQ-F-504: [Ubiquitous] The system shall transfer control to the destination program specified in the communication area, passing the populated communication area as context.

---

## 44. User List Display and Pagination
As an administrator, I want to browse a paginated list of up to 10 users per page from the user security store so that I can locate, select, and act on user records efficiently.

### Requirements

REQ-F-505: [Ubiquitous] The system shall populate the screen header with the current system date formatted as MM/DD/YY and the current system time formatted as HH:MM:SS, along with the application titles, transaction ID, and program name.

REQ-F-506: [Ubiquitous] When populating a user data row, the system shall move the user ID (up to 8 characters), first name (up to 20 characters), last name (up to 20 characters), and user type (1 character) from the user security store (legacy: AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS) record to the corresponding row fields on the screen; for the first row, the system shall also store the user ID in the communication area as the first user ID of the page; for the tenth row, the system shall also store the user ID in the communication area as the last user ID of the page.

REQ-F-507: [Event-driven] When forward-page processing reads the next record from the user security store, the system shall retrieve the next record in key sequence; if end-of-file is reached, the system shall set the end-of-file flag and display the message 'You have reached the bottom of the page...'; if any other error occurs, the system shall set the error flag and display the message 'Unable to lookup User...'.

REQ-F-508: [Event-driven] When backward-page processing reads the previous record from the user security store, the system shall retrieve the previous record in key sequence; if end-of-file is reached, the system shall set the end-of-file flag and display the message 'You have reached the top of the page...'; if any other error occurs, the system shall set the error flag and display the message 'Unable to lookup User...'.

REQ-F-509: [Event-driven] When the user presses the Enter key, the system shall evaluate which of the 10 user rows has a selection indicator, capture the selection action code and user ID from that row, validate that the selection action is 'U', 'u', 'D', or 'd', process the user ID search field (resetting to low-values if empty or capturing the entered value otherwise), reset the page number to 0, and invoke forward-page processing to retrieve matching users.

REQ-F-510: [Unwanted] If an invalid selection action code (not 'U', 'u', 'D', or 'd') is detected when the user presses Enter, the system shall set an error message indicating the invalid selection.

REQ-F-511: [Event-driven] When forward-page processing completes without error, the system shall clear the user ID input field on the screen.

REQ-F-512: [Event-driven] When the user presses PF7 and the current page number is greater than 1, the system shall set the search key to the first user ID of the current page (or low-values if empty), set the next-page flag to indicate pages are available, and invoke backward-page processing to retrieve the previous page.

REQ-F-513: [Unwanted] If the user presses PF7 and the current page number is not greater than 1, the system shall display the message 'You are already at the top of the page...' without erasing the screen.

REQ-F-514: [Event-driven] When the user presses PF8 and the next-page flag indicates pages are available, the system shall set the search key to the last user ID of the current page (or high-values if empty) and invoke forward-page processing to retrieve the next page.

REQ-F-515: [Unwanted] If the user presses PF8 and the next-page flag indicates no further pages are available, the system shall display the message 'You are already at the bottom of the page...' without erasing the screen.

REQ-F-516: [Unwanted] If the user presses an unrecognized or invalid function key, the system shall display the error message 'Invalid key pressed. Please see below...' and redisplay the screen.

REQ-F-517: [Event-driven] When a browse of the user security store is initiated and the search key is not found, the system shall set the end-of-file flag and display the message 'You are at the top of the page...'; if any other error occurs, the system shall set the error flag and display the message 'Unable to lookup User...'.

---

## 45. User Selection and Program Navigation
As an administrator, I want to select a user from the list and be routed to the appropriate view or delete screen so that I can perform the intended action on the selected user record.

### Requirements

REQ-F-518: [Ubiquitous] The system shall scan all 10 user list rows, extract the first non-empty selection flag and corresponding user ID, and store them in the shared session context; if no row is selected, the system shall clear both the selection flag and user ID in the session context.

REQ-F-519: [Event-driven] When the program is invoked with a non-empty communication area and the program-context flag indicates first entry, the system shall copy the caller's context from the communication area, set the program-context flag to re-entry mode, clear the screen output buffer, and process the initial user selection from the screen input.

REQ-F-520: [Event-driven] When the program is re-entered with a non-empty communication area and the program-context flag is already set, the system shall receive the updated screen input and dispatch based on the attention identifier pressed.

REQ-F-521: [Event-driven] When the user selection flag indicates a view action ('U' or 'u') and a user ID is selected, the system shall set the destination program to the user detail handler, populate the session context with the current transaction ID and program name, reset the program-context flag to first-entry state, and transfer control to the user detail handler.

REQ-F-522: [Event-driven] When the user selection flag indicates a delete action ('D' or 'd') and a user ID is selected, the system shall set the destination program to the delete-user handler, populate the communication area with the current transaction ID and program name, reset the program-context flag to first-entry state, and transfer control to the delete-user handler.

---

## 46. User Record Update (Invoked Program)
As an administrator, I want to view and update a user's security record so that user details remain accurate and access controls are current.

### Requirements

REQ-F-523: [Event-driven] When the user update program is invoked with an empty communication area, the system shall set the destination program to the signon screen and transfer control to it.

REQ-F-524: [Event-driven] When the user update program is invoked with a non-empty communication area, the system shall copy the caller's communication area into local working storage.

REQ-F-525: [Event-driven] When the user presses the ENTER key on the user update screen and the user ID field is empty, the system shall set the error flag, display the message 'User ID can NOT be empty...', and return focus to the user ID field.

REQ-F-526: [Event-driven] When the user presses the ENTER key on the user update screen and the user ID is valid, the system shall clear the first name, last name, password, and user type input fields, retrieve the matching record from the user security store using the user ID as the key, and populate the screen with the retrieved user's first name, last name, password, and user type.

REQ-F-527: [Event-driven] When a user record is successfully retrieved from the user security store for update, the system shall display the message 'Press PF5 key to save your updates ...'.

REQ-F-528: [Unwanted] If the user record is not found in the user security store during retrieval for update, the system shall set the error flag, display the message 'User ID NOT found...', and return focus to the user ID field.

REQ-F-529: [Unwanted] If any error other than not-found occurs when retrieving a user record from the user security store, the system shall set the error flag, display the message 'Unable to lookup User...', and return focus to the first name field.

REQ-F-530: [Event-driven] When the user presses PF5 to save updates and any required field (user ID, first name, last name, password, or user type) is empty, the system shall set the error flag, display the corresponding error message ('User ID can NOT be empty...', 'First Name can NOT be empty...', 'Last Name can NOT be empty...', 'Password can NOT be empty...', or 'User Type can NOT be empty...'), and return focus to the empty field.

REQ-F-531: [Event-driven] When the user presses PF5 to save updates and all fields are valid, the system shall retrieve the current user record from the user security store, compare each input field with the stored value, update any fields that differ, and write the modified record back to the user security store.

REQ-F-532: [Unwanted] If no fields differ from the stored record when the user attempts to save updates, the system shall display the message 'Please modify to update ...' and redisplay the screen without writing to the user security store.

REQ-F-533: [Event-driven] When the user presses PF12 on the user update screen, the system shall set the destination program to the administration program and transfer control.

REQ-F-534: [Event-driven] When the user presses PF3 on the user update screen and a prior program is recorded in the communication area, the system shall set the destination program to that prior program and transfer control; if no prior program is recorded, the system shall set the destination program to the administration program and transfer control.

---

## 47. User Record Deletion (Invoked Program)
As an administrator, I want to look up and delete a user security record so that deprovisioned users are removed from the system.

### Requirements

REQ-F-535: [Event-driven] When the user deletion program is invoked without a communication area, the system shall set the destination program to the signon screen and transfer control to it.

REQ-F-536: [Event-driven] When the user deletion program is invoked with a non-empty communication area, the system shall copy the communication area into the local record structure.

REQ-F-537: [Event-driven] When the user presses Enter on the user deletion screen and the user ID field is empty, the system shall set the error flag to 'Y', display the message 'User ID can NOT be empty...', and return focus to the user ID field.

REQ-F-538: [Event-driven] When the user presses Enter on the user deletion screen and the user ID is valid, the system shall retrieve the matching record from the user security store, display the user's first name, last name, and user type, and prompt 'Press PF5 key to delete this user ...'.

REQ-F-539: [Unwanted] If the user record is not found in the user security store during deletion lookup, the system shall set the error flag and display a not-found message.

REQ-F-540: [Unwanted] If any error other than not-found occurs when retrieving a user record from the user security store during deletion, the system shall set the error flag and display the message 'Unable to lookup User...'.

REQ-F-541: [Event-driven] When the user presses PF5 to confirm deletion and the user ID is valid, the system shall retrieve the user record from the user security store and delete it; on successful deletion, the system shall clear all screen fields and display the message 'User <user-id> has been deleted ...' in green; if the record is not found, the system shall display the message 'User ID NOT found...'; if any other error occurs, the system shall display the message 'Unable to Update User...'.

REQ-F-542: [Unwanted] If the user presses PF5 to confirm deletion and the user ID field is empty, the system shall set the error flag to 'Y', display the message 'User ID can NOT be empty...', and return focus to the user ID field.

REQ-F-543: [Event-driven] When the user presses PF4 on the user deletion screen, the system shall clear all screen input fields and the message area, reset focus to the user ID field, and display the blank screen.

REQ-F-544: [Unwanted] If an invalid or unrecognized function key is pressed on the user deletion screen, the system shall display the message 'Invalid key pressed. Please see below... ' and redisplay the screen.

REQ-F-545: [Event-driven] When the user presses PF3 on the user deletion screen and an originating program is recorded in the communication area, the system shall set the destination program to that originating program and transfer control; if no originating program is recorded, the system shall set the destination program to the administration screen and transfer control.

REQ-F-546: [Event-driven] When the user presses PF12 on the user deletion screen, the system shall set the destination program to the administration screen and transfer control.

REQ-F-547: [Ubiquitous] Before transferring control from the user deletion program, the system shall validate that the destination program is set (defaulting to the signon screen if empty), populate the communication area with the current transaction ID and program name, and reset the program-context flag to zero.

## 48. Program Navigation and Screen Transition Control
As a session manager, I want the navigation handler to route users to the correct destination screen based on their input context and keyboard action so that users are always directed to an appropriate screen and navigation state is preserved across program transfers.

### Requirements

REQ-F-548: [Event-driven] When the program is invoked without a session context (communication area), the system shall set the destination program to the signon screen and transfer control to it.

REQ-F-549: [Event-driven] When the program is invoked with a session context (communication area), the system shall copy the incoming session context — including the originating transaction ID, originating program name, and previously set destination program — into local working storage.

REQ-F-550: [Complex] While the program is being re-entered after a previous interaction, when the user presses PF3, the system shall set the destination program to the administration screen and transfer control to it.

REQ-F-551: [Ubiquitous] Before transferring control to the destination program, the system shall validate the destination program name and, if it is uninitialized, default it to the signon screen.

REQ-F-552: [Ubiquitous] Before transferring control to the destination program, the system shall record the current transaction ID and program name as the originating context in the session context, and reset the program context flag to indicate first-time entry for the destination program.

---

## 49. User Registration and Security File Management
As a system administrator, I want to register new users by collecting their information, validating required fields, and writing the record to the user security store so that new users can be granted access to the system.

### Requirements

REQ-F-553: [Event-driven] When the user submits input from the user addition screen, the system shall receive the entered data — first name, last name, user ID, password, and user type — and populate the input buffer with those values.

REQ-F-554: [Ubiquitous] The system shall retrieve the current system date and time and populate the screen header with the transaction ID, program name, application titles, current date formatted as MM/DD/YY, and current time formatted as HH:MM:SS.

REQ-F-555: [Ubiquitous] The system shall move the current message (error, success, or empty) to the error message field and send the user addition screen to the terminal.

REQ-F-556: [Event-driven] When all required user input fields (first name, last name, user ID, password, and user type) pass validation, the system shall copy the validated user ID (up to 8 characters), first name (up to 20 characters), last name (up to 20 characters), password (up to 8 characters), and user type (1 character) from the screen input buffer to the user security record, then write the record to the user security data store (AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS).

REQ-F-557: [Event-driven] When the user presses Enter on the user addition screen, the system shall validate each required field (first name, last name, user ID, password, user type) and, for each field that is empty, set the error flag and display a field-specific error message.

REQ-F-558: [Event-driven] When the user presses the PF4 function key, the system shall clear all user input fields and the message area, then redisplay the blank user addition screen.

REQ-F-559: [Event-driven] When the program attempts to write a validated user record to the user security data store and the write succeeds, the system shall clear the screen input fields, set the success message color to green, and display a success message.

REQ-F-560: [Event-driven] When the program attempts to write a validated user record to the user security data store and the user ID already exists, the system shall display a duplicate-key error message.

REQ-F-561: [Event-driven] When the program attempts to write a validated user record to the user security data store and any error other than a duplicate key occurs, the system shall display a generic error message.

REQ-F-562: [Event-driven] When the user presses an unrecognized function key on the user addition screen, the system shall display an invalid-key error message.

## 50. Program Navigation and Screen Transition Control
As a system user, I want function-key inputs to route me to the correct program so that I can navigate between application functions without losing session context.

### Requirements

REQ-F-563: [Event-driven] When the program is invoked with an empty session context, the system shall set the destination program to the sign-on program and transfer control to it.

REQ-F-564: [Event-driven] When the program is invoked with a non-empty session context, the system shall copy the caller's session context into local working storage.

REQ-F-565: [Event-driven] When the program is re-entered with a valid session context, the system shall evaluate the function key pressed by the user and route to the appropriate navigation handler; recognized keys are ENTER, PF3, PF4, PF5, and PF12, and unrecognized keys are handled by a default case.

REQ-F-566: [Event-driven] When the user presses the PF12 function key, the system shall set the destination program to the administration program.

REQ-F-567: [Event-driven] When the user presses the PF3 function key and no prior program is recorded in the session context, the system shall set the destination program to the administration program; otherwise the system shall set the destination program to the prior program.

REQ-F-568: [Ubiquitous] Before transferring control to the destination program, the system shall validate that the destination program is set; if the destination program is blank or contains low-values, the system shall default it to the sign-on program.

REQ-F-569: [Ubiquitous] Before transferring control to the destination program, the system shall record the current transaction identifier and current program name in the session context, and reset the program context flag to zero to indicate initial entry state for the destination program.

REQ-F-570: [Ubiquitous] The system shall transfer control to the destination program, passing the updated session context.

---

## 51. User Security Record Update and Display
As an administrator, I want to look up, validate, and update user security records through an interactive screen so that user credentials and access roles are kept accurate.

### Requirements

REQ-F-571: [Ubiquitous] The system shall retrieve the current system date and time, reformat the date into MM/DD/YY format and the time into HH:MM:SS format, and populate the screen header with the application titles, transaction identifier, program name, current date, and current time before displaying the user update screen.

REQ-F-572: [Ubiquitous] When preparing the user update screen for display, the system shall move the current message to the error message output field and send the screen to the user.

REQ-F-573: [Ubiquitous] The system shall receive user input from the user update screen and capture the response code and reason code for subsequent processing.

REQ-F-574: [Event-driven] When the user presses the ENTER key and the user ID field is empty or contains only spaces, the system shall set the error flag to 'Y', display the message 'User ID can NOT be empty...', return focus to the user ID field, and send the screen.

REQ-F-575: [Event-driven] When the user presses the ENTER key and the user ID field is populated, the system shall clear the first name, last name, password, and user type input fields, then retrieve the user record from the user security data store (AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS) using the entered user ID as the key.

REQ-F-576: [Event-driven] When the user record retrieval succeeds, the system shall populate the screen input fields with the retrieved user's first name (up to 20 characters), last name (up to 20 characters), password (up to 8 characters), and user type (1 character), and display the message 'Press PF5 key to save your updates ...'.

REQ-F-577: [Event-driven] When the user record retrieval returns a not-found condition, the system shall set the error flag to 'Y', display the message 'User ID NOT found...', return focus to the user ID field, and send the screen.

REQ-F-578: [Event-driven] When the user record retrieval returns any error other than not-found, the system shall set the error flag to 'Y', display the message 'Unable to lookup User...', return focus to the first name field, and send the screen.

REQ-F-579: [Event-driven] When the user presses PF3 or PF5 to update a user record and the user ID field is empty or contains only spaces, the system shall set the error flag to 'Y', display the message 'User ID can NOT be empty...', return focus to the user ID field, and send the screen.

REQ-F-580: [Event-driven] When the user presses PF3 or PF5 to update a user record and the first name field is empty or contains only spaces, the system shall set the error flag to 'Y', display the message 'First Name can NOT be empty...', return focus to the first name field, and send the screen.

REQ-F-581: [Event-driven] When the user presses PF3 or PF5 to update a user record and the last name field is empty or contains only spaces, the system shall set the error flag to 'Y', display the message 'Last Name can NOT be empty...', return focus to the last name field, and send the screen.

REQ-F-582: [Event-driven] When the user presses PF3 or PF5 to update a user record and the password field is empty or contains only spaces, the system shall set the error flag to 'Y', display the message 'Password can NOT be empty...', return focus to the password field, and send the screen.

REQ-F-583: [Event-driven] When the user presses PF3 or PF5 to update a user record and the user type field is empty or contains only spaces, the system shall set the error flag to 'Y', display the message 'User Type can NOT be empty...', return focus to the user type field, and send the screen.

REQ-F-584: [Event-driven] When all required fields (user ID, first name, last name, password, user type) are populated and the user presses PF3 or PF5, the system shall retrieve the current user record from the user security data store using the entered user ID, compare each input field against the stored values, and set the user-modified flag to 'Y' for each field that differs.

REQ-F-585: [Event-driven] When the user-modified flag is 'Y' after field comparison, the system shall write the updated user record to the user security data store.

REQ-F-586: [Event-driven] When the update to the user security data store succeeds, the system shall display a success message that includes the user ID.

REQ-F-587: [Event-driven] When the update to the user security data store returns a not-found condition, the system shall display a not-found message and return focus to the user ID field.

REQ-F-588: [Event-driven] When the update to the user security data store returns any error other than not-found, the system shall display an error message and return focus to the first name field.

REQ-F-589: [Event-driven] When no input fields differ from the stored record (user-modified flag remains unset), the system shall display the message 'Please modify to update ...' and send the screen.

REQ-F-590: [Event-driven] When the user presses PF4 to clear the screen, the system shall clear all user input fields (user ID, first name, last name, password, user type) and the message area to spaces, return focus to the user ID field, and send the cleared screen.

## 52. Screen Navigation and Program Transfer
As a session operator, I want the system to evaluate my function-key input and transfer control to the correct destination program so that navigation between application screens is consistent and context is preserved across transitions.

### Requirements

REQ-F-591: [Event-driven] When the program is invoked without a session context (communication area), the system shall set the destination program to the signon screen and transfer control to it.

REQ-F-592: [Event-driven] When a session context (communication area) is provided by the caller, the system shall load the communication area data into the local navigation context, establishing the originating program, transaction ID, and prior state for the current invocation.

REQ-F-593: [Complex] While the program is re-entered after a previous interaction, when the user presses PF3, the system shall set the destination program to the originating program recorded in the session context if that value is present and non-empty; otherwise the system shall default the destination to the administration screen.

REQ-F-594: [Complex] While the program is re-entered after a previous interaction, when the user presses PF12, the system shall set the destination program to the administration screen.

REQ-F-595: [Ubiquitous] The system shall validate that the destination program field in the session context is populated; if it is empty or contains low-values, the system shall default it to the signon screen.

REQ-F-596: [Ubiquitous] The system shall populate the session context with its own transaction ID in the from-transaction-ID field, its own program name in the from-program field, and reset the program context flag to zero before transferring control.

REQ-F-597: [Ubiquitous] The system shall transfer control to the destination program identified in the session context, passing the updated session context as the shared navigation context.

---

## 53. User Deletion Workflow
As an administrator, I want to look up a user by ID, review their details, and confirm deletion so that user records can be removed from the system accurately and with appropriate safeguards.

### Requirements

REQ-F-598: [Ubiquitous] The system shall retrieve the current system date and time, format them as MM/DD/YY and HH:MM:SS respectively, and populate the screen header with the transaction ID, program name, current date, and current time before each screen display.

REQ-F-599: [Ubiquitous] The system shall move the current message to the screen's error message field and present the screen to the operator.

REQ-F-600: [Ubiquitous] The system shall receive the operator's input from the screen, capturing the user ID and all other entered fields together with the response code and reason code.

REQ-F-601: [Event-driven] When the Enter key is pressed and the user ID field is empty or contains only spaces, the system shall set the error flag to 'Y' and display the error message 'User ID can NOT be empty...'.

REQ-F-602: [Event-driven] When the Enter key is pressed and the user ID is non-empty, the system shall clear the first name, last name, and user type fields, use the entered user ID as the lookup key, retrieve the matching user record from the user security data store (legacy: AWS.M2.CARDDEMO.USRSEC.VSAM.KSDS), and display the retrieved user's first name, last name, and user type with the prompt 'Press PF5 key to delete this user ...'.

REQ-F-603: [Event-driven] When the PF5 key is pressed and the user ID field is empty or contains only spaces, the system shall set the error flag to 'Y' and display the error message 'User ID can NOT be empty...'.

REQ-F-604: [Event-driven] When the PF5 key is pressed and the user ID is non-empty, the system shall retrieve the user record from the user security data store using the entered user ID as the lookup key and then delete that user record.

REQ-F-605: [Event-driven] When the deletion of a user record succeeds, the system shall clear all screen fields and display the success message 'User &lt;user-id&gt; has been deleted ...'.

REQ-F-606: [Event-driven] When the deletion attempt results in a not-found response, the system shall display the error message 'User ID NOT found...'.

REQ-F-607: [Event-driven] When the deletion attempt results in any response other than success or not-found, the system shall display the error message 'Unable to Update User...'.

REQ-F-608: [Event-driven] When the PF4 key is pressed, the system shall clear all screen input fields (user ID, first name, last name, user type) and the message area, and display the blank screen to allow the operator to begin a new deletion operation.

REQ-F-609: [Event-driven] When a function key other than Enter, PF3, PF4, PF5, or PF12 is pressed, the system shall display the error message 'Invalid key pressed. Please see below... ' and redisplay the screen.

REQ-F-610: [Event-driven] When the user record lookup by user ID results in a not-found response, the system shall set the error flag to 'Y' and display a not-found error message.

REQ-F-611: [Event-driven] When the user record lookup by user ID results in any response other than success or not-found, the system shall set the error flag to 'Y' and display an error message.

### Open Questions

OQ-009: Rule 3a95fad5 describes that on first entry the system may optionally process a pre-selected user ID passed in the session context. The conditions under which a pre-selected user ID is present and how it is sourced are not fully specified. — Owner: application design team