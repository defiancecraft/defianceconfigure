package com.defiancecraft.configure.migrators;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.cli.CommandLine;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.jnbt.ByteTag;
import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.ListTag;
import org.jnbt.NBTInputStream;
import org.jnbt.ShortTag;
import org.jnbt.StringTag;
import org.jnbt.Tag;

import com.defiancecraft.configure.util.Asker;
import com.defiancecraft.configure.util.Logger;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.DBRef;
import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;

public class EnderChestMigrator implements Migrator {

	private static final String ITEMS_JSON_URL = "http://minecraft-ids.grahamedgecombe.com/items.json";
	private static final String USER_AGENT 	   = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/41.0.2272.89 Safari/537.36";
	private static final int BUFFER_SIZE       = 8192;
	private static final Map<Integer, String> ENCHANTMENTS = new HashMap<Integer, String>();
	
	static {
		
		// Because everyone loves hardcoded enchantments!
		ENCHANTMENTS.put(0, "PROTECTION_ENVIRONMENTAL");
		ENCHANTMENTS.put(1, "PROTECTION_FIRE");
		ENCHANTMENTS.put(2, "PROTECTION_FALL");
		ENCHANTMENTS.put(3, "PROTECTION_EXPLOSIONS");
		ENCHANTMENTS.put(4, "PROTECTION_PROJECTILE");
		ENCHANTMENTS.put(5, "OXYGEN");
		ENCHANTMENTS.put(6, "WATER_WORKER");
		ENCHANTMENTS.put(7, "THORNS");
		ENCHANTMENTS.put(8, "DEPTH_STRIDER");
		ENCHANTMENTS.put(16, "DAMAGE_ALL");
		ENCHANTMENTS.put(17, "DAMAGE_UNDEAD");
		ENCHANTMENTS.put(18, "DAMAGE_ARTHROPODS");
		ENCHANTMENTS.put(19, "KNOCKBACK");
		ENCHANTMENTS.put(20, "FIRE_ASPECT");
		ENCHANTMENTS.put(21, "LOOT_BONUS_MOBS");
		ENCHANTMENTS.put(32, "DIG_SPEED");
		ENCHANTMENTS.put(33, "SILK_TOUCH");
		ENCHANTMENTS.put(34, "DURABILITY");
		ENCHANTMENTS.put(35, "LOOT_BONUS_BLOCKS");
		ENCHANTMENTS.put(48, "ARROW_DAMAGE");
		ENCHANTMENTS.put(49, "ARROW_KNOCKBACK");
		ENCHANTMENTS.put(50, "ARROW_FIRE");
		ENCHANTMENTS.put(51, "ARROW_INFINITE");
		ENCHANTMENTS.put(61, "LUCK");
		ENCHANTMENTS.put(62, "LURE");
		
	}
	
	public String getName() {
		return "enderchest";
	}

	public String getDescription() {
		return "Migrates enderchest data to the database for the EnderStorage module";
	}
	
