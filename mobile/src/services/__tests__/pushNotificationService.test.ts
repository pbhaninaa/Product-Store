import { PermissionsAndroid, Platform } from 'react-native';
import notifee from '@notifee/react-native';
import { requestPushNotificationPermission } from '../pushNotificationService';

describe('requestPushNotificationPermission (Android 13+)', () => {
  const originalVersion = Platform.Version;

  beforeEach(() => {
    jest.clearAllMocks();
    Platform.OS = 'android';
    Object.defineProperty(Platform, 'Version', { value: 34, configurable: true });
  });

  afterAll(() => {
    Object.defineProperty(Platform, 'Version', { value: originalVersion, configurable: true });
  });

  // Requesting an already granted permission still opens the system activity, which steals window
  // focus and hides the keyboard. The app called this every 800ms while signed in.
  it('does not open the permission prompt when already granted', async () => {
    jest.spyOn(PermissionsAndroid, 'check').mockResolvedValue(true);
    const request = jest.spyOn(PermissionsAndroid, 'request');

    await expect(requestPushNotificationPermission()).resolves.toBe(true);

    expect(request).not.toHaveBeenCalled();
    expect(notifee.requestPermission).not.toHaveBeenCalled();
    expect(notifee.getNotificationSettings).toHaveBeenCalled();
  });

  it('asks when not yet granted', async () => {
    jest.spyOn(PermissionsAndroid, 'check').mockResolvedValue(false);
    const request = jest
      .spyOn(PermissionsAndroid, 'request')
      .mockResolvedValue(PermissionsAndroid.RESULTS.GRANTED);

    await expect(requestPushNotificationPermission()).resolves.toBe(true);
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('returns false when the user denies', async () => {
    jest.spyOn(PermissionsAndroid, 'check').mockResolvedValue(false);
    jest.spyOn(PermissionsAndroid, 'request').mockResolvedValue(PermissionsAndroid.RESULTS.DENIED);

    await expect(requestPushNotificationPermission()).resolves.toBe(false);
  });
});
