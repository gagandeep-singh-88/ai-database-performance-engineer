import { Box, Chip, Typography } from '@mui/material';
import ConstructionIcon from '@mui/icons-material/Construction';

interface Props {
  title: string;
  module: string;
}

export default function ComingSoonPage({ title, module }: Props) {
  return (
    <Box sx={{ textAlign: 'center', pt: 10 }}>
      <ConstructionIcon sx={{ fontSize: 56, color: 'text.secondary' }} />
      <Typography variant="h4" sx={{ mt: 2 }}>
        {title}
      </Typography>
      <Chip label={module} color="secondary" variant="outlined" sx={{ mt: 2 }} />
    </Box>
  );
}
