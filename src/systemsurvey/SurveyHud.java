package systemsurvey;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CampaignUIAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.listeners.CampaignUIRenderingListener;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.ui.Fonts;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * Survey bar on the campaign screen, top centre under the message line, while the fleet is in a star system.
 * Transient listener, added on every load. Progress is recomputed a few times a second, not every frame.
 */
public class SurveyHud implements CampaignUIRenderingListener {

	public static final float W = 280f;
	public static final float H = 20f;
	public static final float TOP = 30f;
	public static final Color GREEN = new Color(50, 130, 0);

	private transient SpriteAPI lineSprite;
	private transient LabelAPI label;
	private transient String labelText;
	private transient StarSystemAPI cachedSystem;
	private transient float cachedFraction;
	private transient boolean cachedComplete;
	private transient String cachedText;
	private transient long lastUpdate;

	@Override
	public void renderInUICoordsBelowUI(ViewportAPI viewport) {
	}

	@Override
	public void renderInUICoordsAboveUIAndTooltips(ViewportAPI viewport) {
	}

	@Override
	public void renderInUICoordsAboveUIBelowTooltips(ViewportAPI viewport) {
		try {
			render();
		} catch (Throwable t) {
			// never take the game down over a HUD
			Global.getLogger(SurveyHud.class).warn("System Survey HUD failed", t);
		}
	}

	private void render() {
		if (!SurveyData.getBoolean("systemsurvey_showHud", true)) return;
		CampaignUIAPI ui = Global.getSector().getCampaignUI();
		if (ui == null || ui.isShowingDialog() || ui.isShowingMenu() || ui.getCurrentCoreTab() != null || ui.isHideUI()) return;
		CampaignFleetAPI pf = Global.getSector().getPlayerFleet();
		if (pf == null || pf.isInHyperspace() || !(pf.getContainingLocation() instanceof StarSystemAPI)) return;
		StarSystemAPI system = (StarSystemAPI) pf.getContainingLocation();

		long now = System.currentTimeMillis();
		if (system != cachedSystem || now - lastUpdate > 250) {
			cachedSystem = system;
			lastUpdate = now;
			cachedComplete = SurveyData.isComplete(system);
			if (cachedComplete) {
				cachedFraction = 1f;
				cachedText = SurveyData.getHeadline(system);
			} else {
				cachedFraction = SurveyData.getProgress(system).fraction();
				cachedText = "System survey " + (int) Math.floor(cachedFraction * 100f) + "%";
			}
		}

		float sw = Global.getSettings().getScreenWidth();
		float sh = Global.getSettings().getScreenHeight();
		float x = (float) Math.floor((sw - W) / 2f);
		float y = (float) Math.floor(sh - TOP - H);

		Color fill = cachedComplete ? GREEN : Global.getSettings().getColor("progressBarStandardColor");
		drawBar(x, y, W, H, cachedFraction, fill, 1f);
		drawLabel(x + 4f, y, W - 8f, H, cachedText);
	}

	private void drawLabel(float x, float y, float w, float h, String text) {
		String shown = fit(text, Fonts.DEFAULT_SMALL, w);
		if (label == null || !shown.equals(labelText)) {
			label = Global.getSettings().createLabel(shown, Fonts.DEFAULT_SMALL);
			label.setColor(Misc.getTextColor());
			label.setAlignment(com.fs.starfarer.api.ui.Alignment.MID);
			labelText = shown;
		}
		float th = label.computeTextHeight("Ag");
		// position is the label's bottom left in UI coordinates
		label.getPosition().setSize(w, th);
		label.getPosition().setLocation(x, y + (float) Math.floor((h - th) / 2f));
		label.render(1f);
	}

	private static String fit(String s, String font, float w) {
		float max = w - 2f;
		if (Global.getSettings().computeStringWidth(s, font) <= max) return s;
		int lo = 0;
		int hi = s.length();
		while (lo < hi) {
			int mid = (lo + hi + 1) / 2;
			if (Global.getSettings().computeStringWidth(s.substring(0, mid).trim() + "...", font) <= max) lo = mid;
			else hi = mid - 1;
		}
		return lo == 0 ? "..." : s.substring(0, lo).trim() + "...";
	}

	// ---- vanilla progress bar, translation of obfuscated code (0.98a-RC8), via the starsector-ui-design recipe ----

	private SpriteAPI lineTexture() {
		if (lineSprite == null) lineSprite = Global.getSettings().getSprite("graphics/hud/line4x4.png");
		return lineSprite != null && lineSprite.getTextureId() != 0 ? lineSprite : null;
	}

