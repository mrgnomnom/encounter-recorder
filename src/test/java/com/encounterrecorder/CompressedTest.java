package com.encounterrecorder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

public class CompressedTest
{
	private static final String LINES = "{\"t\":0,\"c\":1,\"e\":\"header\"}\n{\"t\":0,\"c\":2,\"e\":\"tick\"}\n{\"t\":1,\"c\":32,\"e\":\"tick\"}\n";

	private static byte[] pack(String text, String endLine, int ticks) throws IOException
	{
		Path file = Files.createTempFile("recording", ".jsonl");
		try
		{
			Files.write(file, text.getBytes(StandardCharsets.UTF_8));
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (FileChannel in = FileChannel.open(file, StandardOpenOption.READ))
			{
				Compressed.gzip(in, Compressed.wholeLines(in), endLine, ticks, out);
			}
			return out.toByteArray();
		}
		finally
		{
			Files.delete(file);
		}
	}

	private static String unpack(byte[] gz) throws IOException
	{
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz)))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	@Test
	public void packedRecordingsUnpackToTheSameLinesAndCarryTheirLength() throws IOException
	{
		byte[] gz = pack(LINES, null, 2);
		assertEquals(LINES, unpack(gz));
		assertEquals(2, Compressed.ticksFromHeader(gz, gz.length));
		assertTrue(Compressed.isGzip(gz, gz.length));
	}

	@Test
	public void aRecordingCutOffMidLineLosesOnlyThatLineAndGetsItsEnd() throws IOException
	{
		String end = "{\"t\":2,\"e\":\"end\",\"reason\":\"not stopped\",\"ticks\":2}";
		byte[] gz = pack(LINES + "{\"t\":2,\"c\":62,\"e\":\"cl", end, 2);
		assertEquals(LINES + end + "\n", unpack(gz));
		assertTrue(Recordings.isFinished(unpack(gz)));
		assertEquals(2, Recordings.ticksFromTail(unpack(gz)));
	}

	@Test
	public void aLongRecordingSurvivesPacking() throws IOException
	{
		StringBuilder text = new StringBuilder();
		for (int t = 0; t < 20000; t++)
		{
			text.append("{\"t\":").append(t).append(",\"c\":").append(t * 30).append(",\"e\":\"tick\",\"pad\":\"").append("x".repeat(t % 97)).append("\"}\n");
		}
		byte[] gz = pack(text.toString(), null, 20000);
		assertEquals(text.toString(), unpack(gz));
		assertTrue(gz.length * 5 < text.length());
	}

	@Test
	public void gzipsWithoutOurHeaderHaveNoLength() throws IOException
	{
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (GZIPOutputStream gz = new GZIPOutputStream(out))
		{
			gz.write(LINES.getBytes(StandardCharsets.UTF_8));
		}
		byte[] bytes = out.toByteArray();
		assertEquals(-1, Compressed.ticksFromHeader(bytes, bytes.length));
		assertEquals(-1, Compressed.ticksFromHeader(pack(LINES, null, -1), 30));
		byte[] plain = LINES.getBytes(StandardCharsets.UTF_8);
		assertFalse(Compressed.isGzip(plain, plain.length));
	}

	@Test
	public void finishedAndUnfinishedRecordingsAreToldApart()
	{
		assertTrue(Recordings.isFinished("{\"t\":5,\"c\":1,\"e\":\"tick\"}\n{\"t\":6,\"c\":2,\"e\":\"end\",\"reason\":\"stopped by hand\",\"ticks\":6}\n"));
		assertFalse(Recordings.isFinished("{\"t\":5,\"c\":1,\"e\":\"tick\"}\n{\"t\":6,\"c\":2,\"e\":\"cl"));
		assertFalse(Recordings.isFinished(""));
	}

	@Test
	public void packedRecordingsKeepTheirNamesAndEnding()
	{
		assertEquals("TFA 5man", Recordings.entry("20261027-025914-TFA-5man.jsonl.gz", 0, 1).name);
		assertEquals("TFA-1", Recordings.entry("20261027-025914-TFA_1.jsonl.gz", 0, 1).name);
		assertEquals(2026, Recordings.entry("20261027-025914-TFA.jsonl.gz", 0, 1).when.getYear());
		assertEquals("20261027-025914-Ocellus.jsonl.gz", Recordings.renamed("20261027-025914-TFA.jsonl.gz", "Ocellus"));
		java.time.LocalDateTime t = java.time.LocalDateTime.of(2026, 10, 27, 9, 5);
		assertEquals("20261027-025914-TFA.jsonl.gz", Recordings.importName("20261027-025914-TFA.jsonl.gz", t));
		assertEquals("20261027-090500-clip.jsonl.gz", Recordings.importName("clip.jsonl.gz", t));
		assertEquals("20261027-090500-clip.jsonl.gz", Recordings.importName("clip.gz", t));
		assertTrue(Recordings.isRecordingName("20261027-025914-TFA.jsonl.gz"));
		assertFalse(Recordings.isRecordingName("20261027-025914-TFA.jsonl.gz.part"));
	}

	@Test
	public void packedAndPlainRecordingsShareOneSetOfNames()
	{
		List<String> folder = new ArrayList<>(Arrays.asList("20261027-100000-TFA.jsonl.gz"));
		assertEquals("20261027-110000-TFA_1.jsonl", Recordings.numbered(folder, "20261027-110000-TFA.jsonl", null));
		folder.add("20261027-110000-TFA_1.jsonl");
		assertEquals("20261027-120000-TFA_2.jsonl.gz", Recordings.numbered(folder, "20261027-120000-TFA.jsonl.gz", null));
		// Packing a recording keeps its number: the plain file it replaces is left out
		assertEquals("20261027-110000-TFA_1.jsonl.gz", Recordings.numbered(folder, "20261027-110000-TFA_1.jsonl.gz", "20261027-110000-TFA_1.jsonl"));
	}
}
