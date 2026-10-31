package com.encounterrecorder;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Recordings are written as plain JSON lines and gzipped once they stop, about 18 times smaller. Written
 * plain first so a crash mid-recording never leaves a broken file. The gzip header's comment holds the
 * recording's length, {"ticks":N}, so the lists can show it without unpacking tens of megabytes.
 */
@Slf4j
final class Compressed
{
	static final String EXT = ".gz";
	/** A gzip being written: renamed to .gz only once it's whole */
	private static final String PART = ".part";
	/**
	 * A recording being written holds a lock on this byte, far past its end, so another RuneLite open on the
	 * same folder leaves it alone. Past the end, so it never stops anything reading the file
	 */
	static final long LOCK_AT = Long.MAX_VALUE - 1;
	/** A plain recording left alone this long is no longer being written */
	static final long IDLE_MS = 2 * 60 * 1000;
	/**
	 * How much of a file is read to find its first line. A header is a few hundred bytes, and a gzip from
	 * someone else can unpack to a thousand times its size, so a line must not be read to wherever it ends
	 */
	static final int FIRST_LINE_BYTES = 1 << 16;
	/** How far a gzip is unpacked to find its length: past any real recording, as in the replay page */
	static final long MAX_UNPACKED = 500L * 1024 * 1024;
	private static final Pattern COMMENT_TICKS = Pattern.compile("\"ticks\":(\\d+)");
	private static final int FLAG_EXTRA = 4;
	private static final int FLAG_NAME = 8;
	private static final int FLAG_COMMENT = 16;

	private Compressed()
	{
	}

	static boolean isGzip(byte[] head, int length)
	{
		return length >= 2 && (head[0] & 0xff) == 0x1f && (head[1] & 0xff) == 0x8b;
	}

	/**
	 * Gzips a recording next to itself, then deletes the plain file, and returns the new file's name. Only
	 * whole lines are kept, so a recording cut off by a crash loses just its half-written last line;
	 * {@code endLine}, if given, goes after them. {@code ticks} is the recording's length, or -1 if unknown
	 */
	static String compress(Filepath dir, String fileName, int ticks, String endLine) throws IOException
	{
		Filepath plain = dir.joinSegment(fileName);
		String packedName = fileName + EXT;
		Filepath packed = dir.joinSegment(packedName);
		Filepath part = dir.joinSegment(packedName + PART);
		try (FileChannel in = plain.openFileChannel(StandardOpenOption.READ))
		{
			long keep = wholeLines(in);
			// CREATE_NEW: if another RuneLite is packing the same file, leave it to that one
			try (OutputStream out = new BufferedOutputStream(part.openOutputStream(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE), 1 << 16))
			{
				gzip(in, keep, endLine, ticks, out);
			}
			catch (IOException | RuntimeException e)
			{
				part.deleteIfExists();
				throw e;
			}
		}
		part.moveTo(packed);
		try
		{
			plain.delete();
		}
		catch (IOException e)
		{
			// Open elsewhere, say in the replay, on Windows: the lists show the packed one, and the plain
			// copy goes the next time the plugin starts
			log.warn("Packed {} but couldn't delete the unpacked copy yet", fileName, e);
		}
		return packedName;
	}

	/** Writes the first {@code length} bytes of {@code in}, then {@code endLine}, as a gzip with the length in its header */
	static void gzip(FileChannel in, long length, String endLine, int ticks, OutputStream out) throws IOException
	{
		byte[] comment = ticks >= 0 ? ("{\"ticks\":" + ticks + "}").getBytes(StandardCharsets.ISO_8859_1) : null;
		// Magic, deflate, flags, no time, no extra flags, unknown OS
		out.write(new byte[]{0x1f, (byte) 0x8b, 8, (byte) (comment != null ? FLAG_COMMENT : 0), 0, 0, 0, 0, 0, (byte) 255});
		if (comment != null)
		{
			out.write(comment);
			out.write(0);
		}
		Deflater deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
		CRC32 crc = new CRC32();
		long size = 0;
		try
		{
			DeflaterOutputStream deflate = new DeflaterOutputStream(out, deflater, 1 << 16);
			ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
			while (size < length)
			{
				buffer.clear();
				buffer.limit((int) Math.min(buffer.capacity(), length - size));
				int read = in.read(buffer, size);
				if (read <= 0)
				{
					break;
				}
				deflate.write(buffer.array(), 0, read);
				crc.update(buffer.array(), 0, read);
				size += read;
			}
			if (endLine != null)
			{
				byte[] end = (endLine + "\n").getBytes(StandardCharsets.UTF_8);
				deflate.write(end);
				crc.update(end);
				size += end.length;
			}
			deflate.finish();
		}
		finally
		{
			deflater.end();
		}
		writeIntLE(out, crc.getValue());
		writeIntLE(out, size);
	}

