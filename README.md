# System Survey

Active Sensor Burst scans the objects around the player fleet. Scanning a star system fills a progress bar; at 100%
the system's theme is revealed, the remaining loot is listed in a survey intel entry, and the player earns experience.
In a fully surveyed system the Neutrino Detector shows no false readings (PatchLib patch on the vanilla ability).

- Build and deploy: `bash build.sh` (`--no-deploy` to build only). Depends on PatchLib.
- Live checks: `native-smoke/live`, groups `survey`, `neutrino` and `surveydata`.

---

# Planned feature: System Survey Data (Class I–V)

**Status: implemented. All open questions were approved with the planned defaults** (highest planet class, no data for core worlds, vanilla prices, vanilla icons, no retroactive data). Deviations from the plan are listed under "As built" at the end.

Filling the scan progress bar for a star system gives the player one unit of **System Survey Data** for that system.
There are five classes, Class I to Class V, modelled on vanilla's Class I–V Planetary Survey Data
(`survey_data_1` … `survey_data_5`). The class is picked on vanilla's own scale: the class vanilla would give the
best planet in the system.

Vanilla facts below were checked against this install (0.98.5a-RC4): `starsector-core/data/...` and
`starsector-core/starfarer.api.zip`.

## 1. How vanilla does Planetary Survey Data

### Commodity rows

`starsector-core/data/campaign/commodities.csv`, lines 32–37:

| name | id | demand class | base price | utility | tags | icon | order |
|---|---|---|---|---|---|---|---|
| Survey Data | `survey_data` | `survey_data` | 1000 | 1 | `nonecon, hide_in_codex` | `survey_data.png` | 50 |
| Class I Survey Data | `survey_data_1` | `survey_data` | *(blank)* | 1 | `nonecon, no_loss_from_combat` | `survey_data1.png` | 45 |
| Class II Survey Data | `survey_data_2` | `survey_data` | *(blank)* | 3 | same | `survey_data2.png` | 46 |
| Class III Survey Data | `survey_data_3` | `survey_data` | *(blank)* | 5 | same | `survey_data3.png` | 47 |
| Class IV Survey Data | `survey_data_4` | `survey_data` | *(blank)* | 10 | same | `survey_data4.png` | 48 |
| Class V Survey Data | `survey_data_5` | `survey_data` | *(blank)* | 30 | same | `survey_data5.png` | 49 |

All rows: price variability 0, stack size 1000, cargo space 1, sounds `ui_chip_pickup` / `ui_modspec_drop`,
icon width mult 1. Icons are in `graphics/icons/cargo/`.

**Where the price comes from.** The class rows leave `base price` blank. When the game loads commodities
(`com.fs.starfarer.loading.SpecStore`, the commodity loader), every commodity whose id is not its own demand class
gets its price from the demand-class row:

```java
// translation of obfuscated code, SpecStore commodity loading
if (!spec.isPrimary()) {
    CommoditySpec demand = get(CommoditySpec.class, spec.getDemandClass());
    if (demand != null) {
        spec.setBasePrice(demand.getBasePrice() * spec.getUtility() / demand.getUtility());
    }
}
```

So Class I–V cost 1000 × utility: **1,000 / 3,000 / 5,000 / 10,000 / 30,000** credits. This runs after the
`base price` column is read, so for a row whose demand class is `survey_data` a value typed in `base price` is
overwritten; the price is set through `utility`. `settings.json` notes that survey data has price variability 0, so
the price is flat, and sells with `nonEconItemSellPriceMult` (0.8).

**Descriptions** are in `data/strings/descriptions.csv` (type `RESOURCE`): one shared TriPad paragraph, then one line
per class, from "'Just another rock', as they say" (I) to "an exceptional world ... of great value to any of the major
factions" (V).

### How a planet's class is chosen

`com.fs.starfarer.api.impl.campaign.SurveyPluginImpl.getSurveyDataType(PlanetAPI)` (the `surveyPlugin` in
`data/config/settings.json`). Identical in 0.98a-RC8 and 0.98.5a-RC4; only the survey costs changed between them.

