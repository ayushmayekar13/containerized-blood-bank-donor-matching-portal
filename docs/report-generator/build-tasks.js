// ---------------------------------------------------------------------------
// Generates one standalone Word document per subject task.
//
//   node build-tasks.js <output-directory>
//
// The content comes from tasks.js - the same single source the merged report
// in build.js reads. Neither generator owns the content, so the fifteen
// separate documents and the merged report can never drift apart: correcting a
// task means editing tasks.js once and rebuilding both.
// ---------------------------------------------------------------------------
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, AlignmentType, BorderStyle,
  Header, Footer, PageNumber, NumberFormat, LevelFormat,
} = require('docx');

const H = require('./helpers.js');
const TASKS = require('./tasks.js');
const { p, h2, h3, bullet, code, callout, table, spacer,
        BODY, INK, MUTED, ACCENT, RULE } = H;

const OUT = process.argv[2] || '.';
fs.mkdirSync(OUT, { recursive: true });

/** "Problem Definition and Scope" -> "Problem_Definition_and_Scope" */
const slug = (s) => s
  .replace(/[^A-Za-z0-9 ]/g, '')
  .trim()
  .replace(/\s+/g, '_');

const numbering = {
  config: [
    {
      reference: 'bullets',
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 240 } } } },
      ],
    },
  ],
};

