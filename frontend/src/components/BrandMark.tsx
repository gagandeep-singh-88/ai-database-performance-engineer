import { Box, Typography } from '@mui/material';
import logo from '../image/dbInsight_logo.png';

export default function BrandMark({ size = 'medium' }: { size?: 'small' | 'medium' | 'large' }) {
  const variant = size === 'large' ? 'h4' : size === 'medium' ? 'h6' : 'subtitle1';

  return (
    <Box
        sx={{
                display: 'flex',
                flexDirection: 'row', // Forces items side-by-side on the frontend
                alignItems: 'center', // Vertically aligns the logo middle and text middle
                gap: 1.5              // Adds a clean 12px gap between the logo and text
              }}
    >
      <Box
        component="img"
        src={logo}
        alt="Logo"
        sx={{
          width: 70,
          height: 'auto'
        }}
      />
      <Typography variant={variant} sx={{ fontWeight: 800, whiteSpace: 'nowrap' }}>
        DBInsight<Box component="span" sx={{ color: 'secondary.main' }}>X</Box>
      </Typography>
    </Box>
  );
}
