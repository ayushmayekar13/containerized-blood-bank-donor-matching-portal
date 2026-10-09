// ---------------------------------------------------------------------------
// A small Markdown → docx renderer, scoped to exactly the constructs the task
// documents use: headings, paragraphs, bullet and numbered lists, tables,
// fenced code blocks, blockquotes, horizontal rules, and inline bold, italic,
// `code` and [links](target).
//
// Why a renderer rather than a second copy of the content: the fifteen task
// documents in docs/ are the single source. Generating the Word files from
// them means a correction is one edit, and the .docx can never state something
// the repository's own documentation does not.
// ---------------------------------------------------------------------------
const { Paragraph, TextRun, AlignmentType, BorderStyle, ShadingType } = require('docx');
const H = require('./helpers.js');
const { h1, h2, h3, bullet, numbered, code, table, spacer,
        BODY, MONO, INK, MUTED, ACCENT, RULE, CODE_BG } = H;

/** Split a line into runs, honouring **bold**, *italic*, `code` and [text](link). */
function inline(text, base = {}) {
  const runs = [];
  // Links are flattened to their visible text: a Word document that renders a
  // repository-relative path as a dead hyperlink is worse than plain text.
  let s = text
    // <http://example> autolinks: keep the URL, drop the brackets
    .replace(/<((?:https?|mailto):[^>]+)>/g, '$1')
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g, (_, label, href) =>
      /^https?:/.test(href) ? `${label} (${href})` : label);

  const re = /(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)/g;
  let last = 0, m;
  const push = (t, o) => { if (t) runs.push(new TextRun({ text: t, font: o.mono ? MONO : BODY,
    size: o.mono ? (base.size ? base.size - 2 : 18) : (base.size ?? 21),
    bold: o.bold ?? base.bold, italics: o.italics ?? base.italics,
    color: base.color ?? INK,
    shading: o.mono ? { type: ShadingType.CLEAR, fill: CODE_BG } : undefined })); };

  while ((m = re.exec(s)) !== null) {
    push(s.slice(last, m.index), {});
    const tok = m[0];
    if (tok.startsWith('**'))      push(tok.slice(2, -2), { bold: true });
    else if (tok.startsWith('`'))  push(tok.slice(1, -1), { mono: true });
    else                           push(tok.slice(1, -1), { italics: true });
    last = re.lastIndex;
  }
  push(s.slice(last), {});
  return runs.length ? runs : [new TextRun({ text: '', font: BODY, size: 21 })];
}

function para(text, opts = {}) {
  return new Paragraph({
    spacing: { after: opts.after ?? 140, line: 276 },
    indent: opts.indent,
    border: opts.border,
    shading: opts.shading,
    children: inline(text, opts),
  });
}

/**
 * Column widths: weight each column by the length of its longest cell.
 *
 * docx requires columnWidths to sum to exactly the table width, and every value
 * to be positive. A naive "give the remainder to the last column" goes negative
 * on a wide table - the nine-column compatibility matrix in Task 3 is the one
 * that found this - so the minimum is adaptive and the rounding remainder is
 * settled on the widest column, which can always absorb it.
 */
function widths(rows, total) {
  const cols = rows[0].length;
  // Never demand a minimum that cannot fit: with nine columns in 9360 DXA,
  // a flat 900 minimum leaves nothing to distribute.
  const min = Math.max(400, Math.min(900, Math.floor(total / cols) - 100));

  const weight = new Array(cols).fill(1);
  rows.forEach(r => r.forEach((c, i) => {
    if (i < cols) weight[i] = Math.max(weight[i], Math.min(c.length, 90));
  }));

  const sum = weight.reduce((a, b) => a + b, 0);
  let w = weight.map(m => Math.max(min, Math.round(total * m / sum)));

  // Scale back proportionally if the minimums pushed the total over.
  let over = w.reduce((a, b) => a + b, 0) - total;
  while (over > 0) {
    const widest = w.indexOf(Math.max(...w));
    const take = Math.min(over, w[widest] - min);
    if (take <= 0) break;                 // every column is already at the minimum
    w[widest] -= take;
    over -= take;
  }

  // Settle any remaining rounding difference on the widest column.
  const diff = total - w.reduce((a, b) => a + b, 0);
  if (diff !== 0) w[w.indexOf(Math.max(...w))] += diff;

  // Final guard: docx rejects a non-positive width outright.
  if (w.some(x => x <= 0)) {
    const flat = Math.floor(total / cols);
    w = new Array(cols).fill(flat);
    w[cols - 1] += total - flat * cols;
  }
  return w;
}

