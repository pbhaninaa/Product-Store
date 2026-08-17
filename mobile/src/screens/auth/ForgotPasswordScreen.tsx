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

export default function ForgotPasswordScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>();
  const route = useRoute<RouteProp<RootStackParamList, 'ForgotPassword'>>();
  const { forgotPassword } = useAuth();
  const [email, setEmail] = useState(String(route.params?.email || ''));
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState('');

  const onSubmit = async () => {
    setError('');
    setDone(false);
    setLoading(true);
    try {
      await forgotPassword(email);
      setDone(true);
    } catch (e: any) {
      setError(e?.message || 'Could not send reset link.');
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
            <Text style={styles.title}>Forgot password</Text>
            <Text style={styles.lead}>
              Enter your account email. If it is registered, we will send a link to set a new
              password.
            </Text>
            <ErrorBanner message={error} />
            <SuccessBanner
              message={
                done
                  ? 'If that email is registered, reset instructions have been sent. Check your inbox.'
                  : ''
              }
            />
            <Field
              label="Email"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              editable={!loading && !done}
              onSubmitEditing={onSubmit}
            />
            <PrimaryButton
              title="Send reset link"
              onPress={onSubmit}
              loading={loading}
              disabled={done}
            />
            <View style={styles.footerRow}>
              <Text style={styles.footerText}>Remembered your password?</Text>
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
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 20,
  },
  footerText: { color: colors.textMuted, fontSize: 14 },
});
