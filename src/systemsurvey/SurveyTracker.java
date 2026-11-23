package systemsurvey;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.CommoditySpecAPI;
import com.fs.starfarer.api.characters.AbilityPlugin;
import com.fs.starfarer.api.impl.campaign.ids.Abilities;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;

/**
 * Watches the player's Active Sensor Burst the way vanilla HTFactorTracker does: once per burst, when its level
 * passes 0.9, every scannable object within the scan radius is marked scanned. Transient, added on every load.
 */
public class SurveyTracker implements EveryFrameScript {

	private final IntervalUtil interval = new IntervalUtil(0.04f, 0.06f);
	private boolean canScan = true;

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		interval.advance(days);
		if (!interval.intervalElapsed()) return;

		CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
		if (pf == null) return;
		AbilityPlugin sb = pf.getAbility(Abilities.SENSOR_BURST);
		if (sb == null) return;

		if (sb.isUsable() || sb.getLevel() <= 0) canScan = true;

		if (canScan && !pf.isInHyperspace() && sb.isInProgress() && sb.getLevel() > 0.9f
				&& pf.getContainingLocation() instanceof StarSystemAPI) {
			canScan = false;
			scan(pf, (StarSystemAPI) pf.getContainingLocation());
		}
	}

	public static float getScanRadius(CampaignFleetAPI fleet) {
		float sensor = fleet.getSensorRangeMod().computeEffective(fleet.getSensorStrength());
		return sensor * SurveyData.getFloat("systemsurvey_scanRangeMult", 1.5f);
	}

	public static void scan(CampaignFleetAPI fleet, StarSystemAPI system) {
		boolean wasComplete = SurveyData.isComplete(system);
		float radius = getScanRadius(fleet);
		Set<String> scanned = SurveyData.getScanned(system);
		Color fresh = Misc.getHighlightColor();

		int found = 0;
		int unidentified = 0;
		for (SectorEntityToken e : SurveyData.getScannables(system)) {
			if (scanned.contains(e.getId())) continue;
			float dist = Misc.getDistance(fleet.getLocation(), e.getLocation()) - e.getRadius();
			if (dist > radius) continue;
			scanned.add(e.getId());
			found++;
			if (e.isDiscoverable()) {
				// not yet found by the player: counted, but its position stays hidden
				unidentified++;
			} else if (e.isInCurrentLocation()) {
				e.addFloatingText("Scanned", fresh, 0.5f);
			}
		}

		SurveyData.Progress p = SurveyData.getProgress(system);
		int pct = (int) Math.floor(p.fraction() * 100f);
		if (wasComplete) {
			message("System already surveyed", Misc.getGrayColor(), null);
			return;
		}
		if (found == 0) {
			message("Survey scan: no new objects in range, " + pct + "% complete", Misc.getGrayColor(), pct + "%");
		} else {
			String extra = unidentified > 0 ? " (" + unidentified + " unidentified)" : "";
			message("Survey scan: " + found + " new " + (found == 1 ? "object" : "objects") + extra + ", " + pct + "% complete",
					Misc.getTextColor(), pct + "%");
		}

		if (p.fraction() >= 0.999f) {
			complete(system, p);
		}
	}

	private static void complete(StarSystemAPI system, SurveyData.Progress p) {
		system.getMemoryWithoutUpdate().set(SurveyData.COMPLETE_FLAG, true);
		// paid on every point in the system, also the ones the survey did not need
		long xp = Math.round(p.total * SurveyData.getFloat("systemsurvey_xpPerPoint", 250f));
		SurveyData.DataClass data = SurveyData.getSurveyDataInfo(system);
		String dataId = data != null ? data.id() : null;
		boolean grant = dataId != null && SurveyData.getBoolean("systemsurvey_grantSurveyData", true)
				&& !system.getPersistentData().containsKey(SurveyData.DATA_GRANTED_KEY);
		new SystemSurveyIntel(system, xp, grant ? dataId : null, grant ? data.reason : null);
		if (xp > 0) {
			Global.getSector().getPlayerStats().addXP(xp);
		}
		if (grant) {
			// addCommodity ignores cargo capacity, like vanilla's own survey, so a full hold never loses the data
			Global.getSector().getPlayerFleet().getCargo().addCommodity(dataId, 1);
			system.getPersistentData().put(SurveyData.DATA_GRANTED_KEY, dataId);
			String label = SurveyData.classLabel(dataId);
			String why = data.reason != null ? " (" + data.reason + ")" : "";
			message("Received " + label + " System Survey Data" + why, Misc.getTextColor(), label);
			CommoditySpecAPI spec = Global.getSettings().getCommoditySpec(dataId);
			String sound = spec != null && spec.getSoundId() != null ? spec.getSoundId() : "ui_chip_pickup";
			Global.getSoundPlayer().playUISound(sound, 1f, 1f);
		}
	}

	private static void message(String text, Color color, String highlight) {
		if (highlight == null) {
			Global.getSector().getCampaignUI().getMessageDisplay().addMessage(text, color);
		} else {
			Global.getSector().getCampaignUI().getMessageDisplay().addMessage(text, color, highlight, Misc.getHighlightColor());
		}
	}
}
