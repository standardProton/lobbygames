package me.c7dev.lobbygames;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.PlayerJoinLobbyGameEvent;
import me.c7dev.lobbygames.api.events.PlayerQuitLobbyGameEvent;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;
import me.c7dev.lobbygames.util.Leaderboard;
import me.c7dev.lobbygames.util.PlayerStats;

public class Game {
	
	private GameType gt;
	private List<UUID> players = new ArrayList<UUID>(), no_endtp = new ArrayList<UUID>();
	private HashMap<UUID,ItemStack[]> inventories = new HashMap<UUID,ItemStack[]>(), quit_confirmation = new HashMap<>();
	private HashMap<UUID,GameMode> p_gm = new HashMap<UUID,GameMode>();
	private boolean active = false, can_start = false, was_active = false, console_command_set = false;
	private LobbyGames plugin;
	private Arena arena;
	private long starttime = 0;
	private HashMap<UUID,PlayerStats> pstats = new HashMap<UUID,PlayerStats>();
	private String console_command = null;
	
	public Game(LobbyGames plugin, GameType game_type, Arena arena, Player p) {
		this.players = new ArrayList<UUID>();
		this.players.add(p.getUniqueId());
		this.gt = game_type;
		this.plugin = plugin;
		
		if (!p.isOnline()) {
			Bukkit.getLogger().warning("Could not start a game because a player (" + p.getName() + ") is offline!");
			return;
		}
		if (arena == null || arena.getHostingGame() != null) {
			Bukkit.getLogger().warning("Could not start a new game because the arena doesn't exist or is already in use!");
			return;
		}
		if (arena.getGameType() != gt) {
			Bukkit.getLogger().warning("Could not start a game because the arena types do not match!");
			return;
		}
		
		if (arena.getLeaderboard() != null) arena.getLeaderboard().updateDisplay();
		
		//prepare game
		can_start = true;
		console_command = plugin.getConfigString(GameUtils.getConfigName(gt) + ".console-command-on-end");
		this.arena = arena;
		arena.setHostingGame(this);

		Game concurrent = plugin.getActiveGames().get(p.getUniqueId());
		if (concurrent != null) concurrent.removePlayer(p);
		plugin.getActiveGames().put(p.getUniqueId(), this);
		Bukkit.getPluginManager().callEvent(new PlayerJoinLobbyGameEvent(p, this));

	}

	public GameType getGameType() {return gt;}
	public Player getPlayer1() {return players.size() > 0 ? Bukkit.getPlayer(players.get(0)) : null;}
	public List<UUID> getPlayers() {return this.players;} //do NOT remove players from list without removePlayer
	public boolean isActive() {return this.active;}
	public boolean wasActive() {return was_active;}
	public boolean canStart() {return can_start;}
	public LobbyGames getPlugin() {return plugin;}
	public World getWorld() {return arena.getCenterPixel().getWorld();}
	
	public void setActive(boolean b) {
		this.active = b;
		if (active) {
			was_active = true;
			starttime = System.currentTimeMillis()/1000;
		}
	}
	public Arena getArena() {return arena;}
	
	public String getPlayTime() {
		int splayed = (int) ((System.currentTimeMillis() / 1000) - starttime);
		int min = splayed/60; int s = splayed % 60;
		String time = s + "s";
		if (min > 0) time = min + "m, " + time;
		return time;
	}
	
	public String getConsoleCommand() {return console_command;}
	public void setConsoleCommand(String s) {
		console_command = s;
		console_command_set = true;
	}
	public Game setConsoleCommandPlayer(String s) {
		console_command = console_command.replaceAll("\\Q%player%\\E", s).replaceAll("\\Q%winner%\\E", s);
		console_command_set = true;
		return this;
	}
	public Game setConsoleCommandScore(String s) {
		console_command = console_command.replaceAll("\\Q%score%\\E", s);
		console_command_set = true;
		return this;
	}
	
	public void quitConfirmation(Player p) {
		if (!inventories.containsKey(p.getUniqueId())) return;
		quit_confirmation.put(p.getUniqueId(), p.getInventory().getContents());
		p.getInventory().clear();
		p.getInventory().setItem(3, GameUtils.createWool(1, 5, plugin.getConfigString("yes-text", "§a§lYes")));
		p.getInventory().setItem(5, GameUtils.createWool(1, 14, plugin.getConfigString("no-text", "§c§lNo")));
	}
	
