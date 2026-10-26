#!/usr/bin/env python3
"""Summarise an Encounter Recorder session.

    python3 -I tools/summarize.py SESSION.jsonl                 # overview: what each NPC did, and how often
    python3 -I tools/summarize.py SESSION.jsonl --timeline      # every event, tick by tick
    python3 -I tools/summarize.py SESSION.jsonl --timeline --npc 16677 --from 40 --to 120

The overview lists, for each NPC ID, every animation, graphic, projectile and sound tied to it with
the ticks it happened on and the gaps between repeats. Repeating gaps are attack cycles.
"""
import argparse
import json
import sys
from collections import defaultdict


def load(path):
    with open(path, encoding="utf-8") as f:
        for number, raw in enumerate(f, 1):
            raw = raw.strip()
            if raw:
                try:
                    yield json.loads(raw)
                except json.JSONDecodeError:
                    print(f"line {number}: not JSON, skipped (was the recording cut off?)", file=sys.stderr)


def who(ref):
    # The recorder leaves out null fields, so a missing actor arrives as None
    if not ref:
        return "-"
    kind = ref.get("k")
    if kind == "npc":
        return f"npc{ref['id']}#{ref['i']}"
    if kind == "player":
        return f"player#{ref['i']}"
    return kind


def gaps(ticks):
    return [b - a for a, b in zip(ticks, ticks[1:])]


def short(ticks, limit=12):
    text = ", ".join(map(str, ticks[:limit]))
    return text + (f", ... ({len(ticks)} total)" if len(ticks) > limit else "")


def overview(lines):
    names = {}
    by_npc = defaultdict(lambda: defaultdict(list))
    hits_on_me = []
    marks = []
    object_names = {}
    # Objects mechanics spawn after the map loads: id -> spawn ticks, lifetimes and animations
    spawned = defaultdict(list)
    alive = defaultdict(list)
    lifetimes = defaultdict(list)
    object_anims = defaultdict(set)
    resent = 0
    reload_tick = None
    header = end = None
    for line in lines:
        e, t = line["e"], line["t"]
        if e == "header":
            header = line
        elif e == "end":
            end = line
        elif e == "npcdef":
            names[line["def"]["id"]] = line["def"]["name"]
        elif e == "anim" and (line.get("who") or {}).get("k") == "npc":
            by_npc[line["who"]["id"]][f"anim {line['a']}"].append(t)
        elif e == "spot" and (line.get("who") or {}).get("k") == "npc":
            for spot in line["spot"]:
                by_npc[line["who"]["id"]][f"graphic {spot['id']}"].append(t)
        elif e == "proj" and (line.get("from") or {}).get("k") == "npc":
            flight = line["endCycle"] - line["startCycle"]
            by_npc[line["from"]["id"]][f"projectile {line['id']} ({flight} cycles)"].append(t)
        elif e in ("sound", "area_sound") and (line.get("from") or {}).get("k") == "npc":
            by_npc[line["from"]["id"]][f"sound {line['id']}"].append(t)
        elif e == "say" and (line.get("who") or {}).get("k") == "npc":
            by_npc[line["who"]["id"]][f"says {line['text']!r}"].append(t)
        elif e == "hit" and (line.get("who") or {}).get("k") == "me":
            hits_on_me.append((t, line["amount"], line["type"]))
        elif e == "mark":
            marks.append((t, line["note"]))
        elif e == "objdef":
            object_names[line["def"]["id"]] = line["def"]["name"]
        elif e == "scene":
            alive.clear()
            reload_tick = t
        elif e == "obj" and not line.get("init"):
            if t == reload_tick:
                # After a map load the server re-sends objects that were already there: not a new spawn
                resent += 1
                alive[(line["id"], line.get("x"), line.get("y"), line.get("p"))].append(t)
                continue
            spawned[line["id"]].append(t)
            alive[(line["id"], line.get("x"), line.get("y"), line.get("p"))].append(t)
            if line.get("anim") is not None:
                object_anims[line["id"]].add(line["anim"])
        elif e == "obj_gone":
            place = (line["id"], line.get("x"), line.get("y"), line.get("p"))
            if alive.get(place):
                lifetimes[line["id"]].append(t - alive[place].pop(0))

    if header:
        print(f"{header.get('label')}: revision {header.get('revision')}, world {header.get('world')}, "
              f"{'automatic' if header.get('automatic') else 'by hand'}")
    if end:
        print(f"{end['ticks']} ticks ({end['ticks'] * 0.6:.1f} s), ended: {end['reason']}")
    else:
        print("No end line: the client closed or crashed mid-recording; everything up to then is intact")

    for npc_id, events in sorted(by_npc.items()):
        print(f"\nNPC {npc_id} {names.get(npc_id, '?')}")
        for label, ticks in sorted(events.items(), key=lambda item: item[1][0]):
            repeats = gaps(ticks)
            line = f"  {label:<40} ticks {short(ticks)}"
            if repeats:
                line += f"\n  {'':<40} gaps  {short(repeats)}"
            print(line)

    if spawned:
        print("\nObjects spawned after the map loaded (mechanics usually show up here)")
        for object_id, ticks in sorted(spawned.items(), key=lambda item: item[1][0]):
            name = object_names.get(object_id, "?")
            waves = sorted(set(ticks))
            per_wave = len(ticks) / len(waves)
            print(f"  {object_id} {name if name != 'null' else '(unnamed)'}: {len(ticks)} spawned in {len(waves)} waves, "
                  f"{per_wave:.0f} per wave")
            print(f"  {'':<8}wave ticks {short(waves)}")
            if len(waves) > 1:
                print(f"  {'':<8}wave gaps  {short(gaps(waves))}")
            if lifetimes[object_id]:
                life = sorted(lifetimes[object_id])
                print(f"  {'':<8}lasts {life[0]}-{life[-1]} ticks (median {life[len(life) // 2]})")
            if object_anims[object_id]:
                print(f"  {'':<8}animations {', '.join(map(str, sorted(object_anims[object_id])))}")

    if resent:
        print(f"  ({resent} objects re-sent by the server right after map loads are left out)")

    if hits_on_me:
        print("\nHits on you (tick: amount/type)")
        print("  " + ", ".join(f"{t}: {amount}/{kind}" for t, amount, kind in hits_on_me))
    if marks:
        print("\nMarkers")
        for t, note in marks:
            print(f"  tick {t}: {note}")


