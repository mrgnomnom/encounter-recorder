package com.encounterrecorder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.GraphicsObject;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Point;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Scene;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.DecorativeObjectDespawned;
import net.runelite.api.events.DecorativeObjectSpawned;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GraphicChanged;
import net.runelite.api.events.GraphicsObjectCreated;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.PlayerChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarClientIntChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WallObjectDespawned;
import net.runelite.api.events.WallObjectSpawned;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import java.awt.event.MouseEvent;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.Text;

/**
 * Writes boss encounters to JSON-lines files, one line per event. Every line carries "t", the tick
 * of the recording it belongs to, and "c", the client cycle (20 ms) it arrived on. Events the server
 * sends for a tick arrive before that tick's GameTick, so they share its "t" with the "tick" line that
 * snapshots the state at the end of the tick.
 */
@Slf4j
@PluginDescriptor(
	name = "Encounter Recorder",
	internalName = "encounter-recorder",
	description = "Records boss encounters tick by tick to a local file for research. Shows nothing in game while you fight.",
	tags = {"boss", "record", "log", "research", "timings", "data", "trainer"}
)
public class EncounterRecorderPlugin extends Plugin
{
	static final int FORMAT_VERSION = 6;

	@Inject
	private Client client;
	@Inject
	private ClientThread clientThread;
	@Inject
	private EncounterRecorderConfig config;
	@Inject
	private Gson gson;
	@Inject
	private ConfigManager configManager;

	/**
	 * The one page Open replay goes to. It's fixed rather than a setting so nobody can be talked into
	 * pointing the plugin at a lookalike page that would ask for their RuneLite folders
	 */
	static final String REPLAY_URL = "https://encounter-replay.pages.dev/";
	/** How many frames (20 ms each) a Walk here waits for the client to set where it's walking you */
	private static final int WALK_WAIT_CYCLES = 5;
	/** The stop reason when a recording reaches the length limit */
	private static final String LIMIT_REACHED = "reached the length limit";

	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private MouseManager mouseManager;
	private String lastLayout;

