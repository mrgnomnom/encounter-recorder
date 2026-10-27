package com.encounterrecorder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes player names out of recorded text, for the "Hide player names" setting. Jagex names are matched
 * however the game writes them: any case, and with spaces, non-breaking spaces, underscores or hyphens
 * between their words.
 */
final class Names
{
	static final String HIDDEN = "<player>";

	private Names()
	{
	}

	static String scrub(String text, Collection<String> names)
	{
		if (text == null || text.isEmpty() || names.isEmpty())
		{
			return text;
		}
		// Longer names first, so "Bob Smith" goes before "Bob"
		List<String> sorted = new ArrayList<>(names);
		sorted.sort((a, b) -> b.length() - a.length());
		String out = text;
		for (String name : sorted)
		{
			Pattern p = pattern(name);
			if (p != null)
			{
				out = p.matcher(out).replaceAll(Matcher.quoteReplacement(HIDDEN));
			}
		}
		return out;
	}

	private static Pattern pattern(String name)
	{
		String[] words = name.replace(' ', ' ').trim().split("[ _-]+");
		StringBuilder re = new StringBuilder("(?<![A-Za-z0-9])");
		int kept = 0;
		for (String word : words)
		{
			if (word.isEmpty())
			{
				continue;
			}
			if (kept++ > 0)
			{
				re.append("[ _\\u00A0-]");
			}
			re.append(Pattern.quote(word));
		}
		return kept == 0 ? null : Pattern.compile(re.append("(?![A-Za-z0-9])").toString(), Pattern.CASE_INSENSITIVE);
	}
}
