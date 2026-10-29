package com.encounterrecorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Set;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MenuAction;
import net.runelite.api.gameval.InterfaceID;
import org.junit.Test;

public class PrivacyAndParsingTest
{
	@Test
	public void playerTargetsLoseTheirName()
	{
		assertEquals("<player>", Snap.menuTarget(MenuAction.PLAYER_SECOND_OPTION, "<col=ffffff>Zezima<col=ff00>  (level-126)"));
		assertEquals("<player>", Snap.menuTarget(MenuAction.WIDGET_TARGET_ON_PLAYER, "Ice Barrage -> Zezima"));
	}

	@Test
	public void hiddenNamesAreTakenOutOfTextHoweverTheyreWritten()
	{
		Set<String> names = Set.of("Mr Gnome", "Zezima", "Bob");
		assertEquals("<player> wishes to trade with you.", Names.scrub("Mr Gnome wishes to trade with you.", names));
		assertEquals("Walk here <player>  (level-126)", Names.scrub("Walk here zezima  (level-126)", names));
		assertEquals("<player> and <player>", Names.scrub("Mr\u00A0Gnome and mr_gnome", names));
		// Only whole names: "Bobcat" isn't Bob
		assertEquals("A Bobcat bites <player>.", Names.scrub("A Bobcat bites Bob.", names));
		assertEquals("No names here", Names.scrub("No names here", names));
	}

	@Test
	public void recordingLengthsComeFromTheEndOfTheFile()
	{
		// Stopped: the end line has the count, and its own tick is that count, not one more
		assertEquals(2598, Recordings.ticksFromTail("pose\":808}]}\n{\"t\":2598,\"c\":470542,\"e\":\"end\",\"reason\":\"stopped by hand\",\"ticks\":2598,\"endedMs\":1}\n"));
		// Still being written: the last whole line's tick, counted from 0, past a half-written one
		assertEquals(41, Recordings.ticksFromTail("{\"t\":40,\"c\":9,\"e\":\"tick\"}\n{\"t\":41,\"c\":10,\"e\":\"cl"));
		assertEquals(-1, Recordings.ticksFromTail(""));
		assertEquals("26 Oct, 23:21 · 2,598 ticks", Recordings.entry("20261026-232157-Demo.jsonl", 0, 1, 2598).details(2026));
	}

	@Test
	public void unnamedRecordingsAreNamedAfterThePlace()
	{
		assertEquals("Edgeville", Areas.name(12342, false));
		// Scurrius's room is copied from the Varrock Sewers' map square
		assertEquals("Scurrius", Areas.name(13210, true));
		assertEquals("Varrock Sewers", Areas.name(13210, false));
		assertEquals("The Fractured Archive", Areas.name(16471, true));
		assertNull(Areas.name(1, false));
		assertEquals("Kril-Tsutsaroth", Recording.safeLabel("K'ril Tsutsaroth"));
	}

	@Test
	public void recordingsAreListedByTheirNameNewestFirst()
	{
		assertEquals("Demo", Recordings.entry("20261026-232157-Demo.jsonl", 0, 1).name);
		assertEquals("vardorvis p2", Recordings.entry("20261025-101500-vardorvis-p2.jsonl", 0, 1).name);
		assertEquals("my-own-file", Recordings.entry("my-own-file.jsonl", 0, 1).name);
		assertEquals(2026, Recordings.entry("20261026-232157-Demo.jsonl", 0, 1).when.getYear());
		assertEquals("26 Oct, 23:21", Recordings.entry("20261026-232157-Demo.jsonl", 0, 1).recordedAt(2026));
		assertEquals("26 Oct 2026, 23:21", Recordings.entry("20261026-232157-Demo.jsonl", 0, 1).recordedAt(2027));
	}

