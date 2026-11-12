# CuteKind

A home-screen widget for long-distance couples. One of you draws, and the drawing shows up
in a widget on the other's phone, next to a daily question for the two of you.

Android, Kotlin, Jetpack Compose. No account and no server of your own needed.

| Home | Draw | Widget |
|---|---|---|
| ![Home](app/screenshots/03_home.png) | ![Draw](app/screenshots/07_draw.png) | ![Widget](app/screenshots/10_widget.png) |

## How it works

1. One partner taps **Start a new couple** and then **Share invite link**.
2. The other installs the app and taps the link, which opens the app with the code filled in.
   The code can also be typed by hand: on the first screen, on Home ("My partner sent me a code
   instead") or in Settings ("Join with my partner's code").
3. Draw something and tap **Send**. It appears in your partner's app and on their widget.

Sync goes through an [ntfy](https://ntfy.sh) relay (changeable in Settings, self-hosting works).
The couple code never leaves the phones: it derives both the relay topic and an AES-256-GCM key,
so the relay only sees encrypted text.

Delivery is instant: a small foreground service holds a live connection to the relay, so a
drawing reaches the partner's widget within seconds even when the app is closed. This shows a
silent permanent notification, and works best with battery optimisation turned off for the app
(the app asks once). A 15-minute background check runs as a backup.

## Invite links

The invite link is `https://saquelain.github.io/cutekind/join/#CODE`. The page behind it lives in
`docs/join` and needs GitHub Pages turned on once: repo **Settings → Pages → Deploy from a branch →
`/docs`**. The page has one button that opens the app and pairs. The code sits after `#`, so it is
never sent to the web server. If you fork or rename the repo, change `LINK_BASE` in `Wire.kt` and
the host in `AndroidManifest.xml`.

## Features

Every feature has its own switch in **Settings**:

Instant delivery, stickers (emoji, text and photo), save to gallery (a CuteKind album),
daily question, spicy questions (off by default), answer-to-reveal, question on widget,
caption on widget, days together, doodle streak, thinking-of-you hearts, stroke replay,
memories gallery, notifications, background sync.

## Build

```
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug
./gradlew recordRoborazziDebug   # run tests and re-render app/screenshots
```

GitHub Actions does both on every push and attaches the APK and the screenshots to the run.
