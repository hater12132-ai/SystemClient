# Glass UI redesign – what changed

**Look:** dark ink base + soft blue/violet light orbs (`drawable[-night]/bg_app.xml`, set as window background),
frosted glass panels (`card_background`, `bg_glass_tile`, …), one blue accent, white type.

**Main screen (`activity_main.xml`)**: left = Mods + Misc, centre = hero cube + title + instance chip + LAUNCH,
right = Content tiles. All 24 view IDs and widget types are unchanged (ViewBinding contract preserved).
Launch logic untouched: `launch_button` is still a `Button`, `launchGame()` / `setEnabled(...)` code is unmodified.

**App-wide:** nav bar glass + selected-tab pill, popups/dialogs restyled, other screens' root backgrounds made
transparent so the mesh shows through. Splash / crash / loading screens keep their solid background.

**Java (4 tiny edits):** ThemeManager default = Dark · PersonalizationManager default accent = blue ·
BaseActivity sets `tab.setSelected()` for the nav pill · MainActivity: pill-shaped "Manage" button, hero title no longer accent-tinted.

Light / follow-system themes are still selectable in Settings but were not redesigned (they get the new blue accent and light glass tokens only).
Not compiled here (no Android SDK) – please build and check on a device.
