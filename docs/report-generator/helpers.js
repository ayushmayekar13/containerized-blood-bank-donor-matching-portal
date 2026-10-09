const {
  Paragraph, TextRun, HeadingLevel, AlignmentType, BorderStyle, PageBreak,
  Table, TableRow, TableCell, WidthType, ShadingType, convertInchesToTwip,
} = require('docx');

// ---------------------------------------------------------------------------
// A4 page: 11906 x 16838 DXA. Margins 1134 (2 cm) left/right.
// Usable width = 11906 - 2268 = 9638. Tables use 9360 to leave a hair of slack.
// ---------------------------------------------------------------------------
const CONTENT_W = 9360;
const MONO = 'Consolas';
const BODY = 'Calibri';

const INK      = '1A1A1A';
const MUTED    = '595959';
const ACCENT   = 'B3202E';   // the project's red
const RULE     = 'D0D4D8';
const HEAD_BG  = 'F2F4F6';
const CODE_BG  = 'F7F8FA';
const NOTE_BG  = 'FDF4F5';

// --------------------------------------------------------------- paragraphs

function p(text, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.after ?? 140, line: 276 },
    alignment: opts.align,
    indent: opts.indent,
    children: [new TextRun({
      text,
      font: BODY, size: opts.size ?? 21,
      color: opts.color ?? INK,
      bold: opts.bold, italics: opts.italics,
    })],
  });
}

/** Rich paragraph: pass an array of [text, {bold,italics,mono,color}] pairs. */
function rp(parts, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.after ?? 140, line: 276 },
    alignment: opts.align,
    children: parts.map(([text, o = {}]) => new TextRun({
      text,
      font: o.mono ? MONO : BODY,
      size: o.mono ? 18 : (o.size ?? 21),
      bold: o.bold, italics: o.italics,
      color: o.color ?? INK,
      shading: o.mono ? { type: ShadingType.CLEAR, fill: CODE_BG } : undefined,
    })),
  });
}

function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1,
    keepNext: true,
    spacing: { before: 360, after: 200 },
    border: { bottom: { style: BorderStyle.SINGLE, size: 10, color: ACCENT, space: 6 } },
    children: [new TextRun({ text, font: BODY, size: 30, bold: true, color: ACCENT })],
  });
}

function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    keepNext: true,
    spacing: { before: 280, after: 140 },
    children: [new TextRun({ text, font: BODY, size: 24, bold: true, color: INK })],
  });
}

function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    keepNext: true,
    spacing: { before: 200, after: 100 },
    children: [new TextRun({ text, font: BODY, size: 21, bold: true, color: MUTED })],
  });
}

function bullet(text, level = 0) {
  return new Paragraph({
    numbering: { reference: 'bullets', level },
    spacing: { after: 70, line: 264 },
    children: [new TextRun({ text, font: BODY, size: 21, color: INK })],
  });
}

function numbered(text, level = 0) {
  return new Paragraph({
    numbering: { reference: 'numbers', level },
    spacing: { after: 70, line: 264 },
    children: [new TextRun({ text, font: BODY, size: 21, color: INK })],
  });
}

/** Fixed-width block, shaded, with a left accent bar. */
function code(lines, opts = {}) {
  const arr = Array.isArray(lines) ? lines : lines.split('\n');
  return arr.map((line, i) => new Paragraph({
    keepLines: true,
    keepNext: i < arr.length - 1,
    spacing: { after: i === arr.length - 1 ? (opts.after ?? 160) : 0, line: 240 },
    shading: { type: ShadingType.CLEAR, fill: CODE_BG },
    indent: { left: 170 },
    border: {
      left: { style: BorderStyle.SINGLE, size: 12, color: opts.barColor ?? RULE, space: 6 },
      top: i === 0 ? { style: BorderStyle.SINGLE, size: 2, color: RULE, space: 3 } : undefined,
      bottom: i === arr.length - 1 ? { style: BorderStyle.SINGLE, size: 2, color: RULE, space: 3 } : undefined,
    },
    children: [new TextRun({ text: line || ' ', font: MONO, size: 17, color: INK })],
  }));
}

