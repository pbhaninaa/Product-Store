import { NativeModules } from 'react-native';
import type { ProductStoreAppEnv } from './environments';

type ServiceHubConfigNative = {
  appEnv?: string;
};

function parseAppEnv(raw: string | undefined): ProductStoreAppEnv {
  if (raw === 'uat' || raw === 'prod' || raw === 'sit') {
    return raw;
  }
  return 'sit';
}

export function getAppEnvironment(): ProductStoreAppEnv {
  const native = NativeModules.ServiceHubConfig as ServiceHubConfigNative | undefined;
  return parseAppEnv(native?.appEnv);
}
