# Lead-time requests to external teams (SIMULATED)

In this engagement there is no real data-management, security or infrastructure team to
contact. Requests that would normally carry lead time (data extracts, DBA schema requests,
SSO/IdP configuration, firewall openings, scheduler changes) are **simulated**: the request is
written down here exactly as it would be sent, marked `SENT (simulated)`, and the requesting
playbook proceeds using a documented assumption or a fixture.

## Convention

- One file per request: `.migration/requests/R-<nnnn>_<slug>.md` (sequential id, never reused).
- Front matter (first lines of the file):

```
Id: R-0001
Date: YYYY-MM-DD
Requester: <!mf_* playbook> / <Devin session URL>
Team: <Data Management | DBA | Security/SSO | Infrastructure | Scheduling | Other>
Stream: <stream short name or MODULE>
Status: SENT (simulated)
Assumed answer: <what the requesting session proceeds with until a real answer exists>
Blocking: <yes | no>  (yes only if a STOP is held on it)
```

- Body: the request text as it would be sent (what, why, format, deadline), followed by an
  `## Assumption in force` section describing the fixture or default used meanwhile, and an
  `## Log` section (dated lines) for later status changes (`ANSWERED (simulated)`, `WITHDRAWN`).
- Status changes are appended to the file's `## Log`, and mirrored as a row in
  `.migration/06_decisions.md` when the answer is a decision.
- No personal names or emails; refer to teams and roles only.

No requests have been raised yet.
