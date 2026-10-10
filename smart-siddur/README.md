# סידור חכם — Smart Siddur (Android)

A Hebrew siddur for Android. It picks out the parts of each prayer that apply today
(Ya'aleh Veyavo, Al Hanisim, Tachanun, Hallel, the rain insertions, and so on) and includes
a Hebrew calendar. The text is Nusach Ashkenaz, and the app is set up so more nuschaot can be added later.

## Features

- **Prayers:** Shacharit, Mincha, Ma'ariv, Birkat Hamazon and Me'ein Shalosh (Al Hamichya + Borei Nefashot).
- **Hamburger menu** (right-side drawer, RTL) for choosing what's shown, built from `assets/siddur/index.json`.
- **Pinch to change text size** anywhere in a prayer; the size is saved. There's also a slider in Settings.
- **Smart mode:** the prayer is filtered for the current Hebrew day, and date-specific insertions are highlighted.
  A banner shows the Hebrew date and today's additions. Turn smart mode off to see every variant, with the ones
  not said today dimmed.
  - Ma'ariv after midday, and the brachot after sunset, count as the next Hebrew day. The *אחרי השקיעה* chip
    switches this by hand.
  - On launch the app opens the prayer that fits the time of day.
- **Section jump** menu inside each prayer.
- **Hebrew calendar:** a Hebrew month grid with Yom Tov, fasts, Rosh Chodesh and minor holidays color-coded.
  Tapping a day shows its details: Gregorian date, holidays, parsha and omer. The month's events are listed below
  the grid.
- **Dark / night mode:** follows the phone by default; choose light or dark in Settings, or tap the moon/sun icon in a prayer.
- **Settings:** nusach, theme, Israel/diaspora (this affects holidays, tal umatar and some customs), smart mode,
  long halachic notes, and text size.
- The screen stays on while a prayer is open.

## Building

Open the `smart-siddur` folder in Android Studio (Ladybug or newer), or run:

```sh
./gradlew assembleDebug        # APK in app/build/outputs/apk/debug/
./gradlew test                 # unit tests (logic + validation of the prayer files)
```

Stack: Kotlin, Jetpack Compose (Material 3), kotlinx.serialization and
[KosherJava zmanim](https://github.com/KosherJava/zmanim) for the Hebrew calendar and tefila rules.
minSdk 26, targetSdk 35.

## Project layout

```
app/src/main/
  assets/siddur/
    index.json               menu + list of nuschaot
    ashkenaz/*.json          one file per prayer
  java/com/smartsiddur/
    data/                    models, PrayerRepository (asset loading + nusach fallback), Settings
    logic/                   Condition (the "when" expressions), DayContext (day flags),
                             PrayerLayout (filtering), HebrewMonth (calendar), PrayerTime (sunset/rollover)
    ui/                      SiddurApp (drawer + screen registry), PrayerScreen, CalendarScreen, SettingsScreen
tools/build_content.py       regenerates the prayer JSON from the Sefaria export
```

## Extending

### Add a prayer

1. Create `app/src/main/assets/siddur/ashkenaz/<id>.json`:

   ```json
   {
     "id": "tefilat_haderech",
     "title": "תפילת הדרך",
     "service": null,
     "rollover": "none",
     "sections": [
       { "title": "תפילת הדרך", "level": 2, "items": [
         { "type": "text", "text": "<b>יְהִי רָצוֹן</b> ..." },
         { "type": "instruction", "text": "בשבת:", "when": "shabbat" }
       ]}
     ]
   }
   ```

   - `type` is `text` (prayer), `instruction` (short rubric) or `note` (long halachic note, hidden by default).
   - `text` may use `<b>`, `<small>` and `<big>`, plus newlines.
   - `rollover` is `none`, `noon` (Ma'ariv) or `sunset` (brachot). It decides which Hebrew day the prayer belongs to.
   - `service` is `shacharit`, `mincha`, `maariv` or `null`. It is used for day rules such as tachanun.
2. Add a menu entry to `assets/siddur/index.json`: `{ "type": "prayer", "id": "tefilat_haderech", "title": "תפילת הדרך" }`.

`./gradlew test` checks that every prayer in the menu loads and that every `when` expression parses and only
uses known flags.

### Add a nusach

1. Add it to `nuschaot` in `index.json`, for example `{ "id": "sefard", "title": "ספרד" }`. It then shows up in Settings.
2. Add `assets/siddur/sefard/<id>.json` files using the same prayer ids. Any prayer the new nusach doesn't have
   yet falls back to the default nusach, so you can add them one at a time.

### Add a new kind of screen

Register it in the `screens` map in `ui/SiddurApp.kt`, keyed by a new menu `type` (e.g. `"zmanim"`), then add
menu entries with that type.

### Conditions (`when`)

`when` is a boolean expression made of flags, `&`, `|`, `!` and parentheses, for example
`tachanun & (day_2 | day_5)`. A section or item whose condition is false is hidden in smart mode.
The flags are computed in `logic/DayContext.kt`:

| Flags | Meaning |
|---|---|
| `day_1` … `day_7`, `shabbat` | day of week (1 = Sunday) |
| `israel`, `chul` | location setting |
| `rosh_chodesh`, `chol_hamoed`, `yom_tov`, `chanukah`, `purim`, `fast_day`, `tisha_bav`, `aseret_yemei_teshuva` | the day |
| `pesach`, `shavuot`, `sukkot`, `shemini_atzeret`, `rosh_hashana`, `yom_kippur` | specific festivals (Pesach and Sukkot include Chol Hamoed) |
| `mashiv_haruach`, `morid_hatal`, `tal_umatar`, `vesen_beracha` | rain insertions |
| `yaaleh_veyavo`, `al_hanisim`, `hallel`, `hallel_shalem` | insertions |
| `tachanun` (for this service), `tachanun_day` (Shacharit rule, e.g. for Shir Hama'alot vs. Al Naharot) | tachanun |
| `avinu_malkenu`, `mizmor_letoda`, `lamenatzeach`, `torah_reading`, `ledavid` | other daily rules |
| `omer`, `omer_1` … `omer_49` | Sefirat HaOmer (the count for the coming night in Ma'ariv) |
| `motzaei_shabbat`, `motzaei_yom_tov`, `vihi_noam` | Ma'ariv after Shabbat or Yom Tov |

To add a flag, compute it in `DayContext.computeFlags` and add it to `KNOWN_FLAGS`.

## Prayer text

The Ashkenaz text comes from Sefaria's *Siddur Ashkenaz* export (the "merged" Hebrew version, mostly Torat Emet).
`tools/build_content.py` converts it and adds the `when` conditions. To regenerate the files:

```sh
python3 tools/build_content.py            # downloads the export, or pass a local merged.json path
```

Sefaria's merged version pulls from several source editions with different licenses. Check those licenses before
publishing the app.

### Not included yet

- Musaf for Chol Hamoed (a placeholder note appears on those days). Musaf for Rosh Chodesh is included.
- Shabbat and Yom Tov prayers, Birkat HaLevana and Kri'at Shema al HaMita. All of these exist in the Sefaria
  export, so they can be added through `build_content.py`.
