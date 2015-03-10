package com.defiancecraft.configure.migrators;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.cli.CommandLine;
import org.yaml.snakeyaml.Yaml;

import com.defiancecraft.configure.util.Asker;
import com.defiancecraft.configure.util.Logger;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

public class UserDataMigrator implements Migrator {

	public String getName() {
		return "userdata";
	}

	public String getDescription() {
		return "Moves user data from Essentials and bPermissions to the database.";
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public boolean migrate(CommandLine cmd) {
		
		Asker asker = new Asker(
			"userData", "Essentials userdata folder", "./plugins/Essentials/userdata",
			"bPerms", "bPermissions folder", "./plugins/bPermissions",
			"whitelistGroups", "User groups to whitelist", "hero,stone,diamond,iron",
			"worlds", "Worlds to get permission data from", "world",
			"customMeta", "Transfer custom prefixes/suffixes", "N",
			"dbHost", "MongoDB Host", "localhost",
			"dbPort", "MongoDB Port", "27017",
			"dbUser", "MongoDB User", "",
			"dbPass", "MongoDB Pass", "",
			"dbDB", "MongoDB Database", "minecraft"
		);
		
		// Continue to ask questions until the
		// user confirms them.
		do {
			asker.askQuestions();
		} while (!asker.confirm());	

		ServerAddress addr;
		MongoClient client;
		DB db;
		
		try {

			/*
			 * Attempt to connect to DB
			 */
			Logger.log("[*] Attempting to connect to DB", true);
			
			// Throws UnknownHostException, NumberFormatException
			addr = new ServerAddress(asker.getAnswer("dbHost"), Integer.parseInt(asker.getAnswer("dbPort")));
		
			// Throws MongoException
			if (!asker.getAnswer("dbPass").isEmpty()) {
				MongoCredential cred = MongoCredential.createMongoCRCredential(
						asker.getAnswer("dbUser"),
						asker.getAnswer("dbDB"),
						asker.getAnswer("dbPass").toCharArray());
				client = new MongoClient(addr, Arrays.asList(cred));
			} else {
				client = new MongoClient(addr);
			}
			
			// Throws MongoException
			db = client.getDB(asker.getAnswer("dbDB"));
			DBCollection usersDBC = db.getCollection("users");
			
			// Create the bulk op.
			BulkWriteOperation bulkOperation = usersDBC.initializeUnorderedBulkOperation();
			
			Logger.log("[*] Created connection to database", true);
			
			/*
			 * Open permissions file(s)
			 */
			Logger.log("[*] Loading permissions from files", true);
			
			File bPermsFolder = new File(asker.getAnswer("bPerms"));
			int processed = 0;
			
			for (String worldName : asker.getAnswer("worlds").split(",")) {
				
				Logger.log("[*] Loading permissions from world '%s'", false, worldName);
				File userFile = new File(new File(bPermsFolder, worldName), "users.yml");
				
				Yaml yaml = new Yaml();
				Map<String, Object> userFileYml = (Map<String, Object>)yaml.load(new FileInputStream(userFile));
				
				// Skip if there is no 'users' key in the users.yml file
				if (!userFileYml.containsKey("users")) {
					Logger.log("[!] Invalid user file for world %s, skipping.", false, worldName);
					continue;
				}
				
				/*
				 * Iterate over all users in the world's users.yml
				 */
				Map<String, Object> users = (Map<String, Object>)userFileYml.get("users");
				for (Entry<String, Object> user : users.entrySet()) {
					
					String uuid = user.getKey();
					Logger.log("[*] Getting perms for user '%s'", true, uuid);
					
					// Create DBO for user to insert
					DBObject userDBO = new BasicDBObject();
					userDBO.put("uuid", user.getKey());
					
					/*
					 * Get user's meta and groups from bPermissions config
					 */
					Map<String, Object> data = (Map<String, Object>) user.getValue();
					List<String> groups      = data.containsKey("groups") ? (List<String>) data.get("groups") : new ArrayList<String>();
					
					// Remove any ignored groups
					groups.removeIf((q) -> {
						for (String s : asker.getAnswer("whitelistGroups").split(","))
							if (s.equalsIgnoreCase(q)) return false;
						return true;
					});
					
					/*
					Nope; don't skip users - they need their balances!
					
					// Skip if they have no non-ignored groups
					
					if (groups.size() == 0) {
						Logger.log("[!] Skipping user '%s'; no relevant groups found", true, uuid);
						continue;
					}*/
					
					userDBO.put("groups", groups);
						
					// Get their meta if it exists and we want meta
					if (asker.getAnswer("customMeta").equalsIgnoreCase("y")
							&& data.containsKey("meta")) { 
						
						if (((Map)data.get("meta")).containsKey("prefix")) userDBO.put("custom_prefix", ((Map)data.get("meta")).get("prefix"));
						if (((Map)data.get("meta")).containsKey("suffix")) userDBO.put("custom_suffix", ((Map)data.get("meta")).get("suffix"));
						
					}
					
					/*
					 * Attempt to get user's Essentials file
					 */
					Logger.log("[*] Getting balance/name of user '%s'", true, uuid);
					
					File essentialsFile = new File(new File(asker.getAnswer("userData")), String.format("%s.yml", uuid));
					if (!essentialsFile.exists()) {
						
						Logger.log("[!] No userdata file for user '%s'", true, uuid);
						
						// Load super-complicated defaults
						userDBO.put("balance", 0d);
						
					} else {
					
						// Load their Essentials userdata
						Map<String, Object> essYaml = (Map<String, Object>) yaml.load(new FileInputStream(essentialsFile));
						
						// Add their username to DBO
						if (essYaml.containsKey("lastAccountName")) 
							userDBO.put("name", (String)essYaml.get("lastAccountName"));
						
						// Add their balance to DBO
						if (essYaml.containsKey("money"))
							userDBO.put("balance", essYaml.get("money") instanceof String ? Double.parseDouble((String) essYaml.get("money")) :
												   essYaml.get("money") instanceof Number ? ((Number)essYaml.get("money")).doubleValue() : 0d);
						else
							userDBO.put("balance", 0d);
					
					}
					
					/*
					 * Finally, add them to the DB (via an upsert)!
					 */
					bulkOperation
						.find(new BasicDBObject("uuid", uuid))
						.upsert()
						.replaceOne(userDBO);
					
					// Print out some progress
					if (++processed % 100 == 0)
						Logger.log("[*] Processed %d users", false, processed);
					
				}
					
			}
			
			Logger.log("[*] Executing the bulk operation on DB (warning: this could take a while)");
			bulkOperation.execute(WriteConcern.MAJORITY);
			Logger.log("[*] Finished executing bulk operation! Woop woop");
			
			return true;
			
		} catch (UnknownHostException e) {
			Logger.log("Error: unknown host");
			return false;
		} catch (NumberFormatException e) {
			Logger.log("Error: invalid port");
			return false;
		} catch (MongoException e) {
			Logger.log("Error: database error; %s", false, e.getMessage());
			return false;
		} catch (FileNotFoundException e) {
			Logger.log("Error: file not found; %s", false, e.getMessage());
			return false;
		}
		
	}

}
