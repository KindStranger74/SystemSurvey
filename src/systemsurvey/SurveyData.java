package systemsurvey;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.OrbitalStationAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI.SurveyLevel;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.util.Misc;

/** Scan bookkeeping and theme reading for one star system. Stateless; the state lives on the system. */
public class SurveyData {

	/** HashSet of scanned entity ids, in the system's persistent data. */
	public static final String SCANNED_KEY = "systemsurvey_scanned";
	/** Set on the system's memory once fully surveyed; usable from rules.csv. */
	public static final String COMPLETE_FLAG = "$systemsurvey_complete";

	public static class Progress {
		public float scanned;
		public float total;
		public int objects;
		public int objectsScanned;

		/** Points a full survey needs: the setting's cap, or every point in the system when it has fewer. */
		public float required() {
			return Math.min(total, getFloat("systemsurvey_pointsRequired", 10f));
		}

		public float fraction() {
			float req = required();
			if (req <= 0f) return 1f;
			return Math.min(1f, scanned / req);
		}
	}

	public static class ThemeLine {
		public final String title;
		public final String detail;
		public final Color color;

		public ThemeLine(String title, String detail, Color color) {
			this.title = title;
			this.detail = detail;
			this.color = color;
		}
	}

	// ---- what counts ----

	public static boolean isScannable(SectorEntityToken e) {
		if (e == null || !e.isAlive()) return false;
		if (e instanceof CampaignFleetAPI || e instanceof CampaignTerrainAPI) return false;
		if (e.hasTag(Tags.NON_CLICKABLE) || e.hasTag(Tags.EXPIRES) || e.hasTag(Tags.FADING_OUT_AND_EXPIRING)) return false;
		if (e.hasTag(Tags.ORBITAL_JUNK)) return false;
		if (e instanceof PlanetAPI) {
			return !((PlanetAPI) e).getSpec().isNebulaCenter();
		}
		if (e instanceof JumpPointAPI || e instanceof OrbitalStationAPI) return true;
		if (e instanceof CustomCampaignEntityAPI) {
			if ("base_intel_icon".equals(e.getCustomEntityType())) return false;
			return e.getRadius() > 0f;
		}
		return false;
	}

	public static float weight(SectorEntityToken e) {
		if (e instanceof PlanetAPI) return ((PlanetAPI) e).isStar() ? 1f : 2f;
		if (e instanceof JumpPointAPI) return 1f;
		if (e.hasTag(Tags.SALVAGEABLE) || e.hasTag(Tags.STATION) || e instanceof OrbitalStationAPI) return 3f;
		if (e.hasTag(Tags.OBJECTIVE) || e.hasTag(Tags.STABLE_LOCATION)) return 1f;
		return 2f;
	}