	public void removeQuitConfirmation(Player p) {
		ItemStack[] inv = quit_confirmation.get(p.getUniqueId());
		if (inv == null) return;
		quit_confirmation.remove(p.getUniqueId());
		p.getInventory().setContents(inv);
	}
	
	public boolean isInQuitConfirmation(UUID u) {
		return quit_confirmation.containsKey(u);
	}
	
	public void addScore(Player p, int score) {addScore(p, score, null);}
	public void addScore(Player p, int score, String display_score) { //add the score to the leaderboard and player stats
		String display_name = plugin.getConfig().getBoolean("use-display-names") ? p.getDisplayName() : p.getName();
		if (!gt.isMultiplayer() || gt == GameType.SPLEEF) {
			if (arena.getLeaderboard() != null) arena.getLeaderboard().addScore(display_name, score, display_score);
			List<Leaderboard> boards = plugin.getGlobalLeaderboards().get(gt);
			if (boards != null) {
				for (Leaderboard b : boards) b.addScore(display_name, score, display_score);
			}
		}

		int putscore = score;
		if (gt == GameType.SPLEEF) putscore = plugin.getHighScoreRaw(p.getUniqueId(), getGameType());
		pstats.put(p.getUniqueId(), new PlayerStats(putscore, display_score, 0, (int) ((System.currentTimeMillis()/1000) - starttime)));
	}
	
	public void preparePlayer(Player p, GameMode gm) { //save inventory and set gamemode
		if (inventories.containsKey(p.getUniqueId())) return;
		p_gm.put(p.getUniqueId(), p.getGameMode());
		p.setGameMode(gm);
		inventories.put(p.getUniqueId(), p.getInventory().getContents());		
		p.getInventory().clear();
	}
	
	public void preparePlayer(Player p) {
		if (inventories.containsKey(p.getUniqueId())) return;
		inventories.put(p.getUniqueId(), p.getInventory().getContents());		
		p.getInventory().clear();
		p_gm.put(p.getUniqueId(), p.getGameMode());
		//if (set_gamemode) p.setGameMode(GameMode.ADVENTURE);
	}
	
	public void returnPlayerInv(Player p) { //return inventory and reset gamemode
		if (p.isOnline()) {
			boolean skip_edit = plugin.getConfig().getBoolean("using-per-world-inventory") && !p.getLocation().getWorld().getName().equals(arena.getLocation1().getWorld().getName());
			
			if (inventories.containsKey(p.getUniqueId())) {
				if (!skip_edit) p.getInventory().setContents(inventories.get(p.getUniqueId()));
				inventories.remove(p.getUniqueId());
			}
			else p.getInventory().clear();
			
			if (p_gm.containsKey(p.getUniqueId())) p.setGameMode(p_gm.get(p.getUniqueId()));
		}
	}
	
	public CoordinatePair randomPixel() {
		Random r = new Random();
		CoordinatePair c = new CoordinatePair(r.nextInt(arena.getWidth()) - (arena.getWidth()/2), r.nextInt(arena.getHeight()) - (arena.getHeight()/2));
		if (!arena.isInBounds(getPixel(c))) return randomPixel();
		return c;
	}
	
	public Location getPixel(CoordinatePair c) {
		return getPixel(c.getX(), c.getY());
	}
	
	public void restart() { //overridden by games like sudoku
		
	}
	
	public String getScore(Player p) { //overridden
		return "0";
	}
	
	public CoordinatePair getCoords(Location loc) { //relative to center block
		Location c = arena.getCenterPixel();
		if (arena.vertical) {
			switch (arena.getCoordinateRotation()) {
			case 1: return new CoordinatePair(loc.getBlockZ() - c.getBlockZ(), loc.getBlockY() - c.getBlockY());
			case 2: return new CoordinatePair(loc.getBlockX() - c.getBlockX(), loc.getBlockY() - c.getBlockY());
			case 3: return new CoordinatePair(c.getBlockZ() - loc.getBlockZ(), loc.getBlockY() - c.getBlockY());
			default: return new CoordinatePair(c.getBlockX() - loc.getBlockX(), loc.getBlockY() - c.getBlockY());
			}
		} else {
			return new CoordinatePair(loc.getBlockX() - c.getBlockX(), loc.getBlockZ() - c.getBlockZ());
		}
	}
	
