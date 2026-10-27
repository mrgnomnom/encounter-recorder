package com.encounterrecorder;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The recordings in the sessions folder, for the panel's list: newest first, each shown by the name it was
 * given, without the time stamp in front or the .jsonl after it.
 */
final class Recordings
{
	private static final Pattern FILE = Pattern.compile("^(\\d{8}-\\d{6})-(.*)\\.jsonl$");
	/** A repeated name's number: "TFA_1" in the file, shown as "TFA-1" (labels never hold "_") */
	private static final Pattern COUNTED = Pattern.compile("^(.*)_(\\d+)$");
	private static final Pattern HEADER = Pattern.compile("\"e\"\\s*:\\s*\"header\"");
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm");

	static final class Entry
	{
		final String fileName;
		final String name;
		/** When it was recorded, from its file name, or when the file last changed */
		final LocalDateTime when;
		final long bytes;

		Entry(String fileName, String name, LocalDateTime when, long bytes)
		{
			this.fileName = fileName;
			this.name = name;
			this.when = when;
			this.bytes = bytes;
		}

		String describe()
		{
			return String.format("%s · %s · %,d KB", SHOWN.format(when), fileName, Math.max(1, bytes / 1024));
		}
	}

	private Recordings()
	{
	}

	static List<Entry> list(File dir)
	{
		List<Entry> out = new ArrayList<>();
		File[] files = dir == null ? null : dir.listFiles((d, n) -> n.endsWith(".jsonl"));
		if (files == null)
		{
			return out;
		}
		for (File f : files)
		{
			if (f.isFile())
			{
				out.add(entry(f.getName(), f.lastModified(), f.length()));
			}
		}
		out.sort(Comparator.comparing((Entry e) -> e.when).reversed().thenComparing(e -> e.fileName));
		return out;
	}

	static Entry entry(String fileName, long modifiedMs, long bytes)
	{
		Matcher m = FILE.matcher(fileName);
		LocalDateTime when = null;
		String name = fileName.endsWith(".jsonl") ? fileName.substring(0, fileName.length() - ".jsonl".length()) : fileName;
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
		return new Entry(fileName, name, when, bytes);
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
	static boolean isRecording(File f)
	{
		try (BufferedReader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8))
		{
			String first = r.readLine();
			return first != null && first.trim().startsWith("{") && HEADER.matcher(first).find();
		}
		catch (IOException | RuntimeException e)
		{
			return false;
		}
	}

	/**
	 * The name an imported file gets in the sessions folder: kept if it has the recorder's time stamp,
	 * otherwise stamped with the file's own time so it sorts among the rest
	 */
	static String importName(String fileName, LocalDateTime fileTime)
	{
		if (FILE.matcher(fileName).matches())
		{
			return fileName;
		}
		String base = fileName.toLowerCase().endsWith(".jsonl") ? fileName.substring(0, fileName.length() - 6) : fileName;
		return STAMP.format(fileTime) + "-" + Recording.safeLabel(base) + ".jsonl";
	}

	/** A recording's file name with a new name, keeping its time stamp */
	static String renamed(String fileName, String newName)
	{
		Matcher m = FILE.matcher(fileName);
		String stamp = m.matches() ? m.group(1) : STAMP.format(LocalDateTime.now());
		return stamp + "-" + Recording.safeLabel(newName) + ".jsonl";
	}

	/**
	 * A file name whose shown name no other recording in the folder has: a repeated name gets the next
	 * number, so TFA, then TFA-1, TFA-2... Leaves out the file called {@code except}, the one being renamed.
	 */
	static String numbered(File dir, String fileName, String except)
	{
		List<String> taken = new ArrayList<>();
		for (Entry e : list(dir))
		{
			if (!e.fileName.equals(except))
			{
				taken.add(e.name.toLowerCase());
			}
		}
		Matcher m = FILE.matcher(fileName);
		String stamp = m.matches() ? m.group(1) : STAMP.format(LocalDateTime.now());
		String label = m.matches() ? m.group(2) : Recording.safeLabel(fileName.replaceAll("(?i)\\.jsonl$", ""));
		if (isFree(dir, stamp, label, taken, except))
		{
			return stamp + "-" + label + ".jsonl";
		}
		Matcher counted = COUNTED.matcher(label);
		String base = counted.matches() ? counted.group(1) : label;
		for (int n = 1; ; n++)
		{
			if (isFree(dir, stamp, base + "_" + n, taken, except))
			{
				return stamp + "-" + base + "_" + n + ".jsonl";
			}
		}
	}

	private static boolean isFree(File dir, String stamp, String label, List<String> taken, String except)
	{
		String file = stamp + "-" + label + ".jsonl";
		return !taken.contains(shownName(label).toLowerCase()) && (file.equals(except) || !new File(dir, file).exists());
	}
}
