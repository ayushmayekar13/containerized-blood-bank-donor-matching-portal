# PR-03 — Export the matching report (OPEN, deliberately unmerged)

| | |
|---|---|
| **Branch** | `feature/export-report` → `develop` |
| **Status** | **Open — not merged, on purpose** |
| **Story** | FE-15 (product backlog, not in this release) |
| **Author** | Ayush Mayekar (23102A0018) |

---

## Why this branch is left open

Every other feature in this project reached `develop` through a pull request that was reviewed and
merged. Once merged, a pull request cannot be opened again — so after the repository is pushed to
GitHub there would be no *live* pull request to show an examiner.

This branch exists so there is one. It is a genuine, incomplete feature from the product backlog
(FE-15), not a placeholder commit: the groundwork is real and the remaining work is honestly listed
below.

After pushing:

```bash
git push origin feature/export-report
gh pr create --base develop --head feature/export-report \
  --title "Export the matching report as CSV" \
  --body-file docs/pr/PR-03-export-report.md
```

## What this pull request would do

Let a technician export the ranked shortlist for a request as CSV, so it can be handed to whoever is
making the phone calls, or attached to a case record.

```
GET /requests/{id}/export.csv     →  text/csv, one row per matched donor
GET /api/requests/{id}/matches.csv
```

```csv
rank,donorId,fullName,bloodGroup,city,phone,daysSinceLastDonation,exactGroupMatch,sameCity
1,4,Sneha Patil,B+,Thane,9820010002,142,true,true
2,12,Vikram Shinde,B+,Pune,9820010012,200,true,false
3,1,Rohit Deshmukh,O-,Thane,9820010001,160,false,true
```

## Acceptance criteria (not yet met)

- AC-E1 — Given a request with matches, when I request the CSV, then it contains one row per
  shortlisted donor **in rank order**.
- AC-E2 — Given a request with no matches, then a CSV with only a header row is returned, not a 404 —
  "nobody is available" is an answer, not an error.
- AC-E3 — Given a `DONOR` account, then the export is refused with 403. **The export must not become
  a way around the role rules**, which is the one genuinely risky thing about this feature.
- AC-E4 — Given a donor name containing a comma or a quotation mark, then the field is correctly
  escaped per RFC 4180.
- AC-E5 — The response carries `Content-Disposition: attachment` with a filename containing the
  request id.

## Remaining work

- [ ] `CsvExportService` with RFC 4180 escaping (AC-E4)
- [ ] Controller endpoints for the UI and API paths
- [ ] Role check in the **service** layer, not only on the URL (AC-E3)
- [ ] Unit tests: rank order, empty shortlist, escaping, role refusal
- [ ] Selenium journey: download and assert the header row
- [ ] An export button on the request detail page
- [ ] Documentation in `docs/API.md`

## Design note

The exported file contains donor **phone numbers**. That makes this feature the project's first real
data-protection question: a CSV can be emailed, printed or left on a shared drive, and nothing in the
portal can track it once it leaves. Before merging, this needs either a recorded audit entry per
export or a redaction option — which is why the role check in AC-E3 is listed as a service-layer
requirement rather than a URL rule.

That open question is itself a reasonable thing to discuss in a viva: the feature is small, and the
non-technical consequence is the hard part.
