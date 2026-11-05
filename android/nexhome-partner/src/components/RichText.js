import { View } from 'react-native';

import { colors } from '../theme';
import { Text } from './ui';

/** HTML entities the editor writes. */
function decode(text) {
  return text
    .replace(/&nbsp;/g, ' ')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/&amp;/g, '&');
}

/** Bold, italic and underlined runs of one paragraph or list item, as nested Text. */
function Inline({ html, style }) {
  const parts = [];
  const state = { bold: 0, italic: 0, underline: 0 };
  const tokens = html.replace(/<br\s*\/?>/gi, '\n').split(/(<\/?(?:b|strong|i|em|u)\b[^>]*>)/i);
  tokens.forEach((token, i) => {
    const tag = /^<(\/?)(b|strong|i|em|u)\b/i.exec(token);
    if (tag) {
      const change = tag[1] ? -1 : 1;
      const name = tag[2].toLowerCase();
      if (name === 'b' || name === 'strong') state.bold += change;
      else if (name === 'i' || name === 'em') state.italic += change;
      else state.underline += change;
      return;
    }
    const text = decode(token.replace(/<[^>]+>/g, ''));
    if (!text) return;
    parts.push(
      <Text
        key={i}
        weight={state.bold > 0 ? 'bold' : 'regular'}
        style={[style, state.italic > 0 ? { fontStyle: 'italic' } : null, state.underline > 0 ? { textDecorationLine: 'underline' } : null]}
      >
        {text}
      </Text>
    );
  });
  return <Text style={style}>{parts}</Text>;
}

/**
 * Formatted text from the console's editor (p, br, b / strong, i / em, u, ul / ol with li): paragraphs, bullet and
 * numbered lists, bold, italic and underlined runs. Plain text (older policies) shows its lines as paragraphs.
 */
export default function RichText({ html, fontSize = 14, color = colors.ink }) {
  const style = { fontSize, lineHeight: Math.round(fontSize * 1.55), color };
  const source = html && /<\/?(p|br|b|strong|i|em|u|ul|ol|li)\b/i.test(html)
    ? html
    : (html ?? '').split(/\n+/).filter((line) => line.trim()).map((line) => `<p>${line}</p>`).join('');
  const blocks = [];
  const pattern = /<(ul|ol)[^>]*>([\s\S]*?)<\/\1>|<p[^>]*>([\s\S]*?)<\/p>/gi;
  let match;
  let last = 0;
  while ((match = pattern.exec(source))) {
    const between = source.slice(last, match.index).trim();
    if (between) blocks.push({ kind: 'p', html: between });
    if (match[1]) {
      const items = [...match[2].matchAll(/<li[^>]*>([\s\S]*?)<\/li>/gi)].map((m) => m[1]);
      blocks.push({ kind: match[1].toLowerCase(), items });
    } else {
      blocks.push({ kind: 'p', html: match[3] });
    }
    last = pattern.lastIndex;
  }
  const rest = source.slice(last).trim();
  if (rest) blocks.push({ kind: 'p', html: rest });

  return (
    <View style={{ gap: 10 }}>
      {blocks.map((block, i) =>
        block.kind === 'p' ? (
          <Inline key={i} html={block.html} style={style} />
        ) : (
          <View key={i} style={{ gap: 4 }}>
            {block.items.map((item, n) => (
              <View key={n} style={{ flexDirection: 'row', gap: 8, paddingLeft: 4 }}>
                <Text style={[style, { minWidth: 14 }]}>{block.kind === 'ol' ? `${n + 1}.` : '•'}</Text>
                <View style={{ flex: 1 }}>
                  <Inline html={item} style={style} />
                </View>
              </View>
            ))}
          </View>
        )
      )}
    </View>
  );
}
