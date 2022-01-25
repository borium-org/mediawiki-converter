package org.borium.mediawikiconverter;

import java.io.*;
import java.util.*;

public class MediaWikiConverter
{
	/** Input folder with trailing separator. */
	private static String inputFolder;

	/** Output folder with trailing separator. */
	private static String outputFolder;

	/** Name of the first page to load. */
	private static String indexPage;

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
		readPage(indexPage, "index.html");
		// TODO Auto-generated method stub
	}

	private static void readPage(String inputFileName, String outputFileName)
	{
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
				output.add(line);
			}
			br.close();
			PrintWriter pw = new PrintWriter(outputFolder + "/" + outputFileName);
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