const splitRow = (line) => line.trim().replace(/^\|/, '').replace(/\|$/, '')
  .split(/(?<!\\)\|/).map(c => c.trim().replace(/\\\|/g, '|'));

/** Convert one markdown document into an array of docx elements. */
function render(md, opts = {}) {
  const out = [];
  const lines = md.split('\n');
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];

    // fenced code block
    if (/^\s*```/.test(line)) {
      const buf = [];
      i++;
      while (i < lines.length && !/^\s*```/.test(lines[i])) { buf.push(lines[i]); i++; }
      i++;
      out.push(...code(buf.length ? buf : [' '], { barColor: ACCENT }));
      continue;
    }

    // table
    if (/^\s*\|/.test(line) && i + 1 < lines.length && /^\s*\|[\s:|-]+\|\s*$/.test(lines[i + 1])) {
      const header = splitRow(line);
      i += 2;
      const rows = [];
      while (i < lines.length && /^\s*\|/.test(lines[i])) { rows.push(splitRow(lines[i])); i++; }
      const all = [header, ...rows];
      const w = widths(all, H.CONTENT_W);
      // Cell contents go through inline(), so **bold**, `code` and [links]
      // inside a table render as formatting rather than as literal characters.
      out.push(table(header, rows.map(r => r.map(c =>
        ({ runs: inline(c, { size: 19 }) }))), w));
      out.push(spacer(200));
      continue;
    }

    // headings
    let m;
    if ((m = /^#\s+(.*)/.exec(line)))      { out.push(h1(m[1].trim())); i++; continue; }
    if ((m = /^##\s+(.*)/.exec(line)))     { out.push(h2(m[1].trim())); i++; continue; }
    if ((m = /^###\s+(.*)/.exec(line)))    { out.push(h3(m[1].trim())); i++; continue; }

    // horizontal rule
    if (/^\s*---+\s*$/.test(line)) {
      out.push(new Paragraph({
        spacing: { before: 100, after: 180 },
        border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 6 } },
        children: [],
      }));
      i++; continue;
    }

    // blockquote (possibly multi-line)
    if (/^\s*>/.test(line)) {
      const buf = [];
      while (i < lines.length && /^\s*>/.test(lines[i])) { buf.push(lines[i].replace(/^\s*>\s?/, '')); i++; }
      out.push(para(buf.join(' ').trim(), {
        italics: true,
        indent: { left: 340, right: 340 },
        border: { left: { style: BorderStyle.SINGLE, size: 14, color: ACCENT, space: 10 } },
        after: 200,
      }));
      continue;
    }

    // bullet / numbered list item
    if ((m = /^\s*[-*]\s+(.*)/.exec(line))) {
      const level = /^\s{2,}/.test(line) ? 1 : 0;
      out.push(new Paragraph({
        numbering: { reference: 'bullets', level },
        spacing: { after: 70, line: 264 },
        children: inline(m[1].trim()),
      }));
      i++; continue;
    }
    if ((m = /^\s*\d+\.\s+(.*)/.exec(line))) {
      out.push(new Paragraph({
        numbering: { reference: 'numbers', level: 0 },
        spacing: { after: 70, line: 264 },
        children: inline(m[1].trim()),
      }));
      i++; continue;
    }

    // blank
    if (!line.trim()) { i++; continue; }

    // paragraph: join continuation lines (markdown soft wraps)
    const buf = [line.trim()];
    i++;
    while (i < lines.length && lines[i].trim() &&
           !/^\s*([-*]\s|\d+\.\s|#|>|\||```|---+\s*$)/.test(lines[i])) {
      buf.push(lines[i].trim()); i++;
    }
    out.push(para(buf.join(' ')));
  }
  return out;
}

module.exports = { render, inline, para };