```java
public String getSurveyDataType(PlanetAPI planet) {
    if (planet.getMarket() == null) return null;

    int count = 0;
    float value = 0;
    for (MarketConditionAPI mc : planet.getMarket().getConditions()) {
        if (DerelictThemeGenerator.interestingConditionsWithRuins.contains(mc.getId())) {
            count++;
        }
        if (mc.getGenSpec() != null) {
            value += mc.getGenSpec().getRank();
        }
    }

    if (planet.getMarket().hasCondition(Conditions.HABITABLE)) {
        value += 4f;
    }

    float hazard = planet.getMarket().getHazardValue();
    value -= (hazard - 1f) * 2f;

    if (value <= 5) return Commodities.SURVEY_DATA_1;
    if (value <= 8) return Commodities.SURVEY_DATA_2;
    if (value <= 11 && count <= 1) return Commodities.SURVEY_DATA_3;
    if (value <= 14 && count <= 2) return Commodities.SURVEY_DATA_4;
    return Commodities.SURVEY_DATA_5;
}
```

- `value` is the sum of each condition's `rank` from `data/campaign/procgen/condition_gen_data.csv`, plus 4 if
  habitable, minus 2 per 100% hazard above 100%.
- `count` is how many of the planet's conditions are in `DerelictThemeGenerator.interestingConditionsWithRuins`:
  plentiful volatiles, rich and ultrarich rare ore, ultrarich ore, bountiful and adequate farmland, plentiful
  organics, habitable, vast ruins and extensive ruins. A planet with many of these is pushed up to Class IV or V even
  at a lower value.
- It reads the market's full condition list whatever the planet's survey level, so it works on unsurveyed planets.
  Vanilla calls it the same way before a survey in `RemoteSurveyDataForPlanetIntel.pickMinClass()` and
  `DerelictThemeGenerator` (which uses it to find Class IV/V planets for Domain survey data hints).

Vanilla gets the plugin with
`(SurveyPlugin) Global.getSettings().getNewPluginInstance("surveyPlugin")`
(`RemoteSurveyDataForPlanetIntel.java`, `PlaythroughLog.java`), so another mod that replaces the survey plugin
changes our classes too.

## 2. From planets to a whole system

**Rule: a system's class is the highest vanilla class of any of its eligible planets.**

```
eligible planet = not a star, not a nebula centre, has a market, market.isPlanetConditionMarketOnly()
system class    = max over eligible planets of getSurveyDataType(planet)   (I=1 ... V=5)
no eligible planet -> Class I
```

Why this rule:

- It uses vanilla's scale without changing it. A Class V system is a system that holds a Class V world, the thing
  that makes a system worth coming back to, so the class tells the player what the system is worth.
- The rarity follows vanilla's own. Taking the best planet raises the odds a little for systems with many planets,
  as it should: a full system is a better find.