	@Test
	public void importedAndRenamedRecordingsKeepTheirPlaceInTime()
	{
		java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 10, 27, 9, 5, 0);
		assertEquals("20261026-232157-Demo.jsonl", Recordings.importName("20261026-232157-Demo.jsonl", t));
		assertEquals("20261027-090500-demo-ticks-8-16.jsonl", Recordings.importName("demo-ticks-8-16.jsonl", t));
		assertEquals("20261026-232157-Poopsoul-fight.jsonl", Recordings.renamed("20261026-232157-Demo.jsonl", "Poopsoul fight"));
	}

	@Test
	public void onlyRecordingsCanBeImported()
	{
		assertTrue(Recordings.isHeader("{\"t\":0,\"c\":1,\"e\":\"header\",\"format\":5}"));
		assertFalse(Recordings.isHeader("hello"));
		assertFalse(Recordings.isHeader(null));
		// An imported name keeps its stamp and number but loses anything a file name shouldn't hold
		assertEquals("20261026-232157-TFA_2.jsonl", Recordings.importName("20261026-232157-TFA_2.jsonl", java.time.LocalDateTime.now()));
		assertEquals("20261026-232157-a-b.jsonl", Recordings.importName("20261026-232157-a:b.jsonl", java.time.LocalDateTime.now()));
	}

	@Test
	public void repeatedNamesGetTheNextNumber()
	{
		java.util.List<String> folder = new java.util.ArrayList<>();
		String first = Recordings.numbered(folder, "20261027-100000-TFA.jsonl", null);
		assertEquals("20261027-100000-TFA.jsonl", first);
		folder.add(first);
		String second = Recordings.numbered(folder, "20261027-110000-TFA.jsonl", null);
		assertEquals("20261027-110000-TFA_1.jsonl", second);
		folder.add(second);
		assertEquals("20261027-120000-TFA_2.jsonl", Recordings.numbered(folder, "20261027-120000-TFA.jsonl", null));
		assertEquals("TFA-1", Recordings.entry(second, 0, 0).name);
		// Importing a numbered one that's taken moves on to the next number; renaming a file to its own name keeps it
		assertEquals("20261027-110000-TFA_2.jsonl", Recordings.numbered(folder, second, null));
		assertEquals(second, Recordings.numbered(folder, second, second));
		// Same name, any case
		assertEquals("20261027-130000-tfa_2.jsonl", Recordings.numbered(folder, "20261027-130000-tfa.jsonl", null));
	}

	@Test
	public void replayLinksCarryOnlyTheFileName()
	{
		assertEquals("https://encounter-replay.pages.dev/#session=20261026-131912-GE2.jsonl",
			EncounterRecorderPlugin.replayLink("20261026-131912-GE2.jsonl"));
		assertEquals("https://encounter-replay.pages.dev/", EncounterRecorderPlugin.replayLink(null));
	}

	@Test
	public void bankPinInterfacesAreRecognised()
	{
		assertTrue(EncounterRecorderPlugin.isBankPinInterface(InterfaceID.BANKPIN_KEYPAD));
		assertTrue(EncounterRecorderPlugin.isBankPinInterface(InterfaceID.BANKPIN_SETTINGS));
		assertFalse(EncounterRecorderPlugin.isBankPinInterface(InterfaceID.INVENTORY));
	}

	@Test
	public void onlyLocalChatIsEverRecordedFromPlayers()
	{
		assertEquals(EncounterRecorderPlugin.ChatKind.LOCAL, EncounterRecorderPlugin.chatKind(ChatMessageType.PUBLICCHAT));
		assertEquals(EncounterRecorderPlugin.ChatKind.LOCAL, EncounterRecorderPlugin.chatKind(ChatMessageType.AUTOTYPER));
		assertEquals(EncounterRecorderPlugin.ChatKind.GAME, EncounterRecorderPlugin.chatKind(ChatMessageType.GAMEMESSAGE));
		for (ChatMessageType never : new ChatMessageType[]{
			ChatMessageType.PRIVATECHAT, ChatMessageType.PRIVATECHATOUT, ChatMessageType.MODPRIVATECHAT,
			ChatMessageType.FRIENDSCHAT, ChatMessageType.CLAN_CHAT, ChatMessageType.CLAN_GUEST_CHAT,
			ChatMessageType.CLAN_GIM_CHAT, ChatMessageType.FRIENDNOTIFICATION, ChatMessageType.TRADEREQ,
			ChatMessageType.BROADCAST, ChatMessageType.LOGINLOGOUTNOTIFICATION})
		{
			assertEquals(never.name(), EncounterRecorderPlugin.ChatKind.NEVER, EncounterRecorderPlugin.chatKind(never));
		}
	}

	@Test
	public void npcTargetsKeepTheirNameWithoutTags()
	{
		assertEquals("Vardorvis  (level-1040)", Snap.menuTarget(MenuAction.NPC_SECOND_OPTION, "<col=ffff00>Vardorvis<col=ff00>  (level-1040)"));
	}

	@Test
	public void autoRecordIdsIgnoreJunk()
	{
		Set<Integer> ids = EncounterRecorderPlugin.parseIds(" 16677, abc ,12223,, ");
		assertEquals(2, ids.size());
		assertTrue(ids.contains(16677));
		assertTrue(ids.contains(12223));
		assertTrue(EncounterRecorderPlugin.parseIds(null).isEmpty());
	}

	@Test
	public void labelsAreSafeFileNames()
	{
		assertEquals("Vardorvis-p2", Recording.safeLabel("  Vardorvis / p2?  "));
		assertEquals("session", Recording.safeLabel("<>:"));
		assertEquals("session", Recording.safeLabel(null));
		assertEquals(40, Recording.safeLabel("a".repeat(100)).length());
	}
}
