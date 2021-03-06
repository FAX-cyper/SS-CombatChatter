package org.histidine.chatter.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.campaign.SetMercFactionScript;

public class ChatterModPlugin extends BaseModPlugin
{
	public static boolean hasTwigLib = Global.getSettings().getModManager().isModEnabled("ztwiglib");
	
	@Override
	public void onGameLoad(boolean newGame) {
		Global.getSector().addTransientListener(new SetMercFactionScript());
		ChatterDataManager.loadCharacters();
	}
}