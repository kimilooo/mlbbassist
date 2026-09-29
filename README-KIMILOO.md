# MLBB Assist — Kimiloo Edition

This fork keeps the original overlay workflow and improves the Gemini scoreboard analysis for live counter-building.

## Changes
- Match-specific enemy threat ranking instead of generic recommendations.
- Reads visible items for the top two enemy threats.
- Labels threat type such as physical burst, magic burst, DPS, crit, sustain, heal, shield, CC, tank, or mixed.
- Returns an explicit `BUY FIRST` → `THEN` item order.
- Avoids recommending an item already visible in the player's current build.
- Uses `unclear` when an item icon cannot be identified confidently instead of guessing.
- Fixes the malformed JSON example in the original Gemini prompt.
- Adds a GitHub Actions workflow that builds a debug APK on every push to `main`.

Version: `1.2-kimiloo`
