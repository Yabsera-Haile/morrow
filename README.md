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

## Layout

```
app/src/main/java/app/morrow/diary/
  ui/        theme · icons · today · journal · week · settings · navigation
  domain/    DayClock (4 AM day boundary) · QuoteOfTheDay
  data/      Room database (SQLCipher-encrypted) · DAOs · settings store · DiaryRepository
  security/  KeyVault (database key, wrapped by the Android Keystore)
app/schemas/ Room schema history, for future migrations
```
