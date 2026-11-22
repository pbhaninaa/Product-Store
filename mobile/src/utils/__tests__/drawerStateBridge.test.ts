import { DRAWER_STATE_BRIDGE, isDrawerStateMessage } from '../drawerStateBridge';

describe('drawer state bridge', () => {
  it('accepts only well-formed WEB_DRAWER messages', () => {
    expect(isDrawerStateMessage({ type: 'WEB_DRAWER', open: true })).toBe(true);
    expect(isDrawerStateMessage({ type: 'WEB_DRAWER', open: false })).toBe(true);
    expect(isDrawerStateMessage({ type: 'WEB_DRAWER', open: 'yes' })).toBe(false);
    expect(isDrawerStateMessage({ type: 'WEB_APP_STATE', open: true })).toBe(false);
    expect(isDrawerStateMessage(null)).toBe(false);
  });

  it('watches for an open temporary Vuetify drawer and installs once', () => {
    expect(DRAWER_STATE_BRIDGE).toContain('.v-navigation-drawer--temporary.v-navigation-drawer--open');
    expect(DRAWER_STATE_BRIDGE).toContain('__productStoreDrawerBridgeInstalled');
    expect(DRAWER_STATE_BRIDGE).toContain("type: 'WEB_DRAWER'");
  });
});
