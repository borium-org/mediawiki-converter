package org.borium.mediawikiconverter;

import java.io.*;

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
		// TODO Auto-generated method stub
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
}