	/**
	 * Every mouse press, including ones that hit nothing a menu acts on, so misclicks can be measured
	 * against the buttons in the ui_layout lines. Presses arrive on the input thread and are written
	 * from the client thread, which also applies the bank PIN guard
	 */
	private final MouseAdapter pressListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent e)
		{
			if (recording != null)
			{
				int x = e.getX();
				int y = e.getY();
				int button = e.getButton();
				clientThread.invokeLater(() ->
				{
					if (recording == null || bankPinShowing())
					{
						return;
					}
					JsonObject line = emit("press");
					line.addProperty("mx", x);
					line.addProperty("my", y);
					line.addProperty("button", button);
				});
			}
			return e;
		}
	};
	@Inject
	private RecordingOverlay overlay;

	private EncounterRecorderPanel panel;
	private NavigationButton navButton;

	@Getter
	private Recording recording;
	private Set<Integer> autoIds = Collections.emptySet();
	private final Set<Integer> definedNpcs = new HashSet<>();
	private final Set<Integer> definedObjects = new HashSet<>();
	private final Set<Integer> definedItems = new HashSet<>();
	private final Map<Actor, Integer> moveSets = new WeakHashMap<>();
	private boolean players;
	private boolean names;
	private boolean bankPinOpen;
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
	private boolean sceneDirty;
	/** The client cycle of a Walk here waiting for the tile the client works out for it, or -1 */
	private int walkClickCycle = -1;
	private int walkWaited;
	private LocalPoint lastDestination;

	private final HotkeyListener recordHotkey = new HotkeyListener(() -> config.recordHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			String label = panel.label();
			clientThread.invoke(() -> toggleRecording(label));
		}
	};

	private final HotkeyListener markHotkey = new HotkeyListener(() -> config.markHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			String note = panel.note();
			clientThread.invoke(() -> mark(note));
		}
	};

	@Provides
	EncounterRecorderConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(EncounterRecorderConfig.class);
	}

	@Override
	protected void startUp()
	{
		// The replay's address used to be a setting: drop whatever was saved for it
		configManager.unsetConfiguration(EncounterRecorderConfig.GROUP, "replayUrl");
		// "Player names" (on unless turned off) became "Hide player names": carry over a choice to leave them out
		String oldNames = configManager.getConfiguration(EncounterRecorderConfig.GROUP, "recordPlayerNames");
		if (oldNames != null)
		{
			if ("false".equals(oldNames))
			{
				configManager.setConfiguration(EncounterRecorderConfig.GROUP, "hidePlayerNames", true);
			}
			configManager.unsetConfiguration(EncounterRecorderConfig.GROUP, "recordPlayerNames");
		}
		autoIds = parseIds(config.autoRecordNpcIds());
		panel = new EncounterRecorderPanel(this);
		panel.showRecordPlayers(config.recordOtherPlayers());
		refreshReplays();
		navButton = NavigationButton.builder()
			.tooltip("Encounter Recorder")
			.icon(icon())
			.priority(10)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(recordHotkey);
		keyManager.registerKeyListener(markHotkey);
		mouseManager.registerMouseListener(pressListener);
	}

	@Override
	protected void shutDown()
	{
		if (recording != null)
		{
			stopRecording("plugin turned off");
		}
		keyManager.unregisterKeyListener(recordHotkey);
		keyManager.unregisterKeyListener(markHotkey);
		mouseManager.unregisterMouseListener(pressListener);
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (EncounterRecorderConfig.GROUP.equals(event.getGroup()))
		{
			autoIds = parseIds(config.autoRecordNpcIds());
			boolean on = config.recordOtherPlayers();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showRecordPlayers(on);
				}
			});
		}
	}

	void setRecordPlayers(boolean on)
	{
		configManager.setConfiguration(EncounterRecorderConfig.GROUP, "recordOtherPlayers", on);
	}

	static Set<Integer> parseIds(String text)
	{
		Set<Integer> ids = new HashSet<>();
		for (String part : Text.fromCSV(text == null ? "" : text))
		{
			try
			{
				ids.add(Integer.parseInt(part.trim()));
			}
			catch (NumberFormatException e)
			{
				log.debug("Ignoring auto-record entry {}", part);
			}
		}
		return Collections.unmodifiableSet(ids);
	}

	Filepath sessionsRoot() throws IOException
	{
		return getPluginDirectory();
	}

	// Panel actions arrive on the Swing thread and hop over to the client thread

	void requestStart(String label)
	{
		clientThread.invoke(() ->
		{
			if (recording == null)
			{
				startRecording(label, false, Collections.emptySet());
			}
		});
	}

	void requestStop()
	{
		clientThread.invoke(() ->
		{
			if (recording != null)
			{
				stopRecording("stopped by hand");
			}
		});
	}

	void requestMark(String note)
	{
		clientThread.invoke(() -> mark(note));
	}

	private Filepath sessionsDir() throws IOException
	{
		return sessionsRoot().joinSegment("sessions");
	}

	/** Whether a recording is the one being written now, which can't be renamed or deleted */
	boolean isRecordingNow(String fileName)
	{
		Recording r = recording;
		return r != null && r.getFileName().equals(fileName);
	}

	void deleteRecording(String fileName)
	{
		inBackground(() ->
		{
			sessionsDir().joinSegment(fileName).delete();
			return null;
		}, "Couldn't delete " + fileName);
	}

	void renameRecording(String fileName, String newName)
	{
		inBackground(() ->
		{
			Filepath dir = sessionsDir();
			String to = Recordings.numbered(Recordings.names(dir), Recordings.renamed(fileName, newName), fileName);
			if (!to.equals(fileName))
			{
				dir.joinSegment(fileName).moveTo(dir.joinSegment(to));
			}
			return null;
		}, "Couldn't rename " + fileName);
	}

	/**
	 * The recordings folder's location, for pasting into a file manager: plugins may not open folders
	 * themselves. Found off the game's threads, since it makes the folder if it isn't there yet
	 */
	void findSessionsFolder(Consumer<String> found)
	{
		Thread finder = new Thread(() ->
		{
			try
			{
				Filepath dir = sessionsDir();
				dir.createDirectories();
				String path = dir.toString();
				SwingUtilities.invokeLater(() -> found.accept(path));
			}
			catch (IOException e)
			{
				log.warn("Could not find the recordings folder", e);
				SwingUtilities.invokeLater(() ->
				{
					if (panel != null)
					{
						panel.showProblem("Couldn't find the recordings folder: " + e.getMessage());
					}
				});
			}
		}, "encounter-recorder-folder");
		finder.setDaemon(true);
		finder.start();
	}

	/** Copies a recording from anywhere into the sessions folder, leaving the original where it was */
	void importRecording(Filepath from)
	{
		inBackground(() ->
		{
			if (!Recordings.isRecording(from))
			{
				return from.getFileName() + " isn't an Encounter Recorder recording.";
			}
			LocalDateTime time = LocalDateTime.ofInstant(from.getLastModifiedTime().toInstant(), java.time.ZoneId.systemDefault());
			Filepath dir = sessionsDir();
			dir.createDirectories();
			String to = Recordings.numbered(Recordings.names(dir), Recordings.importName(from.getFileName(), time), null);
			from.copyTo(dir.joinSegment(to));
			return null;
		}, "Couldn't import " + from.getFileName());
	}

	private interface FileJob
	{
		/** Does the work; returns a message for the player when it can't be done, or null */
		String run() throws IOException;
	}

	/** File work off the game's threads, then the list refreshed and any problem shown */
	private void inBackground(FileJob job, String failure)
	{
		Thread worker = new Thread(() ->
		{
			String problem;
			try
			{
				problem = job.run();
			}
			catch (IOException | RuntimeException e)
			{
				log.warn(failure, e);
				problem = failure + ": " + e.getMessage();
			}
			String message = problem;
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null && message != null)
				{
					panel.showProblem(message);
				}
			});
			refreshReplays();
		}, "encounter-recorder-files");
		worker.setDaemon(true);
		worker.start();
	}

	/** Lists the recordings in the background and shows them in the panel */
	void refreshReplays()
	{
		Thread lister = new Thread(() ->
		{
			List<Recordings.Entry> found;
			try
			{
				found = Recordings.list(sessionsDir());
			}
			catch (IOException e)
			{
				found = Collections.emptyList();
			}
			List<Recordings.Entry> entries = found;
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showReplays(entries);
				}
			});
		}, "encounter-recorder-list");
		lister.setDaemon(true);
		lister.start();
	}

	/**
	 * Opens Encounter Replay in the browser, on a saved recording if there is one. The page reads the
	 * recording from the player's own RuneLite folder, so only its file name goes in the link
	 */
	void openReplay(String fileName)
	{
		LinkBrowser.browse(replayLink(fileName));
	}

	/** The replay page's address, opening a recording by its file name */
	static String replayLink(String fileName)
	{
		return fileName == null ? REPLAY_URL : REPLAY_URL + "#session=" + fileName;
	}

	private void startRecording(String label, boolean automatic, Set<Integer> watched)
	{
		if (label == null || label.trim().isEmpty())
		{
			label = unnamedLabel();
		}
		recording = new Recording(gson, this::sessionsRoot, label, automatic, watched);
		lastLayout = null;
		lastWidgets.clear();
		definedNpcs.clear();
		definedObjects.clear();
		definedItems.clear();
		moveSets.clear();
		// Fixed for the whole session, so a file never switches halfway between with and without players
		players = config.recordOtherPlayers();
		names = !config.hidePlayerNames();
		seenProjectiles.clear();
		walkClickCycle = -1;
		lastDestination = null;

		JsonObject header = emit("header");
		header.addProperty("format", FORMAT_VERSION);
		header.addProperty("label", label);
		header.addProperty("automatic", automatic);
		header.addProperty("revision", client.getRevision());
		header.addProperty("world", client.getWorld());
		JsonArray worldTypes = new JsonArray();
		client.getWorldType().forEach(type -> worldTypes.add(type.name()));
		header.add("worldTypes", worldTypes);
		header.addProperty("startedMs", System.currentTimeMillis());
		header.addProperty("startTick", client.getTickCount());
		header.addProperty("otherPlayers", players);
		header.addProperty("playerNames", names);
		header.addProperty("localChat", config.recordLocalChat());
		header.addProperty("sceneLayout", config.recordSceneLayout());
		header.addProperty("vars", config.recordVars());
		// Click positions in "click" lines are canvas pixels at this size
		header.addProperty("canvasW", client.getCanvasWidth());
		header.addProperty("canvasH", client.getCanvasHeight());
		header.addProperty("resized", client.isResized());

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			dumpScene();
			WorldView wv = client.getTopLevelWorldView();
			for (NPC npc : wv.npcs())
			{
				npcSpawned(npc, true);
			}
			if (client.getLocalPlayer() != null)
			{
				look(client.getLocalPlayer());
			}
			if (players)
			{
				for (Player player : wv.players())
				{
					if (player != client.getLocalPlayer())
					{
						JsonObject spawn = emit("player_spawn");
						spawn.add("who", Snap.ref(client, player));
						nameOf(spawn, player);
						look(player);
					}
				}
			}
			emit("ui_tab").addProperty("tab", client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL));
			containers(client.getItemContainer(InventoryID.INV), "inv");
			containers(client.getItemContainer(InventoryID.WORN), "worn");
			for (Skill skill : Skill.values())
			{
				JsonObject s = emit("stat");
				s.addProperty("skill", skill.name());
				s.addProperty("xp", client.getSkillExperience(skill));
				s.addProperty("level", client.getRealSkillLevel(skill));
				s.addProperty("boosted", client.getBoostedSkillLevel(skill));
			}
		}
		log.debug("Recording {} started", recording.getFileName());
		refreshPanel();
	}

	private void stopRecording(String reason)
	{
		JsonObject end = emit("end");
		end.addProperty("reason", reason);
		end.addProperty("ticks", recording.getTicks());
		end.addProperty("endedMs", System.currentTimeMillis());
		String name = recording.getFileName();
		String why = reason.equals(LIMIT_REACHED)
			? String.format("Stopped at the %,d-tick length limit (see settings)", recording.getTicks())
			: null;
		recording.close();
		recording = null;
		log.debug("Recording {} stopped: {}", name, reason);
		if (panel != null)
		{
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.showStopped(name, why);
					refreshReplays();
				}
			});
		}
	}

	/** The start recording hotkey: starts a recording, or stops the one running, as the panel's button does */
	private void toggleRecording(String label)
	{
		// Like a marker, a start or stop during PIN entry would time the keypresses
		if (bankPinShowing())
		{
			return;
		}
		if (recording == null)
		{
			startRecording(label, false, Collections.emptySet());
		}
		else
		{
			stopRecording("stopped by hand");
		}
	}

	/**
	 * Where the client is walking you, each time it changes: the tile it puts the red flag on and sends to
	 * the server. For a Walk here, this is the clicked tile for certain: the client works it out from the
	 * click while drawing the next frame and sets it the frame after, so it's watched for a few frames. If
	 * it doesn't change, the click was on the tile you were already walking to. Minimap clicks show up here
	 * too, with no click to go with them
	 */
	@Subscribe
	public void onClientTick(ClientTick event)
	{
		if (recording == null)
		{
			return;
		}
		LocalPoint destination = client.getLocalDestinationLocation();
		boolean changed = destination != null && !destination.equals(lastDestination);
		lastDestination = destination;
		int click = -1;
		if (walkClickCycle >= 0)
		{
			if (!changed && ++walkWaited < WALK_WAIT_CYCLES)
			{
				return;
			}
			click = walkClickCycle;
			walkClickCycle = -1;
		}
		else if (!changed)
		{
			return;
		}
		// Like a click, a destination set during PIN entry would time the keypresses
		if (destination == null || bankPinShowing())
		{
			return;
		}
		JsonObject line = emit("dest");
		Snap.tile(line, client.getTopLevelWorldView(), destination);
		line.remove("lx");
		line.remove("ly");
		if (click >= 0)
		{
			line.addProperty("click", click);
		}
	}

	/**
	 * A name for a recording started without one: the place you're in, or else the highest-level NPC
	 * around (usually the boss), or else the nearest NPC. Empty if there's none of those, which names it
	 * "session"
	 */
	private String unnamedLabel()
	{
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (me == null || wv == null)
		{
			return "";
		}
		// In an instance, the map square it was copied from
		WorldPoint where = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		String place = where == null ? null : Areas.name(where.getRegionID(), wv.isInstance());
		if (place != null)
		{
			return place;
		}
		NPC strongest = null;
		NPC nearest = null;
		int nearestDistance = Integer.MAX_VALUE;
		for (NPC npc : wv.npcs())
		{
			String name = npc.getName();
			if (name == null || name.equals("null"))
			{
				continue;
			}
			if (npc.getCombatLevel() > 0 && (strongest == null || npc.getCombatLevel() > strongest.getCombatLevel()))
			{
				strongest = npc;
			}
			int distance = npc.getWorldLocation().distanceTo(me.getWorldLocation());
			if (distance < nearestDistance)
			{
				nearest = npc;
				nearestDistance = distance;
			}
		}
		NPC named = strongest != null ? strongest : nearest;
		return named == null ? "" : Text.removeTags(named.getName());
	}

	private void mark(String note)
	{
		// A marker hotkey pressed during PIN entry would time the keypresses
		if (recording == null || bankPinShowing())
		{
			return;
		}
		emit("mark").addProperty("note", note == null ? "" : note);
	}

	private JsonObject emit(String event)
	{
		JsonObject line = Snap.line(event, recording.getTicks(), client.getGameCycle());
		recording.add(line);
		return line;
	}

	private void refreshPanel()
	{
		if (panel == null || recording == null)
		{
			return;
		}
		String status = String.format("%s%n%d ticks, %,d lines, %,d KB%s",
			recording.getFileName(),
			recording.getTicks(),
			recording.getLines(),
			recording.bytesWritten() / 1024,
			recording.hasFailed() ? "\nWriting failed: see the client log" : "");
		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.showRecording(status);
			}
		});
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (recording == null)
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		JsonObject tick = emit("tick");
		tick.addProperty("abs", client.getTickCount());
		tick.addProperty("ms", System.currentTimeMillis());

		Player me = client.getLocalPlayer();
		if (me != null)
		{
			tick.add("me", self(me));
			moves(me);
		}
		tick.add("ui", ui());

		boolean watchedSeen = false;
		JsonArray npcs = new JsonArray();
		for (NPC npc : wv.npcs())
		{
			npcs.add(Snap.actor(client, npc));
			moves(npc);
			watchedSeen |= recording.getWatchedNpcIds().contains(npc.getId());
		}
		tick.add("npcs", npcs);

		if (players)
		{
			JsonArray others = new JsonArray();
			for (Player player : wv.players())
			{
				if (player != me)
				{
					others.add(Snap.actor(client, player));
					moves(player);
				}
			}
			tick.add("players", others);
		}

		layout();
		widgets(InterfaceID.COMBAT_INTERFACE);
		widgets(InterfaceID.MAGIC_SPELLBOOK);
		recording.endTick();

		if (recording.isAutomatic())
		{
			recording.ticksSinceWatchedNpc = watchedSeen ? 0 : recording.ticksSinceWatchedNpc + 1;
			if (recording.ticksSinceWatchedNpc >= config.autoStopTicks())
			{
				stopRecording("watched NPCs gone");
				return;
			}
		}
		int limit = config.maxTicks();
		if (limit > 0 && recording.getTicks() >= limit)
		{
			stopRecording(LIMIT_REACHED);
			return;
		}
		refreshPanel();
	}

	/**
	 * Writes an actor's stand, walk, run and turn animations when they first appear and whenever they
	 * change, e.g. when a player switches weapon or an NPC changes form. Together with the per-tick pose
	 * this is what a replica needs to animate movement the way the client does
	 */
	private void moves(Actor actor)
	{
		JsonArray set = Snap.moveSet(actor);
		Integer hash = set.hashCode();
		if (!hash.equals(moveSets.put(actor, hash)))
		{
			JsonObject line = emit("moves");
			line.add("who", Snap.ref(client, actor));
			line.add("anims", set);
		}
	}

	private JsonObject self(Player me)
	{
		JsonObject m = Snap.actor(client, me);
		m.addProperty("hpNow", client.getBoostedSkillLevel(Skill.HITPOINTS));
		m.addProperty("hpMax", client.getRealSkillLevel(Skill.HITPOINTS));
		m.addProperty("prayNow", client.getBoostedSkillLevel(Skill.PRAYER));
		m.addProperty("prayMax", client.getRealSkillLevel(Skill.PRAYER));
		// Both are stored by the game in tenths or hundredths: 10000 run energy is 100%, 1000 spec is 100%
		m.addProperty("run", client.getEnergy());
		m.addProperty("spec", client.getVarpValue(VarPlayerID.SA_ENERGY));
		JsonArray prayers = new JsonArray();
		// Read from each prayer's varbit: Deadeye shares Eagle Eye's, and Mystic Vigour shares Mystic Might's,
		// so both names appear when either is on. The raw varbits are in the "var" lines to tell them apart
		for (Prayer prayer : Prayer.values())
		{
			if (client.getVarbitValue(prayer.getVarbit()) == 1)
			{
				prayers.add(prayer.name());
			}
		}
		m.add("prayers", prayers);
		LocalPoint destination = client.getLocalDestinationLocation();
		if (destination != null)
		{
			JsonObject dest = new JsonObject();
			Snap.tile(dest, client.getTopLevelWorldView(), destination);
			m.add("dest", dest);
		}
		return m;
	}

	/**
	 * The side panel and settings a replay needs to draw the interface as it was. They are also in the
	 * "var" and "ui_tab" lines as they change; repeating them here lets a replay jump to any tick
	 */
	private JsonObject ui()
	{
		JsonObject u = new JsonObject();
		u.addProperty("tab", client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL));
		u.addProperty("spellbook", client.getVarbitValue(VarbitID.SPELLBOOK));
		u.addProperty("quickPrayers", client.getVarbitValue(VarbitID.QUICKPRAYER_ACTIVE));
		u.addProperty("style", client.getVarpValue(VarPlayerID.COM_MODE));
		// Which set of attack styles the wielded weapon has (the combat tab's buttons)
		u.addProperty("weaponType", client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
		u.addProperty("autoRetaliate", client.getVarpValue(VarPlayerID.OPTION_NODEF) == 0);
		u.addProperty("runOn", client.getVarpValue(VarPlayerID.OPTION_RUN) == 1);
		return u;
	}

	@Subscribe
	public void onVarClientIntChanged(VarClientIntChanged event)
	{
		// Tab switches by hotkey never pass through a menu click, so this is the only record of them
		if (recording != null && event.getIndex() == VarClientID.TOPLEVEL_PANEL)
		{
			emit("ui_tab").addProperty("tab", client.getVarcIntValue(VarClientID.TOPLEVEL_PANEL));
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOADING:
				sceneDirty = true;
				break;
			case LOGGED_IN:
				if (sceneDirty && recording != null)
				{
					dumpScene();
				}
				sceneDirty = false;
				break;
			case LOGIN_SCREEN:
			case HOPPING:
				if (recording != null)
				{
					stopRecording("logged out or hopped");
				}
				break;
			default:
				break;
		}
	}

	/**
	 * Where the player is and what the arena is made of. Written at the start and after every map load.
	 * Arrays are indexed [plane][sceneX][sceneY].
	 */
	private void dumpScene()
	{
		WorldView wv = client.getTopLevelWorldView();
		Scene scene = wv.getScene();
		JsonObject s = emit("scene");
		s.addProperty("baseX", wv.getBaseX());
		s.addProperty("baseY", wv.getBaseY());
		s.addProperty("plane", wv.getPlane());
		s.addProperty("instance", wv.isInstance());
		s.add("regions", Snap.ints(wv.getMapRegions()));
		if (wv.isInstance())
		{
			// Each entry packs the real-world chunk an instance chunk was copied from: see WorldPoint.fromLocalInstance
			JsonArray templates = new JsonArray();
			for (int[][] plane : wv.getInstanceTemplateChunks())
			{
				templates.add(Snap.grid(plane));
			}
			s.add("templates", templates);
		}
		if (!config.recordSceneLayout())
		{
			return;
		}

		JsonArray collision = new JsonArray();
		CollisionData[] maps = wv.getCollisionMaps();
		if (maps != null)
		{
			for (CollisionData map : maps)
			{
				collision.add(map == null ? new JsonArray() : Snap.grid(map.getFlags()));
			}
		}
		s.add("collision", collision);
		if (!wv.isInstance())
		{
			// Outside instances the terrain and objects are the game cache's own map for these regions
			return;
		}

		JsonArray heights = new JsonArray();
		for (int[][] plane : wv.getTileHeights())
		{
			heights.add(Snap.grid(plane));
		}
		s.add("heights", heights);

		JsonArray settings = new JsonArray();
		for (byte[][] plane : wv.getTileSettings())
		{
			JsonArray rows = new JsonArray();
			for (byte[] row : plane)
			{
				JsonArray cells = new JsonArray();
				for (byte cell : row)
				{
					cells.add(cell);
				}
				rows.add(cells);
			}
			settings.add(rows);
		}
		s.add("settings", settings);

		Tile[][][] tiles = scene.getTiles();
		for (Tile[][] plane : tiles)
		{
			for (Tile[] column : plane)
			{
				for (Tile tile : column)
				{
					if (tile == null)
					{
						continue;
					}
					for (GameObject obj : tile.getGameObjects())
					{
						// Big objects sit in every tile they cover: only write them from their own tile
						if (obj != null && obj.getSceneMinLocation().equals(tile.getSceneLocation()))
						{
							objectSeen(obj, true);
						}
					}
					if (tile.getWallObject() != null)
					{
						objectSeen(tile.getWallObject(), true);
					}
					if (tile.getDecorativeObject() != null)
					{
						objectSeen(tile.getDecorativeObject(), true);
					}
					if (tile.getGroundObject() != null)
					{
						objectSeen(tile.getGroundObject(), true);
					}
				}
			}
		}
	}

	private void objectSeen(TileObject obj, boolean initial)
	{
		if (definedObjects.add(obj.getId()))
		{
			emit("objdef").add("def", Snap.objectDefinition(client.getObjectDefinition(obj.getId())));
		}
		JsonObject o = Snap.object(obj);
		o.addProperty("init", initial);
		JsonObject line = emit("obj");
		o.entrySet().forEach(entry -> line.add(entry.getKey(), entry.getValue()));
	}

	private void objectGone(TileObject obj)
	{
		JsonObject line = emit("obj_gone");
		line.addProperty("id", obj.getId());
		Snap.tile(line, obj.getWorldView(), obj.getLocalLocation(), obj.getPlane());
		line.addProperty("pl", obj.getPlane());
	}

	/**
	 * Map loads respawn every object in the scene, which the scene dump already lists
	 */
	private boolean recordingObjects()
	{
		return recording != null && client.getGameState() == GameState.LOGGED_IN;
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		if (recordingObjects())
		{
			objectSeen(event.getGameObject(), false);
		}
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (recordingObjects())
		{
			objectGone(event.getGameObject());
		}
	}

	@Subscribe
	public void onWallObjectSpawned(WallObjectSpawned event)
	{
		if (recordingObjects())
		{
			objectSeen(event.getWallObject(), false);
		}
	}

	@Subscribe
	public void onWallObjectDespawned(WallObjectDespawned event)
	{
		if (recordingObjects())
		{
			objectGone(event.getWallObject());
		}
	}

	@Subscribe
	public void onDecorativeObjectSpawned(DecorativeObjectSpawned event)
	{
		if (recordingObjects())
		{
			objectSeen(event.getDecorativeObject(), false);
		}
	}

	@Subscribe
	public void onDecorativeObjectDespawned(DecorativeObjectDespawned event)
	{
		if (recordingObjects())
		{
			objectGone(event.getDecorativeObject());
		}
	}

	@Subscribe
	public void onGroundObjectSpawned(GroundObjectSpawned event)
	{
		if (recordingObjects())
		{
			objectSeen(event.getGroundObject(), false);
		}
	}

	@Subscribe
	public void onGroundObjectDespawned(GroundObjectDespawned event)
	{
		if (recordingObjects())
		{
			objectGone(event.getGroundObject());
		}
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		NPC npc = event.getNpc();
		if (recording == null)
		{
			if (autoIds.contains(npc.getId()) && client.getGameState() == GameState.LOGGED_IN)
			{
				// The start snapshot includes this NPC, so it isn't written twice
				startRecording(Text.removeTags(npc.getName()), true, autoIds);
			}
			return;
		}
		npcSpawned(npc, false);
	}

	private void npcSpawned(NPC npc, boolean initial)
	{
		defineNpc(npc);
		JsonObject line = emit("npc_spawn");
		Snap.actor(client, npc).entrySet().forEach(entry -> line.add(entry.getKey(), entry.getValue()));
		line.addProperty("init", initial);
		Snap.overrides(line, npc);
	}

	private void defineNpc(NPC npc)
	{
		if (definedNpcs.add(npc.getId()))
		{
			emit("npcdef").add("def", Snap.npcDefinition(npc));
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (recording == null)
		{
			return;
		}
		NPC npc = event.getNpc();
		JsonObject line = emit("npc_gone");
		line.add("who", Snap.ref(client, npc));
		line.addProperty("dead", npc.isDead());
		Snap.actorTile(line, npc);
	}

	@Subscribe
	public void onNpcChanged(NpcChanged event)
	{
		if (recording == null)
		{
			return;
		}
		NPC npc = event.getNpc();
		defineNpc(npc);
		JsonObject line = emit("npc_changed");
		line.add("who", Snap.ref(client, npc));
		line.addProperty("from", event.getOld() == null ? -1 : event.getOld().getId());
		Snap.overrides(line, npc);
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (recording != null && players && event.getPlayer() != client.getLocalPlayer())
		{
			JsonObject spawn = emit("player_spawn");
			spawn.add("who", Snap.ref(client, event.getPlayer()));
			nameOf(spawn, event.getPlayer());
			look(event.getPlayer());
		}
	}

	@Subscribe
	public void onPlayerChanged(PlayerChanged event)
	{
		Player player = event.getPlayer();
		if (recording != null && (player == client.getLocalPlayer() || players))
		{
			look(player);
		}
	}

	private void look(Player player)
	{
		PlayerComposition comp = player.getPlayerComposition();
		if (comp == null)
		{
			return;
		}
		for (int raw : comp.getEquipmentIds())
		{
			if (raw >= PlayerComposition.ITEM_OFFSET)
			{
				defineItem(raw - PlayerComposition.ITEM_OFFSET);
			}
		}
		JsonObject line = emit("look");
		Snap.look(line, client, player, comp);
		nameOf(line, player);
	}

	/** Adds a player's name, unless "Hide player names" is on */
	private void nameOf(JsonObject line, Player player)
	{
		if (names && player.getName() != null)
		{
			line.addProperty("name", Text.removeTags(player.getName()).replace('\u00A0', ' '));
		}
	}

	/**
	 * Where the visible prayer, inventory and equipment buttons are on screen, written whenever it
	 * changes: opening a tab, resizing the window, moving the panel
	 */
	private final Map<Integer, String> lastWidgets = new HashMap<>();

	/**
	 * What an interface shows, written whenever that changes: every visible sprite, text, item and filled
	 * box with its place inside the interface. The game decides which attack styles a weapon has and
	 * which spells are lit (runes, staves, levels, quests), so this records its answer rather than
	 * working it out again
	 */
	private void widgets(int group)
	{
		Widget root = client.getWidget(group, 0);
		if (root == null)
		{
			return;
		}
		JsonArray list = new JsonArray();
		for (int child = 0; child < 512; child++)
		{
			Widget w = client.getWidget(group, child);
			if (w == null)
			{
				continue;
			}
			snapWidget(w, group, list, 0);
		}
		String text = list.toString();
		if (text.equals(lastWidgets.get(group)))
		{
			return;
		}
		lastWidgets.put(group, text);
		JsonObject line = emit("ui_widgets");
		line.addProperty("group", group);
		line.addProperty("w", root.getWidth());
		line.addProperty("h", root.getHeight());
		line.add("widgets", list);
	}

	private void snapWidget(Widget w, int group, JsonArray list, int depth)
	{
		// Shown unless it or something above it inside this interface hides it; the tab itself being
		// closed doesn't count, so the spellbook is known even while another tab is open
		for (Widget p = w; p != null && (p.getId() >>> 16) == group; p = p.getParent())
		{
			if (p.isSelfHidden())
			{
				return;
			}
		}
		int[] at = offset(w, group);
		boolean drawn = w.getSpriteId() > 0 || (w.getText() != null && !w.getText().isEmpty()) || w.getItemId() > 0 || (w.getType() == 3 && w.isFilled());
		if (drawn)
		{
			JsonArray o = new JsonArray();
			o.add(at[0]);
			o.add(at[1]);
			o.add(w.getWidth());
			o.add(w.getHeight());
			o.add(w.getType());
			o.add(w.getSpriteId());
			o.add(w.getText() == null ? "" : hideNames(Text.removeTags(w.getText())));
			o.add(w.getTextColor());
			o.add(w.getOpacity());
			o.add(w.getItemId());
			o.add(w.getItemQuantity());
			o.add(w.getFontId());
			o.add(w.getXTextAlignment());
			o.add(w.getYTextAlignment());
			o.add(w.getTextShadowed());
			o.add(w.getSpriteTiling());
			list.add(o);
		}
		if (depth < 4 && w.getDynamicChildren() != null)
		{
			for (Widget c : w.getDynamicChildren())
			{
				if (c != null)
				{
					snapWidget(c, group, list, depth + 1);
				}
			}
		}
	}

	/** A widget's place inside its interface, from relative positions, less any scrolling */
	private static int[] offset(Widget w, int group)
	{
		int x = 0;
		int y = 0;
		for (Widget p = w; p != null && (p.getId() >>> 16) == group; p = p.getParent())
		{
			x += p.getRelativeX();
			y += p.getRelativeY();
			Widget parent = p.getParent();
			if (parent != null && (parent.getId() >>> 16) == group)
			{
				x -= parent.getScrollX();
				y -= parent.getScrollY();
			}
		}
		return new int[]{x, y};
	}

	private void layout()
	{
		JsonObject o = new JsonObject();
		o.add("prayer", buttons(InterfaceID.PRAYERBOOK));
		o.add("worn", buttons(InterfaceID.WORNITEMS));
		JsonArray inv = new JsonArray();
		Widget container = client.getWidget(InterfaceID.INVENTORY, 0);
		if (container != null && !container.isHidden() && container.getDynamicChildren() != null)
		{
			for (Widget slot : container.getDynamicChildren())
			{
				if (slot != null && !slot.isHidden() && slot.getBounds() != null)
				{
					JsonArray b = Snap.rect(slot.getBounds());
					b.add(slot.getIndex());
					inv.add(b);
				}
			}
		}
		o.add("inv", inv);
		String text = o.toString();
		if (!text.equals(lastLayout))
		{
			lastLayout = text;
			JsonObject line = emit("ui_layout");
			o.entrySet().forEach(e -> line.add(e.getKey(), e.getValue()));
		}
	}

	/** The visible components of an interface that have menu actions, as [x, y, w, h, component] */
	private JsonArray buttons(int group)
	{
		JsonArray list = new JsonArray();
		for (int child = 0; child < 128; child++)
		{
			Widget w = client.getWidget(group, child);
			if (w == null || w.isHidden() || w.getBounds() == null || w.getActions() == null)
			{
				continue;
			}
			boolean actionable = false;
			for (String action : w.getActions())
			{
				actionable |= action != null && !action.isEmpty();
			}
			if (actionable)
			{
				JsonArray b = Snap.rect(w.getBounds());
				b.add(child);
				list.add(b);
			}
		}
		return list;
	}

	private void defineItem(int id)
	{
		if (id >= 0 && definedItems.add(id))
		{
			ItemComposition def = client.getItemDefinition(id);
			JsonObject line = emit("itemdef");
			line.addProperty("id", id);
			line.addProperty("name", Text.removeTags(def.getName()));
			line.addProperty("stackable", def.isStackable());
			line.addProperty("noted", def.getNote() != -1);
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		if (recording != null && players && event.getPlayer() != client.getLocalPlayer())
		{
			emit("player_gone").add("who", Snap.ref(client, event.getPlayer()));
		}
	}

	private boolean wanted(Actor actor)
	{
		if (recording == null || actor == null)
		{
			return false;
		}
		return actor instanceof NPC || actor == client.getLocalPlayer() || (actor instanceof Player && players);
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		if (!wanted(actor))
		{
			return;
		}
		JsonObject line = emit("anim");
		line.add("who", Snap.ref(client, actor));
		line.addProperty("a", actor.getAnimation());
		line.addProperty("o", actor.getCurrentOrientation());
		Snap.actorTile(line, actor);
	}

	@Subscribe
	public void onGraphicChanged(GraphicChanged event)
	{
		Actor actor = event.getActor();
		if (!wanted(actor))
		{
			return;
		}
		JsonObject line = emit("spot");
		line.add("who", Snap.ref(client, actor));
		line.add("spot", Snap.spotAnims(actor));
	}

	@Subscribe
	public void onGraphicsObjectCreated(GraphicsObjectCreated event)
	{
		if (recording == null)
		{
			return;
		}
		GraphicsObject gfx = event.getGraphicsObject();
		JsonObject line = emit("gfx");
		line.addProperty("id", gfx.getId());
		line.addProperty("start", gfx.getStartCycle());
		line.addProperty("z", gfx.getZ());
		Snap.tile(line, gfx.getWorldView(), gfx.getLocation(), gfx.getLevel());
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile proj = event.getProjectile();
		// The event repeats while a projectile tracks its target: the first one is its launch
		if (recording == null || !seenProjectiles.add(proj))
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		JsonObject line = emit("proj");
		line.addProperty("id", proj.getId());
		line.add("from", Snap.ref(client, proj.getSourceActor()));
		line.add("to", Snap.ref(client, proj.getTargetActor()));
		JsonObject start = new JsonObject();
		if (proj.getSourcePoint() != null)
		{
			Snap.tile(start, wv, LocalPoint.fromWorld(wv, proj.getSourcePoint()), proj.getSourceLevel());
		}
		line.add("start", start);
		JsonObject target = new JsonObject();
		if (proj.getTargetPoint() != null)
		{
			Snap.tile(target, wv, LocalPoint.fromWorld(wv, proj.getTargetPoint()), proj.getTargetLevel());
		}
		line.add("target", target);
		line.addProperty("startCycle", proj.getStartCycle());
		line.addProperty("endCycle", proj.getEndCycle());
		line.addProperty("startHeight", proj.getStartHeight());
		line.addProperty("endHeight", proj.getEndHeight());
		line.addProperty("slope", proj.getSlope());
		line.addProperty("startPos", proj.getStartPos());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!wanted(event.getActor()))
		{
			return;
		}
		JsonObject line = emit("hit");
		line.add("who", Snap.ref(client, event.getActor()));
		line.addProperty("type", event.getHitsplat().getHitsplatType());
		line.addProperty("amount", event.getHitsplat().getAmount());
		line.addProperty("mine", event.getHitsplat().isMine());
		line.addProperty("others", event.getHitsplat().isOthers());
		line.addProperty("until", event.getHitsplat().getDisappearsOnGameCycle());
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (recording == null)
		{
			return;
		}
		JsonObject line = emit("sound");
		line.addProperty("id", event.getSoundId());
		line.addProperty("delay", event.getDelay());
		line.add("from", Snap.ref(client, event.getSource()));
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		if (recording == null)
		{
			return;
		}
		WorldView wv = client.getTopLevelWorldView();
		JsonObject line = emit("area_sound");
		line.addProperty("id", event.getSoundId());
		line.addProperty("delay", event.getDelay());
		line.addProperty("range", event.getRange());
		line.add("from", Snap.ref(client, event.getSource()));
		Snap.tile(line, wv, LocalPoint.fromScene(event.getSceneX(), event.getSceneY(), wv));
	}

	/**
	 * True while the bank PIN keypad is up. Nothing that could give the PIN away is recorded then: no
	 * clicks, click positions or var changes. The keypad shuffles its buttons, but the clicked
	 * component and screen position together would still reveal the digits.
	 */
	private boolean bankPinShowing()
	{
		if (bankPinOpen)
		{
			return true;
		}
		// Covers a keypad that was already open when the plugin started
		Widget keypad = client.getWidget(InterfaceID.BANKPIN_KEYPAD, 0);
		return keypad != null && !keypad.isHidden();
	}

	static boolean isBankPinInterface(int interfaceId)
	{
		return interfaceId == InterfaceID.BANKPIN_KEYPAD || interfaceId == InterfaceID.BANKPIN_SETTINGS;
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (recording == null || !config.recordVars() || bankPinShowing())
		{
			return;
		}
		JsonObject line = emit("var");
		line.addProperty("varp", event.getVarpId());
		line.addProperty("varbit", event.getVarbitId());
		line.addProperty("value", event.getValue());
	}

	enum ChatKind
	{
		/**
		 * Public chat said out loud, shown over the speaker's head
		 */
		LOCAL,
		/**
		 * Messages from the game itself
		 */
		GAME,
		/**
		 * Private, friends, clan and group chat, and notifications that name other players
		 */
		NEVER
	}

	static ChatKind chatKind(ChatMessageType type)
	{
		switch (type)
		{
			case PUBLICCHAT:
			case MODCHAT:
			case AUTOTYPER:
			case MODAUTOTYPER:
				return ChatKind.LOCAL;
			case GAMEMESSAGE:
			case ENGINE:
			case SPAM:
			case CONSOLE:
			case ITEM_EXAMINE:
			case NPC_EXAMINE:
			case OBJECT_EXAMINE:
			case DIALOG:
			case MESBOX:
			case NPC_SAY:
			case LEVELUPMESSAGE:
			case WELCOME:
			case DIDYOUKNOW:
			case TENSECTIMEOUT:
				return ChatKind.GAME;
			default:
				return ChatKind.NEVER;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (recording == null)
		{
			return;
		}
		ChatKind kind = chatKind(event.getType());
		if (kind == ChatKind.NEVER || (kind == ChatKind.LOCAL && !config.recordLocalChat()))
		{
			return;
		}
		JsonObject line = emit("chat");
		line.addProperty("type", event.getType().name());
		if (kind == ChatKind.LOCAL)
		{
			// The speaker by index only: the name is used to find them and never written
			line.add("who", Snap.ref(client, speaker(event.getName())));
		}
		line.addProperty("text", hideNames(Text.removeTags(event.getMessage())));
	}

	/** With "Hide player names" on, the names of everyone in the scene taken out of a piece of text */
	private String hideNames(String text)
	{
		if (names || text == null)
		{
			return text;
		}
		java.util.Set<String> present = new java.util.HashSet<>();
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player.getName() != null)
			{
				present.add(Text.removeTags(player.getName()));
			}
		}
		Player me = client.getLocalPlayer();
		if (me != null && me.getName() != null)
		{
			present.add(Text.removeTags(me.getName()));
		}
		return Names.scrub(text, present);
	}

	private Player speaker(String name)
	{
		if (name == null)
		{
			return null;
		}
		String wanted = Text.toJagexName(Text.removeTags(name));
		for (Player player : client.getTopLevelWorldView().players())
		{
			if (player.getName() != null && Text.toJagexName(player.getName()).equalsIgnoreCase(wanted))
			{
				return player;
			}
		}
		return null;
	}

	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged event)
	{
		Actor actor = event.getActor();
		if (recording == null || actor == null || (actor instanceof Player && !config.recordLocalChat()))
		{
			return;
		}
		JsonObject line = emit("say");
		line.add("who", Snap.ref(client, actor));
		line.addProperty("text", hideNames(Text.removeTags(event.getOverheadText())));
	}

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		// Unlike objects, ground items aren't in the map dump: the ones already lying there when a map
		// loads arrive as spawns during the load, so they're recorded then too
		if (recording != null)
		{
			defineItem(event.getItem().getId());
			JsonObject line = emit("item");
			line.addProperty("id", event.getItem().getId());
			line.addProperty("qty", event.getItem().getQuantity());
			Snap.tile(line, client.getTopLevelWorldView(), event.getTile().getLocalLocation(), event.getTile().getPlane());
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		if (recordingObjects())
		{
			JsonObject line = emit("item_gone");
			line.addProperty("id", event.getItem().getId());
			Snap.tile(line, client.getTopLevelWorldView(), event.getTile().getLocalLocation(), event.getTile().getPlane());
		}
	}

	// Runs after other plugins, which may cancel the click: a cancelled click never reaches the server
	@Subscribe(priority = -100)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (recording == null || bankPinShowing())
		{
			return;
		}
		if (event.getMenuAction() != null && isWidgetAction(event.getMenuAction()) && event.getParam1() != -1
			&& isBankPinInterface(WidgetUtil.componentToInterface(event.getParam1())))
		{
			return;
		}
		JsonObject line = emit("click");
		line.addProperty("option", hideNames(Text.removeTags(event.getMenuOption())));
		line.addProperty("target", hideNames(Snap.menuTarget(event.getMenuAction(), event.getMenuTarget())));
		line.addProperty("action", event.getMenuAction() == null ? null : event.getMenuAction().name());
		line.addProperty("id", event.getId());
		line.addProperty("p0", event.getParam0());
		line.addProperty("p1", event.getParam1());
		line.addProperty("item", event.getItemId());
		// For walking, the menu entry's params are the mouse position, not the tile: the clicked tile is
		// the scene tile under the mouse
		if (event.getMenuAction() == MenuAction.WALK)
		{
			// Only a guess (see onClientTick): the tile under the mouse when the frame before was drawn
			walkClickCycle = client.getGameCycle();
			walkWaited = 0;
			WorldView wv = client.getTopLevelWorldView();
			Tile tile = wv.getSelectedSceneTile();
			if (tile != null)
			{
				JsonObject to = new JsonObject();
				Snap.tile(to, wv, tile.getLocalLocation(), tile.getPlane());
				to.remove("lx");
				to.remove("ly");
				line.add("tile", to);
			}
		}
		// Which interface component was clicked, as [interface, component]: for interface entries param1 is
		// the packed component, and for inventory, prayer and spell clicks p0 is the slot within it
		if (event.getMenuAction() != null && isWidgetAction(event.getMenuAction()) && event.getParam1() != -1)
		{
			JsonArray widget = new JsonArray();
			widget.add(WidgetUtil.componentToInterface(event.getParam1()));
			widget.add(WidgetUtil.componentToId(event.getParam1()));
			line.add("widget", widget);
		}
		line.addProperty("consumed", event.isConsumed());
		// Where on screen the clicked button was, so the click can be placed inside it
		Widget clicked = event.getWidget();
		if (clicked != null && clicked.getBounds() != null)
		{
			line.add("wb", Snap.rect(clicked.getBounds()));
		}
		Point mouse = client.getMouseCanvasPosition();
		if (mouse != null)
		{
			line.addProperty("mx", mouse.getX());
			line.addProperty("my", mouse.getY());
		}
	}

	/**
	 * Menu entries whose param1 is an interface component rather than a scene coordinate
	 */
	private static boolean isWidgetAction(MenuAction action)
	{
		switch (action)
		{
			case CC_OP:
			case CC_OP_LOW_PRIORITY:
			case WIDGET_TARGET:
			case WIDGET_TARGET_ON_WIDGET:
			case WIDGET_TYPE_1:
			case WIDGET_TYPE_4:
			case WIDGET_TYPE_5:
			case WIDGET_CLOSE:
			case WIDGET_CONTINUE:
			case WIDGET_FIRST_OPTION:
			case WIDGET_SECOND_OPTION:
			case WIDGET_THIRD_OPTION:
			case WIDGET_FOURTH_OPTION:
			case WIDGET_FIFTH_OPTION:
			case RUNELITE_WIDGET:
				return true;
			default:
				return false;
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (recording == null)
		{
			return;
		}
		if (event.getContainerId() == InventoryID.INV)
		{
			containers(event.getItemContainer(), "inv");
		}
		else if (event.getContainerId() == InventoryID.WORN)
		{
			containers(event.getItemContainer(), "worn");
		}
	}

	private void containers(ItemContainer container, String which)
	{
		if (container == null)
		{
			return;
		}
		JsonObject line = emit("items");
		line.addProperty("which", which);
		JsonArray items = new JsonArray();
		for (Item item : container.getItems())
		{
			defineItem(item.getId());
			JsonArray pair = new JsonArray();
			pair.add(item.getId());
			pair.add(item.getQuantity());
			items.add(pair);
		}
		line.add("items", items);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		if (recording == null)
		{
			return;
		}
		JsonObject line = emit("stat");
		line.addProperty("skill", event.getSkill().name());
		line.addProperty("xp", event.getXp());
		line.addProperty("level", event.getLevel());
		line.addProperty("boosted", event.getBoostedLevel());
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!wanted(event.getSource()))
		{
			return;
		}
		JsonObject line = emit("target");
		line.add("who", Snap.ref(client, event.getSource()));
		line.add("at", Snap.ref(client, event.getTarget()));
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (wanted(event.getActor()))
		{
			emit("death").add("who", Snap.ref(client, event.getActor()));
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKPIN_KEYPAD)
		{
			bankPinOpen = true;
		}
		if (recording != null)
		{
			emit("widget_open").addProperty("group", event.getGroupId());
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKPIN_KEYPAD)
		{
			bankPinOpen = false;
		}
		if (recording != null)
		{
			emit("widget_close").addProperty("group", event.getGroupId());
		}
	}

	private static BufferedImage icon()
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(new Color(0xC0392B));
		g.fillOval(2, 2, 12, 12);
		g.setColor(Color.WHITE);
		g.fillOval(6, 6, 4, 4);
		g.dispose();
		return image;
	}
}