	public boolean migrate(CommandLine cmd) {

		Asker asker = new Asker(
			"playerData", "Where is the playerdata directory?", "./world/playerdata",
			"dbHost", "MongoDB Host", "localhost",
			"dbPort", "MongoDB Port", "27017",
			"dbUser", "MongoDB User", "",
			"dbPass", "MongoDB Pass", "",
			"dbDB", "MongoDB Database", "minecraft"
		);
		
		do {
			asker.askQuestions();
		} while (!asker.confirm());
		
		ServerAddress addr;
		MongoClient client;
		DB db;
		
		try {
			
			/*
			 * Get the item list
			 */
			Logger.log("[*] Obtaining the list of items...", true);
			JsonItem[] jsonItems = getListOfItems();
			JsonItem.registerItems(jsonItems);
				
			/*
			 * Attempt to connect to DB
			 */
			Logger.log("[*] Attempting to connect to DB", true);
			
			addr = new ServerAddress(asker.getAnswer("dbHost"), Integer.parseInt(asker.getAnswer("dbPort")));
		
			if (!asker.getAnswer("dbPass").isEmpty()) {
				MongoCredential cred = MongoCredential.createMongoCRCredential(
						asker.getAnswer("dbUser"),
						asker.getAnswer("dbDB"),
						asker.getAnswer("dbPass").toCharArray());
				client = new MongoClient(addr, Arrays.asList(cred));
			} else {
				client = new MongoClient(addr);
			}
			
			db = client.getDB(asker.getAnswer("dbDB"));
			
			/*
			 * Get collections, and generate bulk operation
			 */
			DBCollection usersDBC = db.getCollection("users");
			DBCollection banksDBC = db.getCollection("banks");
			
			// Create the bulk op.
			BulkWriteOperation bulkOperation = banksDBC.initializeUnorderedBulkOperation();
			
			Logger.log("[*] Created connection to database", true);
			
			/*
			 * Try to open playerData directory
			 */
			File playerDataDirectory = new File(asker.getAnswer("playerData"));
			if (!playerDataDirectory.isDirectory()) {
				Logger.log("Error: playerdata directory is non-existent/not a directory.");
				return false;
			}
			
			// List playerdata files if they are in UUID format
			File[] playerFiles = playerDataDirectory.listFiles(new FilenameFilter() {
				public boolean accept(File dir, String name) {
					return name.matches("^[a-zA-Z0-9]{8}-(?:[a-zA-Z0-9]{4}-){3}[a-zA-Z0-9]{12}\\.dat$");
				}
			});
			
			/*
			 * Iterate over player files
			 */
			
			Logger.log("[*] Beginning iteration over player files...", false);
			int processed = 0;
			
			for (File playerFile : playerFiles) {

				// Process the player!
				try {
					Logger.log("[*] Processing user file '%s'", true, playerFile.getName());
					processPlayerFile(db, usersDBC, bulkOperation, playerFile);
				} catch (Exception e) {
					Logger.log("[!] Invalid userdata file: %s", false, playerFile.getName());
					//[DEBUG]
					e.printStackTrace();
					//[/DEBUG]
				}
				
				// Log the number of processed users, and percent completed
				if (++processed % 100 == 0)
					Logger.log(
							"[*] (%d%%) Processed %d users",
							false,
							(int)((double)processed / playerFiles.length * 100),
							processed
					);
				
			}
			
			/*
			 * Execute bulk write operation
			 */
			Logger.log("[*] Executing the bulk write operation on the DB (warning: this could take a while)");
			bulkOperation.execute(WriteConcern.MAJORITY);
			Logger.log("[*] Finished executing bulk write operation! Woop woop!");
			
			return true;
			
		} catch (MongoException e) {
			Logger.log("Error: database error; %s.", false, e.getMessage());
		} catch (NumberFormatException e) {
			Logger.log("Error: invalid port.");
		} catch (UnknownHostException e) {
			Logger.log("Error: unknown host.");
		} catch (MalformedURLException e) {
			Logger.log("Error: malformed URL.");
		} catch (IOException e) {
			Logger.log("Error: IOException; stack trace below");
			e.printStackTrace();
		} catch (Exception e) {
			Logger.log("Error: shit. Apparently, it's a '%s'; stack trace below", false, e.getClass().getSimpleName());
		}
		
		return false;
		
	}
	
	/**
	 * Processes a single player file, adding their bank to
	 * the DB via `bulkOperation`, and, if necessary, creating
	 * the user via `users`.
	 *
	 * @param db Database object to use
	 * @param users Users DBCollection
	 * @param bulkOperation BulkWriteOperation to use to add user's bank
	 * @param playerFile Player file to process
	 * @throws IOException If an IO error occurs
	 * @throws MongoException If a DB error occurs
	 */
	private void processPlayerFile(DB db, DBCollection users, BulkWriteOperation bulkOperation, File playerFile) throws IOException, MongoException {
		
		NBTInputStream in = new NBTInputStream(new FileInputStream(playerFile), true);
		CompoundTag root = (CompoundTag) in.readTag();
		in.close();
		
		ListTag items = (ListTag) root.getValue().get("EnderItems");
		
		// Skip player if they have no ender items
		if (items.getValue().size() == 0)
			return;

		DBObject bankDBO = new BasicDBObject();
		List<DBObject> bankItems = new ArrayList<DBObject>();
		
		// Iterate over player's EnderItems, adding to the
		// list of bankItems the processed [serialized] item.
		for (Tag itemTag : items.getValue())
			bankItems.add(processItem(itemTag));
		
		// Attempt to obtain a reference to the user.
		String uuid = playerFile.getName().replace(".dat", "");
		DBRef userRef = getReferenceToUser(db, users, uuid);
		
		bankDBO.put("items", bankItems);
		bankDBO.put("user", userRef);
		
		// Finally, insert the DBObject!
		bulkOperation.insert(bankDBO);
		
	}

