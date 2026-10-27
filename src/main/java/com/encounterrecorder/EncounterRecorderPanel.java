package com.encounterrecorder;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

class EncounterRecorderPanel extends PluginPanel
{
	private final JTextField label = new JTextField();
	private final JTextField note = new JTextField();
	private final JButton startStop = new JButton("Start recording");
	private final JCheckBox players = new JCheckBox("Players around you");
	private final JTextArea status = new JTextArea("Not recording");
	private final JButton replay = new JButton("Open replay");
	private boolean recording;
	/** The recording saved last, which Open replay opens */
	private String lastSaved;

	EncounterRecorderPanel(EncounterRecorderPlugin plugin)
	{
		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel controls = new JPanel(new GridLayout(0, 1, 0, 4));
		controls.add(new JLabel("Session name"));
		label.setToolTipText("Goes into the file name, e.g. vardorvis-p2");
		controls.add(label);
		players.setToolTipText("<html>Record what other players nearby are doing: movement, animations, gear,<br>"
			+ "hitsplats and projectiles. Stored by index, never by name. NPCs are always recorded.</html>");
		players.addActionListener(e -> plugin.setRecordPlayers(players.isSelected()));
		controls.add(players);
		startStop.addActionListener(e ->
		{
			if (recording)
			{
				plugin.requestStop();
			}
			else
			{
				plugin.requestStart(label.getText());
			}
		});
		controls.add(startStop);

		controls.add(new JLabel("Marker note"));
		note.setToolTipText("Written with the next marker, e.g. 'took the spike disk on Melee'");
		controls.add(note);
		JButton mark = new JButton("Drop marker");
		mark.addActionListener(e -> plugin.requestMark(note.getText()));
		controls.add(mark);

		replay.setToolTipText("Open Encounter Replay in your browser, on the recording saved last");
		replay.addActionListener(e -> plugin.openReplay(lastSaved));
		controls.add(replay);

		JButton open = new JButton("Open sessions folder");
		open.addActionListener(e -> plugin.openSessionsFolder());
		controls.add(open);

		status.setEditable(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		status.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

		add(controls, BorderLayout.NORTH);
		add(status, BorderLayout.CENTER);
	}

	void showRecordPlayers(boolean on)
	{
		players.setSelected(on);
	}

	String note()
	{
		return note.getText();
	}

	void showRecording(String text)
	{
		recording = true;
		// The choice is fixed when a recording starts
		players.setEnabled(false);
		startStop.setText("Stop recording");
		status.setText("Recording\n" + text);
	}

	void showStopped(String fileName)
	{
		recording = false;
		players.setEnabled(true);
		startStop.setText("Start recording");
		lastSaved = fileName;
		status.setText("Saved " + fileName + "\nin plugin-data/encounter-recorder/sessions");
	}
}