- `isPlanetConditionMarketOnly()` is the test vanilla itself uses in `DerelictThemeGenerator` to skip inhabited
  planets. It leaves out colonies (the core worlds and the player's own), which are already charted.

Rejected: averaging the planet classes. A system with one Class V world and four barren rocks would come out as
Class II, which hides the one fact that matters.

**Core worlds.** Systems tagged `theme_core` (`Tags.THEME_CORE`, also `_populated` / `_unpopulated`) are charted
space; they give **no** survey data, though the survey still completes with its theme, loot and XP as today.
Every other system gives at least Class I. *(Open question, see the end.)*

The class is computed **once, when the survey completes**, and stored. It does not change later if a planet is
colonized or decivilized.

### Themes and notable objects (added after the first build)

The class is the **highest of three sources**: the best planet (above), the system's themes, and its notable objects.
Both tables are in `data/config/settings.json`, so other mods can add their own themes and objects by JSON merge.

| `systemsurvey_dataClassByTag` (system tag) | class | `systemsurvey_dataClassByEntity` (custom entity type) | class |
|---|---|---|---|
| `theme_remnant_main` (Remnant stronghold) | V | `derelict_mothership` | V |
| `theme_derelict_mothership` | V | `derelict_cryosleeper` | V |
| `theme_derelict_cryosleeper` | V | `coronal_tap` (hypershunt) | V |
| `has_coronal_tap` | V | `derelict_gatehauler` | V |
| `theme_special` | IV | `derelict_survey_ship` | III |
| `theme_remnant_secondary` | III | `inactive_gate` | III |
| `theme_derelict_survey_ship` | III | `station_research_remnant` | III |
| `theme_ruins_main` | III | `orbital_habitat_remnant`, `station_mining_remnant` | II |
| `theme_derelict_probes`, `theme_ruins_secondary` | II | `derelict_probe` | II |

- Tags are what the system was generated as. Objects count while they are still alive in the system, which also
  catches objects placed outside the themes (by quests or other mods).
- Remnant tags do not count once the system has `theme_remnant_destroyed`.
- The source that set the class is kept as the reason: a theme title ("Remnant stronghold"), an object name or a
  planet name. On a tie the theme names it, then the object. It shows in the pickup message
  ("Received Class V System Survey Data (Cryosleeper)") and in the intel ("Survey data: Class V, for Cryosleeper"),
  stored in a new nullable `SystemSurveyIntel.dataReason` field.
- API: `SurveyData.getSurveyDataInfo(system)` returns `DataClass {cls, reason}`; `getSurveyDataClass` still returns
  the commodity id.
- Result on the same generated sector (179 non-core systems), live run 85/85: Class I 18.4%, II 22.9%, III 30.2%,
  IV 12.3%, V 16.2% (planets only, before: 38.5 / 19.0 / 20.7 / 13.4 / 8.4). 61 systems were raised above their best
  planet, most by Survey probes (18 to II) and Remnant outposts (12 to III); Class V came from Remnant strongholds 7,
  motherships 2, cryosleepers 2, hypershunts 2, the Gate Hauler 1.

## 3. New commodities

### `data/campaign/commodities.csv` (new file in the mod; rows are added to vanilla's by id)

Same columns as vanilla. Ids carry the mod prefix so they never collide with vanilla or other mods:

| name | id | demand class | base price | utility | tags | icon | order |
|---|---|---|---|---|---|---|---|
| Class I System Survey Data | `systemsurvey_data_1` | `survey_data` | *(blank)* | 1 | `nonecon, no_loss_from_combat` | `graphics/icons/cargo/survey_data1.png` | 51 |
| Class II System Survey Data | `systemsurvey_data_2` | `survey_data` | *(blank)* | 3 | same | `.../survey_data2.png` | 52 |
| Class III System Survey Data | `systemsurvey_data_3` | `survey_data` | *(blank)* | 5 | same | `.../survey_data3.png` | 53 |
| Class IV System Survey Data | `systemsurvey_data_4` | `survey_data` | *(blank)* | 10 | same | `.../survey_data4.png` | 54 |
| Class V System Survey Data | `systemsurvey_data_5` | `survey_data` | *(blank)* | 30 | same | `.../survey_data5.png` | 55 |

All other cells as vanilla's class rows: price variability 0, stack size 1000, cargo space 1, sounds
`ui_chip_pickup` / `ui_modspec_drop`, icon width mult 1.

- **Prices: the same as vanilla's**, 1,000 / 3,000 / 5,000 / 10,000 / 30,000, because demand class `survey_data`
  makes the loader derive them from `utility` (section 1). Using vanilla's demand class keeps them sorted and
  traded as survey data. To tune prices later, change `utility`, not `base price`.
- **Order 51–55** places them right after vanilla's planetary data (45–49) and the hidden generic row (50).
- **Icons: vanilla's `survey_data1.png` … `survey_data5.png`**, reused by path, so no art ships with the mod. The
  names tell planet and system data apart. *(Open question: distinct art.)*
- Not tagged `hide_in_codex`, so they show in the Codex like vanilla's.

### `data/strings/descriptions.csv` (new file in the mod)

Type `RESOURCE`, one row per id, vanilla's voice:

> This TriPad holds a full sensor survey of a star system: positions, orbits and signatures of every major body and
> structure, collated to the Domain Colonial Ministry's system-chart standard. Charts like this underpin
> colonization planning, patrol routing and salvage ventures.

followed by one line per class:

| Class | line |
|---|---|
| I | The system charted here is unremarkable. Nothing in it is worth a second trip. |
| II | The system charted here holds nothing exceptional. |
| III | The system charted here contains a world fit for human use and will sell for a good price. |
| IV | The system charted here contains a world that scores high on the standard feasibility chart. It will prove quite valuable if sold to the right party. |
| V | The system charted here contains an exceptional world and will be of great value to any of the major factions in the Sector. |

## 4. Where it is granted in the scan flow

Today: `SurveyTracker.scan()` → progress reaches 100% (`p.fraction() >= 0.999f`) → `SurveyTracker.complete()` sets
`$systemsurvey_complete` on the system's memory, creates `SystemSurveyIntel` and adds the XP. `complete()` runs once
per system: `scan()` returns early when the system was already complete.

Changes:

1. **`SurveyData`**: new methods
   - `String getSurveyDataClass(StarSystemAPI system)`: section 2's rule, returns `systemsurvey_data_N` or `null`
     for core worlds. Gets the plugin once per call with `getNewPluginInstance("surveyPlugin")` and maps the vanilla
     id `survey_data_N` to `N`. An unknown id from a modded survey plugin counts as Class I.
   - `String classLabel(String id)`: "Class III" etc., from the commodity spec name.
   - constant `DATA_GRANTED_KEY = "systemsurvey_dataGranted"` (system persistent data): the id granted, a guard
     against ever granting twice.
2. **`SurveyTracker.complete()`**, after the XP and before the intel is created:
   ```java
   String dataId = SurveyData.getSurveyDataClass(system);
   if (dataId != null && grantEnabled && !system.getPersistentData().containsKey(DATA_GRANTED_KEY)) {
       Global.getSector().getPlayerFleet().getCargo().addCommodity(dataId, 1);
       system.getPersistentData().put(DATA_GRANTED_KEY, dataId);
   }
   ```
   `addCommodity` ignores cargo capacity, as vanilla's own survey does, so a full hold never loses the data.
3. **Feedback**
   - Campaign message: "Received Class III System Survey Data" with the class highlighted, played with the commodity's
     pickup sound (`ui_chip_pickup`), right after the scan's "% complete" message and the survey intel popup.
   - `SystemSurveyIntel` gets a `String dataId` field (passed to the constructor). The list entry shown when the
     intel is added gets a line "Class III System Survey Data", and the description a line under "System profile":
     "Survey data: Class III" with the commodity icon. Null for core worlds and for intel from older saves; then the
     line is left out.
4. **Setting** in `data/config/settings.json`: `"systemsurvey_grantSurveyData": true`, so the feature can be turned
   off without removing the mod.

Nothing else changes: the scan, the progress bar, XP and the Neutrino patch stay as they are.

## 5. Save compatibility

- **Updating a save that already uses the mod:** works. New state is a key in the system's persistent data and a
  field on new intel; old `SystemSurveyIntel` objects load with `dataId == null`, which the intel handles.
- **Systems surveyed before the update give no data.** Granting it retroactively on load would be possible
  (each `$systemsurvey_complete` system without `systemsurvey_dataGranted`), but a batch of free commodities on
  load is a surprise; left out unless wanted. *(Open question.)*
- **Removing the mod from a save:** already not supported (the save holds `SystemSurveyIntel`). It also becomes
  unsafe for a new reason: unknown `systemsurvey_data_N` commodities in cargo or storage. Selling or dropping
  them first does not make removal safe, because of the intel. The mod's description stays as it is.
- **Adding the mod to an existing save:** works as before; no data is granted for systems until they are surveyed.
- No transient scripts change; the class is stored, never recomputed on load.

## 6. Testing

Build: `bash build.sh`, then a startup probe (native-smoke) to confirm the CSV rows and descriptions load without
errors (a blank required cell or stray quote crashes the loader).

Live checks in `native-smoke/live` (same runner, same RAM workaround as today):

**`survey` group, extended**
- Before completing: record the player's cargo count of every `systemsurvey_data_N`, and
  `SurveyData.getSurveyDataClass(sys)`; independently compute the max of `getSurveyDataType` over the system's
  eligible planets in the check and expect the same class.
- After completing: cargo has exactly one more unit of that class and no change in the others;
  `systemsurvey_dataGranted` holds the id; the intel's `dataId` matches.
- Running `SurveyTracker.scan()` again in the completed system grants nothing more.
- After save and reload: cargo, the granted key and the intel's `dataId` survive; the intel description (with the
  data line) builds.
