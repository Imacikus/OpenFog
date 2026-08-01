// SPDX-License-Identifier: MIT
// Material Design 3 Theme – Seed-Farbe → MD3-Tonal-Palette (nur Dark).
import { SchemeTonalSpot, Hct, hexFromArgb } from '@material/material-color-utilities';

const SEED_COLOR = '#6c63ff';
const THEME_ATTR = 'data-theme';

let theme = null;

function getTheme() {
  if (!theme) {
    const source = Hct.fromInt(parseInt(SEED_COLOR.slice(1), 16));
    theme = new SchemeTonalSpot(source, true, 0);
  }
  return theme;
}

function applyScheme() {
  const s = getTheme();
  const root = document.documentElement;
  root.setAttribute(THEME_ATTR, 'dark');
  const map = {
    '--md-sys-color-primary': s.primary,
    '--md-sys-color-on-primary': s.onPrimary,
    '--md-sys-color-primary-container': s.primaryContainer,
    '--md-sys-color-on-primary-container': s.onPrimaryContainer,
    '--md-sys-color-secondary': s.secondary,
    '--md-sys-color-on-secondary': s.onSecondary,
    '--md-sys-color-secondary-container': s.secondaryContainer,
    '--md-sys-color-on-secondary-container': s.onSecondaryContainer,
    '--md-sys-color-tertiary': s.tertiary,
    '--md-sys-color-on-tertiary': s.onTertiary,
    '--md-sys-color-tertiary-container': s.tertiaryContainer,
    '--md-sys-color-on-tertiary-container': s.onTertiaryContainer,
    '--md-sys-color-error': s.error,
    '--md-sys-color-on-error': s.onError,
    '--md-sys-color-error-container': s.errorContainer,
    '--md-sys-color-on-error-container': s.onErrorContainer,
    '--md-sys-color-background': s.background,
    '--md-sys-color-on-background': s.onBackground,
    '--md-sys-color-surface': s.surface,
    '--md-sys-color-on-surface': s.onSurface,
    '--md-sys-color-surface-variant': s.surfaceVariant,
    '--md-sys-color-on-surface-variant': s.onSurfaceVariant,
    '--md-sys-color-surface-container-lowest': s.surfaceContainerLowest,
    '--md-sys-color-surface-container-low': s.surfaceContainerLow,
    '--md-sys-color-surface-container': s.surfaceContainer,
    '--md-sys-color-surface-container-high': s.surfaceContainerHigh,
    '--md-sys-color-surface-container-highest': s.surfaceContainerHighest,
    '--md-sys-color-outline': s.outline,
    '--md-sys-color-outline-variant': s.outlineVariant,
    '--md-sys-color-scrim': s.scrim,
    '--md-sys-color-shadow': s.shadow
  };
  for (const [key, value] of Object.entries(map)) {
    root.style.setProperty(key, hexFromArgb(value));
  }
}

function initTheme() {
  applyScheme();
  document.dispatchEvent(new CustomEvent('themechange', { detail: { scheme: 'dark' } }));
}

export { initTheme };
