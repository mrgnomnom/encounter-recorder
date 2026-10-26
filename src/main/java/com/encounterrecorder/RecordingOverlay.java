package com.encounterrecorder;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;

/**
 * Says that a recording is running and nothing more: it carries no fight information
 */
class RecordingOverlay extends OverlayPanel
{
	private final EncounterRecorderPlugin plugin;
	private final EncounterRecorderConfig config;

	@Inject
	RecordingOverlay(EncounterRecorderPlugin plugin, EncounterRecorderConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Recording recording = plugin.getRecording();
		if (recording == null || !config.showIndicator())
		{
			return null;
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left("● REC")
			.leftColor(recording.hasFailed() ? Color.ORANGE : Color.RED)
			.right(recording.isAutomatic() ? "auto" : "")
			.build());
		return super.render(graphics);
	}
}
