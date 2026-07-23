import { useNavigate } from 'react-router-dom';
import {
  AppBar,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Grid,
  Stack,
  Toolbar,
  Typography,
} from '@mui/material';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SpeedIcon from '@mui/icons-material/Speed';
import DescriptionIcon from '@mui/icons-material/Description';
import LockIcon from '@mui/icons-material/Lock';
import BrandMark from '../components/BrandMark';
import { BRAND_GRADIENT } from '../theme';

const FEATURES = [
  {
    icon: <MonitorHeartIcon color="secondary" />,
    title: 'Live Health Score',
    body: 'Continuous snapshots of pg_stat_statements, activity and locks distilled into a single health score.',
  },
  {
    icon: <QueryStatsIcon color="secondary" />,
    title: 'AI Query Analyzer',
    body: 'Paste SQL or an EXPLAIN ANALYZE plan and get plain-English root causes, missing indexes and optimized SQL.',
  },
  {
    icon: <SmartToyIcon color="secondary" />,
    title: 'Database Copilot',
    body: 'Ask "Why is my database slow?" and get answers grounded in your real metrics, not generic advice.',
  },
  {
    icon: <SpeedIcon color="secondary" />,
    title: 'Impact Estimates',
    body: 'Every recommendation ships with an estimated performance improvement and cost saving.',
  },
  {
    icon: <DescriptionIcon color="secondary" />,
    title: 'One-Click Reports',
    body: 'Generate an executive-ready PDF health report with root causes, fixes and next steps.',
  },
  {
    icon: <LockIcon color="secondary" />,
    title: 'Read-Only & Secure',
    body: 'Read-only database access with credentials sealed in Google Secret Manager. Your data stays yours.',
  },
];

export default function LandingPage() {
  const navigate = useNavigate();

  return (
    <Box>
      <AppBar position="sticky" elevation={0} sx={{ bgcolor: 'rgba(11,16,32,0.8)', backdropFilter: 'blur(8px)' }}>
        <Toolbar sx={{ justifyContent: 'space-between' }}>
          <BrandMark size="small" />
          <Stack direction="row" spacing={1.5}>
            <Button color="inherit" onClick={() => navigate('/login')}>
              Sign in
            </Button>
            <Button variant="contained" onClick={() => navigate('/register')}>
              Get started
            </Button>
          </Stack>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ pt: { xs: 8, md: 14 }, pb: 10, textAlign: 'center' }}>
        <Chip
          label="AI-powered PostgreSQL performance engineering"
          variant="outlined"
          color="secondary"
          sx={{ mb: 3 }}
        />
        <Typography variant="h1" sx={{ fontSize: { xs: 40, md: 64 }, maxWidth: 900, mx: 'auto' }}>
          Your database is slow.{' '}
          <Box
            component="span"
            sx={{ background: BRAND_GRADIENT, WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}
          >
            Find out why in seconds.
          </Box>
        </Typography>
        <Typography variant="h6" color="text.secondary" sx={{ mt: 3, maxWidth: 680, mx: 'auto', fontWeight: 400 }}>
          DBInsightX connects to your PostgreSQL database, analyzes queries and execution plans, and explains
          performance problems in plain English — like having a senior DBA on call, 24/7.
        </Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} justifyContent="center" sx={{ mt: 5 }}>
          <Button size="large" variant="contained" onClick={() => navigate('/register')}>
            Start free analysis
          </Button>
          <Button size="large" variant="outlined" onClick={() => navigate('/login')}>
            Live demo
          </Button>
        </Stack>
      </Container>

      <Container maxWidth="lg" sx={{ pb: 12 }}>
        <Grid container spacing={3}>
          {FEATURES.map((feature) => (
            <Grid item xs={12} sm={6} md={4} key={feature.title}>
              <Card sx={{ height: '100%' }}>
                <CardContent sx={{ p: 3 }}>
                  <Box sx={{ mb: 1.5 }}>{feature.icon}</Box>
                  <Typography variant="h6" gutterBottom>
                    {feature.title}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {feature.body}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          ))}
        </Grid>
      </Container>

      <Box component="footer" sx={{ borderTop: 1, borderColor: 'divider', py: 4, textAlign: 'center' }}>
        <Typography variant="body2" color="text.secondary">
          DBInsightX — AI Database Performance Engineer · Built on Google Cloud
        </Typography>
      </Box>
    </Box>
  );
}
