package com.encounterrecorder;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.Filepath;

/**
 * The side panel: a Record section to start and stop recordings and drop markers, and a Replays section
 * listing every recording, each opening in Encounter Replay with a click.
 */
class EncounterRecorderPanel extends PluginPanel
{
	private final EncounterRecorderPlugin plugin;
	private final JTextField label = new JTextField();
	private final JTextField note = new JTextField();
	private final JButton startStop = new JButton("Start recording");
	private final JCheckBox players = new JCheckBox("Players around you");
	private final JTextArea status = new JTextArea("Not recording");
	private final JPanel replayList = new JPanel();
	private final JLabel replayCount = new JLabel();
	private boolean recording;
	/** The newest recording, which Open replay opens */
	private String newest;

	EncounterRecorderPanel(EncounterRecorderPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		// ---------- Record ----------
		content.add(section("Record", null));
		content.add(caption("Session name"));
		label.setToolTipText("Goes into the file name, e.g. vardorvis-p2");
		content.add(fill(label));
		players.setToolTipText("<html>Record what other players nearby are doing: movement, animations, gear,<br>"
			+ "hitsplats and projectiles. Stored by index; see Hide player names in the settings. NPCs are always recorded.</html>");
		players.addActionListener(e -> plugin.setRecordPlayers(players.isSelected()));
		content.add(fill(players));
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
		content.add(gap(4));
		content.add(fill(startStop));
		content.add(gap(8));
		content.add(caption("Marker note"));
		note.setToolTipText("Written with the next marker, e.g. 'took the spike disk on Melee'");
		content.add(fill(note));
		JButton mark = new JButton("Drop marker");
		mark.addActionListener(e -> plugin.requestMark(note.getText()));
		content.add(gap(4));
		content.add(fill(mark));
		content.add(gap(8));
		status.setEditable(false);
		status.setLineWrap(true);
		status.setWrapStyleWord(true);
		status.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		status.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		// Not fill(): the status grows a line or two while recording
		JPanel statusBox = new JPanel(new BorderLayout());
		statusBox.add(status, BorderLayout.CENTER);
		statusBox.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(statusBox);

		// ---------- Replays ----------
		content.add(gap(16));
		replayCount.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		replayCount.setFont(FontManager.getRunescapeSmallFont());
		content.add(section("Replays", replayCount));
		JButton open = new JButton("Open replay");
		open.setToolTipText("Open Encounter Replay in your browser, on your newest recording");
		open.addActionListener(e -> plugin.openReplay(newest));
		content.add(fill(open));
		content.add(gap(4));
		JButton importButton = new JButton("Import…");
		importButton.setToolTipText("Copy a recording from anywhere (one a friend sent, or one exported from the website) into your recordings");
		importButton.addActionListener(e -> chooseImport());
		content.add(fill(importButton));
		content.add(gap(6));
		replayList.setLayout(new BoxLayout(replayList, BoxLayout.Y_AXIS));
		content.add(replayList);
		replayList.setAlignmentX(Component.LEFT_ALIGNMENT);

		add(content, BorderLayout.NORTH);
		showReplays(List.of());
	}

	@Override
	public void onActivate()
	{
		plugin.refreshReplays();
	}

	/** Every recording in the sessions folder, newest first; a click opens one in Encounter Replay */
	void showReplays(List<Recordings.Entry> entries)
	{
		newest = entries.isEmpty() ? null : entries.get(0).fileName;
		replayCount.setText(entries.isEmpty() ? "" : String.valueOf(entries.size()));
		replayList.removeAll();
		if (entries.isEmpty())
		{
			JLabel none = new JLabel("No recordings yet");
			none.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			replayList.add(fill(none));
		}
		for (Recordings.Entry e : entries)
		{
			replayList.add(fill(row(e)));
			replayList.add(gap(2));
		}
		replayList.revalidate();
		replayList.repaint();
	}

	private JComponent row(Recordings.Entry e)
	{
		JLabel row = new JLabel(e.name);
		row.setOpaque(true);
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		row.setToolTipText("<html>" + escape(e.describe()) + "<br>Click to open it in Encounter Replay; right-click for more</html>");
		row.setComponentPopupMenu(menu(e));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseEntered(MouseEvent ev)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent ev)
			{
				row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
			}

			@Override
			public void mouseClicked(MouseEvent ev)
			{
				if (ev.getButton() == MouseEvent.BUTTON1)
				{
					plugin.openReplay(e.fileName);
				}
			}
		});
		return row;
	}

	/** Right-click on a recording: open it, rename it or delete it */
	private JPopupMenu menu(Recordings.Entry e)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem open = new JMenuItem("Open in Encounter Replay");
		open.addActionListener(a -> plugin.openReplay(e.fileName));
		JMenuItem rename = new JMenuItem("Rename…");
		rename.addActionListener(a -> rename(e));
		JMenuItem delete = new JMenuItem("Delete…");
		delete.addActionListener(a -> delete(e));
		menu.add(open);
		menu.add(rename);
		menu.addSeparator();
		menu.add(delete);
		return menu;
	}

	private void rename(Recordings.Entry e)
	{
		if (plugin.isRecordingNow(e.fileName))
		{
			showProblem("That's the recording being made now: stop it first.");
			return;
		}
		Object answer = JOptionPane.showInputDialog(this, "New name for " + e.name + ":", "Rename recording",
			JOptionPane.PLAIN_MESSAGE, null, null, e.name);
		if (answer != null && !answer.toString().trim().isEmpty() && !answer.toString().trim().equals(e.name))
		{
			plugin.renameRecording(e.fileName, answer.toString().trim());
		}
	}

	private void delete(Recordings.Entry e)
	{
		if (plugin.isRecordingNow(e.fileName))
		{
			showProblem("That's the recording being made now: stop it first.");
			return;
		}
		int choice = JOptionPane.showConfirmDialog(this, "Delete " + e.name + "?\nIt's deleted for good.", "Delete recording",
			JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
		if (choice == JOptionPane.OK_OPTION)
		{
			plugin.deleteRecording(e.fileName);
		}
	}

	private void chooseImport()
	{
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsOpen()
			.setAcceptsFiles()
			.setDialogTitle("Import a recording")
			.addExtensionFilter("Encounter Recorder recordings (*.jsonl)", "jsonl")
			.showDialog(this);
		if (chosen != null && !chosen.isEmpty())
		{
			plugin.importRecording(chosen.get(0));
		}
	}

	void showProblem(String message)
	{
		JOptionPane.showMessageDialog(this, message, "Encounter Recorder", JOptionPane.WARNING_MESSAGE);
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
		status.setText("Saved " + Recordings.entry(fileName, 0, 0).name + "\nIt's at the top of Replays below");
	}

	private static JComponent section(String title, JComponent right)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(0, 0, 4, 0)));
		JLabel name = new JLabel(title);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.WEST);
		if (right != null)
		{
			row.add(right, BorderLayout.EAST);
		}
		JPanel wrap = fill(row);
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, wrap.getPreferredSize().height));
		return wrap;
	}

	private static JLabel caption(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 2, 0));
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	/** Stretches a component across the panel, as BoxLayout otherwise keeps each at its own width */
	private static JPanel fill(JComponent c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.add(c, BorderLayout.CENTER);
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
		return p;
	}

	private static Component gap(int px)
	{
		Component g = Box.createVerticalStrut(px);
		((JComponent) g).setAlignmentX(Component.LEFT_ALIGNMENT);
		return g;
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
