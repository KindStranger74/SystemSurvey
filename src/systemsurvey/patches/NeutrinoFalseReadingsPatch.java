package systemsurvey.patches;

import java.util.Random;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.abilities.GraviticScanAbility;
import com.fs.starfarer.api.impl.campaign.abilities.GraviticScanData;

import patchlib.api.context.MethodCallContext;
import patchlib.api.match.ClassMatch;
import patchlib.api.match.MethodMatch;
import patchlib.api.patch.Patch;
import patchlib.api.patch.RedirectMethodCall;
import patchlib.api.query.FieldQuery;
import patchlib.api.ref.Ref;
import patchlib.api.spec.FieldQuerySpec;
import systemsurvey.SurveyData;

/**
 * The Neutrino Detector stops showing false readings in a fully surveyed system.
 *
 * Vanilla GraviticScanData.doSpecialPings (0.98a-RC8) ends with
 * {@code int numFalse = random.nextInt(5);} on a Random seeded from the location, then adds that many phantom
 * pings. The redirect lets that call run (other layers and the random sequence stay intact) and reports 0 when the
 * scanning fleet sits in a surveyed system. Everything else in the method, real pings included, is unchanged.
 */
@Patch(target = @ClassMatch(subtype = GraviticScanData.class))
public class NeutrinoFalseReadingsPatch {

	// GraviticScanData keeps its owner in a private field; found by type, not by name
	private static final FieldQuerySpec ABILITY = FieldQuery.create().subtype(GraviticScanAbility.class).build();

	@RedirectMethodCall(
			target = @MethodMatch(methodName = "doSpecialPings", parameterCount = 0),
			call = @MethodMatch(methodName = "nextInt", parameterCount = 1),
			owner = @ClassMatch(subtype = Random.class))
	public static void noFalsePingsWhenSurveyed(MethodCallContext context) {
		Object count = context.call();
		if (isInSurveyedSystem(context)) count = 0;
		context.setResult(count);
	}

	private static boolean isInSurveyedSystem(MethodCallContext context) {
		if (!context.hasField(ABILITY)) return false;
		Ref<GraviticScanAbility> ref = context.getField(ABILITY);
		GraviticScanAbility ability = ref.get();
		if (ability == null) return false;
		CampaignFleetAPI fleet = ability.getFleet();
		if (fleet == null || !(fleet.getContainingLocation() instanceof StarSystemAPI)) return false;
		return SurveyData.isComplete((StarSystemAPI) fleet.getContainingLocation());
	}
}
