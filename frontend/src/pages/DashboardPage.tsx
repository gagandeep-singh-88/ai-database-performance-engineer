import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip as MuiTooltip,
  Typography,
} from '@mui/material';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import CameraAltIcon from '@mui/icons-material/CameraAlt';
import StorageIcon from '@mui/icons-material/Storage';
import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import ScoreGauge from '../components/ScoreGauge';
import { connectionsApi } from '../api/connections';
import { dashboardApi } from '../api/dashboard';
import { metricsApi } from '../api/metrics';
import type { ConnectionResponse } from '../types/connections';
import type { DashboardResponse, Recommendation } from '../types/dashboard';
import { formatBytes } from '../types/metrics';
import { extractApiError } from '../utils/errors';

const SEVERITY_COLOR: Record<string, 'error' | 'warning' | 'info'> = {
  HIGH: 'error',
  MEDIUM: 'warning',
  LOW: 'info',
};

const CHART_INK = '#94a3b8';
const CHART_GRID = 'rgba(255,255,255,0.06)';

function KpiTile({ label, value, hint, warn }: { label: string; value: string; hint?: string; warn?: boolean }) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent sx={{ py: 2 }}>
        <Typography variant="caption" color="text.secondary">
          {label}
        </Typography>
        <Typography variant="h5" sx={{ fontWeight: 800, color: warn ? 'warning.main' : 'text.primary' }}>
          {value}
        </Typography>
        {hint && (
          <Typography variant="caption" color="text.secondary">
            {hint}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

function TrendChart({
  title,
  data,
  dataKey,
  color,
  unit,
  domain,
}: {
  title: string;
  data: Record<string, unknown>[];
  dataKey: string;
  color: string;
  unit?: string;
  domain?: [number | string, number | string];
}) {
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="subtitle2" color="text.secondary" gutterBottom>
          {title}
        </Typography>
        <Box sx={{ height: 200 }}>
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={data} margin={{ top: 6, right: 10, left: -18, bottom: 0 }}>
              <CartesianGrid stroke={CHART_GRID} vertical={false} />
              <XAxis
                dataKey="time"
                tick={{ fill: CHART_INK, fontSize: 11 }}
                stroke="transparent"
                tickLine={false}
              />
              <YAxis
                tick={{ fill: CHART_INK, fontSize: 11 }}
                stroke="transparent"
                tickLine={false}
                domain={domain}
                unit={unit}
              />
              <Tooltip
                cursor={{ stroke: CHART_INK, strokeDasharray: '3 3' }}
                contentStyle={{
                  background: '#111731',
                  border: '1px solid rgba(255,255,255,0.12)',
                  borderRadius: 8,
                  fontSize: 12,
                }}
                labelStyle={{ color: CHART_INK }}
              />
              <Line
                type="monotone"
                dataKey={dataKey}
                stroke={color}
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
                isAnimationActive={false}
              />
            </LineChart>
          </ResponsiveContainer>
        </Box>
      </CardContent>
    </Card>
  );
}

export default function DashboardPage() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [connectionId, setConnectionId] = useState<string>('');
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [collecting, setCollecting] = useState(false);
  const [aiRecs, setAiRecs] = useState<Recommendation[] | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    connectionsApi
      .list()
      .then((list) => {
        setConnections(list);
        if (list.length > 0) setConnectionId(list[0].id);
        else setLoading(false);
      })
      .catch(() => setLoading(false));
  }, []);

  const load = useCallback(() => {
    if (!connectionId) return;
    setLoading(true);
    setError(null);
    setAiRecs(null);
    dashboardApi
      .get(connectionId)
      .then(setDashboard)
      .catch((err) => setError(extractApiError(err)))
      .finally(() => setLoading(false));
  }, [connectionId]);

  useEffect(load, [load]);

  const handleCollect = async () => {
    setCollecting(true);
    try {
      await metricsApi.collectNow(connectionId);
      load();
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setCollecting(false);
    }
  };

  const handleAiRecs = async () => {
    setAiLoading(true);
    setError(null);
    try {
      setAiRecs(await dashboardApi.aiRecommendations(connectionId));
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setAiLoading(false);
    }
  };

  const series = useMemo(() => {
    if (!dashboard) return [];
    return [...dashboard.history]
      .reverse()
      .map((s) => ({
        time: new Date(s.capturedAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
        sessions: s.activeSessions,
        blocked: s.blockedSessions,
        cacheHit: s.cacheHitRatio == null ? null : Number((s.cacheHitRatio * 100).toFixed(2)),
        sizeMb: Number((s.dbSizeBytes / 1e6).toFixed(1)),
      }));
  }, [dashboard]);

  const latest = dashboard?.latest;
  const summary = latest?.summary;
  const recommendations = aiRecs ?? dashboard?.recommendations ?? [];

  if (connections.length === 0 && !loading) {
    return (
      <Box sx={{ textAlign: 'center', pt: 10 }}>
        <StorageIcon sx={{ fontSize: 56, color: 'text.secondary' }} />
        <Typography variant="h5" sx={{ mt: 2 }}>
          Connect a database to light up the dashboard
        </Typography>
        <Button component={RouterLink} to="/app/databases" variant="contained" sx={{ mt: 3 }}>
          Add database
        </Button>
      </Box>
    );
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4">Health Dashboard</Typography>
          <Typography color="text.secondary">
            Live health score with full explainability — every deduction is shown
          </Typography>
        </Box>
        <Stack direction="row" spacing={1.5}>
          <TextField
            select
            size="small"
            value={connectionId}
            onChange={(event) => setConnectionId(event.target.value)}
            sx={{ minWidth: 220 }}
          >
            {connections.map((connection) => (
              <MenuItem key={connection.id} value={connection.id}>
                {connection.name}
              </MenuItem>
            ))}
          </TextField>
          <Button
            variant="outlined"
            startIcon={collecting ? <CircularProgress size={14} /> : <CameraAltIcon />}
            disabled={collecting || !connectionId}
            onClick={handleCollect}
          >
            Refresh snapshot
          </Button>
        </Stack>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'grid', placeItems: 'center', py: 12 }}>
          <CircularProgress />
        </Box>
      ) : !dashboard?.hasData ? (
        <Alert severity="info">
          No snapshots collected yet for this connection — click <strong>Refresh snapshot</strong> to
          capture the first one.
        </Alert>
      ) : (
        <>
          <Grid container spacing={3} sx={{ mb: 3 }}>
            <Grid item xs={12} md={4}>
              <Card sx={{ height: '100%' }}>
                <CardContent sx={{ textAlign: 'center' }}>
                  <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                    Database Health Score
                  </Typography>
                  <ScoreGauge score={dashboard.healthScore} grade={dashboard.grade} />
                  <Stack spacing={0.5} sx={{ mt: 2, textAlign: 'left' }}>
                    {dashboard.factors.length === 0 ? (
                      <Typography variant="body2" color="success.main" sx={{ textAlign: 'center' }}>
                        No issues detected — keep it up!
                      </Typography>
                    ) : (
                      dashboard.factors.map((factor, index) => (
                        <Stack key={index} direction="row" justifyContent="space-between">
                          <Typography variant="body2" color="text.secondary">
                            {factor.label}
                          </Typography>
                          <Typography variant="body2" color="error.main" sx={{ fontWeight: 700 }}>
                            −{factor.penalty}
                          </Typography>
                        </Stack>
                      ))
                    )}
                  </Stack>
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} md={8}>
              <Grid container spacing={2} sx={{ mb: 2 }}>
                <Grid item xs={6} sm={3}>
                  <KpiTile label="DB size" value={summary ? formatBytes(summary.dbSizeBytes) : '—'} />
                </Grid>
                <Grid item xs={6} sm={3}>
                  <KpiTile label="Active sessions" value={String(summary?.activeSessions ?? 0)} />
                </Grid>
                <Grid item xs={6} sm={3}>
                  <KpiTile
                    label="Blocked"
                    value={String(summary?.blockedSessions ?? 0)}
                    warn={(summary?.blockedSessions ?? 0) > 0}
                  />
                </Grid>
                <Grid item xs={6} sm={3}>
                  <KpiTile
                    label="Cache hit"
                    value={summary?.cacheHitRatio == null ? '—' : `${(summary.cacheHitRatio * 100).toFixed(1)}%`}
                    warn={summary?.cacheHitRatio != null && summary.cacheHitRatio < 0.95}
                  />
                </Grid>
              </Grid>
              <Grid container spacing={2}>
                <Grid item xs={12} sm={6}>
                  <TrendChart
                    title="Active sessions over time"
                    data={series}
                    dataKey="sessions"
                    color="#22d3ee"
                  />
                </Grid>
                <Grid item xs={12} sm={6}>
                  <TrendChart
                    title="Cache hit ratio over time"
                    data={series}
                    dataKey="cacheHit"
                    color="#818cf8"
                    unit="%"
                    domain={[80, 100]}
                  />
                </Grid>
              </Grid>
            </Grid>
          </Grid>

          <Grid container spacing={3}>
            <Grid item xs={12} lg={7}>
              <Card sx={{ height: '100%' }}>
                <CardContent>
                  <Typography variant="h6" gutterBottom>
                    Slowest queries
                    <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 1 }}>
                      by total time (pg_stat_statements)
                    </Typography>
                  </Typography>
                  {latest && latest.topQueries.length > 0 ? (
                    <TableContainer sx={{ maxHeight: 320 }}>
                      <Table size="small" stickyHeader>
                        <TableHead>
                          <TableRow>
                            <TableCell>Query</TableCell>
                            <TableCell align="right">Calls</TableCell>
                            <TableCell align="right">Mean (ms)</TableCell>
                            <TableCell align="right">Total (ms)</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {latest.topQueries.slice(0, 8).map((q) => (
                            <TableRow key={q.queryId} hover>
                              <TableCell sx={{ maxWidth: 360 }}>
                                <MuiTooltip title={q.query}>
                                  <Typography noWrap sx={{ fontFamily: 'monospace', fontSize: 12 }}>
                                    {q.query}
                                  </Typography>
                                </MuiTooltip>
                              </TableCell>
                              <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                {q.calls.toLocaleString()}
                              </TableCell>
                              <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                {q.meanTimeMs.toFixed(1)}
                              </TableCell>
                              <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                {q.totalTimeMs.toFixed(0)}
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      No query statistics available.
                    </Typography>
                  )}

                  <Typography variant="h6" sx={{ mt: 3 }} gutterBottom>
                    Missing-index candidates
                  </Typography>
                  {latest && latest.tableStats.length > 0 ? (
                    <TableContainer>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Table</TableCell>
                            <TableCell align="right">Rows seq-read</TableCell>
                            <TableCell align="right">Index scans</TableCell>
                            <TableCell align="right">Live rows</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {latest.tableStats.slice(0, 5).map((t) => {
                            const hotspot = t.seqTupRead > 1_000_000 && t.idxScans < t.seqScans;
                            return (
                              <TableRow key={t.tableName} hover>
                                <TableCell>
                                  <Stack direction="row" spacing={1} alignItems="center">
                                    <span>{t.tableName}</span>
                                    {hotspot && <Chip size="small" color="warning" label="hotspot" />}
                                  </Stack>
                                </TableCell>
                                <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                  {t.seqTupRead.toLocaleString()}
                                </TableCell>
                                <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                  {t.idxScans.toLocaleString()}
                                </TableCell>
                                <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                                  {t.liveRows.toLocaleString()}
                                </TableCell>
                              </TableRow>
                            );
                          })}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      No table statistics available.
                    </Typography>
                  )}
                </CardContent>
              </Card>
            </Grid>

            <Grid item xs={12} lg={5}>
              <Card sx={{ height: '100%' }}>
                <CardContent>
                  <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 1 }}>
                    <Typography variant="h6">
                      {aiRecs ? 'AI recommendations' : 'Recommendations'}
                    </Typography>
                    <Button
                      size="small"
                      variant="outlined"
                      startIcon={aiLoading ? <CircularProgress size={14} /> : <AutoAwesomeIcon />}
                      disabled={aiLoading}
                      onClick={handleAiRecs}
                    >
                      {aiLoading ? 'Asking AI…' : 'Ask AI'}
                    </Button>
                  </Stack>
                  {recommendations.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      Nothing to recommend — this database looks healthy.
                    </Typography>
                  ) : (
                    <Stack spacing={2}>
                      {recommendations.map((rec, index) => (
                        <Box key={index}>
                          <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                            <Chip
                              size="small"
                              color={SEVERITY_COLOR[rec.severity] ?? 'info'}
                              label={rec.severity}
                              sx={{ fontWeight: 700 }}
                            />
                            <Typography variant="subtitle2">{rec.title}</Typography>
                          </Stack>
                          <Typography variant="body2" color="text.secondary">
                            {rec.detail}
                          </Typography>
                          {rec.sql && (
                            <Box
                              sx={{
                                bgcolor: 'rgba(0,0,0,0.35)',
                                border: 1,
                                borderColor: 'divider',
                                borderRadius: 1.5,
                                p: 1,
                                mt: 0.5,
                                overflowX: 'auto',
                              }}
                            >
                              <Typography component="pre" sx={{ fontFamily: 'monospace', fontSize: 12, m: 0 }}>
                                {rec.sql}
                              </Typography>
                            </Box>
                          )}
                        </Box>
                      ))}
                    </Stack>
                  )}
                  {latest && latest.locks.length > 0 && (
                    <>
                      <Typography variant="h6" sx={{ mt: 3 }} gutterBottom>
                        Lock contention
                      </Typography>
                      <Stack spacing={1}>
                        {latest.locks.map((lock, index) => (
                          <Alert key={index} severity="warning" variant="outlined">
                            PID {lock.blockedPid} blocked by PID {lock.blockingPid}:{' '}
                            <code>{lock.blockedQuery?.slice(0, 80)}</code>
                          </Alert>
                        ))}
                      </Stack>
                    </>
                  )}
                </CardContent>
              </Card>
            </Grid>
          </Grid>
        </>
      )}
    </Box>
  );
}
