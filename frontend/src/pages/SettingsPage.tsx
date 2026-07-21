import { useState } from 'react';
import { Box, Tab, Tabs, Typography } from '@mui/material';
import PersonIcon from '@mui/icons-material/Person';
import StorageIcon from '@mui/icons-material/Storage';
import ShieldIcon from '@mui/icons-material/Shield';
import InfoIcon from '@mui/icons-material/Info';
import ProfileTab from './settings/ProfileTab';
import DatabasesTab from './settings/DatabasesTab';
import AiPrivacyTab from './settings/AiPrivacyTab';
import AboutTab from './settings/AboutTab';

const TABS = [
  { label: 'Profile', icon: <PersonIcon fontSize="small" /> },
  { label: 'Databases', icon: <StorageIcon fontSize="small" /> },
  { label: 'AI & Privacy', icon: <ShieldIcon fontSize="small" /> },
  { label: 'About', icon: <InfoIcon fontSize="small" /> },
];

export default function SettingsPage() {
  const [tab, setTab] = useState(0);

  return (
    <Box>
      <Typography variant="h4" sx={{ mb: 0.5 }}>
        Settings
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Manage your profile, databases, AI behavior and privacy preferences.
      </Typography>

      <Tabs
        value={tab}
        onChange={(_, value) => setTab(value)}
        sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}
      >
        {TABS.map((t) => (
          <Tab key={t.label} label={t.label} icon={t.icon} iconPosition="start" sx={{ minHeight: 48 }} />
        ))}
      </Tabs>

      {tab === 0 && <ProfileTab />}
      {tab === 1 && <DatabasesTab />}
      {tab === 2 && <AiPrivacyTab />}
      {tab === 3 && <AboutTab />}
    </Box>
  );
}