- Screenshots: the intel panel and the cargo screen showing the new item and its tooltip.

**New `surveydata` group**
- Commodity specs: each `systemsurvey_data_N` exists, has demand class `survey_data`, name "Class N System Survey
  Data", and base price 1000 / 3000 / 5000 / 10000 / 30000 (the loader-derived price, so a CSV mistake shows).
- Core world: pick a `theme_core` system, complete it, expect no survey data and a working intel.
- Distribution (report, not pass/fail): count the class every non-core system in the sector would get, next to
  vanilla's class for every eligible planet, and write both to the check notes. Expect Class V systems to be rare
  (a few percent) and most systems Class I–II; if not, revisit section 2 before release.
- Market: sell one unit at an open market through `CargoAPI` / market price lookup and expect the vanilla price for
  that class (`nonEconItemSellPriceMult`). *Unverified: that every market buys `nonecon` survey data the same way
  for a mod's ids as for vanilla's; this check settles it.*

Pass criteria: all existing 19 checks still pass, all new checks pass, no errors in `starsector.log`.

## Open questions for approval

1. **System class rule:** highest planet class in the system (planned) — or something else?
2. **Core worlds:** no data (planned) — or Class I like any other system?
3. **Prices:** same as vanilla planet data (planned) — or scaled (for example ×0.5, since a system survey costs no
   supplies or machinery)?
