/** Formatted text of components/admin/common/RichTextEditor: HTML limited to a few tags. */

/** The tags the editor keeps (the backend allows the same, NexHomeRichText); everything else is unwrapped. */
const ALLOWED_TAGS = new Set(['P', 'BR', 'B', 'STRONG', 'I', 'EM', 'U', 'UL', 'OL', 'LI']);

/** Cleans editor HTML to the allowed tags without attributes; a browser's DIV line becomes a paragraph. */
export function cleanRichText(html) {
  const doc = new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html');
  const clean = (node) => {
    [...node.childNodes].forEach((child) => {
      if (child.nodeType === Node.TEXT_NODE) return;
      if (child.nodeType !== Node.ELEMENT_NODE) {
        child.remove();
        return;
      }
      clean(child);
      let element = child;
      if (element.tagName === 'DIV') {
        const paragraph = doc.createElement('p');
        paragraph.append(...element.childNodes);
        element.replaceWith(paragraph);
        element = paragraph;
      }
      if (!ALLOWED_TAGS.has(element.tagName)) {
        element.replaceWith(...element.childNodes);
        return;
      }
      [...element.attributes].forEach((attribute) => element.removeAttribute(attribute.name));
    });
  };
  clean(doc.body);
  const result = doc.body.innerHTML.trim();
  // An editor emptied by the user leaves <br> or an empty paragraph behind
  return doc.body.textContent.trim() === '' ? '' : result;
}

/** The text of editor HTML, for counting characters. */
export function richTextLength(html) {
  return new DOMParser().parseFromString(`<body>${html}</body>`, 'text/html').body.textContent.length;
}

/** Plain text saved before the editor existed, as paragraphs; formatted text is returned as it is. */
export function toRichText(text) {
  if (!text || /<\/?(p|br|b|strong|i|em|u|ul|ol|li)\b/i.test(text)) return text ?? '';
  const escape = (line) => line.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  return text
    .split(/\n+/)
    .filter((line) => line.trim())
    .map((line) => `<p>${escape(line)}</p>`)
    .join('');
}
