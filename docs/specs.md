1. Problem
## Problem

Recently, I received a call from a Zamtel number. The caller used a "prize" story and asked me to read back an SMS code. They had triggered a genuine verification OTP to my phone, which made the story look credible. Because I understand how OTPs work, I refused, hung up, and reported the number to ZICTA via *707#.

This attack doesn't break into the carrier's network. The scammer starts a real login, PIN reset, or account recovery using the victim's number, then uses a phone call to talk the victim into handing over the code. The weakest link is the human, not the cryptography.

Many people, especially mobile money users who don't know how OTPs work, don't realise that the code is the key to their account. A real OTP arriving mid-call feels like proof that the caller is legitimate. Nothing warns the user at the moment it happens.

2. Features

Detect: spot an OTP-style SMS arriving during a call with an unknown number.
Warn: full-screen alert telling the user not to read the code aloud.
Report: one tap sends the caller's number to a backend, with a link to ZICTA's *707# reporting.

3. Out of scope

No Play Store release
No message content sent to any server
No call blocking or recording