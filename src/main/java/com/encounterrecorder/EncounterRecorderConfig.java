package com.encounterrecorder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(EncounterRecorderConfig.GROUP)
public interface EncounterRecorderConfig extends Config
{
	String GROUP = "encounter-recorder";

	@ConfigSection(
		name = "When to record",
		description = "Start and stop recordings by hand, or let a boss start one",
		position = 0
	)
	String whenSection = "when";

	@ConfigSection(
		name = "What to record",
		description = "Which parts of the game end up in the file",
		position = 10
	)
	String whatSection = "what";

	@ConfigSection(
		name = "Display",
		description = "How the recorder shows itself",
		position = 20
	)
	String displaySection = "display";

	@ConfigItem(
		keyName = "autoRecordNpcIds",
		name = "Auto-record NPC IDs",
		description = "Comma-separated NPC IDs. A recording starts when one appears and stops once none have been seen for a while. Leave empty to record by hand only.",
		section = whenSection,
		position = 1
	)
	default String autoRecordNpcIds()
	{
		return "";
	}

	@Range(min = 5, max = 1000)
	@Units(Units.TICKS)
	@ConfigItem(
		keyName = "autoStopTicks",
		name = "Auto-stop after",
		description = "Stop an automatic recording once none of its NPCs have been seen for this many ticks",
		section = whenSection,
		position = 2
	)
	default int autoStopTicks()
	{
		return 50;
	}

	@Range(max = 100000)
	@Units(Units.TICKS)
	@ConfigItem(
		keyName = "maxTicks",
		name = "Length limit",
		description = "Stop a recording once it's this many ticks long: 5,000 is 50 minutes. A busy raid fills about "
			+ "12 MB per 1,000 ticks, solo bossing far less. 0 for no limit.",
		section = whenSection,
		position = 3
	)
	default int maxTicks()
	{
		return 5000;
	}

	@ConfigItem(
		keyName = "recordHotkey",
		name = "Start recording hotkey",
		description = "Starts a recording, named from the side panel's Session name; pressed again, stops it",
		section = whenSection,
		position = 4
	)
	default Keybind recordHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "markHotkey",
		name = "Drop marker hotkey",
		description = "Drops a marker into the recording, with the note typed in the side panel",
		section = whenSection,
		position = 5
	)
	default Keybind markHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "recordOtherPlayers",
		name = "Players around you",
		description = "Record what other players nearby are doing: movement, animations, gear, hitsplats and projectiles. Stored by index, never by name. Also a checkbox in the panel; it applies from the next recording.",
		section = whatSection,
		position = 11
	)
	default boolean recordOtherPlayers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hidePlayerNames",
		name = "Hide player names",
		description = "Leave every player's name out of recordings, yours too: players are stored by index, and names "
			+ "of players nearby are replaced with <player> in chat, game messages and menu targets.",
		section = whatSection,
		position = 12
	)
	default boolean hidePlayerNames()
	{
		return false;
	}

	@ConfigItem(
		keyName = "recordLocalChat",
		name = "Local chat",
		description = "Record public chat typed by you and players nearby, linked to who said it by index, never by name. "
			+ "Private, friends, clan and group chat are never recorded.",
		section = whatSection,
		position = 12
	)
	default boolean recordLocalChat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordSceneLayout",
		name = "Arena layout",
		description = "On each map load, record collision flags. Inside instances, also record tile heights, instance chunks and every object, since the layout is built per fight",
		section = whatSection,
		position = 13
	)
	default boolean recordSceneLayout()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordVars",
		name = "Varbits and varps",
		description = "Record every varbit and varp change. Boss state, HP bars and timers often live here.",
		section = whatSection,
		position = 14
	)
	default boolean recordVars()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showIndicator",
		name = "Recording indicator",
		description = "Show a small REC marker while a recording is running",
		section = displaySection,
		position = 21
	)
	default boolean showIndicator()
	{
		return true;
	}
}