	private void drawBar(float bx, float by, float bw, float bh, float fraction, Color fill, float a) {
		float f = Math.max(0f, Math.min(1f, fraction));
		Color frame = Global.getSettings().getColor("widgetBorderColorBright");
		Color soft = Misc.interpolateColor(fill, Color.white, 0.15f);
		Color shade = Misc.setAlpha(Color.BLACK, 127);
		float ux = bx + 3f;
		float uw = bw - 8f;
		SpriteAPI tex = lineTexture();

		quad(bx, by, bw, bh, Color.BLACK, a);
		line(tex, bx, by, bx, by + bh, 2f, frame, 0.5f, 0.5f, 0.5f, a, false);
		line(tex, bx + 1f, by, bx + 1f, by + bh, 2f, frame, 1f, 1f, 1f, a, true);
		line(tex, bx + bw, by, bx + bw, by + bh, 2f, frame, 0.5f, 0.5f, 0.5f, a, false);
		line(tex, bx + bw - 1f, by, bx + bw - 1f, by + bh, 2f, frame, 1f, 1f, 1f, a, true);
		line(tex, bx + 1f, by, bx + 15f, by, 1f, frame, 1f, 0.5f, 0f, a, false);
		line(tex, bx + 1f, by + bh - 1f, bx + 15f, by + bh, 1f, frame, 1f, 0.5f, 0f, a, false);
		line(tex, bx + bw - 1f, by, bx + bw - 15f, by, 1f, frame, 1f, 0.5f, 0f, a, false);
		line(tex, bx + bw - 1f, by + bh - 1f, bx + bw - 15f, by + bh, 1f, frame, 1f, 0.5f, 0f, a, false);

		float end = bx + 6f + uw;
		float empty = uw * (1f - f);
		if (empty > 0f) {
			float fade = Math.min(10f, empty);
			float start = end - empty + 1.5f;
			for (float ly : new float[]{by + 1f, by + bh - 2f}) {
				line(tex, end + 2f, ly, end + 2f - fade, ly, 1f, frame, 0f, 0.5f, 1f, a, false);
				line(tex, end + 2f - fade, ly, start, ly, 1f, frame, 1f, 1f, 1f, a, false);
			}
			line(tex, start, by + 1f, start, by + bh - 1f, 1f, frame, 1f, 1f, 1f, a, false);
			line(tex, start, by + 1f, start, by + bh - 1f, 1f, Color.white, 0f, 1f, 0f, a, true);
		}

		float fw = uw * f + 1f;
		if (bh - 2f > 1f) {
			quad(ux, by + 1f, fw, bh - 2f, soft, a * 0.75f);
			vgradient(ux, by + 1f, fw, bh - 2f, fill, 0f, 0.75f, a, true);
		}
		float rw = uw - fw - 4f;
		if (rw > 0f && bh - 6f > 1f) {
			quad(ux + fw + 5f, by + 3f, rw, bh - 6f, soft, a * 0.65f * 0.75f);
			vgradient(ux + fw + 5f, by + 3f, rw, bh - 6f, shade, 0f, 0.65f, a, false);
		}
		line(tex, bx + uw * f + 5.5f, by, bx + uw * f + 5.5f, by + bh, 1f, frame, 0.5f, 1f, 0.5f, a, false);
	}

	private static void quad(float x, float y, float w, float h, Color c, float alpha) {
		if (w <= 0f || h <= 0f) return;
		begin(c, alpha);
		GL11.glBegin(GL11.GL_QUADS);
		GL11.glVertex2f(Math.round(x), Math.round(y));
		GL11.glVertex2f(Math.round(x), Math.round(y + h));
		GL11.glVertex2f(Math.round(x + w), Math.round(y + h));
		GL11.glVertex2f(Math.round(x + w), Math.round(y));
		GL11.glEnd();
		end();
	}

	private static void vgradient(float x, float y, float w, float h, Color c, float edge, float mid, float alpha, boolean additive) {
		if (w <= 0f || h <= 0f) return;
		begin(c, alpha);
		if (additive) GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glBegin(GL11.GL_QUADS);
		color(c, alpha * edge);
		GL11.glVertex2f(x, y);
		GL11.glVertex2f(x + w, y);
		color(c, alpha * mid);
		GL11.glVertex2f(x + w, y + h / 2f);
		GL11.glVertex2f(x, y + h / 2f);
		GL11.glVertex2f(x + w, y + h / 2f);
		GL11.glVertex2f(x, y + h / 2f);
		color(c, alpha * edge);
		GL11.glVertex2f(x, y + h);
		GL11.glVertex2f(x + w, y + h);
		GL11.glEnd();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		end();
	}

	private static void line(SpriteAPI tex, float x1, float y1, float x2, float y2, float width, Color c,
							 float a1, float a2, float a3, float alpha, boolean additive) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float len = (float) Math.sqrt(dx * dx + dy * dy);
		if (len <= 0f) return;
		float nx = dy / len * width * 0.5f;
		float ny = -dx / len * width * 0.5f;

		begin(c, alpha);
		if (tex != null) {
			GL11.glEnable(GL11.GL_TEXTURE_2D);
			tex.bindTexture();
		}
		if (additive) GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glBegin(GL11.GL_QUAD_STRIP);
		color(c, alpha * a1);
		GL11.glTexCoord2f(0f, 0f);
		GL11.glVertex2f(x1 - nx, y1 - ny);
		GL11.glTexCoord2f(0f, 1f);
		GL11.glVertex2f(x1 + nx, y1 + ny);
		color(c, alpha * a2);
		GL11.glTexCoord2f(0.5f, 0f);
		GL11.glVertex2f((x1 + x2) * 0.5f - nx, (y1 + y2) * 0.5f - ny);
		GL11.glTexCoord2f(0.5f, 1f);
		GL11.glVertex2f((x1 + x2) * 0.5f + nx, (y1 + y2) * 0.5f + ny);
		color(c, alpha * a3);
		GL11.glTexCoord2f(1f, 0f);
		GL11.glVertex2f(x2 - nx, y2 - ny);
		GL11.glTexCoord2f(1f, 1f);
		GL11.glVertex2f(x2 + nx, y2 + ny);
		GL11.glEnd();
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		end();
	}

	private static void begin(Color c, float alpha) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		color(c, alpha);
	}

	private static void color(Color c, float alpha) {
		float a = c.getAlpha() / 255f * Math.max(0f, Math.min(1f, alpha));
		GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, a);
	}

	private static void end() {
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}
}
