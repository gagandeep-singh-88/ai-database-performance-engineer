import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import type { ConnectionResponse } from '../types/connections';
import type { SnapshotDetail, SnapshotSummary } from '../types/metrics';
import { formatBytes } from '../types/metrics';
import { metricsApi } from '../api/metrics';
import { extractApiError } from '../utils/errors';

interface Props {
  connection: ConnectionResponse;
  open: boolean;
  onClose: () => void;
}

function Metric({ label, value, warn }: { label: string; value: string | number; warn?: boolean }) {
  return (
    <Box sx={{ minWidth: 110 }}>
      <Typography variant="caption" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="h6" color={warn ? 'warning.main' : 'text.primary'}>
        {value}
      </Typography>
    </Box>
  );
}

export default function SnapshotDialog({ connection, open, onClose }: Props) {
  const [detail, setDetail] = useState<SnapshotDetail | null>(null);
  const [history, setHistory] = useState<SnapshotSummary[]>([]);
  const [loading, setLoading] = useState(false);
  const [collecting, setCollecting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    Promise.all([
      metricsApi.latest(connection.id).catch(() => null),
      metricsApi.history(connection.id, 20).catch(() => [] as SnapshotSummary[]),
    ])
      .then(([latest, hist]) => {
        setDetail(latest);
        setHistory(hist);
      })
      .finally(() => setLoading(false));
  }, [connection.id]);

  useEffect(() => {
    if (open) load();
  }, [open, load]);

  const handleCollect = async () => {
    setCollecting(true);
    setError(null);
    try {
      const fresh = await metricsApi.collectNow(connection.id);
      setDetail(fresh);
      const hist = await metricsApi.history(connection.id, 20).catch(() => history);
      setHistory(hist);
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setCollecting(false);
    }
  };

  const summary = detail?.summary;

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>
        Performance snapshots — {connection.name}
        <Typography variant="body2" color="text.secondary">
          {connection.host}:{connection.port}/{connection.databaseName} · {history.length} snapshot
          {history.length === 1 ? '' : 's'} stored
        </Typography>
      </DialogTitle>
      <DialogContent dividers>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        {loading ? (
          <Box sx={{ display: 'grid', placeItems: 'center', py: 6 }}>
            <CircularProgress />
          </Box>
        ) : !summary ? (
          <Alert severity="info">
            No snapshots yet. Click <strong>Collect now</strong> to capture the first one — the
            background collector also runs every 5 minutes.
          </Alert>
        ) : (
          <>
            <Typography variant="caption" color="text.secondary">
              Latest snapshot · {new Date(summary.capturedAt).toLocaleString()}
            </Typography>
            <Stack direction="row" spacing={3} useFlexGap flexWrap="wrap" sx={{ mt: 1, mb: 3 }}>
              <Metric label="DB size" value={formatBytes(summary.dbSizeBytes)} />
              <Metric label="Active sessions" value={summary.activeSessions} />
              <Metric
                label="Idle in txn"
                value={summary.idleInTransaction}
                warn={summary.idleInTransaction > 0}
              />
              <Metric label="Blocked" value={summary.blockedSessions} warn={summary.blockedSessions > 0} />
              <Metric label="Waiting locks" value={summary.waitingLocks} warn={summary.waitingLocks > 0} />
              <Metric
                label="Cache hit"
                value={summary.cacheHitRatio == null ? '—' : `${(summary.cacheHitRatio * 100).toFixed(1)}%`}
                warn={summary.cacheHitRatio != null && summary.cacheHitRatio < 0.9}
              />
              <Metric label="Deadlocks" value={summary.deadlocks} warn={summary.deadlocks > 0} />
            </Stack>

            <Typography variant="subtitle1" sx={{ mb: 1 }}>
              Top queries by total time
            </Typography>
            {detail && detail.topQueries.length > 0 ? (
              <TableContainer sx={{ maxHeight: 280, mb: 3 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Query</TableCell>
                      <TableCell align="right">Calls</TableCell>
                      <TableCell align="right">Mean (ms)</TableCell>
                      <TableCell align="right">Total (ms)</TableCell>
                      <TableCell align="right">Cache hit</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detail.topQueries.slice(0, 10).map((q) => (
                      <TableRow key={q.queryId} hover>
                        <TableCell sx={{ maxWidth: 380 }}>
                          <Tooltip title={q.query}>
                            <Typography variant="body2" noWrap sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                              {q.query}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell align="right">{q.calls.toLocaleString()}</TableCell>
                        <TableCell align="right">{q.meanTimeMs.toFixed(1)}</TableCell>
                        <TableCell align="right">{q.totalTimeMs.toFixed(0)}</TableCell>
                        <TableCell align="right">{(q.hitRatio * 100).toFixed(0)}%</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            ) : (
              <Alert severity="warning" sx={{ mb: 3 }}>
                No query statistics — pg_stat_statements may not be enabled on this target.
              </Alert>
            )}

            <Typography variant="subtitle1" sx={{ mb: 1 }}>
              Sequential scan hotspots
              <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                (high seq reads + few index scans = missing-index candidates)
              </Typography>
            </Typography>
            {detail && detail.tableStats.length > 0 && (
              <TableContainer sx={{ maxHeight: 200 }}>
                <Table size="small" stickyHeader>
                  <TableHead>
                    <TableRow>
                      <TableCell>Table</TableCell>
                      <TableCell align="right">Seq scans</TableCell>
                      <TableCell align="right">Rows seq-read</TableCell>
                      <TableCell align="right">Index scans</TableCell>
                      <TableCell align="right">Live rows</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {detail.tableStats.slice(0, 8).map((t) => {
                      const suspicious = t.seqTupRead > 1_000_000 && t.idxScans < t.seqScans;
                      return (
                        <TableRow key={t.tableName} hover>
                          <TableCell>
                            <Stack direction="row" spacing={1} alignItems="center">
                              <span>{t.tableName}</span>
                              {suspicious && <Chip size="small" color="warning" label="hotspot" />}
                            </Stack>
                          </TableCell>
                          <TableCell align="right">{t.seqScans.toLocaleString()}</TableCell>
                          <TableCell align="right">{t.seqTupRead.toLocaleString()}</TableCell>
                          <TableCell align="right">{t.idxScans.toLocaleString()}</TableCell>
                          <TableCell align="right">{t.liveRows.toLocaleString()}</TableCell>
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 2 }}>
        <Button
          variant="contained"
          startIcon={collecting ? <CircularProgress size={14} /> : <CameraAltIcon />}
          disabled={collecting}
          onClick={handleCollect}
        >
          {collecting ? 'Collecting…' : 'Collect now'}
        </Button>
        <Box sx={{ flexGrow: 1 }} />
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
}
