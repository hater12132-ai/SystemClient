# Monochrome glass UI – what changed

**Palette:** fully monochrome (near-black + off-white). No blue/green left in the launcher or the in-game overlay.
Default accent = off-white; any user-chosen accent still works (text on accent is now contrast-aware).

**Main screen:** left = Mods + Misc, centre = animated hero + LAUNCH, right = Content tiles.
All original view IDs/types unchanged; new ids: `hero_art`, `ambient_backdrop`.

**LAUNCH button:** flat wide button, centred bold uppercase text, no icon (as in the reference), but black
"liquid glass": `ui/views/LiquidGlassDrawable.java` draws a smoked-black body, top sheen, specular highlight and a
diagonal rim light (idea from dashersw/liquid-glass-js). Click/enable logic untouched; disabled state dims.

**Hero:** your GIF converted to a transparent animated WebP (`res/raw/hero_anim.webp`, 161 frames, 30 ms, white on
transparent so it sits on the glass UI). Loaded with ImageDecoder, starts on resume, stops on stop.
`drawable-nodpi/art_hero_static.png` is the fallback first frame.

**Backdrop:** `ui/views/AmbientGradientView.java` – three slow drifting soft light fields (idea from ruucm/shadergradient),
~30 fps, pauses when hidden / animations disabled; hidden automatically if a custom background image is set.

**White icons:** all card/nav/instance icons are tinted with `@color/primary` (off-white, follows accent).

**Java edits:** ThemeManager default Dark · default accent off-white · `PersonalizationManager.contrastOn()` used at 17 sites
that forced white text on accent buttons · MainActivity (glass button, hero, lifecycle) · BaseActivity (selected tab) ·
overlay default accent colors.

Not compiled here (no Android SDK). APK grows ~3 MB (hero animation).

## Round 2 fixes
- Hero animation toned down (view alpha 0.55; smaller on tablets via `@dimen/hero_scale`, values-sw600dp).
- Nav logo is now `drawable-nodpi/ic_systemclient_logo.png` (your icon).
- Tip line replaced by a low-opacity watermark: icon + pixel "SystemClient" wordmark (`wm_systemclient.png`,
  blocky Minecraft-style pixel letters with drop shadow; no Minecraft font file exists in the project).
  `tip_text` is still in the layout (hidden) because MainActivity keeps rotating its text.
- Top dark band removed: the drifting glow is now created in `BaseActivity` behind nav + content (id `ambient_backdrop`
  in values/ids.xml), the nav bar has no fill and its divider is hidden.
- Fixed floating squares spilling over the Mods/Content cards (squares art is now square-aspect and clipped).
