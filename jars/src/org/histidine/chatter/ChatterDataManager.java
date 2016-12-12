package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.histidine.chatter.ChatterLine.MessageType;
import org.histidine.chatter.utils.GeneralUtils;
import org.histidine.chatter.utils.StringHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatterDataManager {
	
	public static final String CONFIG_DIR = "data/config/chatter/";
	public static final String CHARACTERS_DIR = CONFIG_DIR + "characters/";
	public static final String CHARACTERS_LIST = CONFIG_DIR + "characters.csv";
	public static final String FACTION_TAGS_FILE = CONFIG_DIR + "factiontags.csv";
	public static final String CHARACTER_FACTIONS_FILE = CONFIG_DIR + "character_factions.csv";
	public static final String HULL_FACTION_PREFIX_FILE = CONFIG_DIR + "hull_prefixes.csv";
	public static final String SHIP_NAME_FACTION_PREFIX_FILE = CONFIG_DIR + "ship_name_prefixes.csv";
	
	public static final List<ChatterCharacter> CHARACTERS = new ArrayList<>();
	public static final Map<String, ChatterCharacter> CHARACTERS_MAP = new HashMap<>();
	public static final Map<String, Set<String>> FACTION_TAGS = new HashMap<>();
	public static final Map<String, Map<String, Integer>> CHARACTER_FACTIONS = new HashMap<>();
	
	public static final List<String[]> FACTION_HULL_PREFIXES = new ArrayList<>();
	public static final List<String[]> FACTION_SHIPNAME_PREFIXES = new ArrayList<>();
	
	public static Logger log = Global.getLogger(ChatterDataManager.class);
	
	protected static boolean loaded = false;
	
	// FIXME: this lags when the class is first loaded, 
	// and right now the class is loaded at the start of the first battle
	static {
		loadCharacters();
	}
	
	public static void loadCharacters()
	{
		if (loaded) return;
		try {
			// load faction tags
			JSONArray tagsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("faction", FACTION_TAGS_FILE, "chatter");
			for(int x = 0; x < tagsCSV.length(); x++)
			{
				JSONObject row = tagsCSV.getJSONObject(x);
				String factionId = row.getString("faction");
				String tagsStr = row.getString("tags");
				String[] tagsArray = tagsStr.split(",");
				Set<String> tags = new HashSet<>();
				for (String tag: tagsArray) {
					String tagTrimmed = tag.trim();
					tags.add(tagTrimmed);
				}
				FACTION_TAGS.put(factionId, tags);
			}
			
			// load character-faction compatibility data
			JSONArray charFactionsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("character", CHARACTER_FACTIONS_FILE, "chatter");
			for(int x = 0; x < charFactionsCSV.length(); x++)
			{
				JSONObject row = charFactionsCSV.getJSONObject(x);
				String characterId = row.getString("character");
				Map<String, Integer> factionCompat = new HashMap<>();
				Iterator<?> factionsAndGroups = row.keys();
				while( factionsAndGroups.hasNext() ) {
					String factionOrGroup = (String)factionsAndGroups.next();
					if (factionOrGroup.equals("fs_rowSource")) continue;
					if (factionOrGroup.equals("character")) continue;
					factionCompat.put(factionOrGroup, row.getInt(factionOrGroup));
				}
				CHARACTER_FACTIONS.put(characterId, factionCompat);
			}
			
			// load the actual characters
			JSONArray charCSV = Global.getSettings().getMergedSpreadsheetDataForMod("character", CHARACTERS_LIST, "chatter");
			for(int x = 0; x < charCSV.length(); x++)
			{
				JSONObject row = charCSV.getJSONObject(x);
				String characterId = row.getString("character");
				try {
					JSONObject characterEntry = Global.getSettings().loadJSON(CHARACTERS_DIR + characterId + ".json");
					ChatterCharacter character = new ChatterCharacter();
					character.id = characterId;
					character.name = characterEntry.optString("name", characterId);
					character.personalities = GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("personalities"));
					character.gender = GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("gender"));
					character.chance = (float)characterEntry.optDouble("chance", 1);
					character.talkativeness = (float)characterEntry.optDouble("chance", 1);
					
					JSONObject lines = characterEntry.getJSONObject("lines");
					Iterator<?> keys = lines.keys();
					while( keys.hasNext() ) {
						String key = (String)keys.next();
						MessageType type;
						try {
							type = MessageType.valueOf(StringHelper.flattenToAscii(key).toUpperCase());
						} catch (IllegalArgumentException ex) {
							continue;
						}
						JSONArray linesForKey = lines.getJSONArray(key);
						List<ChatterLine> linesForKeyList = new ArrayList<>();
						for (int i=0; i<linesForKey.length(); i++)
						{
							JSONObject lineEntry = linesForKey.getJSONObject(i);
							String text = lineEntry.optString("text");
							String sound = null;
							if (lineEntry.has("sound"))
								sound = lineEntry.getString("sound");
							linesForKeyList.add(new ChatterLine(text, sound));
						}
						character.lines.put(type, linesForKeyList);
					}
					
					character.allowedFactions = getAllowedFactionsForCharacter(character.id);
					
					CHARACTERS.add(character);
					CHARACTERS_MAP.put(characterId, character);
				} catch (IOException | JSONException ex) {	// can't read character file
					log.error("Error loading character " + characterId + ": " + ex);
				}
			}
			
			// map for getting faction ID based on hull ID prefix (e.g. ii_olympus is an II ship)
			JSONArray prefixes = Global.getSettings().getMergedSpreadsheetDataForMod("prefix", HULL_FACTION_PREFIX_FILE, "audio_plus");
			for(int x = 0; x < prefixes.length(); x++)
			{
				String prefix = "<unknown>";
				try {
					JSONObject row = prefixes.getJSONObject(x);
					prefix = row.getString("prefix");
					String faction = row.getString("faction");
					FACTION_HULL_PREFIXES.add(new String[]{prefix, faction});
				} catch (JSONException ex) {
					log.error("Failed to load hull ID prefix – faction mapping for " + prefix, ex);
				}
			}
		
			// map for getting faction ID based on ship name's prefix (e.g. TTS for Tri-Tachyon)
			JSONArray prefixes2 = Global.getSettings().getMergedSpreadsheetDataForMod("prefix", SHIP_NAME_FACTION_PREFIX_FILE, "audio_plus");
			for(int x = 0; x < prefixes2.length(); x++)
			{
				String prefix = "<unknown>";
				try {
					JSONObject row = prefixes2.getJSONObject(x);
					prefix = row.getString("prefix");
					String faction = row.getString("faction");
					FACTION_SHIPNAME_PREFIXES.add(new String[]{prefix, faction});
				} catch (JSONException ex) {
					log.error("Failed to load ship name prefix – faction mapping for " + prefix, ex);
				}
			}
			
		} catch (IOException | JSONException ex) {	// can't read CSV
			log.error(ex);
		}
		
		loaded = true;
	}
	
	public static String getCharacterForOfficer(PersonAPI captain, FleetMemberAPI ship, CombatEngineAPI engine)
	{
		// try to load officer if available
		String officerID = captain.getId();
		Map<String, String> savedOfficers = GeneralUtils.getSavedCharacters();
		
		if (savedOfficers.containsKey(officerID))
		{
			String saved = savedOfficers.get(officerID);
			// this check makes sure it doesn't break if a previously used character is deleted
			if (CHARACTERS_MAP.containsKey(saved)) return saved;
		}
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		WeightedRandomPicker<String> pickerBackup = new WeightedRandomPicker<>();
		
		String gender = "n";
		if (captain.isFemale()) gender = "f";
		else if (captain.isMale()) gender = "m";
		boolean isMission = engine.isMission();
		
		String factionId = captain.getFaction().getId();
		if (captain.getMemoryWithoutUpdate().contains("$originalFaction"))
		{
			factionId = captain.getMemoryWithoutUpdate().getString("$originalFaction");
		}
		
		if (engine.isMission())
		{
			factionId = getFactionIDFromShipNamePrefix(ship.getShipName());
			if (factionId.isEmpty())
				factionId = getFactionIDFromHullID(ship.getHullId());
			//log.info("Detected faction for ship " + ship.getShipName() + " as " + factionId);
		}
		
		for (ChatterCharacter character : CHARACTERS)
		{
			if (!isMission) {
				if (!character.gender.contains(gender)) continue;
			}
			if (ChatterConfig.factionSpecificCharacters && !character.allowedFactions.contains(factionId)) 
				continue;
			
			if (character.personalities.contains(captain.getPersonalityAPI().getId()))
			{
				picker.add(character.name, character.chance);
				pickerBackup.add(character.name, character.chance);
			}
		}
		
		// try to not have duplicate chatter chars among our fleet's officers (unless we've run out)
		if ( !ship.isAlly() && (engine.isInCampaign() || engine.isInCampaignSim()) )
		{
			Iterator<Map.Entry<String, String>> iter = savedOfficers.entrySet().iterator();
			while (iter.hasNext())
			{
				Map.Entry<String, String> tmp = iter.next();
				String existing = tmp.getValue();
				if (picker.getItems().contains(existing))
					picker.remove(existing);
			}
		}
		if (picker.isEmpty()) picker = pickerBackup;
		
		if (picker.isEmpty()) return "default";
		
		String charName = picker.pick();
		if (charName == null) return "default";
		
		log.info("Assigning character " + charName + " to officer " + captain.getName().getFullName());
		if (!ship.isAlly() && !isMission) 
			savedOfficers.put(captain.getId(), charName);
		return charName;
	}
	
	protected static Set<String> getAllowedFactionsForCharacter(String charId) {
		// this will probably break by running from static initializer, find some other way to do this
		Set<String> allowedFactions = new HashSet<>();
		for (FactionAPI faction : Global.getSector().getAllFactions()) {
			String factionId = faction.getId();
			
			if (isCharacterAllowedForFaction(charId, factionId)) {
				allowedFactions.add(factionId);
				//log.info("Character " + charId + " allows faction " + factionId);
			}
		}
		return allowedFactions;
	}
	
	protected static boolean isCharacterAllowedForFaction(String charId, String factionId)
	{
		if (!CHARACTER_FACTIONS.containsKey(charId)) {
			return true;	// character-faction entry not found; ignore factions
		}
		
		Map<String, Integer> allowedFactionsOrGroups = CHARACTER_FACTIONS.get(charId);
		int compatibility = 0;
		if (allowedFactionsOrGroups.containsKey(charId))
			compatibility = allowedFactionsOrGroups.get(charId);
		
		if (compatibility == 1) return true;	// explicitly allow this faction
		else if (compatibility == -1) return false;	// explicitly forbid this faction
		
		Set<String> tags = FACTION_TAGS.get(factionId);
		if (tags == null) return true;
		for (String tag: tags) {
			if (!allowedFactionsOrGroups.containsKey(tag)) continue;
			int groupCompat = allowedFactionsOrGroups.get(tag);
			if (groupCompat == 1) return true;
			else if (groupCompat == -1) return false;
			//else return false;
		}
		
		return false;
	}
	
	/**
	 * Used to guess the faction of the opposing fleet in missions, based on the hull ID of the first enemy ship
	 * @param hullID
	 * @return Faction ID, or empty string if no faction found
	 */
	public static String getFactionIDFromHullID(String hullID)
	{
		hullID = hullID.toLowerCase(Locale.ROOT);
		//log.info("Getting faction for hull ID " + hullID);
		if (hullID.endsWith("_cabal"))
			return "cabal";
		for (String[] mapEntry : FACTION_HULL_PREFIXES)
		{
			if (hullID.startsWith(mapEntry[0]))
				return mapEntry[1];
		}
		return "";
	}
	
	/**
	 * Used to guess the faction of the opposing fleet in missions, based on the name of the first enemy ship
	 * @param shipName
	 * @return Faction ID, or empty string if no faction found
	 */
	public static String getFactionIDFromShipNamePrefix(String shipName)
	{
		//log.info("Getting faction for ship name " + shipName);
		for (String[] mapEntry : FACTION_SHIPNAME_PREFIXES)
		{
			if (shipName.startsWith(mapEntry[0] + " "))
				return mapEntry[1];
		}
		return "";
	}
}