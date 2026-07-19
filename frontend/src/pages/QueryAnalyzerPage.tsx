import { useEffect, useState } from 'react';
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  FormControlLabel,
  IconButton,
  LinearProgress,
  MenuItem,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import AutoAwesomeIcon from '@mui/icons-material/AutoAwesome';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import ScienceIcon from '@mui/icons-material/Science';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import { connectionsApi } from '../api/connections';
import { analyzerApi } from '../api/analyzer';
import type { ConnectionResponse } from '../types/connections';
import type { QueryAnalysisResponse } from '../types/analyzer';
import { extractApiError } from '../utils/errors';

const DEMO_SQL = `SELECT c.full_name, count(*) AS order_count
FROM orders o
JOIN customers c ON c.id = o.customer_id
WHERE o.status = 'pending'
GROUP BY c.full_name
ORDER BY order_count DESC
LIMIT 10;`;

const SEVERITY_COLOR: Record<string, 'error' | 'warning' | 'info' | 'default'> = {
  HIGH: 'error',
  MEDIUM: 'warning',
  LOW: 'info',
  INFO: 'default',
};

function SqlBlock({ sql }: { sql: string }) {
  return (
    <Box
      sx={{
        position: 'relative',
        bgcolor: 'rgba(0,0,0,0.35)',
        border: 1,
        borderColor: 'divider',
        borderRadius: 2,
        p: 1.5,
        pr: 5,
        overflowX: 'auto',
      }}
    >
      <Typography component="pre" sx={{ fontFamily: 'monospace', fontSize: 13, m: 0, whiteSpace: 'pre-wrap' }}>
        {sql}
      </Typography>
      <Tooltip title="Copy SQL">
        <IconButton
          size="small"
          sx={{ position: 'absolute', top: 6, right: 6 }}
          onClick={() => navigator.clipboard.writeText(sql)}
        >
          <ContentCopyIcon fontSize="small" />
        </IconButton>
      </Tooltip>
    </Box>
  );
}

