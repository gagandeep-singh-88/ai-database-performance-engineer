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
  IconButton,
  MenuItem,
  Paper,
  Snackbar,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import EditIcon from '@mui/icons-material/Edit';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import BoltIcon from '@mui/icons-material/Bolt';
import StorageIcon from '@mui/icons-material/Storage';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import HelpOutlineIcon from '@mui/icons-material/HelpOutline';
import { connectionsApi } from '../../api/connections';
import type { ConnectionResponse, SslMode } from '../../types/connections';
import { extractApiError } from '../../utils/errors';

const STATUS_META = {
  HEALTHY: { color: 'success' as const, icon: <CheckCircleIcon fontSize="small" />, label: 'Healthy' },
  UNREACHABLE: { color: 'error' as const, icon: <CancelIcon fontSize="small" />, label: 'Unreachable' },
  UNKNOWN: { color: 'default' as const, icon: <HelpOutlineIcon fontSize="small" />, label: 'Not tested' },
};

interface EditForm {
  name: string;
  host: string;
  port: number;
  databaseName: string;
  username: string;
  password: string;
  sslMode: SslMode;
}

const editFormFor = (c: ConnectionResponse): EditForm => ({
  name: c.name,
  host: c.host,
  port: c.port,
  databaseName: c.databaseName,
  username: c.username,
  password: '',
  sslMode: c.sslMode,
});

