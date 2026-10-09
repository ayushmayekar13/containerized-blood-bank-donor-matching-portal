# Word report generator

Generates `DevOps_Project_Report_23102A0018.docx` — the submitted Word report covering all 15
subject tasks.

```bash
cd docs/report-generator
npm install
npm run build          # both outputs
npm run build:merged   # just the 45-page consolidated report
npm run build:tasks    # just the fifteen per-task documents
```

Two outputs, one source:

| Command | Produces |
|---|---|
| `build.js` | `DevOps_Project_Report_23102A0018.docx` — 45 pages, with a title page, declaration, table of contents, seven numbered sections and three appendices |
| `build-tasks.js` | `task-reports/Task_NN_*.docx` — fifteen standalone documents, one per task |

Both read `tasks.js`, so the per-task documents and the merged report **cannot drift apart**:
correcting a task is one edit followed by one rebuild.

## Why the report is generated rather than hand-written

The same reason the Jenkins controller is bootstrapped from Groovy and the target node is described
in `group_vars`: **a document that exists only as a binary file cannot be reviewed in a diff, and
cannot be regenerated after an edit is lost.**

Keeping the generator in the repository means a correction to one task's content is a one-line
change to `tasks.js` followed by `npm run build`, rather than hunting through 45 pages of a Word
file — and `git log` shows what changed in the report and why, exactly as it does for the code.

## Layout

| File | Contains |
|---|---|
| `tasks.js` | The content for all 15 tasks: statement, deliverable, work done, decisions with reasoning, captured output, defects, evidence and verification commands |
| `helpers.js` | Formatting primitives — headings, body text, bullets, code blocks, callouts and tables, plus the page geometry and colour tokens |
| `build.js` | Assembles the merged report: title page, declaration, table of contents, seven numbered sections, the 15 task sections, and three appendices |
| `build-tasks.js` | Emits one standalone document per task, each with its own title block, header and page numbering |

## Notes on the formatting

- **A4** (11906 × 16838 DXA) with 2 cm side margins; usable width 9638, tables set to 9360.
- Tables declare both `columnWidths` and a per-cell `width` in DXA, because percentage widths
  render incorrectly in Google Docs.
- The table of contents is a Word field. `features: { updateFields: true }` makes Word populate it
  on open — without it the contents page opens empty and looks broken.
- Headings, code blocks and callouts carry `keepNext` / `keepLines` so a heading never ends a page
  alone and a callout never splits across a page boundary.
- Each of the 15 tasks starts on a fresh page.

## Verifying the output

```bash
soffice --headless --convert-to pdf ../../DevOps_Project_Report_23102A0018.docx
pdftoppm -jpeg -r 85 DevOps_Project_Report_23102A0018.pdf page
```

Requires LibreOffice and Poppler (`brew install --cask libreoffice && brew install poppler`).
