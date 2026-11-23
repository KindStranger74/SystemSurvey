package systemsurvey;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.misc.FleetLogIntel;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * One per fully surveyed system. Shows on the sector map at the system, so hovering the icon tells the theme.
 * Theme and loot are read live, so the remaining loot stays current.
 */
public class SystemSurveyIntel extends FleetLogIntel {

	public static final String TAG = "System surveys";

	protected StarSystemAPI system;
	protected long xp;

	public SystemSurveyIntel(StarSystemAPI system, long xp) {
		this.system = system;
		this.xp = xp;
		setSound("ui_discovered_entity");
		setIconId("systemsurvey_survey");
		setListInfoParam(DISCOVERED_PARAM);
		Global.getSector().getIntelManager().addIntel(this, false, null);
		setListInfoParam(null);
	}

	@Override
	protected String getName() {
		return "Survey: " + system.getNameWithLowercaseTypeShort();
	}

	@Override
	public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
		info.addPara(getName(), getTitleColor(mode), 0f);
		bullet(info);
		Color tc = getBulletColorForMode(mode);
		List<SurveyData.ThemeLine> themes = SurveyData.getThemes(system);
		float pad = 3f;
		for (SurveyData.ThemeLine t : themes) {
			info.addPara(t.title, pad, tc, t.color, t.title);
			pad = 0f;
		}
		if (getListInfoParam() == DISCOVERED_PARAM && xp > 0) {
			info.addPara("%s experience", 0f, tc, Misc.getHighlightColor(), Misc.getWithDGS(xp));
		}
		unindent(info);
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		Color h = Misc.getHighlightColor();
		Color g = Misc.getGrayColor();
		Color tc = Misc.getTextColor();
		float opad = 10f;
		float pad = 3f;

		info.addPara("A full sensor survey of the " + system.getNameWithLowercaseTypeShort() + " is complete.", opad);

		info.addSectionHeading("System profile", getFactionForUIColors().getBaseUIColor(),
				getFactionForUIColors().getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		float p = opad;
		for (SurveyData.ThemeLine t : SurveyData.getThemes(system)) {
			info.addPara(t.title, t.color, p);
			info.addPara(t.detail, g, 0f);
			p = pad + 4f;
		}
		String danger = SurveyData.getDangerHint(system);
		if (danger != null) {
			info.addPara(danger, Misc.getNegativeHighlightColor(), opad);
		}

		info.addSectionHeading("Remaining finds", getFactionForUIColors().getBaseUIColor(),
				getFactionForUIColors().getDarkUIColor(), com.fs.starfarer.api.ui.Alignment.MID, opad);
		Map<String, Integer> loot = SurveyData.getLoot(system);
		if (loot.isEmpty()) {
			info.addPara("Nothing left to salvage or explore.", g, opad);
		} else {
			info.setBulletedListMode(BULLET);
			float lp = opad;
			for (Map.Entry<String, Integer> e : loot.entrySet()) {
				int n = e.getValue();
				String count = n > 1 ? " x" + n : "";
				info.addPara(e.getKey() + count, lp, tc, h, count.trim());
				lp = pad;
			}
			info.setBulletedListMode(null);
		}

		if (xp > 0) {
			info.addPara("Survey earned %s experience.", opad, h, Misc.getWithDGS(xp));
		}

		addLogTimestamp(info, tc, opad);
		addDeleteButton(info, width);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.remove(Tags.INTEL_FLEET_LOG);
		tags.add(Tags.INTEL_EXPLORATION);
		tags.add(TAG);
		return tags;
	}

	@Override
	public SectorEntityToken getMapLocation(SectorMapAPI map) {
		if (system.getCenter() != null) return system.getCenter();
		return system.getHyperspaceAnchor();
	}

	@Override
	public boolean shouldRemoveIntel() {
		// permanent record; the player can delete it
		return isEnded();
	}

	public StarSystemAPI getSystem() {
		return system;
	}
}