	public Location getPixel(int x, int y) { //relative to center block
		//Location c = new Location(arena.getCenterPixel().getWorld(), arena.getCenterPixel().getX(), arena.getCenterPixel().getY(), arena.getCenterPixel().getZ());
		Location c = arena.getCenterPixel().clone();
		if (arena.vertical) {
			if (gt.isDirectBlockMapping()) {
				switch (arena.getCoordinateRotation()) {
				case 1: return c.add(0, y, x);
				case 2: return c.add(x, y, 0);
				case 3: return c.add(0, y, -x);
				default: return c.add(-x, y, 0);
				}
			}
			else {
				switch(arena.getCoordinateRotation()) {
				case 1: return c.add(0, y, x);
				case 2: return c.add(-x, y, 0);
				case 3: return c.add(0, y, -x);
				default: return c.add(x, y, 0);
				}
			}
		} else {
			if (gt.isDirectBlockMapping()) return c.add(x, 0, y);
			switch (arena.getCoordinateRotation()) { //rotation # is which coord plane l2 is in
				case 1: return c.add(x, 0, -y);
				case 2: return c.add(-y, 0, -x);
				case 3: return c.add(-x, 0, y);
				default: return c.add(y, 0, x);
			}
		}
		
	}
	
	public void appendPlayer(Player p) { //add a player to the game - multiplayer
		if (gt.isMultiplayer()) {
			Game concurrent = plugin.getActiveGames().get(p.getUniqueId());
			if (concurrent != null) concurrent.removePlayer(p);
			//preparePlayer(p);
			plugin.getActiveGames().put(p.getUniqueId(), this);
			this.players.add(p.getUniqueId());
			Bukkit.getPluginManager().callEvent(new PlayerJoinLobbyGameEvent(p, this));
		}
	}
	
	public void removePlayer(Player p) { //remove player from game
		if (!this.players.contains(p.getUniqueId())) return;
		if (arena.getSpawn1() != null && !no_endtp.contains(p.getUniqueId())) p.teleport(arena.getSpawn1());
		
		if (players.size() <= 1) end();
		else {
			plugin.getActiveGames().remove(p.getUniqueId());
			players.remove(p.getUniqueId());
			returnPlayerInv(p);
			long delay = plugin.getConfig().getLong("cooldown-seconds");
			if (delay > 0) plugin.getJoinCooldown().put(p.getUniqueId(), System.currentTimeMillis() + (1000*delay));
			Bukkit.getPluginManager().callEvent(new PlayerQuitLobbyGameEvent(p, this));
			
			if (was_active && !pstats.containsKey(p.getUniqueId())) {
				pstats.put(p.getUniqueId(), new PlayerStats(Integer.MIN_VALUE, "0", 0, (int) ((System.currentTimeMillis()/1000) - starttime)));
			}
		}
	}
	
	public void noEndTeleportation(UUID u) { //ex. if the player runs away from game
		no_endtp.add(u);
	}
	
	public void clearArmorStands() {
		arena.clearArmorStands();
	}
	
	public int getDuration() {
		if (!was_active) return 0;
		return (int) ((System.currentTimeMillis()/1000) - starttime);
	}
	
	public void end() { //use removePlayer when applicable
		if (!can_start) return;
		
		new BukkitRunnable() {
			@Override
			public void run() {
				if (was_active) {
					int seconds = (int) ((System.currentTimeMillis()/1000) - starttime);
					for (UUID u : players) {
						Player p = Bukkit.getPlayer(u);
						if (p != null) {
							PlayerStats stats = pstats.get(u);
							if (stats == null) stats = new PlayerStats(Integer.MIN_VALUE, "0", 0, seconds);
							plugin.setHighScore(p, gt, stats);
						}
					}
				}
			}
		}.runTaskLater(plugin, 10l);

		active = false;
		can_start = false;
		arena.setHostingGame(null);
		Bukkit.getPluginManager().callEvent(new GameEndEvent(this));
		
		long delay = plugin.getConfig().getLong("cooldown-seconds");
		long cooldown = System.currentTimeMillis() + (1000*delay);
		
		for (UUID u : players) {
			Player p = Bukkit.getPlayer(u);
			plugin.getActiveGames().remove(u);
			if (delay > 0) plugin.getJoinCooldown().put(u, cooldown);
			if (p != null) {
				returnPlayerInv(p);
				Bukkit.getPluginManager().callEvent(new PlayerQuitLobbyGameEvent(p, this));
			}
		}
		
		if (console_command_set && console_command != null && console_command.length() > 0) {
			for (String cmd : console_command.split("\n")) {
				cmd = cmd.trim();
				if (cmd.startsWith("/")) cmd = cmd.substring(1);
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.trim());
			}
		}
		
		plugin.saveArenas();
	}
	
	
	

}