export default function DatabasesTab() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [snackbar, setSnackbar] = useState<string | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [editing, setEditing] = useState<ConnectionResponse | null>(null);
  const [editForm, setEditForm] = useState<EditForm | null>(null);
  const [editError, setEditError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  const refresh = useCallback(() => {
    setLoading(true);
    connectionsApi
      .list()
      .then(setConnections)
      .catch(() => setSnackbar('Failed to load connections'))
      .finally(() => setLoading(false));
  }, []);

  useEffect(refresh, [refresh]);

  const openEdit = (connection: ConnectionResponse) => {
    setEditing(connection);
    setEditForm(editFormFor(connection));
    setEditError(null);
  };

  const set = (field: keyof EditForm) => (event: { target: { value: string } }) =>
    setEditForm((f) =>
      f ? { ...f, [field]: field === 'port' ? Number(event.target.value) : event.target.value } : f,
    );

  const handleSaveEdit = async (event: FormEvent) => {
    event.preventDefault();
    if (!editing || !editForm) return;
    setEditError(null);
    setSaving(true);
    try {
      await connectionsApi.update(editing.id, editForm);
      setEditing(null);
      setSnackbar(`Connection "${editForm.name}" updated`);
      refresh();
    } catch (err) {
      setEditError(extractApiError(err));
    } finally {
      setSaving(false);
    }
  };

  const handleTest = async (connection: ConnectionResponse) => {
    setTestingId(connection.id);
    try {
      const result = await connectionsApi.test(connection.id);
      setSnackbar(
        result.success
          ? `${connection.name}: healthy (${result.latencyMs} ms)`
          : `${connection.name}: ${result.message}`,
      );
    } catch (err) {
      setSnackbar(extractApiError(err));
    } finally {
      setTestingId(null);
      refresh();
    }
  };

  const handleToggleMonitoring = async (connection: ConnectionResponse) => {
    setTogglingId(connection.id);
    try {
      await connectionsApi.setMonitoring(connection.id, !connection.monitoringEnabled);
      setSnackbar(`Monitoring ${connection.monitoringEnabled ? 'disabled' : 'enabled'} for "${connection.name}"`);
      refresh();
    } catch (err) {
      setSnackbar(extractApiError(err));
    } finally {
      setTogglingId(null);
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

  if (loading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  if (connections.length === 0) {
    return (
      <Card>
        <CardContent sx={{ textAlign: 'center', py: 8 }}>
          <StorageIcon sx={{ fontSize: 48, color: 'text.secondary' }} />
          <Typography variant="h6" sx={{ mt: 2 }}>
            No databases yet
          </Typography>
          <Typography color="text.secondary">
            Add a database from the Databases page to manage it here.
          </Typography>
        </CardContent>
      </Card>
    );
  }

  return (
    <Box>
      <TableContainer component={Paper} variant="outlined">
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>Database</TableCell>
              <TableCell>Host</TableCell>
              <TableCell>Port</TableCell>
              <TableCell>SSL</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Monitoring</TableCell>
              <TableCell>Last connection</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {connections.map((connection) => {
              const meta = STATUS_META[connection.status];
              return (
                <TableRow key={connection.id} hover>
                  <TableCell>
                    <Typography variant="body2" fontWeight={600}>
                      {connection.name}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {connection.databaseName}
                    </Typography>
                  </TableCell>
                  <TableCell>{connection.host}</TableCell>
                  <TableCell>{connection.port}</TableCell>
                  <TableCell>
                    <Chip
                      size="small"
                      color={connection.sslMode === 'DISABLE' ? 'default' : 'success'}
                      label={connection.sslMode === 'DISABLE' ? 'Off' : connection.sslMode.toLowerCase()}
                    />
                  </TableCell>
                  <TableCell>
                    <Chip size="small" color={meta.color} icon={meta.icon} label={meta.label} />
                  </TableCell>
                  <TableCell>
                    <Tooltip title={connection.monitoringEnabled ? 'Disable monitoring' : 'Enable monitoring'}>
                      <span>
                        <Switch
                          size="small"
                          checked={connection.monitoringEnabled}
                          disabled={togglingId === connection.id}
                          onChange={() => handleToggleMonitoring(connection)}
                        />
                      </span>
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    {connection.lastTestedAt ? new Date(connection.lastTestedAt).toLocaleString() : '—'}
                  </TableCell>
                  <TableCell align="right">
                    <Stack direction="row" spacing={0.5} justifyContent="flex-end">
                      <Tooltip title="Test connection">
                        <IconButton
                          size="small"
                          disabled={testingId === connection.id}
                          onClick={() => handleTest(connection)}
                        >
                          {testingId === connection.id ? <CircularProgress size={16} /> : <BoltIcon fontSize="small" />}
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => openEdit(connection)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip title="Delete">
                        <IconButton size="small" onClick={() => handleDelete(connection)}>
                          <DeleteOutlineIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </Stack>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <Dialog open={Boolean(editing)} onClose={() => setEditing(null)} maxWidth="sm" fullWidth>
        {editForm && (
          <Box component="form" onSubmit={handleSaveEdit}>
            <DialogTitle>Edit &quot;{editing?.name}&quot;</DialogTitle>
            <DialogContent>
              <Stack spacing={2.5} sx={{ mt: 0.5 }}>
                <TextField label="Display name" required value={editForm.name} onChange={set('name')} />
                <Stack direction="row" spacing={2}>
                  <TextField label="Host" required fullWidth value={editForm.host} onChange={set('host')} />
                  <TextField
                    label="Port"
                    type="number"
                    required
                    sx={{ width: 140 }}
                    value={editForm.port}
                    onChange={set('port')}
                  />
                </Stack>
                <TextField label="Database name" required value={editForm.databaseName} onChange={set('databaseName')} />
                <Stack direction="row" spacing={2}>
                  <TextField label="Username" required fullWidth value={editForm.username} onChange={set('username')} />
                  <TextField
                    label="New password"
                    type="password"
                    fullWidth
                    value={editForm.password}
                    onChange={set('password')}
                    helperText="Leave blank to keep the current password"
                  />
                </Stack>
                <TextField label="SSL mode" select value={editForm.sslMode} onChange={set('sslMode')}>
                  <MenuItem value="DISABLE">disable</MenuItem>
                  <MenuItem value="PREFER">prefer</MenuItem>
                  <MenuItem value="REQUIRE">require</MenuItem>
                </TextField>
                {editError && <Alert severity="error">{editError}</Alert>}
              </Stack>
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 2.5 }}>
              <Button onClick={() => setEditing(null)}>Cancel</Button>
              <Button type="submit" variant="contained" disabled={saving}>
                {saving ? 'Saving…' : 'Save'}
              </Button>
            </DialogActions>
          </Box>
        )}
      </Dialog>

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
