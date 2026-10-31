package com.encounterrecorder;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.client.util.Filepath;

/**
 * The recordings in the sessions folder, for the panel's list: newest first, each shown by the name it was
 * given, without the time stamp in front or the .jsonl (or .jsonl.gz, once packed) after it.
 */
final class Recordings
{
	private static final Pattern FILE = Pattern.compile("^(\\d{8}-\\d{6})-(.*)\\.jsonl(?:\\.gz)?$");
	/** A recording's ending: plain, or packed when it stopped */
	private static final Pattern EXTENSION = Pattern.compile("(?i)\\.jsonl(?:\\.gz)?$|\\.gz$");
	/** A repeated name's number: "TFA_1" in the file, shown as "TFA-1" (labels never hold "_") */
	private static final Pattern COUNTED = Pattern.compile("^(.*)_(\\d+)$");
	private static final Pattern HEADER = Pattern.compile("\"e\"\\s*:\\s*\"header\"");
	private static final Pattern TICK = Pattern.compile("^\\{\"t\":(\\d+),");
	private static final Pattern END_TICKS = Pattern.compile("\"e\":\"end\".*\"ticks\":(\\d+)");
	/** How much of a file's end is read for its last line */
	static final int TAIL_BYTES = 8192;
	/** Each recording's length in ticks, by name, size and time, so a file is only read again once it changes */
	private static final Map<String, Integer> TICKS = new ConcurrentHashMap<>();
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");
	/** In the list, the year only when it isn't this one */
	private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("d MMM, HH:mm");

	static final class Entry
	{
		final String fileName;
		final String name;
		/** When it was recorded, from its file name, or when the file last changed */
		final LocalDateTime when;
		final long bytes;
		/** How many ticks it spans, or -1 if its end couldn't be read */
		final int ticks;

		Entry(String fileName, String name, LocalDateTime when, long bytes, int ticks)
		{
			this.fileName = fileName;
			this.name = name;
			this.when = when;
			this.bytes = bytes;
			this.ticks = ticks;
		}

		/** The line under its name: "28 Oct, 15:25 · 2,598 ticks" */
		String details(int thisYear)
		{
			return recordedAt(thisYear) + (ticks >= 0 ? String.format(" · %,d ticks", ticks) : "");
		}

		/** When it was recorded, short enough to sit beside its name: "28 Oct, 15:25" */
		String recordedAt(int thisYear)
		{
			return (when.getYear() == thisYear ? SHORT : SHOWN).format(when);
		}

		String describe()
		{
			return String.format("%s · %s · %,d KB", SHOWN.format(when), fileName, Math.max(1, bytes / 1024));
		}
	}

	private Recordings()
	{
	}

	static List<Entry> list(Filepath dir) throws IOException
	{
		List<Entry> out = new ArrayList<>();
		for (Filepath f : files(dir))
		{
			long modified = f.getLastModifiedTime().toMillis();
			long bytes = f.size();
			String key = f.getFileName() + ":" + bytes + ":" + modified;
			Integer ticks = TICKS.get(key);
			if (ticks == null)
			{
				ticks = isPacked(f.getFileName()) ? Compressed.ticksOf(f) : ticksFromTail(tail(f, bytes));
				TICKS.put(key, ticks);
			}
			out.add(entry(f.getFileName(), modified, bytes, ticks));
		}
		out.sort(Comparator.comparing((Entry e) -> e.when).reversed().thenComparing(e -> e.fileName));
		return out;
	}

	/** The names of the recordings in the folder */
	static List<String> names(Filepath dir) throws IOException
	{
		return files(dir).stream().map(Filepath::getFileName).collect(Collectors.toList());
	}

	private static List<Filepath> files(Filepath dir) throws IOException
	{
		if (!dir.isDirectory())
		{
			return new ArrayList<>();
		}
		try (Stream<Filepath> walk = dir.walk(1))
		{
			List<Filepath> found = walk.filter(f -> isRecordingName(f.getFileName()) && f.isFile()).collect(Collectors.toList());
			// A plain recording whose packed copy is there too is on its way out: list it once
			Set<String> names = found.stream().map(Filepath::getFileName).collect(Collectors.toSet());
			found.removeIf(f -> names.contains(f.getFileName() + Compressed.EXT));
			return found;
		}
	}

	static boolean isRecordingName(String fileName)
	{
		return fileName.endsWith(".jsonl") || fileName.endsWith(".jsonl" + Compressed.EXT);
	}

	static boolean isPacked(String fileName)
	{
		return fileName.endsWith(Compressed.EXT);
	}

	/** How a recording's file name ends: .jsonl.gz once packed, .jsonl before */
	private static String extension(String fileName)
	{
		return isPacked(fileName.toLowerCase()) ? ".jsonl" + Compressed.EXT : ".jsonl";
	}

	/** The last few KB of a file, enough for its last line */
	static String tail(Filepath f, long bytes)
	{
		long from = Math.max(0, bytes - TAIL_BYTES);
		ByteBuffer buffer = ByteBuffer.allocate((int) (bytes - from));
		try (FileChannel channel = f.openFileChannel(StandardOpenOption.READ))
		{
			while (buffer.hasRemaining() && channel.read(buffer, from + buffer.position()) > 0)
			{
				// Keep reading until the tail is in
			}
		}
		catch (IOException e)
		{
			return "";
		}
		return new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
	}

