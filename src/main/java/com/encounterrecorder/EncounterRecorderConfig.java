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

	@ConfigSection(
		name = "Replay",
		description = "Where the Open replay button takes you",
		position = 30
	)
	String replaySection = "replay";

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

	@ConfigItem(
		keyName = "markHotkey",
		name = "Mark hotkey",
		description = "Drops a marker into the recording, with the note typed in the side panel",
		section = whenSection,
		position = 3
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
		keyName = "recordPlayerNames",
		name = "Player names",
		description = "Record players' names (yours and those around you) so replays can label them. "
			+ "Turn off to store players by index only. Files stay on your computer either way.",
		section = whatSection,
		position = 12
	)
	default boolean recordPlayerNames()
	{
		return true;
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

	@ConfigItem(
		keyName = "replayUrl",
		name = "Replay address",
		description = "The Encounter Replay page the Open replay button opens. It reads your recordings and game cache from your own RuneLite folder; nothing is uploaded.",
		section = replaySection,
		position = 31
	)
	default String replayUrl()
	{
		return "http://127.0.0.1:5180/";
	}
}
