import { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Grid,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf';
import DownloadIcon from '@mui/icons-material/Download';
import MonitorHeartIcon from '@mui/icons-material/MonitorHeart';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ChecklistIcon from '@mui/icons-material/Checklist';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import TimelineIcon from '@mui/icons-material/Timeline';
import HistoryIcon from '@mui/icons-material/History';
import { connectionsApi } from '../api/connections';
import { reportsApi } from '../api/reports';
import type { ConnectionResponse } from '../types/connections';
import { extractApiError } from '../utils/errors';

const SECTIONS = [
  { icon: <MonitorHeartIcon color="primary" />, title: 'Health score & deductions', detail: 'The explainable 0-100 score with every penalty listed' },
  { icon: <AutoAwesomeIcon color="primary" />, title: 'AI executive summary & key findings', detail: 'AI-written narrative grounded in your latest snapshot' },
  { icon: <ChecklistIcon color="primary" />, title: 'Prioritized action plan', detail: 'Ranked recommendations with ready-to-run SQL' },
  { icon: <QueryStatsIcon color="primary" />, title: 'Top queries & table access patterns', detail: 'pg_stat_statements and pg_stat_user_tables highlights' },
  { icon: <TimelineIcon color="primary" />, title: 'Snapshot trend', detail: 'Cache hit ratio, sessions and locking over recent collections' },
  { icon: <HistoryIcon color="primary" />, title: 'Recent query analyses', detail: 'Summaries from the Query Analyzer' },
];

async function blobErrorMessage(err: unknown): Promise<string> {
  // With responseType 'blob', API error bodies arrive as blobs — decode before parsing
  const data = (err as { response?: { data?: unknown } })?.response?.data;
  if (data instanceof Blob) {
    try {
      const parsed = JSON.parse(await data.text());
      if (parsed?.message) return parsed.message as string;
    } catch {
      // fall through to generic extraction
    }
  }
  return extractApiError(err);
}

export default function ReportsPage() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [connectionId, setConnectionId] = useState('');
  const [generating, setGenerating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastFilename, setLastFilename] = useState<string | null>(null);

  useEffect(() => {
    connectionsApi
      .list()
      .then((list) => {
        setConnections(list);
        if (list.length > 0) setConnectionId(list[0].id);
      })
      .catch(() => undefined);
  }, []);

  const generate = async () => {
    if (!connectionId) return;
    setError(null);
    setLastFilename(null);
    setGenerating(true);
    try {
      const { blob, filename } = await reportsApi.downloadPdf(connectionId);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      anchor.click();
      URL.revokeObjectURL(url);
      setLastFilename(filename);
    } catch (err) {
      setError(await blobErrorMessage(err));
    } finally {
      setGenerating(false);
    }
  };

  return (
    <Box sx={{ maxWidth: 980, mx: 'auto' }}>
      <Grid container spacing={3}>
        <Grid item xs={12} md={5}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Stack spacing={2} alignItems="flex-start">
                <PictureAsPdfIcon color="primary" sx={{ fontSize: 44 }} />
                <Typography variant="h5" fontWeight={700}>
                  Optimization Report
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  A shareable PDF that turns your collected metrics into an executive summary and a
                  prioritized, SQL-ready action plan — written by AI, grounded in your latest snapshot.
                </Typography>
                <TextField
                  select
                  fullWidth
                  size="small"
                  label="Database"
                  value={connectionId}
                  onChange={(e) => setConnectionId(e.target.value)}
                >
                  {connections.length === 0 && <MenuItem value="">No connections yet</MenuItem>}
                  {connections.map((c) => (
                    <MenuItem key={c.id} value={c.id}>
                      {c.name} ({c.databaseName})
                    </MenuItem>
                  ))}
                </TextField>
                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={!connectionId || generating}
                  startIcon={generating ? <CircularProgress size={18} color="inherit" /> : <DownloadIcon />}
                  onClick={generate}
                >
                  {generating ? 'Generating (takes ~15s)…' : 'Generate PDF report'}
                </Button>
                {lastFilename && (
                  <Alert severity="success" sx={{ width: '100%' }}>
                    Downloaded {lastFilename}
                  </Alert>
                )}
                {error && (
                  <Alert severity="error" sx={{ width: '100%' }} onClose={() => setError(null)}>
                    {error}
                  </Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} md={7}>
          <Card sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="h6" sx={{ mb: 1 }}>
                What's inside
              </Typography>
              <List dense>
                {SECTIONS.map((section) => (
                  <ListItem key={section.title} disableGutters>
                    <ListItemIcon sx={{ minWidth: 40 }}>{section.icon}</ListItemIcon>
                    <ListItemText primary={section.title} secondary={section.detail} />
                  </ListItem>
                ))}
              </List>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}
