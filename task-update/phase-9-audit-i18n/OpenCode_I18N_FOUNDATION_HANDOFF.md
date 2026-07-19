# OpenCode I18N Foundation Handoff

## Scope

Task 2A adds the English/Vietnamese internationalization foundation to `vibegraph-web`. It does not attempt to translate every screen, import prompt, or user-generated content.

## I18N Structure

> Superseded note: the initial implementation used `src/i18n` with `en` / `vi`.
> The project decision was changed after this handoff. The canonical structure is now
> `src/language` with `en-US` / `vi-VN`. Do not recreate `src/i18n`.

- `vibegraph-web/src/language/index.ts`: creates the typed `vue-i18n` instance in Composition API mode, validates the stored locale, defaults and falls back to English, persists locale changes, and updates the document language.
- `vibegraph-web/src/language/locales/en-US.json`: English source schema and messages.
- `vibegraph-web/src/language/locales/vi-VN.json`: Vietnamese messages matching the English schema.
- `vibegraph-web/src/components/ui/LanguageSelector.vue`: shared English/Vietnamese selector.
- Storage key: `vg_locale`.
- Supported locales: `en-US`, `vi-VN`.

The app installs i18n in `vibegraph-web/src/main.ts`. Selectors are available on the login page, user shell, and admin shell without changing existing navigation or authentication behavior.

## Adding Keys

1. Add the English key to `src/language/locales/en-US.json`. English is the `MessageSchema` source of truth.
2. Add the same nested key to `src/language/locales/vi-VN.json`.
3. In a Vue component, use `const { t } = useI18n({ useScope: 'global' })` and render `t('namespace.key')`.
4. For unit tests that mount an i18n-enabled component directly, include `i18n` in `global.plugins` and reset with `setLocale('en-US')` where English assertions are used.
5. Do not move import prompts or user-generated values into locale files.

## Files Changed For Task 2A

- `vibegraph-web/package.json`
- `vibegraph-web/package-lock.json`
- `vibegraph-web/src/main.ts`
- `vibegraph-web/src/language/index.ts`
- `vibegraph-web/src/language/locales/en-US.json`
- `vibegraph-web/src/language/locales/vi-VN.json`
- `vibegraph-web/src/components/ui/LanguageSelector.vue`
- `vibegraph-web/src/components/ui/__tests__/LanguageSelector.spec.ts`
- `vibegraph-web/src/components/layouts/UserLayout.vue`
- `vibegraph-web/src/components/layouts/AdminLayout.vue`
- `vibegraph-web/src/components/layouts/__tests__/UserLayout.spec.ts`
- `vibegraph-web/src/components/layouts/__tests__/AdminLayout.spec.ts`
- `vibegraph-web/src/views/LoginView.vue`
- `vibegraph-web/src/views/__tests__/LoginView.spec.ts`

Parallel agents also modified admin views and other frontend/business files during this task. Those changes were preserved and are not claimed as Task 2A work.

## Foundation Keys Added

- Common buttons: Save, Cancel, Delete, Disable, Enable, Search, Close, Create, Update, Retry.
- Primary user and admin navigation labels.
- Login labels, validation fallback, connection fallback, and sign-out labels.
- Language selector labels.
- Admin shell labels.

Some additional admin namespaces were merged to remain compatible with parallel i18n work already present in the worktree.

## Verification

- `npm --prefix vibegraph-web run type-check`: passed before later parallel edits; the latest rerun is blocked by in-progress `DashboardView.vue` TypeScript errors (`periodLabel`, `MONTH_LABELS`, and translation call signatures).
- `npm --prefix vibegraph-web run test:unit -- --run src/components/ui/__tests__/LanguageSelector.spec.ts src/components/layouts/__tests__/UserLayout.spec.ts src/components/layouts/__tests__/AdminLayout.spec.ts src/views/__tests__/LoginView.spec.ts`: 4 files passed, 11 tests passed.
- `npm --prefix vibegraph-web run build`: passed after the foundation integration, with the existing large-chunk warning.
- Full `npm --prefix vibegraph-web run test:unit -- --run`: 473 of 476 tests passed on the first run. Subsequent parallel admin-view i18n edits introduced incomplete admin namespaces/test expectations while this handoff was being written.
- `git diff --check`: passed for Task 2A changes; the latest run only reports line-ending warnings in unrelated concurrent files.

## Screens Not Yet Translated

Foundation scope intentionally leaves most application content in English, including:

- Registration and landing pages.
- User dashboard content and most user feature views.
- Graph, diagram, search, detail, and import workflows.
- Reports, notifications, subscription, usage, profile, and API-key content beyond shell navigation unless translated by another parallel agent.
- Most admin operational content unless translated by another parallel agent.
- Backend/API error messages returned as user-visible text.

Import prompts and user-generated content remain unchanged by design.