	private static void writeIntLE(OutputStream out, long value) throws IOException
	{
		for (int i = 0; i < 4; i++)
		{
			out.write((int) (value >>> (8 * i)) & 0xff);
		}
	}

	/** How many bytes of the file are whole lines: up to and including its last newline */
	static long wholeLines(FileChannel in) throws IOException
	{
		long end = in.size();
		ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
		while (end > 0)
		{
			long from = Math.max(0, end - buffer.capacity());
			buffer.clear();
			buffer.limit((int) (end - from));
			while (buffer.hasRemaining() && in.read(buffer, from + buffer.position()) > 0)
			{
				// Keep reading until the chunk is in
			}
			for (int i = buffer.position() - 1; i >= 0; i--)
			{
				if (buffer.get(i) == '\n')
				{
					return from + i + 1;
				}
			}
			end = from;
		}
		return 0;
	}

	/** The length a gzip's header gives, from its first bytes; -1 if it gives none */
	static int ticksFromHeader(byte[] head, int length)
	{
		if (!isGzip(head, length) || length < 10)
		{
			return -1;
		}
		int flags = head[3] & 0xff;
		int at = 10;
		if ((flags & FLAG_EXTRA) != 0)
		{
			if (at + 2 > length)
			{
				return -1;
			}
			at += 2 + ((head[at] & 0xff) | (head[at + 1] & 0xff) << 8);
		}
		if ((flags & FLAG_NAME) != 0)
		{
			while (at < length && head[at] != 0)
			{
				at++;
			}
			at++;
		}
		if ((flags & FLAG_COMMENT) == 0 || at >= length)
		{
			return -1;
		}
		int start = at;
		while (at < length && head[at] != 0)
		{
			at++;
		}
		Matcher m = COMMENT_TICKS.matcher(new String(head, start, at - start, StandardCharsets.ISO_8859_1));
		return m.find() ? Integer.parseInt(m.group(1)) : -1;
	}

	/** A recording's length in ticks: from its header, or else from its last lines, unpacking it to reach them */
	static int ticksOf(Filepath f)
	{
		byte[] head = new byte[512];
		int length;
		try (InputStream in = f.openInputStream())
		{
			length = readUpTo(in, head);
		}
		catch (IOException e)
		{
			return -1;
		}
		int ticks = ticksFromHeader(head, length);
		if (ticks >= 0)
		{
			return ticks;
		}
		// Gzipped by something else: keep the last few KB as it unpacks
		try (InputStream in = new GZIPInputStream(f.openInputStream(), 1 << 16))
		{
			return ticksFromEnd(in, MAX_UNPACKED);
		}
		catch (IOException e)
		{
			return -1;
		}
	}

	/** The length from the end of an unpacking recording; -1 if it gives none or runs past {@code limit} */
	static int ticksFromEnd(InputStream in, long limit)
	{
		byte[] ring = new byte[Recordings.TAIL_BYTES];
		long total = 0;
		try
		{
			byte[] chunk = new byte[1 << 16];
			for (int read; (read = in.read(chunk)) > 0; )
			{
				for (int i = 0; i < read; i++)
				{
					ring[(int) ((total + i) % ring.length)] = chunk[i];
				}
				total += read;
				if (total > limit)
				{
					return -1;
				}
			}
		}
		catch (IOException e)
		{
			// Cut off: what came out so far will do
		}
		int kept = (int) Math.min(total, ring.length);
		byte[] tail = new byte[kept];
		for (int i = 0; i < kept; i++)
		{
			tail[i] = ring[(int) ((total - kept + i) % ring.length)];
		}
		return Recordings.ticksFromTail(new String(tail, StandardCharsets.UTF_8));
	}

	private static int readUpTo(InputStream in, byte[] into) throws IOException
	{
		int length = 0;
		for (int read; length < into.length && (read = in.read(into, length, into.length - length)) > 0; )
		{
			length += read;
		}
		return length;
	}

