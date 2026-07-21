import { useEffect, useState, type FormEvent } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Divider,
  Grid,
  Snackbar,
  Stack,
  TextField,
} from '@mui/material';
import { profileApi } from '../../api/profile';
import { useAuth } from '../../context/AuthContext';
import { extractApiError } from '../../utils/errors';

export default function ProfileTab() {
  const { user, updateUser } = useAuth();
  const [fullName, setFullName] = useState(user?.fullName ?? '');
  const [organization, setOrganization] = useState(user?.organization ?? '');
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [changingPassword, setChangingPassword] = useState(false);
  const [passwordError, setPasswordError] = useState<string | null>(null);

  const [snackbar, setSnackbar] = useState<string | null>(null);

  useEffect(() => {
    setFullName(user?.fullName ?? '');
    setOrganization(user?.organization ?? '');
  }, [user]);

  const handleSaveProfile = async (event: FormEvent) => {
    event.preventDefault();
    setProfileError(null);
    setSavingProfile(true);
    try {
      const updated = await profileApi.update(fullName, organization);
      updateUser(updated);
      setSnackbar('Profile updated');
    } catch (err) {
      setProfileError(extractApiError(err));
    } finally {
      setSavingProfile(false);
    }
  };

  const handleChangePassword = async (event: FormEvent) => {
    event.preventDefault();
    setPasswordError(null);
    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match');
      return;
    }
    setChangingPassword(true);
    try {
      await profileApi.changePassword(currentPassword, newPassword);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
      setSnackbar('Password changed');
    } catch (err) {
      setPasswordError(extractApiError(err));
    } finally {
      setChangingPassword(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Card>
        <CardHeader title="Profile" subheader="Your account details" />
        <Divider />
        <CardContent component="form" onSubmit={handleSaveProfile}>
          <Grid container spacing={2.5}>
            <Grid item xs={12} md={6}>
              <TextField
                label="Full name"
                fullWidth
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                label="Organization"
                fullWidth
                value={organization}
                onChange={(e) => setOrganization(e.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={6}>
              <TextField
                label="Email"
                fullWidth
                disabled
                value={user?.email ?? ''}
                helperText="Email cannot be changed"
              />
            </Grid>
          </Grid>
          {profileError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {profileError}
            </Alert>
          )}
          <Box sx={{ mt: 2.5 }}>
            <Button type="submit" variant="contained" disabled={savingProfile}>
              {savingProfile ? 'Saving…' : 'Save changes'}
            </Button>
          </Box>
        </CardContent>
      </Card>

      <Card>
        <CardHeader title="Change password" />
        <Divider />
        <CardContent component="form" onSubmit={handleChangePassword}>
          <Grid container spacing={2.5}>
            <Grid item xs={12} md={4}>
              <TextField
                label="Current password"
                type="password"
                fullWidth
                required
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                label="New password"
                type="password"
                fullWidth
                required
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                helperText="At least 8 characters"
              />
            </Grid>
            <Grid item xs={12} md={4}>
              <TextField
                label="Confirm new password"
                type="password"
                fullWidth
                required
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
              />
            </Grid>
          </Grid>
          {passwordError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {passwordError}
            </Alert>
          )}
          <Box sx={{ mt: 2.5 }}>
            <Button type="submit" variant="outlined" disabled={changingPassword}>
              {changingPassword ? 'Changing…' : 'Change password'}
            </Button>
          </Box>
        </CardContent>
      </Card>

      <Snackbar
        open={Boolean(snackbar)}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Stack>
  );
}
