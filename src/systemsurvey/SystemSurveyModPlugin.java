package systemsurvey;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;

public class SystemSurveyModPlugin extends BaseModPlugin {

	@Override
	public void onGameLoad(boolean newGame) {
		// transient: re-added on every load, never written to the save
		Global.getSector().addTransientScript(new SurveyTracker());
		Global.getSector().getListenerManager().addListener(new SurveyHud(), true);
	}
}
