package com.defiancecraft.configure.migrators;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public class MigratorRegistry {

	private static List<Migrator> migrators = new ArrayList<Migrator>();
	
	/**
	 * Registers a Migrator with the registry
	 * 
	 * @param m Migrator
	 */
	static void registerMigrator(Migrator m) {
		migrators.add(m);
	}
	
	/**
	 * Gets a Migrator object by name
	 * 
	 * @param name Name of Migrator
	 * @return Optional Migrator object; can contain null if it was not found
	 */
	public static Optional<Migrator> getMigrator(String name) {
		
		Stream<Migrator> migs = migrators
			.stream()
			.filter((m) -> m.getName().equalsIgnoreCase(name));
		
		return migs.findFirst();
		
	}
	
	/**
	 * Gets a list of all Migrators
	 * 
	 * @return List<Migrator>
	 */
	public static List<Migrator> getAllMigrators() {
		return MigratorRegistry.migrators;
	}
	
	static {
		registerMigrator(new UserDataMigrator());
	}
	
}
