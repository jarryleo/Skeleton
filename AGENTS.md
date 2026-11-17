# Skeleton Android

Android library (`:library`) that draws shimmer/skeleton loading animations over any view, published via JitPack as `com.github.rasoulmiri:Skeleton`. `:sample` is the demo app. All code is Java; Gradle files use Groovy DSL.

## Build commands

- `./gradlew assembleDebug` — build all modules (on Windows: `gradlew.bat`)
- `./gradlew :library:assembleDebug` — library only
- `./gradlew :sample:installDebug` — install demo app on a device
- No meaningful unit/instrumented tests exist (only template `Example*Test` classes); verification is building and eyeballing the sample app.

## Toolchain state (AGP 9 + AndroidX, Nov 2025)

- Gradle wrapper **9.1.0**; root `build.gradle` declares **AGP 9.0.1** via the `plugins {}` DSL (`apply false`), modules apply `com.android.library` / `com.android.application`. Requires JDK 17+ to build.
- Repositories are declared once in `settings.gradle` (`pluginManagement` + `dependencyResolutionManagement`): `google()` + `mavenCentral()`. `jcenter()` was removed; do not reintroduce it.
- The workspace skill `.zcode/skills/kotlin-tooling-agp9-migration` documents AGP 9 migration background — consult it before touching AGP/plugin versions.
- AGP 9 accommodations baked in: `namespace` in each module's build.gradle (manifests carry no `package=`; the launcher activity sets `android:exported="true"` explicitly); `buildFeatures { buildConfig = true }` in the library because `CLog` reads `BuildConfig.DEBUG`; `proguard-android-optimize.txt` (plain `.txt` default file is disallowed); sample `MainActivity` uses if/else instead of `switch(R.id…)` because application-module R fields are no longer compile-time constants; Groovy build scripts use `prop = value` assignment syntax (space-assignment is removed in Gradle 10).
- Keep AGP 9's built-in Kotlin enabled (do not set `enableKotlin = false`): it aligns transitive Kotlin versions — disabling it resurrects duplicate-class conflicts between kotlin-stdlib variants. Its side effect, `kotlin-stdlib` appearing in the published POM, is expected and harmless.

## AndroidX

- Migrated to AndroidX (Nov 2025): `androidx.appcompat:appcompat:1.7.0`, `androidx.cardview:cardview:1.0.0`, `androidx.recyclerview:recyclerview:1.3.2`, `androidx.annotation:annotation:1.8.2`; `gradle.properties` sets `android.useAndroidX=true`. No Jetifier needed — no legacy Support Library artifacts remain.
- The old `com.android.support:design:28.0.0` dependency was dropped because nothing referenced design/material classes. If a Material widget is ever needed, add `com.google.android.material:material`.

## JitPack publishing

- `library/build.gradle` applies `maven-publish`. `android { publishing { singleVariant('release') { withSourcesJar() } } }` registers the SoftwareComponent — AGP ≥7.1 requires this or `components.release` does not exist — and an `afterEvaluate` block consumes it with coordinates `com.github.rasoulmiri:Skeleton`; JitPack rewrites the version to the git tag.
- `jitpack.yml` pins `jdk: openjdk17` (JitPack defaults to an older JDK that cannot run AGP 9) and runs `./gradlew :library:publishToMavenLocal`.
- Release flow: commit → tag (e.g. `1.2.0`) → push → watch the first build on jitpack.io.
- `local.properties` is git-ignored and untracked; keep it that way — a committed machine-specific SDK path breaks JitPack builds.

## Modules & architecture

- `library/` — the library. Package `io.rmiri.skeleton`:
  - Root package: public widgets `SkeletonView` and `SkeletonViewGroup` (both extend `SkeletonMaster`).
  - `master/`: engine — `SkeletonMaster` (drawing/animation core), `SkeletonModel` + `SkeletonModelBuilder` (builder-pattern configs), `SkeletonConfig`, `AdapterSkeleton` + `IsCanSetAdapterListener` (RecyclerView wrapper).
  - `utils/`: `CLog`, `ColorUtils`, `ConverterUnitUtil`.
  - `res/values/attrs.xml`: public XML attributes, all prefixed `SK_` (e.g. `SK_animationDuration`).
- `sample/` — demo app (`io.rmiri.skeleton.sample`), depends on `project(':library')`. Activities are organized by usage style: `activity/xml/*` vs `activity/java/*`. Use these as reference examples for how the library is meant to be consumed.
- `demoFile/` — README images and demo APK, not code.

## Conventions & constraints

- **AndroidX, not Support Library** (migrated Nov 2025; see the AndroidX section above).
- Versions: compileSdk 36 in both modules, targetSdk 36 in the sample, minSdk 24 in both. The README states "Minimum API 24" — keep claims consistent when changing minSdk.
- Logging goes through `CLog` (debug-only, tag `"Skeleton"`); never call `android.util.Log` directly in the library.
- Public API is both XML attrs and the builder API (`SkeletonModelBuilder`). `README.md` documents every `SK_*` attribute and builder setter — update the README whenever `attrs.xml` or model setters change.
- Sample app code should stay in sync with the patterns the README teaches, since users copy from it.
