import React, { useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { StackNavigationProp } from '@react-navigation/stack';
import { useAuth } from '../../context/AuthContext';
import {
  Card,
  ErrorBanner,
  Field,
  LinkButton,
  PrimaryButton,
  Screen,
} from '../../components/ui';
import { colors } from '../../theme/colors';
import { isMerchantUser } from '../../utils/merchantRoles';
import { resolveStaffWebPath } from '../../utils/authSessionBridge';
import type { RootStackParamList } from '../../navigation/types';

export default function LoginScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>();
  const { login, setMerchantSlug, refreshSession } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const goStaffHome = async (res: any) => {
    const roles = (res && res.roles) || [];
    const slug =
      (res && res.tenant && res.tenant.slug) ||
      (res && res.merchantSlug) ||
      '';
    if (slug) setMerchantSlug(String(slug).trim());

    const session = await refreshSession();
    const path = resolveStaffWebPath(
      session || {
        roles,
        tenant: String(slug || '').trim(),
      },
    );

    if (path === '/login') {
      setError('This account cannot use the mobile app. Clients should use the web portal.');
      return;
    }

    if (
      !isMerchantUser({ roles }) &&
      !roles.includes('SUPPORT_USER') &&
      !roles.includes('PLATFORM_ADMIN')
    ) {
      setError('This account cannot use the mobile app. Clients should use the web portal.');
      return;
    }

    navigation.replace('WebApp', { path });
  };

  const onSubmit = async () => {
    setError('');
    setLoading(true);
    try {
      const res = await login(email, password);
      await goStaffHome(res);
    } catch (e: any) {
      setError(e?.message || 'Sign in failed.');
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
            <Text style={styles.overline}>Providers &amp; support</Text>
            <Text style={styles.title}>Sign in</Text>
            <Text style={styles.lead}>
              For merchant owners, staff, and platform support. Customers shop on the web portal —
              not in this app.
            </Text>

            <ErrorBanner message={error} />

            <Field
              label="Email"
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              autoComplete="email"
            />
            <Field
              label="Password"
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              autoComplete="password"
              onSubmitEditing={onSubmit}
            />

            <View style={styles.rowEnd}>
              <LinkButton
                title="Forgot password?"
                onPress={() => navigation.navigate('ForgotPassword', { email })}
              />
            </View>

            <PrimaryButton title="Sign in" onPress={onSubmit} loading={loading} />

            <View style={styles.footerRow}>
              <Text style={styles.footerText}>New merchant?</Text>
              <LinkButton title="Create a store" onPress={() => navigation.navigate('Signup')} />
            </View>
          </Card>
        </ScrollView>
      </KeyboardAvoidingView>
    </Screen>
  );
}

const styles = StyleSheet.create({
  pad: {
    padding: 16,
    paddingTop: 28,
    paddingBottom: 40,
  },
  overline: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: colors.textFaint,
    marginBottom: 8,
  },
  title: {
    fontSize: 28,
    fontWeight: '800',
    color: colors.text,
    marginBottom: 8,
  },
  lead: {
    fontSize: 14,
    lineHeight: 21,
    color: colors.textMuted,
    marginBottom: 20,
  },
  rowEnd: {
    alignItems: 'flex-end',
    marginBottom: 16,
  },
  footerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 20,
  },
  footerText: {
    color: colors.textMuted,
    fontSize: 14,
  },
});