	/**
	 * Processes an item tag into a DBObject; the item tag should
	 * be an item in the player's EnderItems/Inventory.
	 * 
	 * Item tags take the structure shown here: {@link http://minecraft.gamepedia.com/Player.dat_format#Item_structure}
	 * This structure is converted into serialized equivalents through
	 * this method and the {@link #processMeta(String, CompoundTag)} method.
	 * 
	 * To deserialize new tags or metadata, look through the source code for
	 * the following files/methods:
	 * - org.bukkit.craftbukkit.inventory.CraftItemStack#getItemMeta(ItemStack)
	 * - org.bukkit.craftbukkit.inventory.CraftMetaItem#CraftMetaItem(NBTTagCompound)
	 * - Any relevant class file as per the aforementioned getItemMeta() method. 
	 * 
	 * @param itemTag The item tag to process
	 * @return A serialized DBObject
	 */
	@SuppressWarnings("deprecation")
	private DBObject processItem(Tag itemTag) {
		
		DBObject itemDBO = new BasicDBObject();
		CompoundTag item = (CompoundTag) itemTag;
		
		itemDBO.put("amount", (int)((ByteTag)item.getValue().get("Count")).getValue());
		itemDBO.put("slot", (int)((ByteTag)item.getValue().get("Slot")).getValue());
		itemDBO.put("damage", ((ShortTag)item.getValue().get("Damage")).getValue());
		
		// Apparently, IDs can be strings or shorts..
		// Fucking Notch.
		int itemId = 0;
		
		if (item.getValue().get("id") instanceof StringTag) {
			String minecraftId = ((StringTag)item.getValue().get("id")).getValue();
			itemId = JsonItem.getIdFromName(minecraftId);
		} else if (item.getValue().get("id") instanceof ShortTag) {
			itemId = ((ShortTag)item.getValue().get("id")).getValue();
		}
		
		String bukkitId = Material.getMaterial(itemId).name().toUpperCase();
		
		itemDBO.put("type", bukkitId);
		
		// Process the meta using the material name and the 'tag' tag.
		if (item.getValue().containsKey("tag"))
			itemDBO.put("meta", processMeta(bukkitId, (CompoundTag)item.getValue().get("tag")));
			
		
		return itemDBO;
		
	}