	/** A recording's first line, unpacked if it's gzipped */
	static String firstLine(Filepath f) throws IOException
	{
		byte[] head = new byte[2];
		int length;
		try (InputStream in = f.openInputStream())
		{
			length = readUpTo(in, head);
		}
		try (InputStream in = f.openInputStream();
			InputStream unpacked = isGzip(head, length) ? new GZIPInputStream(in) : in)
		{
			return firstLine(unpacked);
		}
	}

	/**
	 * The start of a stream, up to its first newline or {@link #FIRST_LINE_BYTES}. Stops at the newline, so
	 * a recording cut off further on still gives its header
	 */
	static String firstLine(InputStream in) throws IOException
	{
		byte[] start = new byte[FIRST_LINE_BYTES];
		int length = 0;
		for (int read; length < start.length && (read = in.read(start, length, start.length - length)) > 0; )
		{
			for (int i = length; i < length + read; i++)
			{
				if (start[i] == '\n')
				{
					return new String(start, 0, i, StandardCharsets.UTF_8);
				}
			}
			length += read;
		}
		return new String(start, 0, length, StandardCharsets.UTF_8);
	}

	/** Whether another RuneLite holds this file's lock, so it's still recording into it */
	static boolean inUse(Filepath f)
	{
		try (FileChannel channel = f.openFileChannel(StandardOpenOption.READ, StandardOpenOption.WRITE))
		{
			FileLock lock = channel.tryLock(LOCK_AT, 1, false);
			if (lock == null)
			{
				return true;
			}
			lock.release();
			return false;
		}
		catch (IOException | OverlappingFileLockException e)
		{
			return true;
		}
	}

	/**
	 * Packs what a recording that never stopped left behind: the client closed before it finished packing,
	 * or crashed, or was killed. Its file gets the end line it never wrote. Only plain recordings nothing
	 * has touched for {@link #IDLE_MS}, and that no other RuneLite has locked, are packed; one that ended
	 * properly but stayed plain (from before recordings were packed, or imported) is left as it is
	 */
	static void finishLeftovers(Filepath dir, long now)
	{
		if (!dir.isDirectory())
		{
			return;
		}
		List<Filepath> files;
		try (Stream<Filepath> walk = dir.walk(1))
		{
			files = walk.filter(Filepath::isFile).collect(Collectors.toList());
		}
		catch (IOException e)
		{
			return;
		}
		for (Filepath f : files)
		{
			String name = f.getFileName();
			try
			{
				if (now - f.getLastModifiedTime().toMillis() < IDLE_MS)
				{
					continue;
				}
				if (name.endsWith(".jsonl" + EXT + PART))
				{
					// Packing stopped halfway; the plain file it came from is still there
					f.deleteIfExists();
				}
				else if (name.endsWith(".jsonl"))
				{
					finishLeftover(dir, f);
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Could not finish leftover recording {}", name, e);
			}
		}
	}

	private static void finishLeftover(Filepath dir, Filepath f) throws IOException
	{
		String name = f.getFileName();
		Filepath packed = dir.joinSegment(name + EXT);
		if (packed.isFile())
		{
			// Packed, but closed before the plain copy was deleted: the packed one holds at least as much
			if (unpackedSize(packed) >= f.size())
			{
				f.delete();
			}
			return;
		}
		if (inUse(f))
		{
			return;
		}
		String tail = Recordings.tail(f, f.size());
		if (Recordings.isFinished(tail))
		{
			return;
		}
		int ticks = Recordings.ticksFromTail(tail);
		String end = ticks < 0 ? null
			: "{\"t\":" + ticks + ",\"e\":\"end\",\"reason\":\"not stopped: the client closed or crashed\",\"ticks\":" + ticks + "}";
		compress(dir, name, ticks, end);
		log.debug("Packed leftover recording {}", name);
	}

	/** A gzip's unpacked size, from its last four bytes (modulo 4 GB, more than any recording) */
	private static long unpackedSize(Filepath f) throws IOException
	{
		try (FileChannel in = f.openFileChannel(StandardOpenOption.READ))
		{
			ByteBuffer last = ByteBuffer.allocate(4);
			long size = in.size();
			if (size < 4)
			{
				return -1;
			}
			in.read(last, size - 4);
			return (last.get(0) & 0xffL) | (last.get(1) & 0xffL) << 8 | (last.get(2) & 0xffL) << 16 | (last.get(3) & 0xffL) << 24;
		}
	}
}
