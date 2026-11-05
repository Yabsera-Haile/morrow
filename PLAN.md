# Morrow — product plan

*Working name. A private, offline daily diary built around one loop:*
**morning goals → evening check-in → plans for tomorrow → transfer next morning**, plus a weekly review.

Design principles, in priority order:

1. **Privacy**: entries never leave the phone unless you export a backup yourself.
2. **No friction**: open, type, done. No save buttons, no forms, no wizards.
3. **Calm and warm look**: it should feel like a paper journal, not a productivity dashboard.

---

## 1. Decisions so far

| Topic | Decision |
|---|---|
| Platform | Android only |
| Storage | On the phone only, encrypted. Manual **encrypted backup file** you export/import yourself |
| Internet | The app has **no internet permission**, so it can't send data anywhere |
| Look | "Warm paper": cream paper, ink text, terracotta accent, serif headings. Warm charcoal dark mode |
| Extras | Streaks & weekly stats (can be hidden in Settings) |
| Not in v1 | Home-screen widget, typing in notifications, cloud sync, photos, tags, mood tracking |
| Starter PIN | **1234**. Settings shows a reminder until you change it |
| Morrow PIN | Separate from the phone's screen-lock PIN, 4–8 digits |
| Fingerprint | Optional, with an on/off switch in Settings. It uses the fingerprints already saved on the phone |
| Daily quote | One quote a day, shown large on Today and on the lock screen, rotating through 9 quotes (list in section 2) |

---

## 2. The daily loop

### Morning: set goals
- The morning reminder opens the app → fingerprint → **Today** with the keyboard already up.
- A **"From last night"** card sits above the goal list:
  - the plans you wrote last evening, each with a **+** to add it as a goal,
  - **Transfer all** adds every plan at once, with **Undo**,
  - a small "Not done yesterday" line for goals you didn't finish and didn't already carry over.
- Typing a goal + **Enter** adds it and keeps the cursor in the box for the next one. Pasting several lines creates several goals.
- If a weekly focus is set, it shows as a small chip under the date ("This week: …").

### During the day
- Tap a goal's circle to mark it done at any time (light haptic tick).
- The evening section stays collapsed: *"Evening check-in · 9:30 PM — Start now"*.

### Evening: check in
- The evening reminder opens Today in **evening mode**, a single scrolling page:
  1. **Goals**: tap to mark done. Unticked = not done. Each not-done goal has a **→ Tomorrow** button that copies it into tomorrow's plans.
  2. **Notes**: free text, as long or short as you like.
  3. **Plans for tomorrow**: a list, same Enter-to-add behaviour.
- A **Done for today** button finishes the check-in (this is what counts for the streak). Everything is saved as you type, so leaving early loses nothing.

### Daily quote
- After the date, the day's quote is the biggest thing on Today, so you read it every time you open the app. It also shows on the lock screen while you unlock.
- It stays the same all day. The next day moves on to the next quote in the list.
- Starting list:
  1. "Admire, then aim."
  2. "I'm not late. I'm on my own clock."
  3. "Slow is still forward."
  4. "Unfinished isn't failed. It's carried over."
  5. "Plan it at night. Live it by day."
  6. "Rest is part of the plan."
  7. "One thing done beats five things maybe."
  8. "My pace is not a delay."
  9. "Today is a draft. Tomorrow I edit."
- In Settings → Display you can turn the quote off or edit the list (add, remove or reorder).
- Quotes are stored outside the encrypted diary, because the lock screen has to show one before you unlock. Don't put anything private in a quote.

### Edge cases
- **Late nights**: a "Day starts at" setting (default **4:00 AM**) means a check-in at 1 AM still belongs to the previous day.
- **Missed evening**: the morning card shows unfinished goals from the most recent day you used.
- **Several days skipped**: the card only looks at the most recent day, so there's no backlog to clear.
- **Editing the past**: any day can be opened from the Journal and edited.

---

## 3. Weekly review

Opened from the weekly reminder, a card on Today on review day, or the Journal.

