package com.encounterrecorder;

import static org.junit.Assert.assertEquals;
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