	/**
	 * How many ticks a recording spans, from the end of its file: a stopped one ends with its count; one
	 * still being written, or cut off, by its last whole line's tick, counted from 0. -1 if there's none
	 */
	static int ticksFromTail(String tail)
	{
		String line = lastWholeLine(tail);
		if (line == null)
		{
			return -1;
		}
		Matcher end = END_TICKS.matcher(line);
		if (end.find())
		{
			return Integer.parseInt(end.group(1));
		}
		Matcher tick = TICK.matcher(line);
		return tick.find() ? Integer.parseInt(tick.group(1)) + 1 : -1;
	}

	/** Whether a recording ended properly, with its end line, rather than being cut off */
	static boolean isFinished(String tail)
	{
		String line = lastWholeLine(tail);
		return line != null && END_TICKS.matcher(line).find();
	}

	private static String lastWholeLine(String tail)
	{
		String[] lines = tail.split("\n");
		for (int i = lines.length - 1; i >= 0; i--)
		{
			String line = lines[i].trim();
			// The first line of the tail may be cut off at the front, and the last at the back
			if (line.startsWith("{") && line.endsWith("}"))
			{
				return line;
			}
		}
		return null;
	}

	static Entry entry(String fileName, long modifiedMs, long bytes)
	{
		return entry(fileName, modifiedMs, bytes, -1);
	}

	static Entry entry(String fileName, long modifiedMs, long bytes, int ticks)
	{
		Matcher m = FILE.matcher(fileName);
		LocalDateTime when = null;
		String name = EXTENSION.matcher(fileName).replaceFirst("");
		if (m.matches())
		{
			// The recorder turns spaces in a session's name into hyphens; turn them back for showing
			name = shownName(m.group(2));
			try
			{
				when = LocalDateTime.parse(m.group(1), STAMP);
			}
			catch (DateTimeParseException e)
			{
				// Not a time after all: fall back on the file's
			}
		}
		if (when == null)
		{
			when = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(modifiedMs), java.time.ZoneId.systemDefault());
		}
		return new Entry(fileName, name, when, bytes, ticks);
	}

	private static String shownName(String label)
	{
		if (label.isEmpty())
		{
			return "session";
		}
		Matcher counted = COUNTED.matcher(label);
		return counted.matches() ? counted.group(1).replace('-', ' ') + "-" + counted.group(2) : label.replace('-', ' ');
	}

	/** Whether a file is a recording: its first line is a recording's header */
	static boolean isRecording(Filepath f)
	{
		try (BufferedReader r = Compressed.openReader(f))
		{
			return isHeader(r.readLine());
		}
		catch (IOException | RuntimeException e)
		{
			return false;
		}
	}

	static boolean isHeader(String firstLine)
	{
		return firstLine != null && firstLine.trim().startsWith("{") && HEADER.matcher(firstLine).find();
	}

	/**
	 * The name an imported file gets in the sessions folder: kept if it has the recorder's time stamp,
	 * otherwise stamped with the file's own time so it sorts among the rest
	 */
	static String importName(String fileName, LocalDateTime fileTime)
	{
		Matcher m = FILE.matcher(fileName);
		if (m.matches())
		{
			// Keep its stamp and any number, but nothing a file name shouldn't hold
			Matcher counted = COUNTED.matcher(m.group(2));
			return m.group(1) + "-" + (counted.matches() ? Recording.safeLabel(counted.group(1)) + "_" + counted.group(2) : Recording.safeLabel(m.group(2))) + extension(fileName);
		}
		return STAMP.format(fileTime) + "-" + Recording.safeLabel(EXTENSION.matcher(fileName).replaceFirst("")) + extension(fileName);
	}

	/** A recording's file name with a new name, keeping its time stamp */
	static String renamed(String fileName, String newName)
	{
		Matcher m = FILE.matcher(fileName);
		String stamp = m.matches() ? m.group(1) : STAMP.format(LocalDateTime.now());
		return stamp + "-" + Recording.safeLabel(newName) + extension(fileName);
	}

	/**
	 * A file name whose shown name no other recording in the folder has: a repeated name gets the next
	 * number, so TFA, then TFA-1, TFA-2... {@code existing} is the folder's file names, from {@link #names};
	 * the file called {@code except}, the one being renamed, is left out.
	 */
	static String numbered(Collection<String> existing, String fileName, String except)
	{
		List<String> files = new ArrayList<>();
		List<String> taken = new ArrayList<>();
		for (String name : existing)
		{
			if (!name.equals(except))
			{
				files.add(name.toLowerCase());
				taken.add(entry(name, 0, 0).name.toLowerCase());
			}
		}
		Matcher m = FILE.matcher(fileName);
		String stamp = m.matches() ? m.group(1) : STAMP.format(LocalDateTime.now());
		String label = m.matches() ? m.group(2) : Recording.safeLabel(EXTENSION.matcher(fileName).replaceFirst(""));
		String extension = extension(fileName);
		if (isFree(files, stamp + "-" + label + extension, label, taken))
		{
			return stamp + "-" + label + extension;
		}
		Matcher counted = COUNTED.matcher(label);
		String base = counted.matches() ? counted.group(1) : label;
		for (int n = 1; ; n++)
		{
			if (isFree(files, stamp + "-" + base + "_" + n + extension, base + "_" + n, taken))
			{
				return stamp + "-" + base + "_" + n + extension;
			}
		}
	}

	private static boolean isFree(List<String> files, String file, String label, List<String> taken)
	{
		return !taken.contains(shownName(label).toLowerCase()) && !files.contains(file.toLowerCase());
	}
}
