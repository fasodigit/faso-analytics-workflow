# Upgrade notes — Angular 19 -> 21 (analytics-frontend)

Date: 2026-05-11
Branch: `chore/bump-angular-21`
Author: FASO Digitalisation

## Target Angular version

| Candidate | npm dist-tag (2026-05-11) | Decision |
|---|---|---|
| Angular 22 | `next: 22.0.0-next.12` (pre-release) | Skipped — not stable on npm |
| **Angular 21** | **`latest: 21.2.12` (stable)** | **Target retained** |

Rationale: per instruction "If Angular 22 `latest` exists -> target 22, else if 21 `latest` exists -> target 21", Angular 22 is still tagged `next` at the time of the bump. Target retained = Angular 21 stable (`21.2.12`).

## Bump matrix

| Package | Before | After | Compat verdict |
|---|---|---|---|
| @angular/core | ^19.0.0 | ^21.0.0 (-> 21.2.12) | OK — major bump 19 -> 20 -> 21, standalone APIs preserved |
| @angular/animations | ^19.0.0 | ^21.0.0 | OK |
| @angular/cdk | ^19.0.0 | ^21.0.0 (-> 21.2.10) | OK |
| @angular/common | ^19.0.0 | ^21.0.0 | OK |
| @angular/compiler | ^19.0.0 | ^21.0.0 | OK |
| @angular/forms | ^19.0.0 | ^21.0.0 | OK |
| @angular/platform-browser | ^19.0.0 | ^21.0.0 | OK |
| @angular/platform-browser-dynamic | ^19.0.0 | ^21.0.0 | OK |
| @angular/router | ^19.0.0 | ^21.0.0 | OK |
| @angular-devkit/build-angular | ^19.0.0 | ^21.0.0 (-> 21.2.10) | OK — application builder (esbuild) default |
| @angular/cli | ^19.0.0 | ^21.0.0 (-> 21.2.10) | OK |
| @angular/compiler-cli | ^19.0.0 | ^21.0.0 (-> 21.2.12) | OK |
| @ngx-formly/core | ^6.3.10 | ^7.1.0 | OK — major v6 -> v7, peer requires `@angular/forms >= 13.2`. APIs used (`FieldType`, `FieldArrayType`, `FieldTypeConfig`, `FormlyFieldConfig`, `FormlyModule.forRoot`) preserved. No code change required. |
| @ngx-formly/material | ^6.3.10 | **DROPPED** | Was declared but never imported anywhere in `src/`. Removed (would otherwise have required adding `@angular/material` as a peer just for an unused symbol). |
| @swimlane/ngx-graph | ^9.0.1 | `12.0.0-alpha.1` | OK — only release that lists `@angular/core 21.x` in peerDependencies (latest stable v9 peers cap at Angular 18.x, v10/v11 don't exist on npm, v12.0.0-alpha.1 is the official Angular 21 pre-release). Pinned exact (no `^`) to avoid accidental upgrades to an unstable next. **NOTE — ADR-003 fallback to rete.js is NOT needed** because the alpha works on Angular 21. |
| ngx-echarts | ^19.0.0 | ^21.0.0 (-> 21.0.0) | OK — peer requires `@angular/core >= 21.0.0`. API (`provideEchartsCore`) unchanged. |
| ag-grid-community | ^32.3.0 | ^35.2.1 | OK — peer `@angular/core >= 18`. `ClientSideRowModelModule`, `ModuleRegistry`, `ColDef`, `GridOptions` all preserved (still exported from `ag-grid-community` main entry as of v35). CSS imports `ag-grid-community/styles/{ag-grid,ag-theme-alpine}.css` still available in v35. The new JS-based theming (v33+) is OPT-IN; the legacy CSS theme keeps working via the `class="ag-theme-alpine"` selector. |
| ag-grid-angular | ^32.3.0 | ^35.2.1 | OK |
| echarts | ^5.5.1 | (unchanged) | OK |
| rxjs | ~7.8.0 | (unchanged) | OK — Angular 21 peer is `^6.5.3 \|\| ^7.4.0` |
| stompjs | ^2.3.3 | (unchanged) | OK |
| sockjs-client | ^1.6.1 | (unchanged) | OK |
| tslib | ^2.3.0 | (unchanged) | OK |
| zone.js | ~0.15.0 | (unchanged) | OK — Angular 21 peer is `~0.15.0 \|\| ~0.16.0`. Kept on `~0.15` to avoid an extra moving part. **Zoneless mode (Angular 18+) NOT enabled in this PR — Phase 5 follow-up.** |
| typescript | ~5.6.0 | ~5.9.0 (-> 5.9.3) | REQUIRED bump — Angular 21 peer is `typescript >= 5.9 < 6.0`. |
| @types/node | ^22.0.0 | (unchanged) | OK |
| json-schema-to-typescript | ^15.0.4 | (unchanged) | OK |
| prettier | ^3.4.0 | (unchanged) | OK |

## Files modified

- `services/analytics-frontend/package.json` — bumps + dropped `@ngx-formly/material`.
- `services/analytics-frontend/angular.json` — added `configurations.production` / `configurations.development` (Angular 21 application builder required an explicit `production` configuration for `ng build --configuration production`; the bare `ng build` invocation used previously was silently picking up zero optimization). Also added `allowedCommonJsDependencies: ["dagre", "webcola"]` to silence the optimization-bailout warnings on `@swimlane/ngx-graph` deps.
- `services/analytics-frontend/package-lock.json` — fully regenerated (`rm -rf node_modules package-lock.json && npm install --legacy-peer-deps`).

No source files (`src/**/*.ts`, `src/**/*.html`, `src/**/*.css`) were modified. The Phase 1 demo route (`/demo`) and Phase 2 builder route (`/builder`) compile unchanged.

## Validation outcome

| Step | Command | Result |
|---|---|---|
| Install | `npm install --legacy-peer-deps` | OK — 896 packages, 0 vulnerabilities |
| Type check | `npx tsc --noEmit -p tsconfig.app.json` | OK — 0 errors |
| Dev build | `npx ng build` | OK |
| Production build | `npx ng build --configuration production` | OK (1 budget warning, no error) |

## Bundle size delta

Comparison is made on the Phase 3 baseline produced by the existing `dist/` directory under Angular 19 (the dev build there was non-optimized; numbers from `ls -lh` + `gzip -c <f> | wc -c`).

| Bundle | Angular 19 (dev build, Phase 3 baseline) | Angular 21 (prod build, this PR) |
|---|---|---|
| Initial total (gzipped) | ~430 KB (dev, non-minified) | **385 KB (prod, minified)** |
| Grand total all chunks gzipped | n/a (dev) | **684 KB** |
| Main chunk gzip | 218 KB (dev) | 126 KB (prod) |
| Lazy `demo-page` chunk gzip | n/a (only widget chunks visible in dev) | 184 KB (prod) |
| Lazy `builder-page` chunk gzip | n/a (only widget chunks visible in dev) | 111 KB (prod) |

Notes:
- The Phase 3 baseline of 327 KB referenced in the original CHANGELOG was prior to the 14 visualizations added in Phase 3 (PR #9), which added ag-grid + ECharts geo to the initial bundle. The current initial gzip of ~385 KB on Angular 21 is in the same ballpark and slightly better than the 430 KB-equivalent Phase-3 dev artifact.
- A 1 MB initial-budget warning is emitted (initial = 1.34 MB raw, ~385 KB gzipped). Tuning the budget or splitting `chunk-CHJDOU5P.js` (134 KB gzipped — likely ECharts+ngx-graph+ag-grid common code) is a Phase 5 task.

## Known regressions / follow-ups

1. **Zoneless mode** — Angular 18+ supports `provideExperimentalZonelessChangeDetection()`. NOT enabled here; the demo + builder still rely on `provideZoneChangeDetection({ eventCoalescing: true })`. To convert in Phase 5, audit ngx-echarts + ngx-graph + ag-grid-angular for zone-less compatibility.
2. **`@swimlane/ngx-graph 12.0.0-alpha.1`** — pinned exact (pre-release). Watch the project for a stable v12 (or v11) release matching Angular 21. ADR-003 fallback (rete.js) remains the documented escape hatch if the alpha is yanked.
3. **ag-grid v33+ theming API** — the modern JS-based theming (e.g. `theme: themeQuartz`) is NOT adopted in this PR; we keep the legacy CSS-class theme (`class="ag-theme-alpine"`). Migration is optional and orthogonal to the Angular bump.
4. **Bundle budget warning** — initial JS exceeds 1 MB raw. Either bump the budget or split. Phase 5.
5. **`@ngx-formly/material` removed** — if a future story needs Material-styled formly inputs, re-add `@ngx-formly/material@^7` and pull in `@angular/material@^21`.

## Migration steps applied

No `ng update` invocation was needed in the end: the migration was a single `package.json` rewrite + `rm -rf node_modules package-lock.json && npm install --legacy-peer-deps`. The Angular 21 schematics did not need to run because:

- The project is already on the standalone-APIs default (Angular 17+).
- The project already uses the application builder (`@angular-devkit/build-angular:application`), Angular 17+ default.
- No deprecated symbols are used (`NgModule`-only patterns, `@Input({transform: ...})` migration, control-flow `*ngIf -> @if` — the latter was already adopted in the Phase 3 pivot-table renderer template).
- No `@angular/material`, no SSR, no service worker.

Should a future bump require schematics, the canonical command is:
```bash
npx -y @angular/cli@21 ng update @angular/cli@21 @angular/core@21 --allow-dirty --force
```