- **Stats** (hideable): goals done %, check-ins (e.g. 6/7), current streak, and a 7-day bar strip.
- **Wins**: goals you completed this week (collapsed list).
- **Still open**: unfinished goals, each with **+ Next week** (added to Monday's plans).
- **Reflection**: *What went well?* · *What got in the way?*
- **Focus for next week**: up to 3 short items, shown as chips on Today all next week.
- **Finish review**.

Defaults: the week runs Monday–Sunday and the review is due **Sunday evening**. Both can be changed.

---

## 4. Reminders (all times set by you)

| Reminder | Default | Opens |
|---|---|---|
| Morning goals | 7:00 AM daily | Today, keyboard ready |
| Evening check-in | 9:30 PM daily | Today in evening mode |
| Weekly review | Sunday 7:00 PM | Week review |

- Each reminder can be turned on/off, and you can pick which days the daily ones run (e.g. weekdays only).
- **Smart skip** (on by default): no morning reminder if you already wrote goals, and no evening reminder if you already checked in.
- Reminder text is always generic ("Time to set today's goals"). **Your diary content never appears in a notification** or on the lock screen.
- Reminders are rescheduled after a reboot, a time change or a timezone change.

---

## 5. Lock & privacy

- **Morrow PIN**: the app has its own PIN, separate from your phone's screen lock. It's 4–8 digits and starts as 1234. The app can't see your phone's PIN, so it can't check that the two are different, but the Change PIN screen reminds you not to reuse it.
- **Fingerprint unlock (optional)**: on by default, with an on/off switch in Settings. Android doesn't let apps have their own fingerprints, so this uses the ones already saved on your phone.
  - Turn it off and only the Morrow PIN opens the app.
  - Turning it off also deletes the fingerprint key, so it really is off, not just hidden.
- **Unlock flow**: with fingerprint on, the fingerprint prompt appears automatically and the Morrow PIN is the fallback. With it off, you just see the PIN pad.
- **Your phone's PIN never opens Morrow**: there's no "use phone PIN" fallback, only the Morrow PIN.
- **New fingerprint on the phone**: if anyone adds a fingerprint to the phone, Morrow's fingerprint unlock switches off until you enter your Morrow PIN.
- **Auto-lock** after the app has been in the background for: immediately / 30 s / **1 min** (default) / 5 min.
- **Wrong PIN**: after 5 wrong tries you have to wait 30 s, and the wait gets longer with each further miss.
- **Forgot PIN**: if fingerprint unlock is on, use it to get in, then set a new PIN.
- **Hidden from Recents**: the app shows a blank card in the app switcher, and screenshots are blocked (on by default, can be turned off).
- **Real encryption, not just a lock screen**: the database is encrypted with a random key. That key is stored only in wrapped form, unlockable by your PIN or your fingerprint, and it's protected by the phone's secure hardware.
  > Trade-off: if you forget your PIN *and* your fingerprint stops working (for example after a factory reset), nobody can recover your entries. The only way back is a backup file.
- **No Android cloud backup**: system auto-backup is switched off, so nothing copies itself to Google.

### Backup
- **Export**: choose a backup password, then save a single encrypted file (`morrow-backup-2026-11-05.morrow`) anywhere: Downloads, a USB drive, or the Google Drive app. The file can't be read without the password.
- **Import**: pick the file and enter its password. Your current data gets **replaced**, after a confirmation.
- Settings shows "Last backup: 12 days ago", and the weekly review nudges you once a month if you haven't exported a backup.

---

## 6. Screens

| Screen | Purpose |
|---|---|
| **Lock** | Fingerprint prompt plus PIN pad |
| **Today** (home) | Morning mode / day mode / evening mode of the same page |
| **Week review** | Stats, wins, still open, reflection, next week's focus |
| **Journal** | Month calendar with dots (● checked in, ○ goals only), search across goals/notes/plans, tap a day to read or edit |
| **Settings** | Reminders · Security · Day & week · Display · Backup · About |
| **First run** | One welcome screen → notification permission → confirm reminder times → enable fingerprint |

There's no bottom tab bar. Today is the app: the Journal and Settings open from two small icons in its header, so the screen stays quiet.

Interactions: Enter adds the next item · paste splits lines · tap text to edit · swipe left to delete (with Undo) · long-press and drag to reorder.

---

## 7. Data model

```
day          date (PK, YYYY-MM-DD) · note · checkin_done_at · created_at · updated_at
goal         id · date → day · text · position · done · origin (manual | plan | carried) · created_at
plan         id · written_on → day · text · position · transferred_to_goal_id (nullable)
week_review  week_start (PK) · went_well · got_in_way · completed_at
week_focus   id · for_week (the week it shows on Today) · text · position
```

Settings (reminder times, theme, the quote list, etc.) live in a separate small preferences store. It holds the only things readable while the app is locked, and none of them is diary text:
- the quote list, so the lock screen can show today's quote
- *"goals written for date X"* and *"check-in done for date X"*, which smart skip needs

---

## 8. Milestones

1. ✅ **Foundation**: project setup, theme and fonts, navigation, encrypted database, data model
2. **Lock**: PIN pad, fingerprint, auto-lock, hidden from Recents, starter PIN
3. **Morning**: goals list (add / edit / reorder / delete), "From last night" transfer card, daily quote
4. **Evening**: check-off, → Tomorrow, notes, plans, Done for today, autosave
5. **Reminders**: settings screen, scheduled reminders, deep links, smart skip, reschedule on reboot
6. **Week review**: stats, streaks, reflection, next week's focus
7. **Journal**: calendar, search, viewing and editing past days
8. **Backup**: encrypted export/import
9. **Polish**: dark mode, motion and haptics, accessibility pass, signed release APK installed on your phone

---

## 9. Design

Screens (most of them clickable): https://claude.ai/artifact/CsUrDTZR1n8KKZuvsufABy
Lock · Today (morning) · Today (evening) · Evening in dark mode · Week review · Journal · Settings · Reminders

### Design tokens

| Token | Light | Dark |
|---|---|---|
| Paper (background) | `#F6F1E7` | `#1C1A17` |
| Card | `#EFE7D9` | `#2A2622` |
| Input surface | `#FCFAF5` | `#26231F` |
| Hairline | `#E2D8C8` | `#3A352F` |
| Ink (text) | `#2A2520` | `#EDE6DA` |
| Muted text | `#6B6158` | `#A89E91` |
| Accent (terracotta) | `#A8492A` | `#E08A64` |
| Done (sage) | `#4F6B4A` | `#8FB088` |
| Empty circle | `#8A7F73` | `#7D746A` |

All text colours pass WCAG AA (4.5:1) on their backgrounds. Done and not-done are told apart by shape (filled check vs. empty ring), not by colour alone.

- **Type**: Fraunces (serif) for headings, dates, greetings and numerals; Inter for everything else. Both fonts are bundled in the app.
- **Shape**: 14–18 px corner radius on cards and buttons, fully rounded chips. Touch targets are at least 44 px.
- **Tone**: short, warm and second-person ("Good evening. How did today go?"). No exclamation marks, no guilt.

---

## 10. Stack

### Choice: native Android with Kotlin + Jetpack Compose

Since the app is Android-only, going native means:
- the most reliable reminders on Android,
- direct access to the phone's secure hardware for the fingerprint and the encryption keys,
- the fewest third-party libraries, which keeps the privacy surface small.

| Option | Why not |
|---|---|
| Flutter | Very good UI, but fingerprint, alarms and keystore all go through plugins. That's an extra layer and extra dependencies for no benefit when there's only one platform |
| React Native / Expo | Same plugin problem, a heavier runtime, and a large JavaScript dependency tree to trust |
| Web app (PWA) | Can't do reliable scheduled notifications, fingerprint unlock or hardware-backed encryption |

### Building blocks

| Need | Choice |
|---|---|
| Language & UI | Kotlin, Jetpack Compose, Material 3 with a custom "warm paper" theme |
| Structure | One activity, Navigation Compose, ViewModel + StateFlow (state flows one way), simple manual dependency wiring. No DI framework is needed for an app this size |
| Database | Room on **SQLCipher for Android**: the whole database is encrypted with AES-256 |
| Keys | **Android Keystore** (hardware-backed on modern phones) |
| Fingerprint | `androidx.biometric` BiometricPrompt (strong biometrics only) with a CryptoObject, so the fingerprint actually unlocks the encryption key and isn't just a yes/no gate |
| PIN | PBKDF2-HMAC-SHA256 (built into Android, no extra library) |
| Preferences | Jetpack DataStore |
| Reminders | AlarmManager exact alarms + NotificationCompat. Receivers for boot, time-change and timezone-change re-arm them |
| Backup | Android's own "save / open file" picker (Storage Access Framework) + AES-256-GCM, with the key derived from the backup password |
| Dates | `java.time` |
| Tests | JUnit + Turbine for the logic (transfer, streaks, day boundary, week stats). Compose UI tests for the main flows. On-device tests for the encryption |
| Build | Gradle 9.8 (Kotlin DSL) with a version catalog, AGP 9.5, Kotlin 2.4. **minSdk 26** (Android 8.0), compileSdk 37, targetSdk 36 |

### How the encryption fits together

```
random 256-bit database key (DBK)
 ├─ wrapped by the PIN:   PBKDF2(PIN) → AES-GCM, then wrapped again by a Keystore key
 │                        (so a copied file can't be brute-forced off the phone)
 └─ wrapped by the fingerprint (only while fingerprint unlock is on): a Keystore key that needs a strong biometric,
                          and is invalidated if a new fingerprint is enrolled
```
- **Changing the PIN** re-wraps the key. Your entries aren't re-encrypted, so it's instant.
- **Locking** closes the database and wipes the key from memory.

### Permissions (the complete list)

- `POST_NOTIFICATIONS`: show reminders
- `USE_EXACT_ALARM`, plus `SCHEDULE_EXACT_ALARM` for Android 12 only: reminders on time
- `RECEIVE_BOOT_COMPLETED`: re-arm reminders after a restart
- `USE_BIOMETRIC`: fingerprint unlock

**No `INTERNET` permission.** The build will fail if any library tries to add it.

### Code layout

```
app/src/main/java/…/morrow/
  ui/        theme · lock · today · week · journal · settings · onboarding
  domain/    DayClock (4 AM boundary) · TransferPlans · StreakCalculator · WeekStats
  data/      Room entities + DAOs · repositories · DataStore prefs
  security/  KeyVault (wrap/unwrap) · PinLock · BiometricUnlock · AutoLock
  reminders/ ReminderScheduler · receivers · notifications · deep links
  backup/    BackupExporter · BackupImporter (encrypted file format)
```

### Getting it on your phone

- **Tools**: Android Studio and JDK 17 are already on this PC.
- **Build**: a signed release APK.
- **Install**: either over USB (turn on Developer options → USB debugging) or by copying the APK to the phone and opening it. The second way needs "Install unknown apps" allowed for your file manager.
- **Keep the signing key safe.** Every future update has to be signed with the same key. If the key is lost, the only way to update is to uninstall, which deletes your diary unless you exported a backup first.
- **Reminders on some phones**: brands like Samsung and Xiaomi can delay reminders to save battery. Settings links straight to the screen where you exempt Morrow.
