# Word report generator

Renders the Word deliverables **from the repository's own markdown documentation**, so a `.docx`
can never state something the repository does not.

```bash
cd docs/report-generator
npm install
npm run build          # both outputs
```

| Command | Produces |
|---|---|
| `npm run build:merged` | `DevOps_Project_Report_23102A0018.docx` — the consolidated report, ~86 pages |
| `npm run build:tasks` | `task-reports/Task_NN_*.docx` — fifteen standalone documents, one per task |

## Chain of custody

```
docs/NN-*.md                    ← the single source: one document per task
      ↓  scripts/build-final-report.sh
docs/FINAL-REPORT.md            ← generated, never hand-edited
      ↓  build.js                           ↓  build-tasks.js
DevOps_Project_Report_*.docx              task-reports/Task_NN_*.docx
```

Correcting a task is **one edit** to that task's markdown, then:

```bash
bash scripts/build-final-report.sh
cd docs/report-generator && npm run build
```

Nothing downstream is maintained by hand, so the per-task documents, the merged report and the
markdown in the repository cannot drift apart.

## Document format

Each task document follows the same structure, and the renderer reproduces it faithfully:

| Element | Rendered as |
|---|---|
| `# Task N — Title` + `**Deliverable:**` | Title block with a metadata table |
| `## N. Section`, `### N.N Subsection` | Numbered headings, picked up by the table of contents |
| Markdown tables | Word tables with proportional column widths |
| Fenced code blocks | Monospace, shaded, with a red left rule |
| `> blockquote` | Indented italic with a left rule |
| `## N. Evidence index` | File-to-evidence table |
| `## N. Task N Definition of Done` | D1–D12 scoring table |
| `**Outcome:** …` | Bold closing paragraph |

## Layout

| File | Contains |
|---|---|
| `md.js` | The Markdown → docx renderer: headings, lists, tables, code blocks, blockquotes, rules, and inline bold / italic / `code` / links |
| `helpers.js` | Formatting primitives and the page geometry, colour tokens and table builder |
| `doc.js` | Shared `Document` factory: A4 page, numbering, running header, page footer |
| `build.js` | The consolidated report: title page, declaration, table of contents, then `FINAL-REPORT.md` |
| `build-tasks.js` | One standalone document per task, each with its own title block and header |

## Notes on the formatting

- **A4** (11906 × 16838 DXA), 2 cm side margins; usable width 9638, tables set to 9360.
- Tables declare both `columnWidths` and a per-cell `width` in DXA, because percentage widths render
  incorrectly in Google Docs. Column widths are proportional to content length, with an adaptive
  minimum — a flat minimum went negative on the nine-column compatibility matrix in Task 3.
- Table cells are rendered through the same inline parser as body text, so `**bold**` and `` `code` ``
  inside a cell render as formatting rather than as literal characters.
- The table of contents is a Word field. `features: { updateFields: true }` makes Word populate it on
  open — without it the contents page opens empty and looks broken.
- Headings, code blocks and callouts carry `keepNext` / `keepLines`, so a heading never ends a page
  alone and a code block never splits across a page boundary.
- Repository-relative links are flattened to their visible text: a dead hyperlink in a Word document
  is worse than plain text. External `http(s)` links keep the URL in parentheses.

## Verifying the output

```bash
soffice --headless --convert-to pdf ../../DevOps_Project_Report_23102A0018.docx
pdftoppm -jpeg -r 85 DevOps_Project_Report_23102A0018.pdf page
```

Requires LibreOffice and Poppler: `brew install --cask libreoffice && brew install poppler`.
