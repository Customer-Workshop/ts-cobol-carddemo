# Phase 0 modernization discovery

This directory records source-verified discovery for the CardDemo modernization.

- [Legacy inventory](inventory.md): COBOL, BMS, copybooks, batch assets, assembler, optional modules, and data files.
- [Traceability matrix](traceability-matrix.md): online and batch contracts carried into later phases.
- [Business rules](business-rules/): line-by-line specifications for the highest-risk programs.
- [Open questions](open-questions.md): behavior requiring a later migration decision or mainframe evidence.

## Target architecture

The target is a React TypeScript SPA calling a REST API implemented with Node.js and TypeScript, backed by PostgreSQL; batch COBOL workloads become an explicit job runner, and each BMS screen becomes a React page while preserving the legacy transaction semantics documented below.