SKIP_IN_TIMELINE = {"tick", "scene", "obj", "objdef", "npcdef", "itemdef", "look", "stat", "items", "var"}


def describe(line):
    e = line["e"]
    if e in ("anim", "spot", "hit", "target", "death", "say", "npc_gone", "npc_changed"):
        detail = {k: v for k, v in line.items() if k not in ("t", "c", "e", "who", "sx", "sy", "lx", "ly")}
        return f"{e:<12} {who(line.get('who')):<18} {json.dumps(detail, separators=(',', ':'))}"
    if e == "proj":
        return f"{e:<12} {who(line.get('from')):<18} -> {who(line.get('to'))} id {line['id']} flight {line['endCycle'] - line['startCycle']} cycles"
    if e in ("sound", "area_sound"):
        return f"{e:<12} {who(line.get('from')):<18} id {line['id']} delay {line['delay']}"
    if e == "npc_spawn":
        return f"{e:<12} {who(line.get('who')):<18} at {line.get('x')},{line.get('y')}"
    detail = {k: v for k, v in line.items() if k not in ("t", "c", "e")}
    return f"{e:<12} {json.dumps(detail, separators=(',', ':'))[:160]}"


def involves(line, npc_id):
    for key in ("who", "from", "to"):
        ref = line.get(key)
        if isinstance(ref, dict) and ref.get("id") == npc_id:
            return True
    return line["e"] in ("mark", "gfx", "header", "end")


def timeline(lines, npc_id, start, stop, show_vars):
    skip = SKIP_IN_TIMELINE - ({"var"} if show_vars else set())
    for line in lines:
        t = line["t"]
        if t < start or (stop is not None and t > stop) or line["e"] in skip:
            continue
        if npc_id is not None and not involves(line, npc_id):
            continue
        print(f"t{t:<5} c{line['c']:<7} {describe(line)}")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("session")
    parser.add_argument("--timeline", action="store_true", help="print events tick by tick instead of the overview")
    parser.add_argument("--npc", type=int, help="only events involving this NPC ID")
    parser.add_argument("--from", dest="start", type=int, default=0, help="first tick to show")
    parser.add_argument("--to", dest="stop", type=int, help="last tick to show")
    parser.add_argument("--vars", action="store_true", help="include varbit and varp changes in the timeline")
    args = parser.parse_args()
    lines = list(load(args.session))
    if args.timeline:
        timeline(lines, args.npc, args.start, args.stop, args.vars)
    else:
        overview(lines)


if __name__ == "__main__":
    main()
