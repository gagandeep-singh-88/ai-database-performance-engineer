import { Box, Typography } from '@mui/material';

const GAUGE_COLORS: Record<string, string> = {
  A: '#34d399',
  B: '#34d399',
  C: '#fbbf24',
  D: '#fbbf24',
  F: '#f87171',
};

/** SVG arc gauge: hero number + grade, colored by status (not series) color. */
export default function ScoreGauge({ score, grade }: { score: number; grade: string }) {
  const color = GAUGE_COLORS[grade] ?? '#94a3b8';

  return (
    <Box sx={{ position: 'relative', width: 180, mx: 'auto' }}>
      <svg viewBox="0 0 180 100" width="180" height="100" role="img" aria-label={`Health score ${score} out of 100, grade ${grade}`}>
        <path
          d="M 10 95 A 80 80 0 0 1 170 95"
          fill="none"
          stroke="rgba(255,255,255,0.08)"
          strokeWidth="12"
          strokeLinecap="round"
        />
        <path
          d="M 10 95 A 80 80 0 0 1 170 95"
          fill="none"
          stroke={color}
          strokeWidth="12"
          strokeLinecap="round"
          strokeDasharray={`${(score / 100) * 251} 251`}
          style={{ transition: 'stroke-dasharray 0.8s ease' }}
        />
      </svg>
      <Box sx={{ position: 'absolute', top: 34, left: 0, right: 0, textAlign: 'center' }}>
        <Typography variant="h3" sx={{ fontWeight: 800, lineHeight: 1 }}>
          {score}
        </Typography>
        <Typography variant="body2" sx={{ color, fontWeight: 700 }}>
          Grade {grade}
        </Typography>
      </Box>
    </Box>
  );
}
