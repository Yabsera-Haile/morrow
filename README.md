# Morrow

A private, offline Android diary: morning goals, evening check-in, plans for tomorrow, and a weekly review.
See [PLAN.md](PLAN.md) for the full plan, design and stack.

## Build

Needs Android Studio, or JDK 17+ and the Android SDK with platform 37.0 installed.

```
gradlew assembleDebug            # debug APK
gradlew testDebugUnitTest        # logic tests (no phone needed)
gradlew connectedDebugAndroidTest   # encryption tests, on a phone or emulator
```

Build output goes to `%LOCALAPPDATA%\MorrowBuild` instead of the project folder, so OneDrive doesn't sync it.
The debug APK ends up in `%LOCALAPPDATA%\MorrowBuild\app\outputs\apk\debug\app-debug.apk`.

Every build also runs `verify<Variant>NoNetwork`, which fails if any network permission ends up in the app.

Debug builds have a **Developer** section at the bottom of Settings with "Add sample days", which fills
the last three days with goals, plans and notes so you can try "From last night". It lives in
`app/src/debug`; release builds get the empty twin in `app/src/release`, so it never ships.

## Layout

```
app/src/main/java/app/morrow/diary/
  ui/        theme · icons · lock (lock screen, PIN pad, change PIN, fingerprint prompts) ·
             today · journal · week · settings · navigation
  domain/    DayClock (4 AM day boundary) · QuoteOfTheDay
  data/      Room database (SQLCipher-encrypted) · DAOs · settings store · DiaryRepository · DiarySession
  security/  KeyVault (PIN and fingerprint slots for the database key) ·
             LockManager (unlock, auto-lock, wrong-PIN limits) · PinPolicy
app/schemas/ Room schema history, for future migrations
```
