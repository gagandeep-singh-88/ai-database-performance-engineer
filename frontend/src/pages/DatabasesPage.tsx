import { useCallback, useEffect, useState, type FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Snackbar,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import StorageIcon from '@mui/icons-material/Storage';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import BoltIcon from '@mui/icons-material/Bolt';
import ShieldIcon from '@mui/icons-material/Shield';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import SnapshotDialog from '../components/SnapshotDialog';
import { connectionsApi } from '../api/connections';
import type { ConnectionResponse, ConnectionTestResult, SslMode } from '../types/connections';
import { extractApiError } from '../utils/errors';

const STATUS_META = {
  HEALTHY: { color: 'success' as const, icon: <CheckCircleIcon fontSize="small" />, label: 'Healthy' },
  UNREACHABLE: { color: 'error' as const, icon: <CancelIcon fontSize="small" />, label: 'Unreachable' },
  UNKNOWN: { color: 'default' as const, icon: <HelpOutlineIcon fontSize="small" />, label: 'Not tested' },
};

const EMPTY_FORM = {
  name: '',
  host: '',
  port: 5432,
  databaseName: '',
  username: '',
  password: '',
  sslMode: 'PREFER' as SslMode,
};

export default function DatabasesPage() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [dialogError, setDialogError] = useState<string | null>(null);
  const [testResult, setTestResult] = useState<ConnectionTestResult | null>(null);
  const [testing, setTesting] = useState(false);
  const [saving, setSaving] = useState(false);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [snackbar, setSnackbar] = useState<string | null>(null);
  const [metricsConnection, setMetricsConnection] = useState<ConnectionResponse | null>(null);

  const refresh = useCallback(() => {
    setLoading(true);
    connectionsApi
      .list()
      .then(setConnections)
      .catch(() => setSnackbar('Failed to load connections'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(refresh, [refresh]);

  const openDialog = () => {
    setForm(EMPTY_FORM);
    setDialogError(null);
    setTestResult(null);
    setDialogOpen(true);
  };

  const set = (field: keyof typeof EMPTY_FORM) => (event: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [field]: field === 'port' ? Number(event.target.value) : event.target.value }));

  const handleTestBeforeSave = async () => {
    setDialogError(null);
    setTestResult(null);
    setTesting(true);
    try {
      setTestResult(await connectionsApi.testAdhoc({ ...form }));
    } catch (err) {
      setDialogError(extractApiError(err));
    } finally {
      setTesting(false);
    }
  };

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    setDialogError(null);
    setSaving(true);
    try {
      const created = await connectionsApi.create(form);
      setDialogOpen(false);
      setSnackbar(`Connection "${created.name}" added`);
      // immediately test the stored connection so the card shows real status
      await connectionsApi.test(created.id).catch(() => undefined);
      refresh();
    } catch (err) {
      setDialogError(extractApiError(err));
    } finally {
      setSaving(false);
    }
  };

  const handleTestCard = async (connection: ConnectionResponse) => {
    setTestingId(connection.id);
    try {
      const result = await connectionsApi.test(connection.id);
      setSnackbar(
        result.success
          ? `${connection.name}: healthy (${result.latencyMs} ms, ${result.serverVersion})`
          : `${connection.name}: ${result.message}`,
      );
    } catch (err) {
      setSnackbar(extractApiError(err));
    } finally {
      setTestingId(null);
      refresh();
    }
  };

  const handleDelete = async (connection: ConnectionResponse) => {
    if (!window.confirm(`Delete connection "${connection.name}"? Its stored credentials are removed too.`)) return;
    try {
      await connectionsApi.remove(connection.id);
      setSnackbar(`Connection "${connection.name}" deleted`);
      refresh();
    } catch (err) {
      setSnackbar(extractApiError(err));
    }
  };

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4">Databases</Typography>
          <Typography color="text.secondary">
            PostgreSQL targets monitored with strictly read-only access
          </Typography>
        </Box>
        <Button variant="contained" startIcon={<AddIcon />} onClick={openDialog}>
          Add database
        </Button>
      </Stack>

      {loading ? (
        <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
          <CircularProgress />
        </Box>
      ) : connections.length === 0 ? (
        <Card>
          <CardContent sx={{ textAlign: 'center', py: 8 }}>
            <StorageIcon sx={{ fontSize: 48, color: 'text.secondary' }} />
            <Typography variant="h6" sx={{ mt: 2 }}>
              No databases connected yet
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
              Add your first PostgreSQL database to start collecting performance insights.
            </Typography>
            <Button variant="contained" startIcon={<AddIcon />} onClick={openDialog}>
              Add database
            </Button>
          </CardContent>
        </Card>
      ) : (
        <Grid container spacing={3}>
          {connections.map((connection) => {
            const meta = STATUS_META[connection.status];
            return (
              <Grid item xs={12} md={6} lg={4} key={connection.id}>
                <Card sx={{ height: '100%' }}>
                  <CardContent>
                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
                      <Stack direction="row" spacing={1.5} alignItems="center">
                        <StorageIcon color="secondary" />
                        <Box>
                          <Typography variant="h6">{connection.name}</Typography>
                          <Typography variant="body2" color="text.secondary">
                            {connection.host}:{connection.port}/{connection.databaseName}
                          </Typography>
                        </Box>
                      </Stack>
                      <Chip size="small" color={meta.color} icon={meta.icon} label={meta.label} />
                    </Stack>

                    <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>
                      User <code>{connection.username}</code> · SSL {connection.sslMode.toLowerCase()}
                      {connection.lastTestedAt &&
                        ` · tested ${new Date(connection.lastTestedAt).toLocaleTimeString()}`}
                    </Typography>
                    {connection.lastError && (
                      <Alert severity="error" sx={{ mt: 1.5 }} variant="outlined">
                        {connection.lastError}
                      </Alert>
                    )}

                    <Stack direction="row" spacing={1} sx={{ mt: 2 }} alignItems="center">
                      <Button
                        size="small"
                        variant="outlined"
                        startIcon={testingId === connection.id ? <CircularProgress size={14} /> : <BoltIcon />}
                        disabled={testingId === connection.id}
                        onClick={() => handleTestCard(connection)}
                      >
                        Test
                      </Button>
                      <Button
                        size="small"
                        variant="outlined"
                        color="secondary"
                        startIcon={<MonitorHeartIcon />}
                        onClick={() => setMetricsConnection(connection)}
                      >
                        Metrics
                      </Button>
                      <Box sx={{ flexGrow: 1 }} />
                      <Tooltip title="Delete connection">
                        <IconButton size="small" onClick={() => handleDelete(connection)}>
                          <DeleteOutlineIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </CardContent>
                </Card>
              </Grid>
            );
          })}
        </Grid>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <Box component="form" onSubmit={handleSave}>
          <DialogTitle>Add PostgreSQL database</DialogTitle>
          <DialogContent>
            <Alert icon={<ShieldIcon />} severity="info" variant="outlined" sx={{ mb: 2.5, mt: 0.5 }}>
              DBPerfAI connects <strong>read-only</strong>. Credentials are stored in Google Secret
              Manager (or encrypted locally in dev) — never in our database. Tip: use a dedicated
              monitoring user with the <code>pg_monitor</code> role.
            </Alert>
            <Stack spacing={2.5}>
              <TextField label="Display name" required value={form.name} onChange={set('name')} autoFocus />
              <Stack direction="row" spacing={2}>
                <TextField label="Host" required fullWidth value={form.host} onChange={set('host')} />
                <TextField
                  label="Port"
                  type="number"
                  required
                  sx={{ width: 140 }}
                  value={form.port}
                  onChange={set('port')}
                />
              </Stack>
              <TextField label="Database name" required value={form.databaseName} onChange={set('databaseName')} />
              <Stack direction="row" spacing={2}>
                <TextField label="Username" required fullWidth value={form.username} onChange={set('username')} />
                <TextField
                  label="Password"
                  type="password"
                  required
                  fullWidth
                  value={form.password}
                  onChange={set('password')}
                />
              </Stack>
              <TextField label="SSL mode" select value={form.sslMode} onChange={set('sslMode')}>
                <MenuItem value="DISABLE">disable</MenuItem>
                <MenuItem value="PREFER">prefer</MenuItem>
                <MenuItem value="REQUIRE">require</MenuItem>
              </TextField>

              {dialogError && <Alert severity="error">{dialogError}</Alert>}
              {testResult && (
                <Alert severity={testResult.success ? 'success' : 'error'}>
                  {testResult.success ? (
                    <>
                      Connected in {testResult.latencyMs} ms — {testResult.serverVersion} ·{' '}
                      {testResult.databaseSize}
                      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                        <Chip
                          size="small"
                          color={testResult.pgStatStatementsEnabled ? 'success' : 'warning'}
                          label={
                            testResult.pgStatStatementsEnabled
                              ? 'pg_stat_statements ✓'
                              : 'pg_stat_statements missing'
                          }
                        />
                        {testResult.readOnlyEnforced && <Chip size="small" color="success" label="read-only ✓" />}
                      </Stack>
                    </>
                  ) : (
                    testResult.message
                  )}
                </Alert>
              )}
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5 }}>
            <Button
              onClick={handleTestBeforeSave}
              disabled={testing || !form.host || !form.databaseName || !form.username || !form.password}
              startIcon={testing ? <CircularProgress size={14} /> : <BoltIcon />}
            >
              Test connection
            </Button>
            <Box sx={{ flexGrow: 1 }} />
            <Button onClick={() => setDialogOpen(false)}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={saving}>
              {saving ? 'Saving…' : 'Save'}
            </Button>
          </DialogActions>
        </Box>
      </Dialog>

      {metricsConnection && (
        <SnapshotDialog
          connection={metricsConnection}
          open={Boolean(metricsConnection)}
          onClose={() => setMetricsConnection(null)}
        />
      )}

      <Snackbar
        open={Boolean(snackbar)}
        autoHideDuration={5000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
}
