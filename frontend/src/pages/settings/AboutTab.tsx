import { useEffect, useState } from 'react';
import {
  Box,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Grid,
  Link,
  List,
  ListItemButton,
  ListItemText,
  Stack,
  Typography,
} from '@mui/material';
import { settingsApi } from '../../api/settings';
import type { AboutResponse } from '../../types/settings';

const REACT_VERSION = '18.3.1';

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" justifyContent="space-between" sx={{ py: 0.75 }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography fontWeight={600}>{value}</Typography>
    </Stack>
  );
}

export default function AboutTab() {
  const [about, setAbout] = useState<AboutResponse | null>(null);

  useEffect(() => {
    settingsApi.about().then(setAbout).catch(() => setAbout(null));
  }, []);

  if (!about) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', py: 10 }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <Grid container spacing={3}>
      <Grid item xs={12} md={7}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1 }}>
              {about.appName}
            </Typography>
            <Divider sx={{ mb: 1 }} />
            <InfoRow label="Version" value={about.appVersion} />
            <InfoRow label="Build" value={about.buildVersion} />
            <InfoRow label="Java" value={about.javaVersion} />
            <InfoRow label="Spring Boot" value={about.springBootVersion} />
            <InfoRow label="React" value={REACT_VERSION} />
            <InfoRow label="AI provider" value={about.aiProvider} />
            <InfoRow label="AI model" value={about.aiModel} />
            <InfoRow label="Deployment" value={about.cloudPlatform} />
          </CardContent>
        </Card>
      </Grid>
      <Grid item xs={12} md={5}>
        <Card>
          <CardContent>
            <Typography variant="h6" sx={{ mb: 1 }}>
              Useful links
            </Typography>
            <Divider sx={{ mb: 1 }} />
            <List disablePadding>
              <ListItemButton component={Link} href={about.links.support} target="_blank" rel="noopener">
                <ListItemText primary="Support" />
              </ListItemButton>
              <ListItemButton component={Link} href={about.links.documentation} target="_blank" rel="noopener">
                <ListItemText primary="Documentation" />
              </ListItemButton>
              <ListItemButton component={Link} href={about.links.license} target="_blank" rel="noopener">
                <ListItemText primary="License" />
              </ListItemButton>
            </List>
          </CardContent>
        </Card>
      </Grid>
    </Grid>
  );
}
