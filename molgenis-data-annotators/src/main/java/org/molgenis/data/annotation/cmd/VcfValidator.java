package org.molgenis.data.annotation.cmd;

import static org.apache.commons.io.FilenameUtils.removeExtension;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VcfValidator
{
	private String vcfValidatorLocation;

	public VcfValidator(String vcfValidatorLocation)
	{
		this.vcfValidatorLocation = vcfValidatorLocation;
	}

	/**
	 * Validation method that calls the perl executable of the vcf-validator. Logs vcf validation output into a log file
	 * 
	 * @param The
	 *            VCF file generated by the annotation process
	 * @return Success or fail message
	 */
	public String validateVCF(File vcfFile)
	{
		try
		{
			// Checks if vcf-tools is present
			if (vcfValidatorLocation == null || !new File(vcfValidatorLocation).exists())
			{
				return "No vcf-validator present, skipping validation.";
			}

			ProcessBuilder processBuilder = new ProcessBuilder(vcfValidatorLocation, vcfFile.getAbsolutePath(), "-u",
					"-d").directory(new File(vcfValidatorLocation).getParentFile());

			Process proc = processBuilder.start();

			InputStream inputStream = proc.getInputStream();
			Scanner scanner = new Scanner(inputStream);

			InputStream errorStream = proc.getErrorStream();
			Scanner errorScanner = new Scanner(errorStream);

			String line = "";
			Integer errorCount = null;
			Pattern p = Pattern.compile("(\\d+)\\s*errors\\s*total");

			String fileNameWithoutExtension = removeExtension(vcfFile.getName());
			File logFile = new File(fileNameWithoutExtension + "-validation.log");
			if (!logFile.exists())
			{
				logFile.createNewFile();
			}

			FileWriter fileWriter = new FileWriter(logFile.getAbsoluteFile(), true);
			BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

			Date date = new Date();

			bufferedWriter.write("### Validation report for " + vcfFile.getName() + "\n");
			bufferedWriter.write("### Validation date: " + date + "\n");
			while (proc.isAlive() || scanner.hasNext() || errorScanner.hasNext())
			{
				if (errorScanner.hasNext())
				{
					while (errorScanner.hasNext())
					{
						line = errorScanner.nextLine();
						bufferedWriter.write("ERR> " + line + "\n");
					}
				}

				while (scanner.hasNext())
				{
					line = scanner.nextLine();
					bufferedWriter.write(line + "\n");
					Matcher m = p.matcher(line);
					if (m.find())
					{
						errorCount = Integer.parseInt(m.group(1));
					}
				}
			}

			bufferedWriter.write("\n##################################################\n");
			bufferedWriter.close();

			scanner.close();

			if (errorCount != null && errorCount == 0)
			{
				return "VCF file [" + vcfFile.getName() + "] passed validation.";
			}
			else
			{
				return "VCF file [" + vcfFile.getName() + "] did not pass validation, see the log for more details.";
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException("Something went wrong: " + e);
		}
	}
}