4. **Icons:** reuse vanilla's (planned) — or new art for system data?
5. **Old saves:** no retroactive data (planned) — or grant it once on load?

## As built

Implemented as planned, with these differences and results:

- **Intel constructor:** `SystemSurveyIntel(system, xp, dataId)`; `dataId` is null when nothing was granted (core world,
  setting off), so a survey never claims data it did not give. `getDataId()` was added for the checks.
- **Description line:** shown as the commodity icon (32x32, from the commodity spec) above "Survey data: Class N"
  under "System profile". The list entry line ("Class N System Survey Data") shows only in the discovery popup, like the XP line.
- **Pickup sound:** `Global.getSoundPlayer().playUISound(spec.getSoundId(), 1, 1)`, with the commodity spec's sound id
  (`ui_chip_pickup`).
- **Extra `SurveyData` helpers:** `getSurveyPlanets(system)` (the eligible planets) and `vanillaClass(id)`.
- **Market sell check:** no API gives the player's exact sell quote for a non-economy item, so the `surveydata` group
  compares the mod's ids against vanilla's `survey_data_N` through `CargoStackAPI.getBaseValuePerUnit` and
  `MarketAPI.getDemandPrice` (equal, 1,000 / 3,000 / 5,000 / 10,000 / 30,000; x0.8 `nonEconItemSellPriceMult` on sale).
  An actual sell transaction in the trade UI was **not** run.
- **Class distribution (one generated sector, 179 non-core systems, 24 core):** Class I 38.5%, II 19.0%, III 20.7%,
  IV 13.4%, V 8.4%. Vanilla's class of the 472 eligible planets: I 54.7%, II 20.8%, III 14.2%, IV 6.8%, V 3.6%.
  Class V is rarer than I-II but more than "a few percent" (8%), and I-II are 57% of systems, not "most" by a wide margin.
  This is a single sector; it is the report the plan asked for, and the rule was left as approved.
- **Startup probe:** the mod has no startup probe; the live runs boot the game with the new CSVs, which load without errors.
