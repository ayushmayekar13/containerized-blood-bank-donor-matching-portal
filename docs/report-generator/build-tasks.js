// ---------------------------------------------------------------------------
// One standalone Word document per subject task, rendered from that task's own
// markdown document in docs/. The markdown is the single source: the .docx can
// never state something the repository's documentation does not.
//
//   node build-tasks.js <output-directory>
// ---------------------------------------------------------------------------
const fs = require('fs');
const path = require('path');
const { Packer, Paragraph, TextRun, AlignmentType, BorderStyle } = require('docx');

const { render } = require('./md.js');
const { makeDocument } = require('./doc.js');
const { table, spacer, BODY, INK, MUTED, ACCENT } = require('./helpers.js');

const DOCS = path.resolve(__dirname, '..');
const OUT = process.argv[2] || '.';
fs.mkdirSync(OUT, { recursive: true });

const slug = (s) => s.replace(/[^A-Za-z0-9 ]/g, '').trim().replace(/\s+/g, '_');

const files = fs.readdirSync(DOCS)
  .filter(f => /^\d\d-.*\.md$/.test(f))
  .sort();

(async () => {
  for (const file of files) {
    const md = fs.readFileSync(path.join(DOCS, file), 'utf8');
    const lines = md.split('\n');

    // Title line: "# Task 7 — Jenkins Installation and ..."
    const titleLine = lines[0].replace(/^#\s+/, '').trim();
    const [, num, title] = /^Task\s+(\d+)\s+[—-]\s+(.*)$/.exec(titleLine) || [, '?', titleLine];

    // Pull the metadata lines out of the front matter so they become a table
    const deliverable = (md.match(/^\*\*Deliverable:\*\*\s*([\s\S]*?)(?=\n\n)/m) || [, ''])[1]
      .replace(/\s+/g, ' ').trim();
    const completed = (md.match(/^\*\*Completed:\*\*\s*(.*)$/m) || [, ''])[1]
      .replace(/\*\*/g, '').replace(/`/g, '').trim();

    // Body = everything after the first `---`, with the front matter removed
    const bodyStart = lines.findIndex((l, idx) => idx > 0 && /^---\s*$/.test(l));
    const body = lines.slice(bodyStart + 1).join('\n');

    const children = [];
    const push = (...xs) => xs.forEach(x => Array.isArray(x) ? children.push(...x) : children.push(x));

    // ---- compact title block (these documents run to a few pages) ----------
    push(new Paragraph({
      alignment: AlignmentType.CENTER,
      spacing: { before: 120, after: 40 },
      children: [new TextRun({ text: `DEVOPS PROJECT  ·  TASK ${num} OF 15`,
        font: BODY, size: 18, bold: true, color: ACCENT, characterSpacing: 50 })],
    }));
    push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { after: 60 },
      children: [new TextRun({ text: title, font: BODY, size: 34, bold: true, color: INK })],
    }));
    push(new Paragraph({
      alignment: AlignmentType.CENTER, spacing: { after: 220 },
      border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: ACCENT, space: 8 } },
      children: [new TextRun({ text: 'Containerized Blood Bank Donor Matching Portal',
        font: BODY, size: 20, color: MUTED })],
    }));

    push(table(null, [
      ['Deliverable', deliverable],
      ['Student', 'AYUSH MAYEKAR   ·   Roll No. 23102A0018'],
      ['Class', 'BE Computer Engineering  ·  Division A  ·  DevOps'],
      ['Academic Year', '2026-27, ODD Semester'],
      ['Completed', completed],
    ], [2300, 7060], { labelColumn: true }));
    push(spacer(240));

    // ---- the task document itself ----------------------------------------
    push(render(body));

    push(new Paragraph({
      spacing: { before: 400 },
      alignment: AlignmentType.CENTER,
      border: { top: { style: BorderStyle.SINGLE, size: 4, color: '#D0D4D8'.slice(1), space: 10 } },
      children: [new TextRun({
        text: `Task ${num} of 15  ·  Ayush Mayekar  ·  23102A0018  ·  BE CMPN A`,
        font: BODY, size: 17, italics: true, color: MUTED })],
    }));

    const doc = makeDocument({
      title: `Task ${num} — ${title} | DevOps Project | Ayush Mayekar 23102A0018`,
      description: `DevOps subject task ${num} of 15: ${title}.`,
      headerText: `Task ${num}  ·  ${title}  ·  Ayush Mayekar  ·  23102A0018`,
      children,
    });

    const name = `Task_${String(num).padStart(2, '0')}_${slug(title)}.docx`;
    const buf = await Packer.toBuffer(doc);
    fs.writeFileSync(path.join(OUT, name), buf);
    console.log(`  ${name.padEnd(58)} ${(buf.length / 1024).toFixed(0)} KB`);
  }
  console.log(`\n${files.length} task documents written to ${OUT}`);
})();
