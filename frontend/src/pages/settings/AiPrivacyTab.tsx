import { useEffect, useState, type FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Snackbar,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ShieldIcon from '@mui/icons-material/Shield';
import { privacyApi } from '../../api/privacy';
import type { AiResponseStyle, PrivacySettingsDto, SanitizationMode } from '../../types/privacy';
import { extractApiError } from '../../utils/errors';

const ALLOWED = ['SQL Structure', 'Execution Plan', 'Query Statistics', 'CPU Usage', 'Buffer Statistics', 'Index Metadata'];
const NEVER_SENT = [
  'Table Records',
  'Customer Data',
  'Email Addresses',
  'Passwords',
  'Credit Card Numbers',
  'API Keys',
  'JWT Tokens',
];

export default function AiPrivacyTab() {
  const [settings, setSettings] = useState<PrivacySettingsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  useEffect(() => {
    privacyApi
      .getSettings()
      .then(setSettings)
      .catch(() => setError('Failed to load AI & privacy settings'))
      .finally(() => setLoading(false));
  }, []);

  const set = <K extends keyof PrivacySettingsDto>(field: K, value: PrivacySettingsDto[K]) =>
    setSettings((s) => (s ? { ...s, [field]: value } : s));

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    if (!settings) return;
    setError(null);
    setSaving(true);
    try {
      const updated = await privacyApi.updateSettings(settings);
      setSettings(updated);
      setSnackbar('AI & privacy settings saved');
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setSaving(false);
    }
  };

  if (loading || !settings) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Box component="form" onSubmit={handleSave}>
      <Stack spacing={3}>
        <Card>
          <CardHeader title="AI" subheader="Control how the AI analyzes your queries" />
          <Divider />
          <CardContent>
            <Stack spacing={2}>
              <FormControlLabel
                control={
                  <Switch checked={settings.aiEnabled} onChange={(e) => set('aiEnabled', e.target.checked)} />
                }
                label="Enable AI Analysis"
              />
              <Grid container spacing={2.5}>
                <Grid item xs={12} md={6}>
                  <TextField
                    select
                    label="AI Response Style"
                    fullWidth
                    value={settings.aiResponseStyle}
                    onChange={(e) => set('aiResponseStyle', e.target.value as AiResponseStyle)}
                  >
                    <MenuItem value="TECHNICAL">Technical</MenuItem>
                    <MenuItem value="SIMPLE">Simple</MenuItem>
                  </TextField>
                </Grid>
                <Grid item xs={12} md={6}>
                  <TextField
                    type="number"
                    label="Maximum AI Response Length"
                    fullWidth
                    value={settings.maxResponseLength}
                    onChange={(e) => set('maxResponseLength', Number(e.target.value))}
                    inputProps={{ min: 200, max: 8000, step: 100 }}
                    helperText="Characters (200–8000)"
                  />
                </Grid>
              </Grid>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardHeader title="Privacy" subheader="Control what is sanitized and validated before reaching the AI" />
          <Divider />
          <CardContent>
            <Stack spacing={1.5}>
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.sqlSanitizationEnabled}
                    onChange={(e) => set('sqlSanitizationEnabled', e.target.checked)}
                  />
                }
                label="Enable SQL Sanitization"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.payloadValidationEnabled}
                    onChange={(e) => set('payloadValidationEnabled', e.target.checked)}
                  />
                }
                label="Enable Payload Validation"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.showPayloadPreview}
                    onChange={(e) => set('showPayloadPreview', e.target.checked)}
                  />
                }
                label="Show AI Payload Preview"
              />
              <FormControlLabel
                control={
                  <Switch
                    checked={settings.blockOnPiiDetected}
                    onChange={(e) => set('blockOnPiiDetected', e.target.checked)}
                  />
                }
                label="Block AI Request if PII is Detected"
              />
              <TextField
                select
                label="Sanitization Mode"
                sx={{ maxWidth: 360, mt: 1 }}
                value={settings.sanitizationMode}
                onChange={(e) => set('sanitizationMode', e.target.value as SanitizationMode)}
              >
                <MenuItem value="AUTOMATIC">Automatic</MenuItem>
                <MenuItem value="WARN_BEFORE_SENDING">Warn Before Sending</MenuItem>
                <MenuItem value="STRICT_BLOCK">Strict Block</MenuItem>
              </TextField>
            </Stack>
          </CardContent>
        </Card>

        <Card>
          <CardHeader
            avatar={<ShieldIcon color="secondary" />}
            title="What we send to AI"
            subheader="Exactly what information leaves your database — enforced by the Privacy & Sanitization Engine"
          />
          <Divider />
          <CardContent>
            <Grid container spacing={3}>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  <Chip size="small" color="success" label="Allowed" sx={{ mr: 1 }} />
                </Typography>
                <List dense disablePadding>
                  {ALLOWED.map((item) => (
                    <ListItem key={item} disableGutters>
                      <ListItemIcon sx={{ minWidth: 32 }}>
                        <CheckCircleIcon fontSize="small" color="success" />
                      </ListItemIcon>
                      <ListItemText primary={item} />
                    </ListItem>
                  ))}
                </List>
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="subtitle2" sx={{ mb: 1 }}>
                  <Chip size="small" color="error" label="Never sent" sx={{ mr: 1 }} />
                </Typography>
                <List dense disablePadding>
                  {NEVER_SENT.map((item) => (
                    <ListItem key={item} disableGutters>
                      <ListItemIcon sx={{ minWidth: 32 }}>
                        <CancelIcon fontSize="small" color="error" />
                      </ListItemIcon>
                      <ListItemText primary={item} />
                    </ListItem>
                  ))}
                </List>
              </Grid>
            </Grid>
          </CardContent>
        </Card>

        {error && <Alert severity="error">{error}</Alert>}
        <Box>
          <Button type="submit" variant="contained" disabled={saving}>
            {saving ? 'Saving…' : 'Save changes'}
          </Button>
        </Box>
      </Stack>

      <Snackbar
        open={Boolean(snackbar)}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
}
