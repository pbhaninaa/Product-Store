export type RootStackParamList = {
  Boot: undefined;
  Login: undefined;
  Signup: undefined;
  ForgotPassword: { email?: string };
  ResetPassword: { token?: string };
  /** Full merchant admin / support console (Vue SPA in WebView). */
  WebApp: { path?: string };
};
