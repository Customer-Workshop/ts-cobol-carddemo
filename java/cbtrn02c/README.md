# CBTRN02C Java port

This standalone Java 17/Spring Boot project ports the COBOL daily transaction
poster. Fixed-width input files are loaded into sorted in-memory maps and are
saved back using the original record layouts.

Run with:

```sh
mvn -q package -DskipTests
java -jar target/cbtrn02c-1.0.0.jar src/main/resources/sample-data
```

The positional argument is a directory containing `DALYTRAN`, `XREF`,
`ACCOUNT`, and `TCATBAL`; `TRANSACT` and `DALYREJS` are created or rewritten
there. The process returns 4 when one or more records are rejected.