	/**
	 * Processes meta for an individual item; material should
	 * be the Bukkit name for the item.
	 * 
	 * @param material The Bukkit Material name
	 * @param tag The 'tag' tag on the item
	 * @return A DBObject containing the metadata
	 */
	private DBObject processMeta(String material, CompoundTag tag) {
		
		DBObject metaDBO = new BasicDBObject();
		Map<String, Tag> tagContents = tag.getValue();
		metaDBO.put("==", "ItemMeta");
		
		switch (material) {
		
			// Written Book or Book & Quill
			case "WRITTEN_BOOK":
			case "BOOK_AND_QUILL": {
			
				if (material.equalsIgnoreCase("WRITTEN_BOOK"))
					metaDBO.put("meta-type", "BOOK");
				else
					metaDBO.put("meta-type", "BOOK");
				
				if (tagContents.containsKey("resolved"))
					metaDBO.put("resolved", ((ByteTag)tagContents.get("resolved")).getValue());
				
				if (tagContents.containsKey("generation"))
					metaDBO.put("generation", ((IntTag)tagContents.get("generation")).getValue());
				
				if (tagContents.containsKey("author"))
					metaDBO.put("author", ((StringTag)tagContents.get("author")).getValue());
				
				if (tagContents.containsKey("title"))
					metaDBO.put("title", ((StringTag)tagContents.get("title")).getValue());
				
				if (tagContents.containsKey("pages")) {
					List<String> pages = new ArrayList<String>();
					
					for (Tag pageTag : ((ListTag)tagContents.get("pages")).getValue())
						pages.add(((StringTag)pageTag).getValue());
					
					metaDBO.put("pages", pages);
				}
				
				break;
			}
			
			// Skull Item
			case "SKULL_ITEM": {
				
				// "skull-owner" is the only important part as of 1.8; there are more parts, however
				metaDBO.put("meta-type", "SKULL");
				if (tagContents.containsKey("SkullOwner") && tagContents.get("SkullOwner") instanceof StringTag)
					metaDBO.put("skull-owner", ((StringTag)tagContents.get("SkullOwner")).getValue());
				else if (tagContents.containsKey("SkullOwner") && tagContents.get("SkullOwner") instanceof CompoundTag)
					metaDBO.put("skull-owner", ((StringTag)((CompoundTag)tagContents.get("SkullOwner")).getValue().get("Name")).getValue());
				
				break;
				
			}
			
			// Leather Armour
			case "LEATHER_HELMET":
			case "LEATHER_CHESTPLATE":
			case "LEATHER_LEGGINGS":
			case "LEATHER_BOOTS": {
				
				metaDBO.put("meta-type", "LEATHER_ARMOR");
				
				// Warning: ugly casts ahead.
				// But seriously, there didn't seem to be a cleaner way to do this. It
				// just checks for the existence of display -> color in NBT, and sets
				// the color value on the metadata to a serialized version of this.
				if (tagContents.containsKey("display"))
					if (((CompoundTag)tagContents.get("display")).getValue().containsKey("color"))
						metaDBO.put("color", serializeColor(((IntTag)((CompoundTag)tagContents.get("display")).getValue().get("color")).getValue()));
				
				break;
				
			}
			
			// Enchanted Books
			case "ENCHANTED_BOOK": {
				
				metaDBO.put("meta-type", "ENCHANTED");
				
				if (tagContents.containsKey("StoredEnchantments"))
					metaDBO.put("stored-enchants", serializeEnchantments(((ListTag)tagContents.get("StoredEnchantments")).getValue()));
				
				break;
				
			}
			
			// For everything else with meta...
			default:
				metaDBO.put("meta-type", "UNSPECIFIC");
			
		}
		
		// Serialize generic data too. This includes:
		// Display names; lore; enchantments; repair cost; attributes; HideFlags.  
		
		if (tagContents.containsKey("display")) {
			
			Map<String, Tag> displayTag = ((CompoundTag)tagContents.get("display")).getValue();
			
			// Item Display Names
			if (displayTag.containsKey("Name"))
				metaDBO.put("display-name", ((StringTag)displayTag.get("Name")).getValue());
			
			// Item Lore
			if (displayTag.containsKey("Lore")) {
				List<String> lore = new ArrayList<String>();
				for (Tag loreTag : ((ListTag)displayTag.get("Lore")).getValue())
					lore.add(((StringTag)loreTag).getValue());
				metaDBO.put("lore", lore);
			}
			
		}
		
		// Item Enchantments
		if (tagContents.containsKey("ench"))
			metaDBO.put("enchants", serializeEnchantments(((ListTag)tagContents.get("ench")).getValue()));
		
		// Repair Cost
		if (tagContents.containsKey("RepairCost"))
			metaDBO.put("repair-cost", ((IntTag)tagContents.get("RepairCost")).getValue());
		
		// Hide Flags
		if (tagContents.containsKey("HideFlags"))
			metaDBO.put("ItemFlags", ((IntTag)tagContents.get("HideFlags")).getValue());
		
		return metaDBO;
		
	}
	
	/**
	 * Serializes a colour integer value into a DBObject
	 * 
	 * @param color Colour value
	 * @return Serialized colour
	 */
	private DBObject serializeColor(int color) {
		
		DBObject ret = new BasicDBObject(ConfigurationSerialization.SERIALIZED_TYPE_KEY, "Color");
		
		ret.put("RED", color >> 16);
		ret.put("GREEN", (color >> 8) & 0xFF);
		ret.put("BLUE", color & 0xFF);

		return ret;
		
	}
	
