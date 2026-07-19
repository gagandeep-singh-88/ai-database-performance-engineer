import { createTheme, alpha } from '@mui/material/styles';

// Startup-dark palette: deep navy surfaces with an indigo→cyan accent system.
export const BRAND_GRADIENT = 'linear-gradient(135deg, #6366f1 0%, #22d3ee 100%)';

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#818cf8', light: '#a5b4fc', dark: '#6366f1' },
    secondary: { main: '#22d3ee' },
    success: { main: '#34d399' },
    warning: { main: '#fbbf24' },
    error: { main: '#f87171' },
    background: { default: '#0b1020', paper: '#111731' },
    divider: alpha('#818cf8', 0.12),
    text: { primary: '#e2e8f0', secondary: '#94a3b8' },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily:
      '"Inter", system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif',
    h1: { fontWeight: 800, letterSpacing: '-0.02em' },
    h2: { fontWeight: 800, letterSpacing: '-0.02em' },
    h3: { fontWeight: 700, letterSpacing: '-0.01em' },
    h4: { fontWeight: 700 },
    h5: { fontWeight: 700 },
    h6: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { backgroundImage: 'radial-gradient(ellipse at top, #131b3a 0%, #0b1020 55%)' },
      },
    },
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          border: `1px solid ${alpha('#818cf8', 0.14)}`,
          backgroundImage: 'none',
        },
      },
    },
    MuiButton: {
      styleOverrides: {
        containedPrimary: {
          background: BRAND_GRADIENT,
          color: '#0b1020',
          '&:hover': { opacity: 0.92, background: BRAND_GRADIENT },
        },
      },
    },
  },
});

export default theme;
