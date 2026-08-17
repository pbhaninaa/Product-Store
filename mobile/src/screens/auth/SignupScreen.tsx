import React, { useMemo, useState } from 'react';
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
import { slugFromBusinessName } from '../../utils/slug';
import type { RootStackParamList } from '../../navigation/types';

export default function SignupScreen() {
  const navigation = useNavigation<StackNavigationProp<RootStackParamList>>();
  const { signup, setMerchantSlug } = useAuth();
  const [merchantName, setMerchantName] = useState('');
  const [ownerEmail, setOwnerEmail] = useState('');
  const [ownerPassword, setOwnerPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const slugPreview = useMemo(() => {
    try {
      return slugFromBusinessName(merchantName);
    } catch {
      return '';
    }
  }, [merchantName]);

  const onSubmit = async () => {
    setError('');
    setLoading(true);
    try {
      const res = await signup({ merchantName, ownerEmail, ownerPassword });
      const slug =
        (res && res.tenant && res.tenant.slug && String(res.tenant.slug).trim()) ||
        (res && res.merchantSlug && String(res.merchantSlug).trim()) ||
        '';
      if (slug) setMerchantSlug(slug);
      navigation.replace('WebApp', {
        path: `/${encodeURIComponent(slug)}/admin/subscription`,
      });
    } catch (e: any) {
      setError(e?.message || 'Could not sign up.');
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
            <Text style={styles.overline}>Business Owner</Text>
            <Text style={styles.title}>Create your store</Text>
            <Text style={styles.lead}>
              Sign up as a Business Owner. After setup you manage products, orders, and settings in
              this app. Customers shop on the web portal.
            </Text>

            <ErrorBanner message={error} />

            <Field
              label="Business name"
              value={merchantName}
              onChangeText={setMerchantName}
              editable={!loading}
            />
            <Text style={styles.hint}>
              Your store link is created automatically from this name
              {slugPreview ? ` ù /${slugPreview}` : ''}
            </Text>

            <Field
              label="Owner email"
              value={ownerEmail}
              onChangeText={setOwnerEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              editable={!loading}
            />
            <Field
              label="Password"
              value={ownerPassword}
              onChangeText={setOwnerPassword}
              secureTextEntry
              editable={!loading}
              onSubmitEditing={onSubmit}
            />

            <PrimaryButton
              title="Create Business Owner account"
              onPress={onSubmit}
              loading={loading}
            />

            <View style={styles.footerRow}>
              <Text style={styles.footerText}>Already have an account?</Text>
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
  title: {
    fontSize: 26,
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
  hint: {
    marginTop: -8,
    marginBottom: 14,
    fontSize: 12,
    color: colors.textFaint,
  },
  footerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    flexWrap: 'wrap',
    gap: 8,
    marginTop: 20,
  },
  footerText: { color: colors.textMuted, fontSize: 14 },
});
