package com.defiancecraft.configure.migrators;

import org.apache.commons.cli.CommandLine;

public interface Migrator {

	/**
	 * Gets the friendly name for this Migrator
	 * 
	 * @return The name of the Migrator
	 */
	public String getName();
	
	/**
	 * Gets a description of this Migrator
	 * 
	 * @return Description of Migrator
	 */
	public String getDescription();
	
	/**
	 * Performs the migration operation.
	 * 
	 * @param cmd CommandLine object with arguments that may
	 * 			  be required for the Migrator.
	 * @return Whether the operation was successful.
	 */
	public boolean migrate(CommandLine cmd);
	
}