	/**
	 * Serializes a list of enchantments into a DBObject
	 * 
	 * @param enchants Enchants to serialize
	 * @return DBObject
	 */
	private DBObject serializeEnchantments(List<Tag> enchants) {
		
		DBObject ret = new BasicDBObject();
		
		// Put enchants as key-value pairs (enchant => level)
		for (Tag enchantTag : enchants) {
			Map<String, Tag> enchant = ((CompoundTag)enchantTag).getValue();
			int id = ((ShortTag)enchant.get("id")).getValue();
			int lvl = ((ShortTag)enchant.get("lvl")).getValue();

			ret.put(getEnchantmentName(id), lvl);
		}
		
		return ret;
		
	}
	
	/**
	 * Obtains a reference to a user in the DB by
	 * their UUID. If the user does not exist, they
	 * will be created.
	 * 
	 * @param uuid UUID of user
	 * @return DB Reference to user.
	 */
	private DBRef getReferenceToUser(DB db, DBCollection users, String uuid) {
		
		DBObject user = users.findOne(new BasicDBObject("uuid", uuid));
		
		// Insert the user if they don't exist
		if (user == null) {
			Logger.log("[*] Creating user '%s'", false, uuid);
			user = new BasicDBObject("uuid", uuid);
			users.insert(user);
		}
		
		return new DBRef(db, users.getName(), user.get("_id"));
		
	}
	
	/**
	 * Gets a list of JsonItems so that their ID can be obtained
	 * from their Minecraft ID.
	 * 
	 * @return JsonItem[] list
	 * @throws IOException If the list could not be retrieved
	 */
	private JsonItem[] getListOfItems() throws IOException {
		
		// Connect to URL
		URLConnection conn = new URL(ITEMS_JSON_URL).openConnection();
		conn.setRequestProperty("User-Agent", USER_AGENT);
		String json = readStream(conn.getInputStream());
		
		// Parse json
		Gson gson = new GsonBuilder().disableHtmlEscaping().create();
		return gson.fromJson(json, JsonItem[].class);
		
	}
	
	/**
	 * Because Bukkit is an arse and doesn't actually register
	 * enchantments until Bukkit itself is loaded... BRING ON
	 * THE HARDCODED ENCHANTMENTS.
	 * 
	 * @param id ID of enchantment
	 * @return Bukkit Enchantment name, or null if non-existent
	 */
	private String getEnchantmentName(int id) {
		
		return ENCHANTMENTS.containsKey(id) ? ENCHANTMENTS.get(id) : null;
		
	}
	
	/**
	 * Reads an InputStream to a String
	 * 
	 * @param in InputStream to read
	 * @return UTF-8 String
	 * @throws IOException If an IO error occurred
	 */
	private String readStream(InputStream in) throws IOException {
		
		byte[] buffer = new byte[BUFFER_SIZE];
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		int bytesRead = 0;
		
		while ((bytesRead = in.read(buffer)) > -1)
			out.write(buffer, 0, bytesRead);
		
		return out.toString();
		
	}
	
	/**
	 * A class representing a JsonItem as represented in the URL {@link EnderChestMigrator#ITEMS_JSON_URL}.
	 */
	public static class JsonItem {
		
		public int type;
		public int meta;
		public String name;
		public String text_type;
		
		private static Map<String, Integer> itemMap = new HashMap<String, Integer>();
		
		/**
		 * Registers items so that they can be retrieved
		 * using #getByName(String)
		 * 
		 * @param items Items to register
		 */
		public static void registerItems(JsonItem[] items) {
			
			for (JsonItem item : items)
				itemMap.put(item.text_type.toLowerCase(), item.type);
			
		}

		/**
		 * Gets a registered item by name
		 * 
		 * @param name Name of item (Minecraft ID)
		 * @return JsonItem
		 */
		public static int getIdFromName(String name) {
		
			if (name.startsWith("minecraft:"))
				name = name.substring(10);
			
			return itemMap.containsKey(name.toLowerCase()) ? itemMap.get(name.toLowerCase()) : 0;
			
		}
		
	}
	
}
