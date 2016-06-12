package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatterConfig {
	
	public static final String CONFIG_FILE = "chatterConfig.json";
	
	public static boolean idleChatter = true;
	public static boolean allyChatter = true;
	public static boolean selfChatter = false;
	public static boolean factionSpecificCharacters = true;
	public static float minMissileOPFractionForChatter = 0.2f;
	
	public static Logger log = Global.getLogger(ChatterConfig.class);
	
	static {
		try {
			JSONObject settings = Global.getSettings().loadJSON(CONFIG_FILE);
			idleChatter = settings.optBoolean("idleChatter", idleChatter);
			allyChatter = settings.optBoolean("allyChatter", allyChatter);
			selfChatter = settings.optBoolean("selfChatter", selfChatter);
			factionSpecificCharacters = settings.optBoolean("factionSpecificCharacters", factionSpecificCharacters);
			minMissileOPFractionForChatter = (float)settings.optDouble("minMissileOPFractionForChatter", minMissileOPFractionForChatter);
		} 
		catch (IOException | JSONException ex) {
			log.error(ex);
		}
	}
}
