package com.encounterrecorder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class EncounterRecorderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(EncounterRecorderPlugin.class);
		RuneLite.main(args);
	}
}
