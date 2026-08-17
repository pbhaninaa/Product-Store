import React, { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useNavigation, useRoute } from '@react-navigation/native';
import type { RouteProp } from '@react-navigation/native';
import type { StackNavigationProp } from '@react-navigation/stack';
import { useAuth } from '../../context/AuthContext';
import {
  Card,
  ErrorBanner,
  Field,
  LinkButton,
  PrimaryButton,
  Screen,
  SuccessBanner,
} from '../../components/ui';
import { colors } from '../../theme/colors';
import type { RootStackParamList } from '../../navigation/types';

export default function ResetPasswordScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<RootStackParamList, 'ResetPassword'>>();
  const { resetPassword } = useAuth();
  const token = String(route.params?.token || '').trim();
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  const onSubmit = async () => {
    setError('');
    if (!token) {
      setError('Reset token is missing.');
      return;
    }
    if (!newPassword || newPassword.length < 8) {
      setError('New password must be at least 8 characters.');
      return;
    }
    if (newPassword !== confirmPassword) {
      setError('Passwords do not match.');
      return;
    }
    setLoading(true);
    try {
      await resetPassword(token, newPassword);
      setDone(true);
    } catch (e: any) {
      setError(e?.message || 'Could not reset password.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <Screen>
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <ScrollView contentContainerStyle={styles.pad} keyboardShouldPersistTaps="handled">
          <Card>
            <Text style={styles.overline}>Account recovery</Text>
            <Text style={styles.title}>Reset password</Text>
            <Text style={styles.lead}>
              Choose a new password for your Product Store account. Passwords must be at least 8
              characters.
            </Text>
            {!token ? (
              <ErrorBanner message="This reset link is missing a token. Open the link from your email." />
            ) : null}
            <ErrorBanner message={error} />
            <SuccessBanner
              message={
                done
                  ? 'Your password was updated. You can sign in with the new password.'
                  : ''
              }
            />
            {token && !done ? (
              <>
                <Field
                  label="New password"
                  value={newPassword}
                  onChangeText={setNewPassword}
                  secureTextEntry
                  editable={!loading}
                />
                <Field
                  label="Confirm new password"
                  value={confirmPassword}
                  onChangeText={setConfirmPassword}
                  secureTextEntry
                  editable={!loading}
                  onSubmitEditing={onSubmit}
                />
                <PrimaryButton title="Update password" onPress={onSubmit} loading={loading} />
              </>
            ) : null}
            <View style={styles.footerRow}>
              <LinkButton
                title="Request a new link"
                onPress={() => navigation.navigate('ForgotPassword', {})}
              />
              <LinkButton title="Sign in" onPress={() => navigation.navigate('Login')} />
            </View>
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  pad: { padding: 16, paddingTop: 28, paddingBottom: 40 },
  overline: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: colors.textFaint,
    marginBottom: 8,
  },
  title: { fontSize: 26, fontWeight: '800', color: colors.text, marginBottom: 8 },
  lead: { fontSize: 14, lineHeight: 21, color: colors.textMuted, marginBottom: 20 },
  footerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 20,
  },
});
