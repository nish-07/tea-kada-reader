# Tea Kadai Reader: privacy notice (draft for the pilot)

This is a plain-language draft. Before a Google Play release it must be put on a public web page, and a lawyer should read it.

## What the app reads
Notifications from payment apps only: Google Pay, Google Pay for Business, PhonePe, Paytm, BHIM and Airtel Thanks.
The list is fixed in the app (`Allowlist.kt`). A notification from any other app (WhatsApp, messages, Gmail, news, games)
is dropped before its title or text is opened. The app does not have the SMS permission and does not read the SMS app or Gmail.

## What it keeps and sends
For a notification that says money came in, it takes three things and sends them to the shop's own server over HTTPS:
1. the amount,
2. the payer's name as the payment app shows it,
3. the name of the payment app.

The notification text itself is not saved on the phone, not sent, and not written to any log.
The last few payments are shown on the app screen from memory only and disappear when the app closes.
The server stores the same three things in a table of payments for the shop (with the time and which order it confirmed).

## What is stored on the phone
The server address, the shop name and the owner code, encrypted with a key held in the Android Keystore, plus the time you agreed.
Nothing is backed up to the cloud (`allowBackup=false`). Screenshots of the app are blocked.

## Permissions
Only INTERNET. Notification access is switched on by the owner in Android settings, after the owner taps Agree on the in-app explanation.

## Control
"Stop reading" withdraws permission. "Forget this shop" deletes everything stored on the phone, including the key.
The app works without the reader: customers tap "I've paid" and the owner taps "Mark paid".

## Sharing
Data is never sold, never shown to other shops, and never used for advertising. It is shared with nobody except the shop's own server.
