import { Box, IconButton, Tooltip, Typography } from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { Fragment, type ReactNode } from 'react';

/**
 * Lightweight renderer for the copilot's Markdown replies — code fences,
 * headings, bullets, bold and inline code — without pulling in a full
 * markdown dependency.
 */

function CodeBlock({ code }: { code: string }) {
  return (
    <Box
      sx={{
        position: 'relative',
        bgcolor: 'rgba(0,0,0,0.35)',
        border: 1,
        borderColor: 'divider',
        borderRadius: 2,
        p: 1.5,
        pr: 5,
        my: 1,
        overflowX: 'auto',
      }}
    >
      <Typography component="pre" sx={{ fontFamily: 'monospace', fontSize: 13, m: 0, whiteSpace: 'pre-wrap' }}>
        {code}
      </Typography>
      <Tooltip title="Copy">
        <IconButton
          size="small"
          sx={{ position: 'absolute', top: 6, right: 6 }}
          onClick={() => navigator.clipboard.writeText(code)}
        >
          <ContentCopyIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  );
}

function renderInline(text: string): ReactNode[] {
  // Split on **bold** and `code` spans
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, i) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={i}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('`') && part.endsWith('`') && part.length > 2) {
      return (
        <Box
          key={i}
          component="code"
          sx={{ fontFamily: 'monospace', fontSize: '0.85em', bgcolor: 'rgba(255,255,255,0.08)', px: 0.5, borderRadius: 0.5 }}
        >
          {part.slice(1, -1)}
        </Box>
      );
    }
    return <Fragment key={i}>{part}</Fragment>;
  });
}

function renderTextSegment(segment: string, key: number): ReactNode {
  const lines = segment.split('\n');
  const blocks: ReactNode[] = [];
  let bullets: string[] = [];

  const flushBullets = () => {
    if (bullets.length === 0) return;
    blocks.push(
      <Box component="ul" key={`ul-${blocks.length}`} sx={{ my: 0.5, pl: 3 }}>
        {bullets.map((item, i) => (
          <li key={i}>
            <Typography variant="body2" component="span">
              {renderInline(item)}
            </Typography>
          </li>
        ))}
      </Box>,
    );
    bullets = [];
  };

  for (const line of lines) {
    const trimmed = line.trim();
    const bulletMatch = trimmed.match(/^[-*]\s+(.*)/);
    const numberedMatch = trimmed.match(/^\d+\.\s+(.*)/);
    if (bulletMatch || numberedMatch) {
      bullets.push((bulletMatch ?? numberedMatch)![1]);
      continue;
    }
    flushBullets();
    if (trimmed === '') continue;
    const headingMatch = trimmed.match(/^(#{1,4})\s+(.*)/);
    if (headingMatch) {
      blocks.push(
        <Typography key={`h-${blocks.length}`} variant="subtitle2" sx={{ fontWeight: 700, mt: 1.5, mb: 0.5 }}>
          {renderInline(headingMatch[2])}
        </Typography>,
      );
      continue;
    }
    blocks.push(
      <Typography key={`p-${blocks.length}`} variant="body2" sx={{ my: 0.75 }}>
        {renderInline(trimmed)}
      </Typography>,
    );
  }
  flushBullets();
  return <Fragment key={key}>{blocks}</Fragment>;
}

export default function Markdown({ content }: { content: string }) {
  // Split out ```fenced code blocks``` first
  const segments = content.split(/```(?:\w+)?\n?([\s\S]*?)```/g);
  return (
    <Box>
      {segments.map((segment, i) =>
        i % 2 === 1 ? <CodeBlock key={i} code={segment.trimEnd()} /> : renderTextSegment(segment, i),
      )}
    </Box>
  );
}