	public static List<SectorEntityToken> getScannables(StarSystemAPI system) {
		List<SectorEntityToken> result = new ArrayList<SectorEntityToken>();
		for (SectorEntityToken e : system.getAllEntities()) {
			if (isScannable(e)) result.add(e);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static Set<String> getScanned(StarSystemAPI system) {
		Map<String, Object> data = system.getPersistentData();
		Object o = data.get(SCANNED_KEY);
		if (!(o instanceof Set)) {
			o = new HashSet<String>();
			data.put(SCANNED_KEY, o);
		}
		return (Set<String>) o;
	}

	public static boolean isComplete(StarSystemAPI system) {
		return system.getMemoryWithoutUpdate().getBoolean(COMPLETE_FLAG);
	}

	public static Progress getProgress(StarSystemAPI system) {
		Progress p = new Progress();
		Set<String> scanned = getScanned(system);
		for (SectorEntityToken e : getScannables(system)) {
			float w = weight(e);
			p.total += w;
			p.objects++;
			if (scanned.contains(e.getId())) {
				p.scanned += w;
				p.objectsScanned++;
			}
		}
		return p;
	}

	// ---- what the system is ----

	public static List<ThemeLine> getThemes(StarSystemAPI system) {
		List<ThemeLine> lines = new ArrayList<ThemeLine>();
		Color pos = Misc.getPositiveHighlightColor();
		Color neg = Misc.getNegativeHighlightColor();
		Color hl = Misc.getHighlightColor();
		Color text = Misc.getTextColor();

		if (system.hasTag(Tags.THEME_CORE_POPULATED)) {
			lines.add(new ThemeLine("Core world system", "Inhabited and charted; part of the core worlds.", text));
		} else if (system.hasTag(Tags.THEME_CORE_UNPOPULATED)) {
			lines.add(new ThemeLine("Core world system", "Charted space near the core worlds, uninhabited.", text));
		} else if (system.hasTag(Tags.THEME_CORE)) {
			lines.add(new ThemeLine("Core world system", "Charted space of the core worlds.", text));
		}

		if (system.hasTag(Tags.THEME_REMNANT_MAIN)) {
			lines.add(new ThemeLine("Remnant stronghold", remnantDetail(system, true), neg));
		} else if (system.hasTag(Tags.THEME_REMNANT_SECONDARY)) {
			lines.add(new ThemeLine("Remnant outpost", remnantDetail(system, false), neg));
		} else if (system.hasTag(Tags.THEME_REMNANT)) {
			lines.add(new ThemeLine("Remnant activity", remnantDetail(system, false), neg));
		}

		if (system.hasTag(Tags.THEME_DERELICT_MOTHERSHIP)) {
			lines.add(new ThemeLine("Domain-era mothership", "A massive Domain-era derelict signature, likely still active.", pos));
		}
		if (system.hasTag(Tags.THEME_DERELICT_CRYOSLEEPER)) {
			lines.add(new ThemeLine("Cryosleeper", "A Domain-era cryosleeper vessel lies in this system.", pos));
		}
		if (system.hasTag(Tags.THEME_DERELICT_SURVEY_SHIP)) {
			lines.add(new ThemeLine("Derelict survey ship", "Signature of a Domain-era survey ship.", pos));
		}
		if (system.hasTag(Tags.THEME_DERELICT_PROBES)) {
			lines.add(new ThemeLine("Survey probes", "Domain-era survey probes and their caches.", pos));
		}
		if (system.hasTag(Tags.THEME_DERELICT) && !hasAnyDerelictSubtype(system)) {
			lines.add(new ThemeLine("Derelict activity", "Domain-era derelicts and automated drones.", pos));
		}

		if (system.hasTag(Tags.THEME_RUINS_MAIN)) {
			lines.add(new ThemeLine("Major ruins", "Extensive ruins of a former colony; salvage and caches likely.", pos));
		} else if (system.hasTag(Tags.THEME_RUINS_SECONDARY)) {
			lines.add(new ThemeLine("Minor ruins", "Remains of a smaller settlement.", pos));
		} else if (system.hasTag(Tags.THEME_RUINS)) {
			lines.add(new ThemeLine("Ruins", "Remains of a former settlement.", pos));
		}

		if (system.hasTag(Tags.HAS_CORONAL_TAP)) {
			lines.add(new ThemeLine("Coronal hypershunt", "A Domain-era megastructure draws on the star.", hl));
		}
		if (system.hasTag(Tags.THEME_SPECIAL) || system.hasTag(Tags.PK_SYSTEM)) {
			lines.add(new ThemeLine("Anomalous readings", "Something here does not match any known profile.", hl));
		}
		if (system.hasTag(Tags.THEME_HIDDEN) || system.hasTag(Tags.SYSTEM_ABYSSAL)) {
			lines.add(new ThemeLine("Abyssal space", "Far from the charted lanes; background noise is near zero.", hl));
		}
		if (system.hasTag(Tags.THEME_MISC) && lines.isEmpty()) {
			lines.add(new ThemeLine("Minor points of interest", "A few objects worth a look, nothing major.", text));
		}

		// themes added by other mods: show them by tag name
		for (String tag : system.getTags()) {
			if (!tag.startsWith("theme_") || isKnownTag(tag)) continue;
			lines.add(new ThemeLine(prettyTag(tag), "Unclassified signature.", hl));
		}

		if (lines.isEmpty()) {
			lines.add(new ThemeLine("Unremarkable", "Nothing beyond the ordinary.", Misc.getGrayColor()));
		}
		return lines;
	}

	public static String getDangerHint(StarSystemAPI system) {
		if (system.hasTag(Tags.THEME_UNSAFE)) return "Heavy automated defenses: expect strong resistance.";
		if (system.hasTag(Tags.THEME_REMNANT) && !system.hasTag(Tags.THEME_REMNANT_NO_FLEETS)
				&& !system.hasTag(Tags.THEME_REMNANT_DESTROYED)) {
			return "Automated fleets patrol this system.";
		}
		return null;
	}

	/** Short text for the HUD: the first theme, plus how many more. */
	public static String getHeadline(StarSystemAPI system) {
		List<ThemeLine> themes = getThemes(system);
		String s = themes.get(0).title;
		if (themes.size() > 1) s += " +" + (themes.size() - 1);
		return s;
	}

	private static String remnantDetail(StarSystemAPI system, boolean main) {
		boolean station = false;
		for (CampaignFleetAPI f : system.getFleets()) {
			if (f.isStationMode() && Factions.REMNANTS.equals(f.getFaction().getId())) {
				station = true;
				break;
			}
		}
		if (system.hasTag(Tags.THEME_REMNANT_DESTROYED)) {
			return station ? "Automated defenses, mostly destroyed." : "Wrecked automated defenses; little left active.";
		}
		if (system.hasTag(Tags.THEME_REMNANT_SUPPRESSED)) {
			return station ? "A damaged Remnant battlestation is still active." : "Suppressed automated presence.";
		}
		if (system.hasTag(Tags.THEME_REMNANT_RESURGENT)) {
			return station ? "A fully active Remnant battlestation." : "Resurgent automated presence.";
		}
		if (system.hasTag(Tags.THEME_REMNANT_NO_FLEETS)) return "Dormant automated presence.";
		return main ? "A Remnant battlestation anchors this system." : "Scattered automated presence.";
	}

	private static boolean hasAnyDerelictSubtype(StarSystemAPI system) {
		return system.hasTag(Tags.THEME_DERELICT_MOTHERSHIP) || system.hasTag(Tags.THEME_DERELICT_CRYOSLEEPER)
				|| system.hasTag(Tags.THEME_DERELICT_SURVEY_SHIP) || system.hasTag(Tags.THEME_DERELICT_PROBES);
	}

	private static final Set<String> KNOWN = new HashSet<String>();
	static {
		String[] known = {Tags.THEME_HIDDEN, Tags.THEME_CORE_UNPOPULATED, Tags.THEME_CORE_POPULATED, Tags.THEME_CORE,
				Tags.THEME_INTERESTING, Tags.THEME_INTERESTING_MINOR, Tags.THEME_MISC, Tags.THEME_MISC_SKIP,
				Tags.THEME_RUINS, Tags.THEME_RUINS_MAIN, Tags.THEME_RUINS_SECONDARY, Tags.THEME_SPECIAL,
				Tags.THEME_DERELICT, Tags.THEME_DERELICT_MOTHERSHIP, Tags.THEME_DERELICT_CRYOSLEEPER,
				Tags.THEME_DERELICT_SURVEY_SHIP, Tags.THEME_DERELICT_PROBES, Tags.THEME_UNSAFE, Tags.THEME_REMNANT,
				Tags.THEME_REMNANT_MAIN, Tags.THEME_REMNANT_SECONDARY, Tags.THEME_REMNANT_NO_FLEETS,
				Tags.THEME_REMNANT_DESTROYED, Tags.THEME_REMNANT_SUPPRESSED, Tags.THEME_REMNANT_RESURGENT};
		for (String k : known) KNOWN.add(k);
	}

	private static boolean isKnownTag(String tag) {
		return KNOWN.contains(tag);
	}

	private static String prettyTag(String tag) {
		String s = tag.substring("theme_".length()).replace('_', ' ').trim();
		if (s.isEmpty()) return "Unknown";
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ---- what is left to find ----

	/** Remaining salvage by name, then unexplored ruins and unsurveyed planets. Count by label, in order. */
	public static Map<String, Integer> getLoot(StarSystemAPI system) {
		Map<String, Integer> loot = new LinkedHashMap<String, Integer>();
		for (SectorEntityToken e : system.getAllEntities()) {
			if (!e.isAlive() || !(e instanceof CustomCampaignEntityAPI)) continue;
			if (!e.hasTag(Tags.SALVAGEABLE)) continue;
			String name = e.getCustomEntitySpec() != null ? e.getCustomEntitySpec().getDefaultName() : e.getName();
			if (name == null || name.isEmpty()) name = "Salvageable object";
			add(loot, name);
		}
		for (PlanetAPI planet : system.getPlanets()) {
			MarketAPI market = planet.getMarket();
			if (market == null || planet.isStar()) continue;
			if (Misc.hasUnexploredRuins(market)) add(loot, "Unexplored ruins");
		}
		for (PlanetAPI planet : system.getPlanets()) {
			MarketAPI market = planet.getMarket();
			if (market == null || planet.isStar() || market.isInEconomy()) continue;
			if (market.getSurveyLevel() != SurveyLevel.FULL) add(loot, "Planet not fully surveyed");
		}
		return loot;
	}

	private static void add(Map<String, Integer> map, String key) {
		Integer n = map.get(key);
		map.put(key, n == null ? 1 : n + 1);
	}

	// ---- settings ----

	public static float getFloat(String key, float def) {
		try {
			return Global.getSettings().getFloat(key);
		} catch (Exception e) {
			return def;
		}
	}

	public static boolean getBoolean(String key, boolean def) {
		try {
			return Global.getSettings().getBoolean(key);
		} catch (Exception e) {
			return def;
		}
	}
}
