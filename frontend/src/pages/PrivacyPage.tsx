import { useEffect, useMemo, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  FormControlLabel,
  Grid,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ShieldIcon from '@mui/icons-material/Shield';
import GppGoodIcon from '@mui/icons-material/GppGood';
import GppBadIcon from '@mui/icons-material/GppBad';
import VisibilityOffIcon from '@mui/icons-material/VisibilityOff';
import PlayArrowIcon from '@mui/icons-material/PlayArrow';
import { privacyApi } from '../api/privacy';
import type {
  AuditLogItem,
  PayloadPreviewResponse,
  PrivacySettings,
  PrivacyStatus,
} from '../types/privacy';
import { extractApiError } from '../utils/errors';

const DEMO_SQL = `SELECT o.id, c.full_name, c.email
FROM orders o
JOIN customers c ON c.id = o.customer_id
WHERE c.email = 'john.doe@example.com'
  AND c.phone = '+1 (415) 555-2671'
  AND o.status = 'pending'
ORDER BY o.created_at DESC
LIMIT 20;`;

const STATUS_META: Record<PrivacyStatus, { color: 'success' | 'error' | 'default'; label: string; icon: JSX.Element }> = {
  PROTECTED: { color: 'success', label: 'Protected — safe to send to AI', icon: <GppGoodIcon fontSize="small" /> },
  BLOCKED: { color: 'error', label: 'Blocked — sensitive data remained', icon: <GppBadIcon fontSize="small" /> },
  AI_DISABLED: { color: 'default', label: 'AI disabled — nothing is sent', icon: <VisibilityOffIcon fontSize="small" /> },
};

function CodeBox({ text, muted }: { text: string | null; muted?: boolean }) {
  return (
    <Box
      sx={{
        bgcolor: 'rgba(0,0,0,0.35)',
        border: 1,
        borderColor: 'divider',
        borderRadius: 2,
        p: 1.5,
        minHeight: 120,
        overflowX: 'auto',
      }}
    >
      <Typography
        component="pre"
        sx={{ fontFamily: 'monospace', fontSize: 12.5, m: 0, whiteSpace: 'pre-wrap', color: muted ? 'text.secondary' : 'text.primary' }}
      >
        {text?.trim() || '—'}
      </Typography>
    </Box>
  );
}

export default function PrivacyPage() {
  const [settings, setSettings] = useState<PrivacySettings | null>(null);
  const [savingSetting, setSavingSetting] = useState<string | null>(null);
  const [sql, setSql] = useState(DEMO_SQL);
  const [plan, setPlan] = useState('');
  const [metrics, setMetrics] = useState('');
  const [result, setResult] = useState<PayloadPreviewResponse | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [audit, setAudit] = useState<AuditLogItem[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadAudit = () => privacyApi.audit(20).then(setAudit).catch(() => undefined);

  useEffect(() => {
    privacyApi.getSettings().then(setSettings).catch((err) => setError(extractApiError(err)));
    loadAudit();
  }, []);

  const toggleSetting = async (key: 'sqlSanitizationEnabled' | 'aiEnabled', value: boolean) => {
    setSavingSetting(key);
    setError(null);
    try {
      setSettings(await privacyApi.updateSettings({ [key]: value }));
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setSavingSetting(null);
    }
  };

  const runPreview = async () => {
    setPreviewing(true);
    setError(null);
    try {
      const response = await privacyApi.preview({
        sql: sql.trim() || null,
        executionPlan: plan.trim() || null,
        metricsJson: metrics.trim() || null,
      });
      setResult(response);
      loadAudit();
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setPreviewing(false);
    }
  };

  const statusMeta = result ? STATUS_META[result.privacyStatus] : null;
  const canPreview = useMemo(
    () => Boolean(sql.trim() || plan.trim() || metrics.trim()),
    [sql, plan, metrics],
  );

  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <ShieldIcon color="primary" />
          <Typography variant="h5" fontWeight={700}>
            Privacy &amp; Sanitization
          </Typography>
        </Stack>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
          Every payload is redacted and validated before it reaches the AI. Preview exactly what
          Claude would receive — sensitive customer data never leaves your database.
        </Typography>
      </Box>

      {error && <Alert severity="error" onClose={() => setError(null)}>{error}</Alert>}

      {/* Controls */}
      <Card>
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Controls
          </Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
            <FormControlLabel
              control={
                <Switch
                  checked={settings?.sqlSanitizationEnabled ?? true}
                  disabled={!settings || savingSetting === 'sqlSanitizationEnabled'}
                  onChange={(e) => toggleSetting('sqlSanitizationEnabled', e.target.checked)}
                />
              }
              label="SQL sanitization"
            />
            <FormControlLabel
              control={
                <Switch
                  checked={settings?.aiEnabled ?? true}
                  disabled={!settings || savingSetting === 'aiEnabled'}
                  onChange={(e) => toggleSetting('aiEnabled', e.target.checked)}
                />
              }
              label="AI enabled"
            />
          </Stack>
          <Typography variant="caption" color="text.secondary">
            With sanitization off, the payload is still validated — anything containing PII is blocked,
            never sent raw. With AI off, no data is sent at all.
          </Typography>
        </CardContent>
      </Card>

      {/* Input */}
      <Card>
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            AI payload preview
          </Typography>
          <TextField
            label="SQL"
            value={sql}
            onChange={(e) => setSql(e.target.value)}
            fullWidth
            multiline
            minRows={6}
            InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
          />
          <Accordion sx={{ mt: 1.5 }} disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography variant="body2">Execution plan (optional)</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <TextField
                value={plan}
                onChange={(e) => setPlan(e.target.value)}
                placeholder="Paste EXPLAIN / EXPLAIN ANALYZE output"
                fullWidth
                multiline
                minRows={4}
                InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
              />
            </AccordionDetails>
          </Accordion>
          <Accordion disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography variant="body2">Metrics JSON (optional)</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <TextField
                value={metrics}
                onChange={(e) => setMetrics(e.target.value)}
                placeholder='{"executionTimeMs": 42, "rowsExamined": 100000, ...}'
                fullWidth
                multiline
                minRows={4}
                InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
              />
            </AccordionDetails>
          </Accordion>
          <Button
            variant="contained"
            startIcon={previewing ? <CircularProgress size={16} color="inherit" /> : <PlayArrowIcon />}
            onClick={runPreview}
            disabled={previewing || !canPreview}
            sx={{ mt: 2 }}
          >
            Preview sanitized payload
          </Button>
        </CardContent>
      </Card>

      {/* Result */}
      {result && statusMeta && (
        <Card>
          <CardContent>
            <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2 }}>
              <Typography variant="subtitle1" fontWeight={600}>
                Result
              </Typography>
              <Chip
                size="small"
                color={statusMeta.color}
                icon={statusMeta.icon}
                label={statusMeta.label}
              />
            </Stack>

            <Alert severity={result.validation.passed ? 'success' : result.validation.aiEnabled ? 'error' : 'info'} sx={{ mb: 2 }}>
              {result.validation.message}
            </Alert>

            <Grid container spacing={2}>
              <Grid item xs={12} md={6}>
                <Typography variant="overline" color="text.secondary">
                  Original payload
                </Typography>
                <CodeBox text={result.original.sql} muted />
              </Grid>
              <Grid item xs={12} md={6}>
                <Typography variant="overline" color="primary">
                  Sanitized payload (sent to AI)
                </Typography>
                <CodeBox text={result.sanitized.sql} />
              </Grid>
            </Grid>

            {(result.original.executionPlan || result.original.metrics) && (
              <Grid container spacing={2} sx={{ mt: 0.5 }}>
                {result.original.executionPlan && (
                  <>
                    <Grid item xs={12} md={6}>
                      <Typography variant="overline" color="text.secondary">Original plan</Typography>
                      <CodeBox text={result.original.executionPlan} muted />
                    </Grid>
                    <Grid item xs={12} md={6}>
                      <Typography variant="overline" color="primary">Sanitized plan</Typography>
                      <CodeBox text={result.sanitized.executionPlan} />
                    </Grid>
                  </>
                )}
                {result.original.metrics && (
                  <>
                    <Grid item xs={12} md={6}>
                      <Typography variant="overline" color="text.secondary">Original metrics</Typography>
                      <CodeBox text={result.original.metrics} muted />
                    </Grid>
                    <Grid item xs={12} md={6}>
                      <Typography variant="overline" color="primary">Sanitized metrics</Typography>
                      <CodeBox text={result.sanitized.metrics} />
                    </Grid>
                  </>
                )}
              </Grid>
            )}

            {result.findings.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="overline" color="text.secondary">Sensitive data detected</Typography>
                <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 0.5 }}>
                  {result.findings.map((f) => (
                    <Tooltip key={f.type} title={`${f.occurrences} occurrence(s)`}>
                      <Chip size="small" color="warning" variant="outlined" label={`${f.label} × ${f.occurrences}`} />
                    </Tooltip>
                  ))}
                </Stack>
              </Box>
            )}

            {result.removedFields.length > 0 && (
              <Box sx={{ mt: 2 }}>
                <Typography variant="overline" color="text.secondary">Fields removed &amp; reasons</Typography>
                <Table size="small" sx={{ mt: 0.5 }}>
                  <TableHead>
                    <TableRow>
                      <TableCell>Location</TableCell>
                      <TableCell>Category</TableCell>
                      <TableCell>Reason</TableCell>
                      <TableCell align="right">Count</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {result.removedFields.map((field, i) => (
                      <TableRow key={`${field.location}-${i}`}>
                        <TableCell>{field.location}</TableCell>
                        <TableCell>{field.category}</TableCell>
                        <TableCell>{field.reason}</TableCell>
                        <TableCell align="right">{field.occurrences}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
          </CardContent>
        </Card>
      )}

      {/* Audit */}
      <Card>
        <CardContent>
          <Typography variant="subtitle1" fontWeight={600} gutterBottom>
            Audit trail
          </Typography>
          <Typography variant="caption" color="text.secondary">
            Only PII types and outcomes are recorded — never the raw values.
          </Typography>
          <Divider sx={{ my: 1.5 }} />
          {audit.length === 0 ? (
            <Typography variant="body2" color="text.secondary">No sanitization events yet.</Typography>
          ) : (
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>PII detected</TableCell>
                  <TableCell align="right">Removed</TableCell>
                  <TableCell align="right">Size</TableCell>
                  <TableCell>Result</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {audit.map((row) => (
                  <TableRow key={row.id}>
                    <TableCell>{new Date(row.timestamp).toLocaleString()}</TableCell>
                    <TableCell>{row.piiDetected.length ? row.piiDetected.join(', ') : '—'}</TableCell>
                    <TableCell align="right">{row.fieldsRemoved}</TableCell>
                    <TableCell align="right">{row.payloadSizeBytes} B</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={row.validationResult}
                        color={
                          row.validationResult === 'PASSED'
                            ? 'success'
                            : row.validationResult === 'BLOCKED'
                              ? 'error'
                              : 'default'
                        }
                      />
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>
    </Stack>
  );
}