/** Callout box for a key finding. */
function callout(title, body) {
  return [
    new Paragraph({
      keepNext: true,
      keepLines: true,
      spacing: { before: 160, after: 0 },
      shading: { type: ShadingType.CLEAR, fill: NOTE_BG },
      indent: { left: 170, right: 170 },
      border: { left: { style: BorderStyle.SINGLE, size: 18, color: ACCENT, space: 8 } },
      children: [new TextRun({ text: title, font: BODY, size: 21, bold: true, color: ACCENT })],
    }),
    new Paragraph({
      keepLines: true,
      spacing: { before: 40, after: 200, line: 264 },
      shading: { type: ShadingType.CLEAR, fill: NOTE_BG },
      indent: { left: 170, right: 170 },
      border: { left: { style: BorderStyle.SINGLE, size: 18, color: ACCENT, space: 8 } },
      children: [new TextRun({ text: body, font: BODY, size: 20, color: INK })],
    }),
  ];
}

// ------------------------------------------------------------------- tables

function cellPara(text, o = {}) {
  return new Paragraph({
    spacing: { before: 50, after: 50, line: 252 },
    alignment: o.align,
    // `runs` lets a caller supply already-formatted TextRuns, which is how
    // inline markdown inside a table cell (**bold**, `code`, [links]) survives
    // into the document instead of being printed literally.
    children: o.runs ?? [new TextRun({
      text: String(text),
      font: o.mono ? MONO : BODY,
      size: o.mono ? 17 : (o.size ?? 19),
      bold: o.bold,
      color: o.color ?? INK,
    })],
  });
}

/**
 * table(headers, rows, widths, opts)
 *   headers: array of strings (or null for a headerless table)
 *   rows:    array of arrays; a cell may be a string or {text, mono, bold, color}
 *   widths:  array of DXA ints summing to CONTENT_W
 */
function table(headers, rows, widths, opts = {}) {
  const mk = (value, o = {}) => {
    const spec = (value && typeof value === 'object') ? value : { text: value };
    return new TableCell({
      width: { size: o.width, type: WidthType.DXA },
      shading: o.fill ? { type: ShadingType.CLEAR, fill: o.fill } : undefined,
      margins: { top: 60, bottom: 60, left: 110, right: 110 },
      children: [cellPara(spec.text, {
        runs: spec.runs,
        bold: spec.bold ?? o.bold,
        mono: spec.mono,
        color: spec.color,
        align: spec.align ?? o.align,
      })],
    });
  };

  const trs = [];
  if (headers) {
    trs.push(new TableRow({
      tableHeader: true,
      cantSplit: true,
      children: headers.map((hdr, i) => mk(hdr, { width: widths[i], fill: HEAD_BG, bold: true })),
    }));
  }
  rows.forEach((row) => {
    trs.push(new TableRow({
      cantSplit: true,
      children: row.map((c, i) => mk(c, {
        width: widths[i],
        fill: opts.labelColumn && i === 0 ? HEAD_BG : undefined,
        bold: opts.labelColumn && i === 0,
      })),
    }));
  });

  return new Table({
    columnWidths: widths,
    width: { size: CONTENT_W, type: WidthType.DXA },
    borders: {
      top:    { style: BorderStyle.SINGLE, size: 4, color: RULE },
      bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE },
      left:   { style: BorderStyle.SINGLE, size: 4, color: RULE },
      right:  { style: BorderStyle.SINGLE, size: 4, color: RULE },
      insideHorizontal: { style: BorderStyle.SINGLE, size: 2, color: RULE },
      insideVertical:   { style: BorderStyle.SINGLE, size: 2, color: RULE },
    },
    rows: trs,
  });
}

function spacer(after = 180) {
  return new Paragraph({ spacing: { after }, children: [] });
}

function pageBreak() {
  return new Paragraph({ children: [new PageBreak()] });
}

module.exports = {
  CONTENT_W, MONO, BODY, INK, MUTED, ACCENT, RULE, HEAD_BG, CODE_BG,
  p, rp, h1, h2, h3, bullet, numbered, code, callout, table, spacer, pageBreak,
};
