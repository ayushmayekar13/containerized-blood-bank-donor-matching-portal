// Shared Document factory: page geometry, numbering, header and footer.
const {
  Document, Paragraph, TextRun, AlignmentType, BorderStyle,
  Header, Footer, PageNumber, NumberFormat, LevelFormat,
} = require('docx');
const { BODY, MUTED, RULE } = require('./helpers.js');

const numbering = {
  config: [
    {
      reference: 'bullets',
      levels: [
        { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 240 } } } },
        { level: 1, format: LevelFormat.BULLET, text: '◦', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 900, hanging: 240 } } } },
      ],
    },
    {
      reference: 'numbers',
      levels: [
        { level: 0, format: LevelFormat.DECIMAL, text: '%1.', alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 460, hanging: 240 } } } },
      ],
    },
  ],
};

const pageNumberRuns = () => [
  new TextRun({ text: 'Page ', font: BODY, size: 16, color: MUTED }),
  new TextRun({ children: [PageNumber.CURRENT], font: BODY, size: 16, color: MUTED }),
  new TextRun({ text: ' of ', font: BODY, size: 16, color: MUTED }),
  new TextRun({ children: [PageNumber.TOTAL_PAGES], font: BODY, size: 16, color: MUTED }),
];

function makeDocument({ title, description, headerText, children, updateFields = false }) {
  return new Document({
    features: updateFields ? { updateFields: true } : undefined,
    creator: 'Ayush Mayekar',
    title,
    description,
    styles: { default: { document: { run: { font: BODY, size: 21, color: '1A1A1A' } } } },
    numbering,
    sections: [{
      properties: {
        page: {
          size: { width: 11906, height: 16838 },   // A4
          margin: { top: 1250, right: 1134, bottom: 1250, left: 1134, header: 600, footer: 600 },
          pageNumbers: { start: 1, formatType: NumberFormat.DECIMAL },
        },
        titlePage: true,
      },
      headers: {
        default: new Header({
          children: [new Paragraph({
            alignment: AlignmentType.RIGHT,
            spacing: { after: 60 },
            border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 4 } },
            children: [new TextRun({ text: headerText, font: BODY, size: 16, color: MUTED })],
          })],
        }),
        first: new Header({ children: [new Paragraph({ children: [] })] }),
      },
      footers: {
        default: new Footer({
          children: [new Paragraph({
            alignment: AlignmentType.CENTER,
            border: { top: { style: BorderStyle.SINGLE, size: 4, color: RULE, space: 6 } },
            children: pageNumberRuns(),
          })],
        }),
        first: new Footer({
          children: [new Paragraph({ alignment: AlignmentType.CENTER, children: pageNumberRuns() })],
        }),
      },
      children,
    }],
  });
}

module.exports = { makeDocument, numbering };
