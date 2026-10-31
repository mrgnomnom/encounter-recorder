# Encounter Recorder: technical reference

The story of what this is for is in the [README](../README.md). This page is for anyone reading the
files themselves or working on the plugin.

## Running it from source

```
gradlew run
```

This starts a development client with the plugin loaded. Log in with a Jagex account by following
https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts.

## Reading a session without the replay

```
python3 -I tools/summarize.py SESSION.jsonl.gz
python3 -I tools/summarize.py SESSION.jsonl.gz --timeline --npc 16677 --from 40 --to 120
```

The overview lists each NPC's animations, graphics, projectiles, sounds and lines, with the ticks they
happened on and the gaps between repeats. A gap that keeps repeating is an attack cycle.
`tools/replay.html` is the old top-down viewer, kept for quick checks: drop a session onto it.

Files are named `yyyyMMdd-HHmmss-label.jsonl.gz`. A label already in the folder gets a number, stored as
`label_1`, `label_2` and shown as "label-1", "label-2". A recording started without a name is named
after the place you're in ("Edgeville", "The Fractured Archive"), or else the highest-level NPC around,
or else the nearest NPC, or else "session". Place names come from RuneLite's Discord plugin's list, by
map square, with instances named after the map they were copied from.

### Packing

A recording is written as plain `.jsonl` while it runs, so a crash never leaves a broken file. When it
stops, it's gzipped to `.jsonl.gz` (about 18 times smaller: a 26-minute 5-man raid goes from 30 MB to
under 2 MB) and the plain file is deleted. The gzip header's comment field holds the recording's length
as `{"ticks":N}`, so the panel and the replay list it without unpacking the file. Any gzip tool opens
these (`gzip -dc`, 7-Zip, Python's `gzip`), and so does the replay; plain `.jsonl` recordings, from
older versions or shared that way, still work everywhere.

How a recording ends:

| What happens | What's saved |
|---|---|
| *Stop recording*, its hotkey, the boss gone (auto-record) or the length limit | Stopped and packed straight away |
| Logging out or hopping worlds | Stopped (`"logged out or hopped"`) and packed |
| Turning the plugin off | Stopped (`"plugin turned off"`) and packed |
| Closing RuneLite's window | Stopped (`"client closed"`). RuneLite waits up to 10 seconds for plugins as it closes, and packing takes about a second |
| RuneLite crashes, is killed, or closes before packing finishes | The plain file stays, whole apart from a half-written last line. The next time the plugin starts it's given an end line (`"not stopped: the client closed or crashed"`) and packed |

That last tidy-up only touches plain recordings nothing has written to for 2 minutes that have no end
line, and skips any another RuneLite has open: while recording, a client locks a byte far past the end of
its file, which never gets in the way of reading it.

## File format

One JSON object per line. Every line has:

| Field | Meaning |
|---|---|
| `t` | Tick of the recording, starting at 0. Events the server sends for a tick share its `t` with the `tick` line written at the end of it. |
| `c` | Client cycle (20 ms) the event arrived on, for ordering within a tick. Projectile and graphic start cycles use the same clock. |
| `e` | Event type, below. |

Positions are written as `x`/`y`/`p`, the real world tile (translated out of instances, so they match
the game cache's map; inside an instance `p` is the floor of the template a chunk was copied from), `pl`
the floor in the scene itself (format 5), plus `sx`/`sy` scene coordinates that index the `scene` arrays, and `lx`/`ly`
sub-tile position (128 units per tile). Actors are referred to as `{"k":"me"}`,
`{"k":"npc","i":index,"id":npcId}` or `{"k":"player","i":index}`.

| `e` | Written when | Main fields |
|---|---|---|
| `header` | Recording starts | `format`, `label`, `revision`, `world`, `worldTypes`, `startedMs`, `startTick`, which options were on, `canvasW`/`canvasH`/`resized` (the game canvas the click positions are measured on) |
| `scene` | Start, and after each map load | `baseX`, `baseY`, `plane`, `instance`, `regions`, `collision` as `[plane][sceneX][sceneY]`. Inside instances also `templates` (instance chunk sources), `heights` and `settings`; outside them those come from the game cache |
| `obj` / `obj_gone` | Objects in an instance at load (`init: true`), then spawns and despawns anywhere | `id`, `kind` (game/wall/decor/ground), position, `pl` (the scene floor the object is on, from format 3), orientation, `config`, size, `anim` |
| `objdef` | First sight of each object ID | `name`, size, `actions`, `impostors` |
| `tick` | End of every tick | `abs` (client tick count), `ms` (wall clock, to line up with video), `me`, `npcs`, `players`, `ui` |
| `npc_spawn` / `npc_gone` / `npc_changed` | NPCs appear, leave or transform | actor state, `dead`, `from` (old ID), `overrides` (per-NPC model, colour and texture changes) |
| `moves` | An actor first appears, or its movement animations change | `who`, `anims`: stand, stand-turn left, stand-turn right, walk, walk back, walk left, walk right, run |
| `npcdef` | First sight of each NPC ID | `name`, `size`, `level`, `stats`, `models`, `actions` |
| `itemdef` | First sight of each item ID | `id`, `name`, `stackable`, `noted` |
| `look` | Start, a player appearing, and any change to how a player looks | `who`, `name` (replaced if *Hide player names* is on), `gender`, `cb` (combat level), `team`, `equip` (the game's 12-slot body array: values from `itemOffset` up are items, from `kitOffset` up are body kits), `colors`, `npc` if transformed |
| `anim` | An actor's animation changes | `who`, `a`, `o` (orientation), position |
| `spot` | An actor's graphics change | `who`, `spot` list of `id`, `h` (height), `start` cycle, `f` frame |
| `gfx` | A graphic appears on a tile | `id`, `start` cycle, `z`, position |
| `proj` | A projectile launches | `id`, `from`, `to`, `start` and `target` positions, `startCycle`, `endCycle`, heights, `slope` |
| `hit` | A hitsplat lands | `who`, `type`, `amount`, `mine`, `others` |
| `sound` / `area_sound` | A sound plays | `id`, `delay`, `from`, and for area sounds `range` and position |
| `var` | A varbit or varp changes | `varp`, `varbit`, `value` |
| `chat` / `say` | Game messages and local chat; overhead text | `type`, `text`, and for local chat `who` (the speaker by index). Private, friends, clan and group chat are never recorded. |
| `click` | You click a menu option | `option`, `target` (player names replaced), `action`, `id`, `p0`, `p1`, `item`, `widget` as `[interface, component]` for interface clicks (`p0` is then the slot), `mx`/`my` canvas position, `tile` for walk clicks (whose `p0`/`p1` are just the mouse position) the scene tile under the mouse, a guess that can be a tile off: the `dest` line after it has the tile for certain (format 6), `wb` the clicked button's rectangle and `consumed` whether another plugin cancelled it (format 4) |
| `dest` | Where the client is walking you changes, or is settled after a Walk here (format 6) | position of the tile the client flags and sends to the server, and `click` the cycle of the Walk here it's for. Without `click`, it came from somewhere else, like the minimap |
| `ui_tab` | The side panel tab changes, by click or hotkey | `tab` |
| `ui_widgets` | What the combat options (593) or spellbook (218) show changes (format 5) | `group`, `w`/`h`, `widgets` as `[x, y, w, h, type, sprite, text, colour, opacity, item, quantity, font, xAlign, yAlign, shadow, tiled]` |
| `press` | Any mouse press on the game, including ones that hit nothing (format 4) | `mx`/`my` canvas position, `button` |
| `ui_layout` | The visible prayer, inventory or equipment buttons move or change (format 4) | `prayer`, `worn` as `[x, y, w, h, component]`; `inv` as `[x, y, w, h, slot]` |
| `items` | Inventory or equipment changes | `which` (inv/worn), `items` as `[id, qty]` |
| `stat` | Levels or XP change (all skills at start) | `skill`, `xp`, `level`, `boosted` |
| `target` / `death` | An actor targets something; an actor dies | `who`, `at` |
| `widget_open` / `widget_close` | An interface opens or closes | `group` |
| `mark` | You drop a marker | `note` |
| `end` | Recording stops | `reason`, `ticks`, `endedMs` |

Every actor in a `tick` line has its drawn position (`x`/`y`, `lx`/`ly`), `tile` (the tile the
server has it on, which the drawn position catches up to; the south-west tile for big NPCs), `o`
(facing, 0 south, 512 west, 1024 north, 1536 east), `of` (where it is turning to, if still turning),
`a`/`af` (animation and frame), `pose` (the stand/walk/run animation playing), `spot` (graphics on
it), `hp` as `[ratio, scale]`, `int` (what it's interacting with), `head` (overhead prayer icon) and
`skull`.

The `me` object in `tick` lines adds `hpNow`, `hpMax`, `prayNow`, `prayMax`, `run` (10000 = 100%),
`spec` (1000 = 100%), active `prayers` and `dest` (where you're walking to). The `ui` object holds
`tab` (open side panel tab: 3 inventory, 4 equipment, 5 prayer, 6 magic), `spellbook`, `quickPrayers`,
`style` (attack style slot), `autoRetaliate` and `runOn`.

## What "around you" covers

NPCs are always recorded. Other players are recorded when *Players around you* is ticked in the panel
(or in the config); the choice is fixed when a recording starts and saved in the header as
`otherPlayers`. Either way the client only knows about what the server sends it: players and NPCs
within about 15 tiles of you, or further for bosses whose arenas use a wider view. Nothing outside that
range can be recorded by any plugin.

## Privacy

- **Player names** are recorded on `look` and `player_spawn` lines so replays can label players, unless
  *Hide player names* is on: then every name, yours included, is replaced throughout the file. Player
  menu targets are always written as `<player>`.
- **Local chat** (public chat typed by you and players nearby) is recorded unless you turn off *Local
  chat* in the config, linked to the speaker by index. **Private, friends, clan and group chat are never
  recorded**, and neither are notifications that name other players (logins, trade requests, broadcasts).
- **Bank PIN entry is never recorded.** While the PIN keypad is open the recorder drops every click,
  mouse press, click position, var change and marker. The keypad shuffles its buttons, but the clicked
  button and its screen position together would give the digits away. Keyboard input is never recorded.
- Files stay on your computer; the plugin makes no network requests.
