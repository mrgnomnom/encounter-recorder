package com.encounterrecorder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * One recording session: a JSON-lines file written on its own thread. Lines are queued on the client
 * thread and handed over once per tick, so the client thread never touches the disk.
 */
@Slf4j
class Recording
{
	interface Directory
	{
		Filepath get() throws IOException;
	}

	private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Gson gson;
	private final ExecutorService writer;
	private List<JsonObject> pending = new ArrayList<>();
	private BufferedWriter out;
	private volatile boolean failed;

	@Getter
	private volatile String fileName;
	@Getter
	private final boolean automatic;
	/**
	 * The NPC IDs that keep an automatic recording alive
	 */
	@Getter
	private final Set<Integer> watchedNpcIds;
	@Getter
	private int ticks;
	@Getter
	private long lines;
	private final AtomicLong bytes = new AtomicLong();
	@Getter
	private volatile Filepath file;

	int ticksSinceWatchedNpc;

	Recording(Gson gson, Directory directory, String label, boolean automatic, Set<Integer> watchedNpcIds)
	{
		this.gson = gson;
		this.automatic = automatic;
		this.watchedNpcIds = watchedNpcIds;
		this.fileName = FILE_TIME.format(LocalDateTime.now()) + "-" + safeLabel(label) + ".jsonl";
		this.writer = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "encounter-recorder");
			t.setDaemon(true);
			return t;
		});
		writer.execute(() ->
		{
			try
			{
				Filepath dir = directory.get().joinSegment("sessions");
				dir.createDirectories();
				// A name already in the folder gets the next number: TFA, TFA-1, TFA-2...
				fileName = Recordings.numbered(Recordings.names(dir), fileName, null);
				file = dir.joinSegment(fileName);
				out = file.openBufferedWriter(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
			}
			catch (IOException | IllegalArgumentException e)
			{
				log.warn("Could not open recording {}", fileName, e);
				failed = true;
			}
		});
	}

	/**
	 * Keeps letters, digits and dashes so the label is safe in a file name on every OS
	 */
	static String safeLabel(String label)
	{
		String cleaned = label == null ? "" : label.trim().replaceAll("[^A-Za-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
		if (cleaned.isEmpty())
		{
			return "session";
		}
		return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
	}

	void add(JsonObject line)
	{
		pending.add(line);
		lines++;
	}

	void endTick()
	{
		ticks++;
		flush();
	}

	long bytesWritten()
	{
		return bytes.get();
	}

	boolean hasFailed()
	{
		return failed;
	}

	private void flush()
	{
		if (pending.isEmpty())
		{
			return;
		}
		List<JsonObject> batch = pending;
		pending = new ArrayList<>();
		writer.execute(() -> write(batch));
	}

	private void write(List<JsonObject> batch)
	{
		if (failed || out == null)
		{
			return;
		}
		try
		{
			for (JsonObject line : batch)
			{
				String text = gson.toJson(line);
				out.write(text);
				out.write('\n');
				bytes.addAndGet(text.length() + 1);
			}
			out.flush();
		}
		catch (IOException e)
		{
			log.warn("Recording {} stopped writing", fileName, e);
			failed = true;
		}
	}

	/**
	 * Writes what is queued, closes the file and lets the writer thread finish on its own
	 */
	void close()
	{
		flush();
		writer.execute(() ->
		{
			if (out == null)
			{
				return;
			}
			try
			{
				out.close();
			}
			catch (IOException e)
			{
				log.warn("Could not close recording {}", fileName, e);
			}
		});
		writer.shutdown();
	}
}
