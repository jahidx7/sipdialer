# NoxSIP Lite (Android)

A native Android SIP calling app built with Kotlin, Jetpack Compose (Material 3) and the
[Linphone SDK](https://linphone.org/). It plugs into the phone's own dialer (SIM 1 / SIM 2 / SIP chooser),
can act as the default dialer, and keeps SIP accounts registered in the background for reliable incoming calls.

> বাংলা: এটি একটি Android SIP ডায়ালার অ্যাপ। ফোনের ডায়ালারে SIM 1 / SIM 2 / SIP বেছে নিয়ে কল করা যায়।

NoxSIP Lite does **not** read or send SMS and has **no cloud backup**. Messages open in the phone's own messaging app,
and backups are local, password-protected files.

## Features

- **SIP calling** (Linphone SDK 5.3): UDP/TCP/TLS registration, mute, speaker, hold, DTMF, auto re-registration.
- **Multiple SIP accounts**: add, edit, delete, enable/disable; the selected account is used for outgoing calls.
- **Dialer integration**: default-dialer role, SIM 1 / SIM 2 / SIP chooser when dialing, SIM call screen.
- **Local Push to Talk**: discover NoxSIP devices on the same Wi-Fi and stream live voice while the talk button is held. No voice recording is saved; each device must keep the PTT screen open. Router client isolation or blocked Wi-Fi multicast can prevent discovery.
- **Reliable incoming calls**: foreground service, wake locks, full-screen incoming call UI on the lock screen.
- **Auto Answer**: optional setting for incoming SIP calls; disabled by default and applied after block and call-forwarding rules. A selected music/audio file is normalized to WAV and streamed to the caller after the call connects.
- **Audio quality**: G.711 (PCMA/PCMU) by default, optional HD codecs (Opus / G.722), mic boost, DSCP marking,
  live call quality stats and a per-call quality summary in call details.
- **Connection Health** (Settings): current network and SIP endpoint, last registration attempt/success,
  retry timing, last error and manual re-register action.
- **SIP endpoint failover**: optional backup SIP server per account. Temporary primary failures switch to the
  backup; the app probes the primary again after five minutes and on a network change. Both endpoints must
  support the same SIP account.
- **Incoming-call push readiness**: push is not enabled by default. RFC 8599 and an FCM push gateway must be
  configured on the SIP service before push delivery can be enabled; the app continues using its foreground
  SIP service until then.
- **Call recording**: automatic, saved to phone storage.
- **Contacts and call log**: phone contacts, Client / Office contacts, blocked numbers.
- **Speed dial favorites**: star numbers from Recents and call them from the dialer with one tap.
- **Local backup and restore** (Settings → User Account): SIP accounts, call log and saved contacts in one
  password-protected file (AES-256-GCM, PBKDF2). Call recordings are not included.

## Tech stack

Kotlin · Jetpack Compose (Material 3) · Linphone SDK · Room · DataStore · Android Telecom
(ConnectionService, InCallService, CallRedirectionService) · Coroutines/Flow

## Build

Requirements: JDK 17, Android SDK (compileSdk 35), Android Studio or the Gradle wrapper.

```bash
# debug build
./gradlew assembleLiteDebug

# release build (see Signing below)
./gradlew assembleLiteRelease
```

APKs are written to `app/build/outputs/apk/`.

### Signing (release)

1. Create your own keystore (for example with `keytool -genkeypair`).
2. Copy `keystore.properties.example` to `keystore.properties` and fill in your values.

Without `keystore.properties` the release build is produced unsigned. The keystore and `keystore.properties`
are git-ignored. Never commit them.

## Permissions (why)

| Permission | Used for |
| --- | --- |
| `RECORD_AUDIO`, `MODIFY_AUDIO_SETTINGS` | Calls and call recording |
| `CHANGE_WIFI_MULTICAST_STATE` | Discover NoxSIP peers and deliver live audio on the same Wi-Fi for Push to Talk |
| `READ_PHONE_STATE`, `CALL_PHONE`, `MANAGE_OWN_CALLS`, `READ_PHONE_NUMBERS` | Telecom integration, SIM list |
| `READ_CONTACTS`, `READ_CALL_LOG` | Contact names and showing SIM calls in the call list |
| `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT` | Incoming-call popup / lock-screen call UI |
| `FOREGROUND_SERVICE*`, `WAKE_LOCK`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`, exact alarms | Keeping SIP registered in the background |
| `POST_NOTIFICATIONS` | Call notifications |
| `REQUEST_INSTALL_PACKAGES` | Optional self-update installer (disabled in Lite: no update URL is configured) |

On some phones (for example Vivo or Xiaomi) sideloaded apps must be allowed through
*App info → ⋮ → Allow restricted settings* before some permissions can be granted.

## Project structure

```
app/src/main/java/com/noxsip/app/
├── MainActivity.kt          # navigation, wiring
├── SipDialerApplication.kt  # Linphone core setup, audio config
├── sip/                     # SipEngine, call state, quality stats
├── telecom/                 # ConnectionService, call redirection, popups
├── service/                 # foreground service, keep-alive
├── receiver/                # boot receiver
├── data/                    # Room (call history, contacts), DataStore (accounts), BackupManager
├── util/                    # helpers (recording, battery, setup checklist)
└── ui/                      # Compose screens, components, theme
app/src/lite/                # Lite flavor: default-dialer InCallService, SIM call screen
```

## Privacy and security

- SIP passwords are stored encrypted with the Android Keystore.
- The app talks only to the SIP servers you configure. There is no analytics and no cloud service.
- Do not post logs or backup files in issues: they can contain account details.

## License

NoxSIP Lite links against the Linphone SDK, which is licensed under the **GNU GPL v3**. This project is therefore
distributed under the **GNU General Public License v3.0 or later**. See [LICENSE](LICENSE).

Other dependencies (AndroidX, Jetpack Compose, Kotlin, Room, DataStore, kotlinx.coroutines) are Apache-2.0.
