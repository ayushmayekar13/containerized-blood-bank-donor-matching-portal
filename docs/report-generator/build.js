// ---------------------------------------------------------------------------
// The consolidated Word report, rendered from docs/FINAL-REPORT.md.
//
//   node build.js <output.docx>
//
// FINAL-REPORT.md is itself generated from the fifteen task documents by
// scripts/build-final-report.sh, so there is a single chain of custody:
//   docs/NN-*.md  →  docs/FINAL-REPORT.md  →  this .docx
// Nothing in the Word file can state something the repository does not.
// ---------------------------------------------------------------------------
const fs = require('fs');
const path = require('path');
const {
  Packer, Paragraph, TextRun, AlignmentType, BorderStyle, TableOfContents, PageBreak,
} = require('docx');

const { render } = require('./md.js');
const { makeDocument } = require('./doc.js');
const { h1, p, table, spacer, BODY, INK, MUTED, ACCENT } = require('./helpers.js');

const SOURCE = path.resolve(__dirname, '..', 'FINAL-REPORT.md');
const OUT = process.argv[2] || 'DevOps_Project_Report.docx';

const children = [];
const push = (...xs) => xs.forEach(x => Array.isArray(x) ? children.push(...x) : children.push(x));

const titleLine = (text, o = {}) => new Paragraph({
  alignment: AlignmentType.CENTER,
  spacing: { after: o.after ?? 120 },
  children: [new TextRun({ text, font: BODY, size: o.size ?? 24, bold: o.bold,
    color: o.color ?? INK, characterSpacing: o.spacing })],
});

// ---------------------------------------------------------------- title page
push(spacer(500));
push(titleLine('Department of Computer Engineering', { size: 26, bold: true, color: MUTED, after: 60 }));
push(titleLine('Academic Year 2026-27  ·  ODD Semester', { size: 20, color: MUTED, after: 700 }));
push(new Paragraph({
  alignment: AlignmentType.CENTER, spacing: { after: 40 },
  children: [new TextRun({ text: 'DEVOPS PROJECT REPORT', font: BODY, size: 22, bold: true,
    color: ACCENT, characterSpacing: 60 })],
}));
push(new Paragraph({
  alignment: AlignmentType.CENTER, spacing: { after: 240 },
  border: { bottom: { style: BorderStyle.SINGLE, size: 8, color: ACCENT, space: 10 } },
  children: [],
}));
push(titleLine('Containerized Blood Bank', { size: 44, bold: true, after: 20 }));
push(titleLine('Donor Matching Portal', { size: 44, bold: true, after: 220 }));
push(titleLine('A complete DevOps workflow from commit to a provisioned,', { size: 21, color: MUTED, after: 20 }));
push(titleLine('rollback-capable container', { size: 21, color: MUTED, after: 700 }));

push(table(null, [
  ['Submitted by', 'AYUSH MAYEKAR'],
  ['Roll Number', '23102A0018'],
  ['Class', 'BE Computer Engineering  ·  Division A'],
  ['Subject', 'DevOps'],
  ['Project Title', 'Containerized Blood Bank Donor Matching Portal'],
  ['Tasks Covered', 'All 15 subject tasks'],
  ['Final Release', 'v1.3.0'],
], [2900, 6460], { labelColumn: true }));

push(spacer(900));
push(titleLine('Submitted in partial fulfilment of the requirements for the DevOps subject',
  { size: 18, color: MUTED, after: 0 }));
push(new Paragraph({ children: [new PageBreak()] }));

// --------------------------------------------------------------- declaration
push(h1('Declaration'));
push(p('I declare that this project report, titled "Containerized Blood Bank Donor Matching Portal", is my own work, carried out by me for the DevOps subject of the Department of Computer Engineering during the ODD semester of the academic year 2026-27.'));
push(p('All fifteen tasks specified in the subject task sheet were implemented and verified on a working system. Every result quoted in this report — build logs, test counts, pipeline stage timings, Ansible play recaps and container output — is captured output from a real run, retained in the project repository under the evidence/ directory, and can be reproduced using the commands given at the end of each task section.'));
push(p('The twelve defects described in this report were encountered during development and fixed; each is recorded in the repository with its cause rather than only its remedy. Where the project falls short of production readiness, this is stated explicitly.'));
push(spacer(600));
push(table(null, [
  ['Name', 'Ayush Mayekar'],
  ['Roll Number', '23102A0018'],
  ['Class', 'BE Computer Engineering, Division A'],
  ['Date', '9 October 2026'],
  ['Signature', ' '],
], [2900, 6460], { labelColumn: true }));
push(new Paragraph({ children: [new PageBreak()] }));

// ---------------------------------------------------------- table of contents
push(h1('Contents'));
push(new TableOfContents('Contents', { hyperlink: true, headingStyleRange: '1-2' }));
push(new Paragraph({ children: [new PageBreak()] }));

// ------------------------------------------------------------------- the body
// The generated FINAL-REPORT.md already carries its own front matter; the title
// page and declaration above replace it, so the first heading is dropped and
// the rest rendered as-is.
const md = fs.readFileSync(SOURCE, 'utf8');
const body = md
  .replace(/^# Containerized Blood Bank Donor Matching Portal\n/, '')
  .replace(/^## DevOps Project — Consolidated Report\n/m, '');

push(render(body));

const doc = makeDocument({
  title: 'Containerized Blood Bank Donor Matching Portal — DevOps Project Report',
  description: 'DevOps project report covering all 15 subject tasks. Ayush Mayekar, 23102A0018, BE CMPN A.',
  headerText: 'Containerized Blood Bank Donor Matching Portal  ·  Ayush Mayekar  ·  23102A0018',
  children,
  updateFields: true,     // Word fills the TOC field on open
});

Packer.toBuffer(doc).then((buf) => {
  fs.writeFileSync(OUT, buf);
  console.log('written:', OUT, (buf.length / 1024).toFixed(0) + ' KB');
});