function buildTask(t) {
  const children = [];
  const push = (...xs) => xs.forEach(x => Array.isArray(x) ? children.push(...x) : children.push(x));

  // ---------------------------------------------------------------- heading
  // A compact title block rather than a full title page: these documents run
  // to three or four pages, and a whole page of cover for three pages of
  // content reads as padding.
  push(new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 120, after: 40 },
    children: [new TextRun({
      text: `DEVOPS PROJECT  ·  TASK ${t.n} OF 15`,
      font: BODY, size: 18, bold: true, color: ACCENT, characterSpacing: 50,
    })],
  }));

  push(new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 60 },
    children: [new TextRun({ text: t.title, font: BODY, size: 34, bold: true, color: INK })],
  }));

  push(new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 220 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: ACCENT, space: 8 } },
    children: [new TextRun({
      text: 'Containerized Blood Bank Donor Matching Portal',
      font: BODY, size: 20, color: MUTED,
    })],
  }));

  push(table(null, [
    ['Student', 'AYUSH MAYEKAR   ·   Roll No. 23102A0018'],
    ['Class', 'BE Computer Engineering  ·  Division A  ·  DevOps'],
    ['Academic Year', '2026-27, ODD Semester'],
    ['Completed', `${t.date}      (git tag: ${t.tag})`],
  ], [2300, 7060], { labelColumn: true }));
  push(spacer(240));

  // ------------------------------------------------------------- the task
  push(h2('Task statement'));
  push(new Paragraph({
    spacing: { before: 60, after: 200, line: 276 },
    indent: { left: 300, right: 300 },
    border: { left: { style: BorderStyle.SINGLE, size: 14, color: ACCENT, space: 10 } },
    children: [new TextRun({ text: t.statement, font: BODY, size: 21, italics: true, color: INK })],
  }));

  push(h2('Required deliverable'));
  push(p(t.deliverable, { italics: true }));

  push(h2('Work carried out'));
  t.did.forEach(d => push(bullet(d)));
  push(spacer(140));

  push(h2('Decisions taken, and why'));
  push(p('Each decision below is recorded with its reasoning rather than only its outcome, because the reasoning is what the decision has to be defended on.', { size: 19, color: MUTED }));
  push(table(['Decision', 'Reasoning'],
    t.decisions.map(([d, r]) => [{ text: d, bold: true }, r]), [3500, 5860]));
  push(spacer(240));

  if (t.output) {
    push(h2('Captured output'));
    push(p('Real output from a run on the development machine, retained in the project repository.', { size: 19, color: MUTED }));
    push(code(t.output, { barColor: ACCENT, after: 60 }));
    push(new Paragraph({
      spacing: { after: 240 },
      children: [new TextRun({ text: t.outputCap, font: BODY, size: 17, italics: true, color: MUTED })],
    }));
  }

  if (t.callout) push(callout(t.callout[0], t.callout[1]));

  if (t.bugs) {
    push(h2('Defects found in this task'));
    push(table(['ID', 'Defect and cause'],
      t.bugs.map(([id, d]) => [{ text: id, bold: true }, d]), [1200, 8160]));
    push(spacer(240));
  }

  push(h2('Evidence retained'));
  push(table(['Location in the repository', 'What it shows'],
    t.evidence.map(([f, w]) => [{ text: f, mono: true }, w]), [3900, 5460]));
  push(spacer(240));

  push(h2('Verification'));
  push(p('Run from the repository root. These commands reproduce the result shown above.', { size: 19, color: MUTED }));
  push(code(t.verify.map(v => '$ ' + v)));

  // ------------------------------------------------------------- footer note
  push(new Paragraph({
    spacing: { before: 400 },
    alignment: AlignmentType.CENTER,
    border: { top: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 10 } },
    children: [new TextRun({
      text: `Task ${t.n} of 15  ·  Ayush Mayekar  ·  23102A0018  ·  BE CMPN A`,
      font: BODY, size: 17, italics: true, color: MUTED,
    })],
  }));

  return new Document({
    creator: 'Ayush Mayekar',
    title: `Task ${t.n} — ${t.title} | DevOps Project | Ayush Mayekar 23102A0018`,
    description: `DevOps subject task ${t.n} of 15: ${t.title}. Containerized Blood Bank Donor Matching Portal.`,
    styles: { default: { document: { run: { font: BODY, size: 21, color: INK } } } },
    numbering,
    sections: [{
      properties: {
        page: {
          size: { width: 11906, height: 16838 },   // A4
          margin: { top: 1250, right: 1134, bottom: 1250, left: 1134, header: 600, footer: 600 },
          pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL },
        },
        titlePage: true,          // page 1 carries the title block, so no running header there
      },
      headers: {
        default: new Header({
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            spacing: { after: 60 },
            border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 4 } },
            children: [new TextRun({
              text: `Task ${t.n}  ·  ${t.title}  ·  Ayush Mayekar  ·  23102A0018`,
              font: BODY, size: 16, color: MUTED,
            })],
          })],
        }),
        first: new Header({ children: [new Paragraph({ children: [] })] }),
      },
      footers: {
        default: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            border: { top: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 6 } },
            children: [
              new TextRun({ text: 'Page ', font: BODY, size: 16, color: MUTED }),
              new TextRun({ children: [PageNumber.CURRENT], font: BODY, size: 16, color: MUTED }),
              new TextRun({ text: ' of ', font: BODY, size: 16, color: MUTED }),
              new TextRun({ children: [PageNumber.TOTAL_PAGES], font: BODY, size: 16, color: MUTED }),
            ],
          })],
        }),
        first: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            children: [
              new TextRun({ text: 'Page ', font: BODY, size: 16, color: MUTED }),
              new TextRun({ children: [PageNumber.CURRENT], font: BODY, size: 16, color: MUTED }),
              new TextRun({ text: ' of ', font: BODY, size: 16, color: MUTED }),
              new TextRun({ children: [PageNumber.TOTAL_PAGES], font: BODY, size: 16, color: MUTED }),
            ],
          })],
        }),
      },
      children,
    }],
  });
}

(async () => {
  for (const t of TASKS) {
    const name = `Task_${String(t.n).padStart(2, '0')}_${slug(t.title)}.docx`;
    const buf = await Packer.toBuffer(buildTask(t));
    fs.writeFileSync(path.join(OUT, name), buf);
    console.log(`  ${name.padEnd(56)} ${(buf.length / 1024).toFixed(0)} KB`);
  }
  console.log(`\n${TASKS.length} task documents written to ${OUT}`);
})();
