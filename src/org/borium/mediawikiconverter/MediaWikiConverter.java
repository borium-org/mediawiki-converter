package org.borium.mediawikiconverter;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class MediaWikiConverter
{
	/** Input folder with trailing separator. */
	private static String inputFolder;

	/** Output folder with trailing separator. */
	private static String outputFolder;

	/** Name of the first page to load. */
	private static String indexPage;

	/**
	 * Set of files copied to the destination. Set is used to avoid multiple copying
	 * of same file to same destination. Each entry is in 'source->destination'
	 * format so that same file can be copied to multiple destinations if needed.
	 */
	private static Set<String> copyFiles = new HashSet<>();

	/**
	 * Local pages that are already converted. Each entry is in 'wiki page'->'github
	 * page' format.
	 */
	private static HashMap<String, String> localPages = new HashMap<>();

	/** Local links that are seen but not yet converted. */
	private static Set<String> localLinks = new HashSet<>();

	public static void main(String[] args)
	{
		if (args.length == 0)
			args = new String[] { "@ResponseFile.txt" };
		for (int i = 0; i < args.length; i++)
		{
			if (args[i].startsWith("@"))
				readResponseFile(args[i].substring(1));
			else
			{
				switch (args[i])
				{
				case "-input":
					inputFolder = args[i + 1];
					i++;
					break;
				case "-output":
					outputFolder = args[i + 1];
					i++;
					break;
				case "-index":
					indexPage = args[i + 1];
					i++;
					break;
				default:
					throw new RuntimeException("Unrecognized parameter " + args[i]);
				}
			}
		}
		System.out.println("input:  " + inputFolder);
		System.out.println("output: " + outputFolder);
		System.out.println("index:  " + indexPage);

		File outputFolderFile = new File(outputFolder);
		if (!outputFolderFile.exists())
		{
			if (!new File(outputFolder).mkdir())
				throw new RuntimeException("Failed to create output folder " + outputFolder);
		}
		readPage(indexPage);
		while (localLinks.size() > 0)
		{
			String[] elements = localLinks.toArray(new String[localLinks.size()]);
			String url = elements[0];
			localLinks.remove(url);
			int pos;
			while ((pos = url.indexOf("%253A")) != -1)
			{
				url = url.substring(0, pos) + "%3A" + url.substring(pos + 5);
			}
			readPage(url);
		}
	}

	/**
	 * Copy file 'from' 'to', do it only once.
	 *
	 * @param fromFileName
	 * @param toFileName
	 */
	private static void copyFile(String fromFileName, String toFileName)
	{
		fromFileName = inputFolder + fromFileName;
		toFileName = outputFolder + toFileName;
		if (!copyFiles.contains(fromFileName + "->" + toFileName))
		{
			copyFiles.add(fromFileName + "->" + toFileName);
			File from = new File(fromFileName);
			File to = new File(toFileName);
			try
			{
				Path fromPath = from.toPath().normalize();
				Path toPath = to.toPath().normalize();
				String toFilePath = toPath.toString();
				int pos = toFilePath.lastIndexOf('\\');
				String directory = toFilePath.substring(0, pos);
				File dir = new File(directory);
				Files.createDirectories(dir.toPath());
				Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	/**
	 * Process each HTML line and extract links to local pages. The working
	 * assumption is that each link is on one line without wrapping into next line.
	 * Each line can have multiple links. Only internal links that don't begin with
	 * 'http://' or 'https://' are processed, the links with protocol in the
	 * beginning are all external links.
	 *
	 * @param line Line in the input HTML file.
	 */
	private static void processLocalLinks(String line)
	{
		int pos;
		while ((pos = line.indexOf("<a href=\"")) != -1)
		{
			line = line.substring(pos + 9);
			if (line.startsWith("http://") || line.startsWith("https://"))
			{
				line = line.substring(7);
				continue;
			}
			int pos2 = line.indexOf("\" title=\"");
			if (pos2 == -1)
			{
				int pos3 = line.indexOf(".html#");
				if (pos3 == -1)
					throw new RuntimeException("href found without terminator");
				pos2 = pos3 + 5;
			}
			String url = line.substring(0, pos2);
			while ((pos = url.indexOf("%253A")) != -1)
			{
				url = url.substring(0, pos) + "%3A" + url.substring(pos + 5);
			}
			if (!localPages.containsKey(url))
				localLinks.add(url);
			line = line.substring(pos2);
		}
	}

	private static void readPage(String inputFileName)
	{
		localPages.put(inputFileName, inputFileName);
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(inputFolder + "/" + inputFileName));
			ArrayList<String> output = new ArrayList<>();
			String line;
			while ((line = br.readLine()) != null)
			{
				// No editing.
				if (line.startsWith("<link rel=\"alternate\" type=\"application/x-wiki\" title=\"Edit\"")
						&& line.endsWith("&amp;action=edit\"/>"))
					continue;
				if (line.startsWith("<link rel=\"edit\" title=\"Edit\"") && line.endsWith("&amp;action=edit\"/>"))
					continue;
				// No search.
				if (line.startsWith("<link rel=\"search\" type=\"application/opensearchdescription+xml\""))
					continue;
				// No RSD, whatever that is...
				if (line.startsWith("<link rel=\"EditURI\" type=\"application/rsd+xml\"")
						&& line.endsWith("/wiki/api.php?action=rsd\"/>"))
					continue;
				// No Atom feed
				if (line.startsWith("<link rel=\"alternate\" type=\"application/atom+xml\"")
						&& line.endsWith("feed=atom\"/>"))
					continue;
				// No jump links
				if (line.contains("<a class=\"mw-jump-link\" href=\"Special%25"))
					continue;
				// No navigation. This is a hack to reduce number of source lines.
				if (line.equals("<div id=\"mw-navigation\">"))
					line = skipNavigation(br);
				// Pointless comments with some Unicode in them. Git does not like the idea of
				// committing these weird characters.
				if (line.equals("<!-- "))
				{
					skipPast(br, "-->");
					line = "";
				}
				// XXX Try to remove scripts
				if (line.startsWith("<script>document.documentElement.className=\"client-js\";"))
				{
					line = br.readLine();
					continue;
				}
				if (line.startsWith("<script>(RLQ=window.RLQ||[]).push(function(){mw.loader"))
				{
					line = br.readLine();
					continue;
				}
				if (line.startsWith("<script>(RLQ=window.RLQ||[]).push(function(){mw.config"))
					continue;
				// Skip a pointless ever-changing comment, two lines
				if (line.startsWith("<!-- Saved in parser cache"))
				{
					line = br.readLine();
					continue;
				}
				// XXX Try to remove scripts
				// All rejection tests passed, add the line, but optionally do some
				// substitutions first.
				// Favicon - unnecessary but
				if (line.startsWith("<link rel=\"shortcut icon\" href=\"../../favicon.ico\"/>"))
					line = replaceFavicon(line);
				// Fix the load.php location in few places.
				if (line.startsWith("<link rel=\"stylesheet\" href=\"../load.php@"))
					line = replaceLoadLocation(line);
				if (line.startsWith("<script async=\"\" src=\"../load.php@"))
					line = replaceLoadLocation(line);
				// Fix the MediaWiki logo location, just being nice...
				if (line.startsWith("\t<li id=\"footer-poweredbyico\">"))
					line = replaceMediaWikiImageLocation(line);
				// Fix the first level heading. It is same as the page URL without .html, but it
				// looks much better if camel-case title is split into individual components.
				if (line.contains("<h1 id=\"firstHeading\" class=\"firstHeading\" >"))
					line = replaceCamelCaseTitle(line);
				// All headings at any level in the body have [Edit] link. Remove it.
				if (line.contains("<span class=\"mw-editsection\">"))
					line = removeEditSection(line);
				processLocalLinks(line);
				output.add(line);
			}
			br.close();
			PrintWriter pw = new PrintWriter(outputFolder + "/" + inputFileName);
			for (String outputLine : output)
				pw.println(outputLine);
			pw.close();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Read parameters from a response file that is organized as a key-value pairs
	 * separated with '=', with same keys as the input argument string array would
	 * be.
	 *
	 * @param fileName File to read.
	 */
	private static void readResponseFile(String fileName)
	{
		try
		{
			BufferedReader br = new BufferedReader(new FileReader(fileName));
			String line;
			while ((line = br.readLine()) != null)
			{
				String[] split = line.split("=");
				switch (split[0])
				{
				case "-input":
					inputFolder = split[1];
					break;
				case "-output":
					outputFolder = split[1];
					break;
				case "-index":
					indexPage = split[1];
					break;
				default:
					throw new RuntimeException("Unrecognized parameter " + split[0]);
				}
			}
			br.close();
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	/**
	 * Remove edit section. Edit section link is in a &lt;span&gt; with class
	 * mw-editsection, and that span contains a span for '[', link to the edit
	 * functionality, and a span for ']'.
	 *
	 * @param line Input HTML line with edit section.
	 * @return Output HTML line without edit section.
	 */
	private static String removeEditSection(String line)
	{
		String thisLine = line;
		// the end of span with the title text
		int pos = thisLine.indexOf("</span>");
		if (pos == -1)
			throw new RuntimeException("No title span terminator");
		pos += 7;
		String outputLine = thisLine.substring(0, pos);
		thisLine = thisLine.substring(pos);
		// optional span with class mw-headline, include it in output
		if (thisLine.startsWith("<span class=\"mw-headline\""))
		{
			pos = thisLine.indexOf("</span>");
			if (pos == -1)
				throw new RuntimeException("No headline span terminator");
			pos += 7;
			outputLine += thisLine.substring(0, pos);
			thisLine = thisLine.substring(pos);
		}
		// skip over edit span beginning
		String editSpanStart = "<span class=\"mw-editsection\">";
		if (!thisLine.startsWith(editSpanStart))
			throw new RuntimeException("No edit section");
		thisLine = thisLine.substring(editSpanStart.length());
		// skip over opening bracket span
		String spanOpenBracket = "<span class=\"mw-editsection-bracket\">[</span>";
		if (!thisLine.startsWith(spanOpenBracket))
			throw new RuntimeException("No open bracket ");
		thisLine = thisLine.substring(spanOpenBracket.length());
		// skip over the edit link
		if (!thisLine.startsWith("<a href="))
			throw new RuntimeException("No edit link");
		pos = thisLine.indexOf("</a>") + 4;
		thisLine = thisLine.substring(pos);
		// skip over close bracket span
		String spanCloseBracket = "<span class=\"mw-editsection-bracket\">]</span>";
		if (!thisLine.startsWith(spanCloseBracket))
			throw new RuntimeException("No close bracket");
		thisLine = thisLine.substring(spanCloseBracket.length());
		// skip over edit span ending
		String spanEnd = "</span>";
		if (!thisLine.startsWith(spanEnd))
			throw new RuntimeException("No edit section end");
		thisLine = thisLine.substring(spanEnd.length());
		outputLine += thisLine;
		return outputLine;
	}

	/**
	 * Replace camel-case title in &lt;h1&gt; with a split title. If title contains
	 * spaces, such as 'Main Page', leave it as is.
	 *
	 * @param line Input HTML line with &lt;h1&gt; title.
	 * @return Output HTML line with &lt;h1&gt; title split into components.
	 */
	private static String replaceCamelCaseTitle(String line)
	{
		String h1 = "<h1 id=\"firstHeading\" class=\"firstHeading\" >";
		int pos = line.indexOf(h1);
		if (pos == -1)
			throw new RuntimeException("Found h1 once, couldn't find now");
		String heading = line.substring(pos + h1.length());
		pos = heading.indexOf("</h1>");
		if (pos == -1)
			throw new RuntimeException("h1 not terminated");
		heading = heading.substring(0, pos);
		if (heading.indexOf(' ') >= 0)
			return line;
		// https://stackoverflow.com/questions/3752636/java-split-string-when-an-uppercase-letter-is-found
		String[] split = heading.split("(?=\\p{Upper})");
		ArrayList<String> parts = new ArrayList<>();
		for (int i = 0; i < split.length; i++)
		{
			if (split[i].length() > 1)
			{
				parts.add(split[i]);
			}
			else
			{
				String acronym = split[i];
				while (i < split.length - 1)
				{
					if (split[i + 1].length() != 1)
						break;
					acronym += split[i + 1];
					i++;
				}
				parts.add(acronym);
			}
		}
		String newHeading = String.join(" ", parts);
		return "\t" + h1 + newHeading + "</h1>";
	}

	/**
	 * Copy the favicon and replace the location in the web page.
	 *
	 * @param line Line in the input HTML file.
	 * @return New line for the output HTML file.
	 */
	private static String replaceFavicon(String line)
	{
		copyFile("../../favicon.ico", "favicon.ico");
		line = "<link rel=\"shortcut icon\" href=\"favicon.ico\"/>";
		return line;
	}

	/**
	 * Replace load.php location.
	 *
	 * @param line HTML with input file location.
	 * @return HTML with output file location.
	 */
	private static String replaceLoadLocation(String line)
	{
		int pos = line.indexOf("../load.php");
		if (pos == -1)
			throw new RuntimeException("no ../load.php");
		String fileName = line.substring(pos);
		int pos2 = fileName.indexOf('"');
		if (pos2 == -1)
			throw new RuntimeException("no terminator in load.php");
		fileName = fileName.substring(0, pos2);
		while ((pos2 = fileName.indexOf("&amp;")) != -1)
		{
			fileName = fileName.substring(0, pos2) + "&" + fileName.substring(pos2 + 5);
		}
		copyFile(fileName, fileName.substring(3));
		line = line.substring(0, pos) + line.substring(pos + 3);
		return line;
	}

	/**
	 * Replace 3 image locations for MediaWiki images.
	 *
	 * @param line HTML with input file locations.
	 * @return HTML with output file locations.
	 */
	private static String replaceMediaWikiImageLocation(String line)
	{
		String output = "";
		for (int i = 0; i < 3; i++)
		{
			int pos = line.indexOf("../resources");
			if (pos == -1)
				throw new RuntimeException("No ../resources in mediawiki footer");
			output += line.substring(0, pos);
			int pos2 = line.indexOf(".png");
			if (pos2 == -1)
				throw new RuntimeException("No .png in ../resources");
			String fileName = line.substring(pos, pos2 + 4);
			copyFile(fileName, fileName.substring(3));
			output += fileName.substring(3);
			line = line.substring(pos2 + 4);
		}
		return output + line;
	}

	/**
	 * Remove navigation <div>. Reads as much as necessary to skip the web page
	 * section. Returns empty string so that all checks later will not fail with
	 * null pointer but they will not match anything in startsWith() or endsWith().
	 *
	 * @param br Buffered reader for the web page.
	 * @return 0-length string.
	 */
	private static String skipNavigation(BufferedReader br)
	{
		skipPast(br, "</nav>");
		skipPast(br, "</nav>");
		skipPast(br, "</nav>");
		skipPast(br, "</nav>");
		skipPast(br, "</nav>");
		skipPast(br, "</div>");
		skipPast(br, "</nav>");
		skipPast(br, "</nav>");
		skipPast(br, "</div>");
		skipPast(br, "</div>");
		return "";
	}

	/**
	 * Read the input and stop when we see the provided string pattern. Pattern is
	 * not included in the output.
	 *
	 * @param br     Buffered reader for the web page.
	 * @param string String to detect.
	 */
	private static void skipPast(BufferedReader br, String string)
	{
		try
		{
			String line;
			while ((line = br.readLine()) != null)
			{
				if (line.equals(string))
					break;
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
}
