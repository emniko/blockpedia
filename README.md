# Blockpedia

An unofficial, fully native Android guide to Minecraft, powered live by the [Minecraft Wiki](https://minecraft.wiki).
Kotlin + Jetpack Compose. No WebView anywhere: wiki pages are parsed into native components.

## What it does

- **Curated home**: Blocks, Items, Mobs, Biomes, Structures, Crafting, Smelting, Brewing, Enchanting, Effects,
  Trading, Smithing, Redstone, Commands, Advancements, Dimensions, Versions, Tutorials. Each card opens the
  overview article; the folder icon browses the full wiki category.
- **Full wiki reader**: any page, rendered natively — headings, rich text with tappable links, lists,
  scrollable tables (rowspan/colspan aware), infoboxes, images, galleries, notes and **crafting/smelting grids
  drawn as Minecraft-style slots**.
- **Search** with live results and snippets; **category browser** with infinite scroll; **random page**.
- **Table of contents** sheet, section links that scroll in place, bookmarks, reading history.
- **Offline**: every opened page is saved; if the network fails you get the saved copy.

## Build the APK

### Option A — GitHub Actions (no setup)
1. Create a GitHub repo and push this folder.
2. The **Build APK** workflow runs on every push (or trigger it from the Actions tab).
3. Download the `blockpedia-apk` artifact. It contains:
   - `app-debug.apk` — always built
   - `app-release.apk` — minified and smaller, signed with the debug key so it installs directly

### Option B — Android Studio
Open the folder, let Gradle sync, then **Build ▸ Build APK(s)**, or run:
```
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

Requirements: JDK 17, Android SDK 35. Minimum Android version: 8.0 (API 26).

## How it works

| Layer | File | Role |
|---|---|---|
| API | `data/WikiApi.kt` | MediaWiki Action API: `parse`, `search`/`prefixsearch`, `categorymembers`, `random` |
| Cache | `data/PageCache.kt`, `data/WikiRepository.kt` | Raw responses on disk (fresh for 3 days, stale copy used offline) + in-memory LRU |
| Parser | `parse/WikiHtmlParser.kt` | jsoup walk of the wiki HTML into `ContentBlock`s (testable, no Android types) |
| UI | `ui/article/BlockViews.kt` | One Compose renderer per block type |

If the wiki changes its markup, the parser is the only place to adjust; `app/src/test` has unit tests for it
(`./gradlew testDebugUnitTest`).

## Before sharing it

- **License**: wiki text is **CC BY-NC-SA 3.0**. The app credits the wiki on every page and in About. Keep it
  non-commercial (no ads or paid version), and any redistribution of the content must keep the same license.
- **Trademark**: don't name or style it as an official Minecraft app. Google Play has historically rejected
  or removed unofficial Minecraft guides; sideloading the APK is the safe route.
- **User-Agent**: `Graph.kt` identifies the app to the wiki with a GitHub URL — point it at your real repo.
- **Signing**: replace the debug signing config in `app/build.gradle.kts` with your own keystore for a real release.
