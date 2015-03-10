package com.defiancecraft.configure;

import java.util.List;
import java.util.Optional;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.defiancecraft.configure.migrators.Migrator;
import com.defiancecraft.configure.migrators.MigratorRegistry;
import com.defiancecraft.configure.util.Logger;

public class DefianceConfigure {

	private static final Options OPTIONS = new Options();
	
	static {
		OPTIONS.addOption("h", "help", false, "Shows help");
		OPTIONS.addOption(Option.builder("m")
			.longOpt("migrator")
			.hasArg()
			.argName("name")
			.desc("Runs a migrator")
			.build());
		OPTIONS.addOption("l", "list-migrators", false, "Lists all available migrators");
		OPTIONS.addOption("v", "verbose", false, "Enables verbose output");
	}
	
	public static void main(String[] args) {
		
		try {
			parse(args);
		} catch (ParseException e) {
			printHelp();
		}
		
	}
	
	private static void parse(String[] args) throws ParseException {
		
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(OPTIONS, args);
		
		/*
		 * --verbose
		 */
		if (cmd.hasOption('v'))
			Logger.setVerbose(true);
		
		/*
		 * --migrator=<name>
		 */
		if (cmd.hasOption('m')) {
			
			String migrator = cmd.getOptionValue('m');
			Optional<Migrator> mig;
			
			// Fail if migrator is not found.
			if (!(mig = MigratorRegistry.getMigrator(migrator)).isPresent()) {
				Logger.log("Error: migrator %s not found.", false, migrator);
				return;
			}
			
			Logger.log("Found migrator %s!", true, migrator);
			boolean success = mig.get().migrate(cmd);
			
			if (!success)
				Logger.log("Migration operation failed.");
			else
				Logger.log("Migration operation succeeded!");
			
		/*
		 * --list-migrators 
		 */
		} else if (cmd.hasOption("l")) {
			
			List<Migrator> migrators = MigratorRegistry.getAllMigrators();
			if (migrators.size() == 0)
				Logger.log("No migrators are installed.");
			else {
				Logger.log("Installed Migrators:");
				int maxLength = migrators.stream()
						.mapToInt((m) -> m.getName().length())
						.max()
						.getAsInt();
				for (Migrator m : migrators)
					Logger.log("- %s:%s %s", false,
							m.getName(),
							m.getName().length() < maxLength ? new String(new char[maxLength - m.getName().length()]).replace("\0", " ") : "",
							m.getDescription());
			}
			
		/*
		 * --help
		 */
		} else {
			
			printHelp();
			
		}
		
	}
	
	private static void printHelp() {
		
		HelpFormatter formatter = new HelpFormatter();
		formatter.setLeftPadding(2);
		formatter.printHelp(
				"java -jar dcconf.jar",
				"Utility to perform maintenance on DefianceCraft servers and databases and do the necessary configurations automagically.\n\n",
				OPTIONS,
				"",
				false);
		
	}
	
}