export default function QueryAnalyzerPage() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [connectionId, setConnectionId] = useState<string>('');
  const [sql, setSql] = useState('');
  const [explainOutput, setExplainOutput] = useState('');
  const [runAnalyze, setRunAnalyze] = useState(true);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<QueryAnalysisResponse | null>(null);

  useEffect(() => {
    connectionsApi
      .list()
      .then((list) => {
        setConnections(list);
        if (list.length > 0) setConnectionId(list[0].id);
      })
      .catch(() => undefined);
  }, []);

  const handleAnalyze = async () => {
    setError(null);
    setResult(null);
    setLoading(true);
    try {
      setResult(
        await analyzerApi.analyze({
          connectionId: connectionId || null,
          sql: sql.trim() || null,
          explainOutput: explainOutput.trim() || null,
          runAnalyze,
        }),
      );
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setLoading(false);
    }
  };

  const analysis = result?.analysis;

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4">Query Analyzer</Typography>
          <Typography color="text.secondary">
            Paste SQL or an EXPLAIN plan — the AI finds the bottlenecks and writes the fixes
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<ScienceIcon />} onClick={() => setSql(DEMO_SQL)}>
          Try demo query
        </Button>
      </Stack>

      <Card sx={{ mb: 3 }}>
        <CardContent>
          <Stack spacing={2.5}>
            <TextField
              select
              label="Database connection (enables live EXPLAIN + schema grounding)"
              value={connectionId}
              onChange={(event) => setConnectionId(event.target.value)}
              sx={{ maxWidth: 480 }}
            >
              <MenuItem value="">No connection — analyze text only</MenuItem>
              {connections.map((connection) => (
                <MenuItem key={connection.id} value={connection.id}>
                  {connection.name} ({connection.host}:{connection.port}/{connection.databaseName})
                </MenuItem>
              ))}
            </TextField>

            <TextField
              label="SQL query"
              multiline
              minRows={5}
              value={sql}
              onChange={(event) => setSql(event.target.value)}
              placeholder="SELECT ... FROM ... WHERE ..."
              InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
            />

            <Accordion variant="outlined" disableGutters>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="body2" color="text.secondary">
                  Optional: paste EXPLAIN / EXPLAIN ANALYZE output
                  {explainOutput.trim() && ' ✓'}
                </Typography>
              </AccordionSummary>
              <AccordionDetails>
                <TextField
                  fullWidth
                  multiline
                  minRows={5}
                  value={explainOutput}
                  onChange={(event) => setExplainOutput(event.target.value)}
                  placeholder="Seq Scan on orders  (cost=0.00..2041.00 rows=100000 width=8) ..."
                  InputProps={{ sx: { fontFamily: 'monospace', fontSize: 13 } }}
                />
              </AccordionDetails>
            </Accordion>

            <Stack direction="row" spacing={2} alignItems="center">
              <Button
                variant="contained"
                size="large"
                startIcon={<AutoAwesomeIcon />}
                disabled={loading || (!sql.trim() && !explainOutput.trim())}
                onClick={handleAnalyze}
              >
                {loading ? 'Analyzing…' : 'Analyze with AI'}
              </Button>
              <FormControlLabel
                control={
                  <Checkbox
                    checked={runAnalyze}
                    onChange={(event) => setRunAnalyze(event.target.checked)}
                    disabled={!connectionId}
                  />
                }
                label={
                  <Typography variant="body2" color="text.secondary">
                    Run EXPLAIN ANALYZE on target (executes the query, read-only)
                  </Typography>
                }
              />
            </Stack>

            {loading && (
              <Box>
                <LinearProgress />
                <Typography variant="caption" color="text.secondary">
                  The AI is reading the execution plan, checking your indexes, and reasoning about the
                  bottleneck — usually 15–60 seconds…
                </Typography>
              </Box>
            )}
            {error && <Alert severity="error">{error}</Alert>}
          </Stack>
        </CardContent>
      </Card>

      {analysis && (
        <Stack spacing={3}>
          <Card>
            <CardContent>
              <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
                <Box>
                  <Typography variant="h6" gutterBottom>
                    Diagnosis
                  </Typography>
                  <Typography>{analysis.summary}</Typography>
                </Box>
                {analysis.estimatedImprovement && (
                  <Chip
                    icon={<TrendingUpIcon />}
                    color="success"
                    label={analysis.estimatedImprovement}
                    sx={{ fontWeight: 700, flexShrink: 0 }}
                  />
                )}
              </Stack>
            </CardContent>
          </Card>

          {analysis.issues.length > 0 && (
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Issues found
                </Typography>
                <Stack spacing={1.5}>
                  {analysis.issues.map((issue, index) => (
                    <Stack key={index} direction="row" spacing={1.5} alignItems="flex-start">
                      <Chip
                        size="small"
                        color={SEVERITY_COLOR[issue.severity] ?? 'default'}
                        label={issue.severity}
                        sx={{ minWidth: 72, fontWeight: 700 }}
                      />
                      <Box>
                        <Typography variant="subtitle2">{issue.type.replace(/_/g, ' ')}</Typography>
                        <Typography variant="body2" color="text.secondary">
                          {issue.description}
                        </Typography>
                      </Box>
                    </Stack>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          )}

          {analysis.recommendations.length > 0 && (
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Recommendations
                </Typography>
                <Stack spacing={2.5}>
                  {analysis.recommendations.map((rec, index) => (
                    <Box key={index}>
                      <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.5 }}>
                        <Typography variant="subtitle1">
                          {index + 1}. {rec.title}
                        </Typography>
                        {rec.estimatedImprovement && (
                          <Chip size="small" color="success" variant="outlined" label={rec.estimatedImprovement} />
                        )}
                      </Stack>
                      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                        {rec.detail}
                      </Typography>
                      {rec.sql && <SqlBlock sql={rec.sql} />}
                    </Box>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          )}

          {analysis.optimizedSql && (
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Optimized query
                </Typography>
                <SqlBlock sql={analysis.optimizedSql} />
              </CardContent>
            </Card>
          )}

          {(analysis.planExplanation || result?.planUsed) && (
            <Accordion variant="outlined">
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography>Execution plan walkthrough</Typography>
              </AccordionSummary>
              <AccordionDetails>
                {analysis.planExplanation && (
                  <Typography variant="body2" sx={{ mb: 2, whiteSpace: 'pre-wrap' }}>
                    {analysis.planExplanation}
                  </Typography>
                )}
                {result?.planUsed && (
                  <Box sx={{ bgcolor: 'rgba(0,0,0,0.35)', borderRadius: 2, p: 1.5, overflowX: 'auto' }}>
                    <Typography component="pre" sx={{ fontFamily: 'monospace', fontSize: 12, m: 0 }}>
                      {result.planUsed}
                    </Typography>
                  </Box>
                )}
              </AccordionDetails>
            </Accordion>
          )}

          <Typography variant="caption" color="text.secondary" sx={{ textAlign: 'right' }}>
            Analyzed by {result?.model} · {result && new Date(result.createdAt).toLocaleString()}
          </Typography>
        </Stack>
      )}
    </Box>
  );
}
