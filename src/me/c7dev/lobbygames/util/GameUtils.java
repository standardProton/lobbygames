package me.c7dev.lobbygames.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.FireworkEffect.Builder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.material.Wool;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.LobbyGames;

public class GameUtils {

	public static ItemStack createItem(Material material, int amount, byte bytecode, String name, String... lore) {
		ItemStack item = new ItemStack(material, amount, bytecode);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		List<String> lore2 = new ArrayList<String>();
		for (String s : lore) lore2.add(s);
		meta.setLore(lore2);
		item.setItemMeta(meta);
		return item;
	}
	public static String[] COLOR_NAMES = {"WHITE_", "ORANGE_", "MAGENTA_", "LIGHT_BLUE_", "YELLOW_", "LIME_", "PINK_", "GRAY_", "LIGHT_GRAY_", "CYAN_", "PURPLE_", "BLUE_", "BROWN_", "GREEN_", "RED_", "BLACK_"};
	public static ItemStack createWool(int amount, int bytecode, String name, String... loreString) {
		ItemStack item = LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.valueOf(COLOR_NAMES[bytecode] + "WOOL"), amount) 
				: new ItemStack(Material.valueOf("WOOL"), amount, (byte) bytecode);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		List<String> lore = new ArrayList<String>();
		for (String s : loreString) lore.add(s);
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}
	public static ItemStack createClay(int amount, int bytecode, String name, String... loreString) {
		ItemStack item = LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.valueOf(COLOR_NAMES[bytecode] + "TERRACOTTA"), amount) 
				: new ItemStack(Material.valueOf("STAINED_CLAY"), amount, (byte) bytecode);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(name);
		List<String> lore = new ArrayList<String>();
		for (String s : loreString) lore.add(s);
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}
	
	public static ItemStack createArmor(Material m, Color c) {
		ItemStack i = createItem(m, 1, (byte) 0, "§b ");
		LeatherArmorMeta meta = (LeatherArmorMeta) i.getItemMeta();
		meta.setColor(c);
		i.setItemMeta(meta);
		return i;
	}
	
	private static void poolTrapdoors(Location l1, Location l2, BlockFace facing) {
		boolean x = l1.getX() == l2.getX();
		int min, max;
		if (x) {
			min = Math.min(l1.getBlockZ(), l2.getBlockZ());
			max = Math.max(l1.getBlockZ(), l2.getBlockZ());
		}
		else {
			min = Math.min(l1.getBlockX(), l2.getBlockX()); 
			max = Math.max(l1.getBlockX(), l2.getBlockX());
		}
		if (LobbyGames.SERVER_VERSION > 12) {
			Material mat = LobbyGames.SERVER_VERSION >= 13 ? Material.DARK_OAK_TRAPDOOR : Material.valueOf("TRAP_DOOR");
			String face_str = facing.toString().toLowerCase();
			for (int i = min; i < max; i++) {
				Block block = (new Location(l1.getWorld(), x ? l1.getX() : i, l1.getY(), x ? i : l1.getZ())).getBlock();
				block.setType(mat);
				BlockData bd = mat.createBlockData("[facing=" + face_str + ",open=true]");
				block.setBlockData(bd);
			}
		}
	}
	
	public static void initArena(Arena a, LobbyGames plugin) {
		if (a.getGameType() == GameType.CLICKER) { //special case - l1 = l2
			for (int x = -1; x < 2; x++) {
				for (int z = -1; z < 2; z++) {
					Location loc = a.getLocation1().clone().add(x, 0, z);
					if (!(x == 0 && z == 0)) loc.getBlock().setType(Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "ENDER_PORTAL_FRAME" : "END_PORTAL_FRAME"));
					loc.getBlock().getRelative(BlockFace.DOWN).setType(Material.OBSIDIAN);
					loc.add(0, 2, 0).getBlock().setType(Material.BARRIER);
				}
			}
			return;
		}
		if (a.getGameType() == GameType.POOL) {
			fill(a, LobbyGames.SERVER_VERSION <= 12 ? Material.valueOf("WOOL") : Material.GREEN_WOOL, (byte) (LobbyGames.SERVER_VERSION <= 12 ? 13:0), null, (byte) 0);
			fill(a.getLocation1().add(0, 2, 0), a.getLocation2().add(0, 2, 0), false, 0, Material.BARRIER, (byte) 0, true, null, (byte) 0, true);
			Location l1 = a.getLocation1(); Location l2 = a.getLocation2();
			
			if (l1.getX() < l2.getX()) {
				l1.add(-1, 0, 0);
				l2.add(1, 0, 0);
			} else {
				l1.add(1, 0, 0);
				l2.add(-1, 0, 0);
			}
			if (l1.getZ() < l2.getZ()) {
				l1.add(0, 0, -1);
				l2.add(0, 0, 1);
			} else {
				l1.add(0, 0, 1);
				l2.add(0, 0, -1);
			}
			
			Location l3 = l1.clone();
			l3.setX(l2.getX());
			Location l4 = l1.clone();
			l4.setZ(l2.getZ());
			
			BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
			int rot = a.getCoordinateRotation();
			poolTrapdoors(l1, l3, faces[(rot==1 || rot==2) ? 2 : 0]);
			poolTrapdoors(l3, l2, faces[rot >= 2 ? 3 : 1]);
			poolTrapdoors(l2, l4, faces[(rot==1 || rot==2) ? 0 : 2]);
			poolTrapdoors(l4, l1, faces[rot >= 2 ? 1 : 3]);
			
			
			l1.getBlock().setType(Material.AIR);
			l2.getBlock().setType(Material.AIR);
			l3.getBlock().setType(Material.AIR);
			l4.getBlock().setType(Material.AIR);
			
			return;
		}
		
		Material m1 = null, m2 = null;
		byte b1 = (byte) 0, b2 = (byte) 0;
		if (a.getGameType() == GameType.SNAKE) {
			m1 = Material.AIR;
			if (LobbyGames.SERVER_VERSION <= 12) {
				m2 = Material.valueOf("WOOL");
				b2 = (byte) 15;
			} else m2 = Material.BLACK_WOOL;
		}
		else if (a.getGameType() == GameType.MINESWEEPER) {
			m1 = Material.QUARTZ_BLOCK;
			m2 = whiteWool();
			//b2 = (byte) (LobbyGames.SERVER_VERSION <= 12 ? 8 : 0);
		}
		else if (a.getGameType() == GameType.SUDOKU) {
			m1 = Material.valueOf(LobbyGames.SERVER_VERSION > 12 ? "WHITE_CONCRETE" : "WOOL");
		}
		else if (a.getGameType() == GameType.SPLEEF) m1 = Material.SNOW_BLOCK;
		else if (a.getGameType() == GameType.T048) {
			if (LobbyGames.SERVER_VERSION <= 12) {
				m2 = Material.valueOf("WOOL");
				b2 = (byte) 8;
			} else m2 = Material.GRAY_WOOL;
		}
		else if (a.getGameType() == GameType.TICTACTOE) {
			if (LobbyGames.SERVER_VERSION <= 12) m2 = Material.valueOf("WOOD");
			else m2 = Material.DARK_OAK_PLANKS;
		}
		else if (a.getGameType() == GameType.CONNECT4) m1 = LobbyGames.SERVER_VERSION > 12 ? Material.BLUE_STAINED_GLASS : Material.valueOf("STAINED_GLASS");

		GameUtils.fill(a, m1, b1, m2, b2);
	}
	
	public static void fill(Arena arena, Material m1, byte b1, Material m2, byte b2) {
		fill(arena, m1, b1, m2, b2, true);
	}
	
	public static void fill(Arena arena, Material m1, byte b1, Material m2, byte b2, boolean absolute) {
		fill(arena.getLocation1(), arena.getLocation2(), arena.isVerticalLayout(), arena.getCoordinateRotation(), m1, b1, absolute, m2, b2, absolute);
	}
	
	public static void fill(Arena arena, Material m1, byte b1, boolean absolute1, Material m2, byte b2, boolean absolute2) {
		fill(arena.getLocation1(), arena.getLocation2(), arena.isVerticalLayout(), arena.getCoordinateRotation(), m1, b1, absolute1, m2, b2, absolute2);
	}

	public static void fill(Location l1, Location l2, boolean vertical_layout, int rot, Material m1, byte b1, boolean absolute1, Material m2, byte b2, boolean absolute2) { //do not use bytes >0 if not wool
		if (m1 == null && m2 == null) return;
		BlockFace bf = BlockFace.DOWN;
		if (vertical_layout) {
			BlockFace[] bfs = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
			bf = bfs[rot];
		}
		
		int xmin = Math.min(l1.getBlockX(), l2.getBlockX()), xmax = Math.max(l1.getBlockX(), l2.getBlockX());
		int ymin = Math.min(l1.getBlockY(), l2.getBlockY()), ymax = Math.max(l1.getBlockY(), l2.getBlockY());
		int zmin = Math.min(l1.getBlockZ(), l2.getBlockZ()), zmax = Math.max(l1.getBlockZ(), l2.getBlockZ());
		for (int x = xmin; x <= xmax; x++) {
			for (int y = ymin; y <= ymax; y++) {
				for (int z = zmin; z <= zmax; z++) {
					Block b = (Block) new Location(l1.getWorld(), x, y, z).getBlock();
					if (m1 != null && (absolute1 || b.getType() == Material.AIR)) {
						b.setType(m1);
						if (b1 > 0) setWool(b, b1);
					}
					if (m2 != null) {
						b = b.getRelative(bf);
						if (absolute2 || b.getType() == Material.AIR) {
							b.setType(m2);
							if (b2 > 0) setWool(b, b2);
						}
					}
				}
			}
		}
	}
	
	public static String incomingAliases(String s, LobbyGames plugin) {
		if (plugin.getGameAlias().containsKey(s.toLowerCase())) s = plugin.getGameAlias().get(s).toString();
		
		if (s.equalsIgnoreCase("2048")) return "T048";
		if (s.equalsIgnoreCase("connect 4")) return "connect4";
		if (s.equalsIgnoreCase("connect")) return "connect4";
		if (s.equalsIgnoreCase("football")) return "soccer";
		if (s.equalsIgnoreCase("soduko")) return "sudoku";
		if (s.equalsIgnoreCase("tic")) return "tictactoe";
		if (s.equalsIgnoreCase("ttt")) return "tictactoe";
		if (s.equalsIgnoreCase("minesw")) return "minesweeper";
		if (s.equalsIgnoreCase("mines")) return "minesweeper";
		if (s.equalsIgnoreCase("ms")) return "minesweeper";
		return s;
	}
	
	public static String getConfigName(GameType gt) {
		return gt.toString().toLowerCase().replaceAll("t048","2048");
	}
		
	public static String outgoingAliases(GameType gt, LobbyGames plugin) {
		String alias = plugin.getOutgoingGameAlias().get(gt);
		return alias == null ? gt.toString().toLowerCase().replaceAll("t048", "2048").replaceAll("connect4", "Connect 4").replaceAll("tictactoe", "Tic Tac Toe") : alias;
	}
	
	public static void spawnFirework(Location loc, Color color) {
		Firework fw = loc.getWorld().spawn(loc, Firework.class);
		FireworkMeta meta = fw.getFireworkMeta();
		Builder builder = FireworkEffect.builder();
		
		meta.addEffect(builder.flicker(true).withColor(color).build());
		meta.addEffect(builder.withFade(Color.GRAY).build());
		//meta.addEffect(builder.with(Type.BALL_LARGE).build());
		meta.setPower(0);
		fw.setFireworkMeta(meta);
		new BukkitRunnable() {
			@Override
			public void run() {
				fw.detonate();
			}
		}.runTaskLater(LobbyGames.getPlugin(LobbyGames.class), 2l);
	}
	
	public static LeaderboardEntry deserializeEntry(FileConfiguration config, String dir, long expiry) {
		String displayname = config.getString(dir + ".display_name");
		int score = config.getInt(dir + ".score");
		String displayscore = config.getString(dir + ".display_score");
		long expire = config.getLong(dir + ".expires");
		LeaderboardEntry l = new LeaderboardEntry(displayname, score, displayscore, expiry);
		l.setRawExpiration(expire);
		return l;
	}
	
	public static Location deserializeLocation(FileConfiguration config, String dir) {
		if (config.get(dir) == null) return null;
		
		double x = config.getDouble(dir + ".x");
		double y = config.getDouble(dir + ".y");
		double z = config.getDouble(dir + ".z");
		double yaw = config.getDouble(dir + ".yaw");
		double pitch = config.getDouble(dir + ".pitch");
		String world = config.getString(dir + ".world");
		return new Location(Bukkit.getWorld(world), x, y, z, (float) yaw, (float) pitch);
	}
	
	public static int getVersionInt() {
		String vs = Bukkit.getVersion();
		for (int i = 30; i > 8; i--) {
			if (vs.contains("1." + i)) return i;
		}
		return 8;
	}
	
	public static int dist(Location l1, Location l2) {
		return Math.abs(l1.getBlockX() - l2.getBlockX()) + Math.abs(l1.getBlockY() - l2.getBlockY()) + Math.abs(l1.getBlockZ() - l2.getBlockZ());
	}
	
	public static double distDXZ(Location l1, Location l2) {
		return Math.abs(l1.getX() - l2.getX()) + Math.abs(l1.getZ() - l2.getZ());
	}
	
	public static double distSquareXZ(Location l1, Location l2) {
		return Math.max(Math.abs(l1.getX() - l2.getX()), Math.abs(l1.getZ() - l2.getZ()));
	}
	
	public static void repeatParticleLine(Location l1, Location l2, LobbyGames plugin, int frames) {
		new BukkitRunnable() {
			int frame_count = 0;
			@Override
			public void run() {
				if (frame_count >= frames) {
					this.cancel();
					return;
				}
				particleLine(l1, l2);
				frame_count++;
			}
		}.runTaskTimer(plugin, 0, 5l);
	}
	
	public static void particleLine(Location l1, Location l2) {
		particleLine(l1, l2, l1.distance(l2)); //arbitrary
	}
	
	public static void particleLine(Location l1, Location l2, double distance) {
		if (LobbyGames.SERVER_VERSION == 8) return; //not supported yet
		Location s = l1.clone();
		final double particle_spacing = 0.2;
		Vector v = l2.toVector().subtract(l1.toVector()).normalize().multiply(particle_spacing);
		if (LobbyGames.SERVER_VERSION > 12) {
			Particle.DustOptions dsop = new Particle.DustOptions(Color.fromRGB(20, 20, 20), 1f);
			for (double i = 0; i < distance; i += particle_spacing) {
				s.getWorld().spawnParticle(Particle.REDSTONE, s, 1, dsop);
				s.add(v);
			}
		} else {
			for (double i = 0; i < distance; i += particle_spacing) {
				s.getWorld().spawnParticle(Particle.FALLING_DUST, s, 1);
				s.add(v);
			}
		}
	}
	
	public static ItemStack getHandItem(Player p) {
		ItemStack m = null;
		if (LobbyGames.SERVER_VERSION < 12) {
			if (p.getInventory().getItemInHand() != null) m = p.getInventory().getItemInHand();
		}
		else {
			if (p.getInventory().getItemInMainHand() != null) m = p.getInventory().getItemInMainHand();
			else if (p.getInventory().getItemInOffHand() != null) m = p.getInventory().getItemInOffHand();
		}
		return m;
	}
	
	public static void setWool(Block b, byte color) {setWool(b, (int) color);}
	public static void setWool(Block b, int color) {
		if (LobbyGames.SERVER_VERSION <= 12) {
			b.setType(Material.valueOf("WOOL"));
			//b.getState().getData().setData(color);
			BlockState state = b.getState();
			Wool w = (Wool) state.getData();
			String dye_name = COLOR_NAMES[color].substring(0, COLOR_NAMES[color].length()-1);
			if (dye_name.equals("LIGHT_GRAY")) dye_name = "GRAY";
			w.setColor(DyeColor.valueOf(dye_name));
			state.update();
		} else {
			b.setType(Material.valueOf(COLOR_NAMES[(int) color] + "WOOL"));
		}
	}
	
	public static String timeStr(int splayed) {
		int days = splayed/86400; 
		splayed -= (days*86400);
		int h = splayed/3600; 
		splayed -= (h*3600);
		int min = splayed/60; 
		int s = splayed % 60;
		String time = s + "s";
		if (min > 0) time = min + "m, " + time;
		if (h > 0) time = h + "h, " + time;
		if (days > 0) time = days + "d, " + time;
		return time;
	}
	
	public static Material whiteWool() {
		if (LobbyGames.SERVER_VERSION <= 12) return Material.valueOf("WOOL");
		else return Material.WHITE_WOOL;
	}
	
	public static Sound getSound(int version, String legacy, String curr) {
		return Sound.valueOf(LobbyGames.SERVER_VERSION <= version ? legacy : curr);
	}
	public static Sound fireworkBlastSound() {
		Sound sound = null;
		if (LobbyGames.SERVER_VERSION >= 13) sound = Sound.valueOf("ENTITY_FIREWORK_ROCKET_LAUNCH");
		else if (LobbyGames.SERVER_VERSION >= 9) sound = Sound.valueOf("ENTITY_FIREWORK_LAUNCH");
		else sound = Sound.valueOf("FIREWORK_LAUNCH");
		return sound;
	}
	public static Sound getOrbPickupSound() {
		return getSound(11, "ORB_PICKUP", "ENTITY_EXPERIENCE_ORB_PICKUP");
	}

	/*public static void giveRestartHotbar(Player p) {
		p.getInventory().clear();
		p.getInventory().setHeldItemSlot(4);
		p.getInventory().setItem(1, createItem(Material.CHEST, 1, (byte) 0, "§6§lArcade Game Menu §7(Right-click)"));
		p.getInventory().setItem(4, createItem(Material.TRIPWIRE_HOOK, 1, (byte) 0, "§a§lPlay Again §7(Right-click)"));
		p.getInventory().setItem(7, createItem(Material.ENDER_PORTAL_FRAME, 1, (byte) 0, "§c§lQuit §7(Right-click)"));
	}*/
	
}
