import { Box, Typography } from '@mui/material';
import BoltIcon from '@mui/icons-material/Bolt';
import { BRAND_GRADIENT } from '../theme';

export default function BrandMark({ size = 'medium' }: { size?: 'small' | 'medium' | 'large' }) {
  const iconPx = size === 'large' ? 40 : size === 'medium' ? 30 : 24;
  const variant = size === 'large' ? 'h4' : size === 'medium' ? 'h6' : 'subtitle1';

  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
      <Box
        sx={{
          width: iconPx + 10,
          height: iconPx + 10,
          borderRadius: 2,
          display: 'grid',
          placeItems: 'center',
          background: BRAND_GRADIENT,
          color: '#0b1020',
          flexShrink: 0,
        }}
      >
        <BoltIcon sx={{ fontSize: iconPx }} />
      </Box>
      <Typography variant={variant} sx={{ fontWeight: 800, whiteSpace: 'nowrap' }}>
        DBInsight<Box component="span" sx={{ color: 'secondary.main' }}>X</Box>
      </Typography>
    </Box>
  );
}
