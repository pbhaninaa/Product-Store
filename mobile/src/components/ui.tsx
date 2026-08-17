import React from 'react';
import {
  ActivityIndicator,
  StyleSheet,
  Text,
  TextInput,
  TextInputProps,
  TouchableOpacity,
  View,
  ViewStyle,
} from 'react-native';
import { colors } from '../theme/colors';

export function Screen({
  children,
  style,
}: {
  children: React.ReactNode;
  style?: ViewStyle;
}) {
  return <View style={[styles.screen, style]}>{children}</View>;
}

export function Card({ children }: { children: React.ReactNode }) {
  return <View style={styles.card}>{children}</View>;
}

export function Field(props: TextInputProps & { label: string; error?: string }) {
  const { label, error, style, ...rest } = props;
  return (
    <View style={styles.fieldWrap}>
      <Text style={styles.label}>{label}</Text>
      <TextInput
        placeholderTextColor={colors.textFaint}
        style={[styles.input, style]}
        {...rest}
      />
      {error ? <Text style={styles.fieldError}>{error}</Text> : null}
    </View>
  );
}

export function PrimaryButton({
  title,
  onPress,
  loading,
  disabled,
}: {
  title: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
}) {
  const isDisabled = disabled || loading;
  return (
    <TouchableOpacity
      style={[styles.btn, isDisabled && styles.btnDisabled]}
      onPress={onPress}
      disabled={isDisabled}
      activeOpacity={0.85}
    >
      {loading ? (
        <ActivityIndicator color={colors.white} />
      ) : (
        <Text style={styles.btnText}>{title}</Text>
      )}
    </TouchableOpacity>
  );
}

export function LinkButton({ title, onPress }: { title: string; onPress: () => void }) {
  return (
    <TouchableOpacity onPress={onPress} hitSlop={8}>
      <Text style={styles.link}>{title}</Text>
    </TouchableOpacity>
  );
}

export function ErrorBanner({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <View style={styles.errorBanner}>
      <Text style={styles.errorText}>{message}</Text>
    </View>
  );
}

export function SuccessBanner({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <View style={styles.successBanner}>
      <Text style={styles.successText}>{message}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: colors.backgroundAlt,
  },
  card: {
    backgroundColor: colors.surface,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 20,
  },
  fieldWrap: {
    marginBottom: 14,
  },
  label: {
    fontSize: 13,
    fontWeight: '600',
    color: colors.textMuted,
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 16,
    color: colors.text,
    backgroundColor: colors.white,
  },
  fieldError: {
    marginTop: 4,
    color: colors.error,
    fontSize: 12,
  },
  btn: {
    backgroundColor: colors.primary,
    borderRadius: 12,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  btnDisabled: {
    opacity: 0.55,
  },
  btnText: {
    color: colors.white,
    fontSize: 16,
    fontWeight: '700',
  },
  link: {
    color: colors.primary,
    fontWeight: '700',
    fontSize: 14,
    textDecorationLine: 'underline',
  },
  errorBanner: {
    borderWidth: 1,
    borderColor: 'rgba(185, 28, 28, 0.35)',
    backgroundColor: 'rgba(254, 226, 226, 0.7)',
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
  },
  errorText: {
    color: colors.error,
    fontSize: 14,
    fontWeight: '500',
  },
  successBanner: {
    borderWidth: 1,
    borderColor: 'rgba(21, 128, 61, 0.35)',
    backgroundColor: 'rgba(220, 252, 231, 0.7)',
    borderRadius: 12,
    padding: 12,
    marginBottom: 12,
  },
  successText: {
    color: colors.success,
    fontSize: 14,
    fontWeight: '500',
  },
});
