# Echo — AI voice recorder

Native Android (Kotlin + Jetpack Compose).

- **Reliable recording**: foreground service, so recording continues through screen lock, home-screen and app swipe-away.
- **Live notification**: running timer, live voice-flow waveform, Pause / Bookmark / Stop actions.
- **Haptics + sensing**: tactile feedback on every action; shake the phone to drop a bookmark.
- **AI always on**: local smart tags and suggestions with no setup; add OpenAI + Anthropic keys in Settings for transcripts, summaries, titles and action items.
- **Auto build & update**: GitHub Actions builds a signed APK on every push and publishes a rolling `latest` release from `main`; the app shows an update banner.

Build locally: `cd echo-app && gradle assembleRelease`.
