import { useEffect, useRef, useState } from 'react';
import {
  Alert,
  Avatar,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemText,
  MenuItem,
  Paper,
  Stack,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import SendIcon from '@mui/icons-material/Send';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import AddCommentIcon from '@mui/icons-material/AddComment';
import HistoryIcon from '@mui/icons-material/History';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import BoltIcon from '@mui/icons-material/Bolt';
import { connectionsApi } from '../api/connections';
import { copilotApi } from '../api/copilot';
import type { ConnectionResponse } from '../types/connections';
import type { ChatMessageDto, ChatSessionSummary } from '../types/copilot';
import Markdown from '../components/Markdown';
import { extractApiError } from '../utils/errors';

const STARTER_PROMPTS = [
  'How healthy is my database right now?',
  'What are my slowest queries and why?',
  'Which tables need indexes the most?',
  'Is anything blocking or deadlocking?',
];

interface LocalMessage {
  role: 'USER' | 'ASSISTANT';
  content: string;
  suggestedFollowUps?: string[];
  groundedAt?: string | null;
}

export default function CopilotPage() {
  const [connections, setConnections] = useState<ConnectionResponse[]>([]);
  const [connectionId, setConnectionId] = useState<string>('');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [messages, setMessages] = useState<LocalMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    connectionsApi
      .list()
      .then((list) => {
        setConnections(list);
        if (list.length > 0) setConnectionId(list[0].id);
      })
      .catch(() => undefined);
  }, []);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const loadSessions = () =>
    copilotApi
      .sessions()
      .then(setSessions)
      .catch(() => undefined);

  const openSession = async (session: ChatSessionSummary) => {
    setHistoryOpen(false);
    setError(null);
    try {
      const detail = await copilotApi.session(session.id);
      setSessionId(detail.session.id);
      if (detail.session.connectionId) setConnectionId(detail.session.connectionId);
      setMessages(
        detail.messages.map((m: ChatMessageDto) => ({
          role: m.role,
          content: m.content,
          suggestedFollowUps: m.suggestedFollowUps,
        })),
      );
    } catch (err) {
      setError(extractApiError(err));
    }
  };

  const deleteSession = async (id: string) => {
    await copilotApi.remove(id).catch(() => undefined);
    if (id === sessionId) newChat();
    loadSessions();
  };

  const newChat = () => {
    setSessionId(null);
    setMessages([]);
    setError(null);
  };

  const send = async (text: string) => {
    const message = text.trim();
    if (!message || sending) return;
    setError(null);
    setInput('');
    setMessages((prev) => [...prev, { role: 'USER', content: message }]);
    setSending(true);
    try {
      const response = await copilotApi.chat({
        sessionId,
        connectionId: sessionId ? null : connectionId || null,
        message,
      });
      setSessionId(response.sessionId);
      setMessages((prev) => [
        ...prev,
        {
          role: 'ASSISTANT',
          content: response.reply,
          suggestedFollowUps: response.suggestedFollowUps,
          groundedAt: response.groundedAt,
        },
      ]);
    } catch (err) {
      setError(extractApiError(err));
    } finally {
      setSending(false);
    }
  };

  const lastAssistantIndex = messages.map((m) => m.role).lastIndexOf('ASSISTANT');

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 128px)', maxWidth: 900, mx: 'auto' }}>
      {/* Toolbar */}
      <Stack direction="row" spacing={1.5} alignItems="center" sx={{ mb: 2 }}>
        <TextField
          select
          size="small"
          label="Database"
          value={connectionId}
          onChange={(e) => setConnectionId(e.target.value)}
          disabled={Boolean(sessionId)}
          helperText={sessionId ? 'Fixed for this conversation' : undefined}
          sx={{ minWidth: 220 }}
        >
          <MenuItem value="">No connection (general questions)</MenuItem>
          {connections.map((c) => (
            <MenuItem key={c.id} value={c.id}>
              {c.name} ({c.databaseName})
            </MenuItem>
          ))}
        </TextField>
        <Box sx={{ flexGrow: 1 }} />
        <Tooltip title="New chat">
          <IconButton onClick={newChat}>
            <AddCommentIcon />
          </IconButton>
        </Tooltip>
        <Tooltip title="Chat history">
          <IconButton
            onClick={() => {
              loadSessions();
              setHistoryOpen(true);
            }}
          >
            <HistoryIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {/* Thread */}
      <Paper
        variant="outlined"
        sx={{ flexGrow: 1, overflowY: 'auto', p: { xs: 2, md: 3 }, bgcolor: 'transparent', borderRadius: 3 }}
      >
        {messages.length === 0 && (
          <Stack alignItems="center" justifyContent="center" spacing={2} sx={{ height: '100%', textAlign: 'center' }}>
            <Avatar sx={{ width: 56, height: 56, bgcolor: 'primary.dark' }}>
              <SmartToyIcon fontSize="large" />
            </Avatar>
            <Typography variant="h6">Ask me anything about your database</Typography>
            <Typography variant="body2" color="text.secondary" sx={{ maxWidth: 460 }}>
              I answer with the metrics DBInsightX has collected — health score, slow queries, locks, table access
              patterns — not generic advice.
            </Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap justifyContent="center">
              {STARTER_PROMPTS.map((prompt) => (
                <Chip key={prompt} icon={<BoltIcon />} label={prompt} onClick={() => send(prompt)} clickable />
              ))}
            </Stack>
          </Stack>
        )}

        <Stack spacing={2}>
          {messages.map((message, index) => (
            <Box
              key={index}
              sx={{ display: 'flex', justifyContent: message.role === 'USER' ? 'flex-end' : 'flex-start' }}
            >
              <Box
                sx={{
                  maxWidth: '85%',
                  px: 2,
                  py: 1.25,
                  borderRadius: 3,
                  ...(message.role === 'USER'
                    ? { bgcolor: 'rgba(129, 140, 248, 0.18)', borderTopRightRadius: 4 }
                    : { bgcolor: 'rgba(255,255,255,0.04)', border: 1, borderColor: 'divider', borderTopLeftRadius: 4 }),
                }}
              >
                {message.role === 'USER' ? (
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {message.content}
                  </Typography>
                ) : (
                  <>
                    <Markdown content={message.content} />
                    {message.groundedAt && (
                      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                        Grounded in snapshot from {new Date(message.groundedAt).toLocaleTimeString()}
                      </Typography>
                    )}
                    {index === lastAssistantIndex && (message.suggestedFollowUps?.length ?? 0) > 0 && (
                      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap sx={{ mt: 1.5 }}>
                        {message.suggestedFollowUps!.map((followUp) => (
                          <Chip
                            key={followUp}
                            label={followUp}
                            size="small"
                            variant="outlined"
                            onClick={() => send(followUp)}
                            clickable
                          />
                        ))}
                      </Stack>
                    )}
                  </>
                )}
              </Box>
            </Box>
          ))}
          {sending && (
            <Stack direction="row" spacing={1.5} alignItems="center">
              <CircularProgress size={18} />
              <Typography variant="body2" color="text.secondary">
                Analyzing your metrics…
              </Typography>
            </Stack>
          )}
        </Stack>
        <div ref={bottomRef} />
      </Paper>

      {error && (
        <Alert severity="error" sx={{ mt: 1.5 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}

      {/* Composer */}
      <Stack direction="row" spacing={1.5} sx={{ mt: 2 }}>
        <TextField
          fullWidth
          multiline
          maxRows={5}
          placeholder="Ask about slow queries, indexes, locks, health…"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              send(input);
            }
          }}
        />
        <Button
          variant="contained"
          endIcon={sending ? <CircularProgress size={16} color="inherit" /> : <SendIcon />}
          disabled={sending || !input.trim()}
          onClick={() => send(input)}
          sx={{ px: 3, alignSelf: 'flex-end', height: 56 }}
        >
          Send
        </Button>
      </Stack>

      {/* History drawer */}
      <Drawer anchor="right" open={historyOpen} onClose={() => setHistoryOpen(false)}>
        <Box sx={{ width: 340, p: 2 }}>
          <Typography variant="h6" sx={{ mb: 1 }}>
            Chat history
          </Typography>
          <Divider />
          <List>
            {sessions.length === 0 && (
              <Typography variant="body2" color="text.secondary" sx={{ p: 2 }}>
                No previous conversations yet.
              </Typography>
            )}
            {sessions.map((session) => (
              <ListItemButton key={session.id} onClick={() => openSession(session)} sx={{ borderRadius: 2 }}>
                <ListItemText
                  primary={session.title}
                  primaryTypographyProps={{ noWrap: true }}
                  secondary={new Date(session.updatedAt).toLocaleString()}
                />
                <IconButton
                  edge="end"
                  size="small"
                  onClick={(e) => {
                    e.stopPropagation();
                    deleteSession(session.id);
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              </ListItemButton>
            ))}
          </List>
        </Box>
      </Drawer>
    </Box>
  );
}
