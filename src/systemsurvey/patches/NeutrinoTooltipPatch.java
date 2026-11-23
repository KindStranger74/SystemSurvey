package systemsurvey.patches;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.abilities.GraviticScanAbility;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import patchlib.api.context.AfterContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.After;
import patchlib.api.patch.Patch;
import systemsurvey.SurveyData;

/**
 * Adds one line to the Neutrino Detector tooltip in a fully surveyed system, where vanilla's "false readings"
 * sentence no longer holds (see {@link NeutrinoFalseReadingsPatch}). Additive; the vanilla tooltip is unchanged.
 */
@Patch(target = @ClassMatch(subtype = GraviticScanAbility.class))
public class NeutrinoTooltipPatch {

	@After(target = @MethodMatch(methodName = "createTooltip", parameterCount = 2))
	public static void tooltipNote(AfterContext context) {
		// a subclass calling super.createTooltip would otherwise add the line twice
		if (!context.isMostDerivedCall()) return;
		if (Global.CODEX_TOOLTIP_MODE) return;
		Object self = context.getSelf();
		if (!(self instanceof GraviticScanAbility)) return;
		CampaignFleetAPI fleet = ((GraviticScanAbility) self).getFleet();
		if (fleet == null || !(fleet.getContainingLocation() instanceof StarSystemAPI)) return;
		if (!SurveyData.isComplete((StarSystemAPI) fleet.getContainingLocation())) return;
		TooltipMakerAPI tooltip = (TooltipMakerAPI) context.getArg(0);
		tooltip.addPara("This system is %s: false readings are filtered out here.", 10f,
				Misc.getPositiveHighlightColor(), "fully surveyed");
	}
}
