package me.c7dev.lobbygames;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.commands.ConsoleJoinCommand;
import me.c7dev.lobbygames.commands.JoinCommand;
import me.c7dev.lobbygames.commands.LobbyGamesCommand;
import me.c7dev.lobbygames.games.Clicker;
import me.c7dev.lobbygames.games.Connect4;
import me.c7dev.lobbygames.games.Minesweeper;
import me.c7dev.lobbygames.games.Pool;
import me.c7dev.lobbygames.games.Snake;
import me.c7dev.lobbygames.games.Soccer;
import me.c7dev.lobbygames.games.Sudoku;
import me.c7dev.lobbygames.games.T048;
import me.c7dev.lobbygames.games.TicTacToe;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;
import me.c7dev.lobbygames.util.Leaderboard;
import me.c7dev.lobbygames.util.LeaderboardEntry;
import me.c7dev.lobbygames.util.PAPIHook;
import me.c7dev.lobbygames.util.PlayerStats;
import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class LobbyGames extends JavaPlugin {
	
	private HashMap<UUID,Game> active = new HashMap<UUID,Game>();
	private HashMap<GameType,List<Arena>> arenas = new HashMap<>();
	private HashMap<UUID,Long> join_cooldown = new HashMap<>();
	private HashMap<UUID,HashMap<GameType,PlayerStats>> highscore_cache = new HashMap<>();
	private HashMap<GameType,List<Leaderboard>> global_leaderboard = new HashMap<>();
	private HashMap<String,GameType> game_alias = new HashMap<>();
	private HashMap<GameType, String> outgoing_alias = new HashMap<>();
	private HashMap<UUID,Long> proximity_delay = new HashMap<>();
	private boolean interworld = true;
	private long leaderboard_expiry = 86400;
	private boolean saving_enabled = false, papi = false, save_highscores = true, pool_proximity = false, soccer_proximity = false;
	private PAPIHook papi_hook;
	private List<String> blocked_commands = new ArrayList<>();
	private int command_whitelist_mode = 0;
	
	public static int SERVER_VERSION = 12;
	
	public String getVersion() {
		return this.getDescription().getVersion();
	}
	
	public void loadConfigSettings() {
		leaderboard_expiry = getConfig().getLong("leaderboard-entry-expiration") * 86400;
		if (leaderboard_expiry < 0) leaderboard_expiry = -1;
		
		interworld = getConfig().getBoolean("interworld-teleportation-enabled");
		
		command_whitelist_mode = getConfig().getInt("command-block-mode");
		blocked_commands = getConfig().getStringList("command-block-list");
		
		pool_proximity = getConfig().getBoolean("pool.proximity-joining");
		soccer_proximity = getConfig().getBoolean("soccer.proximity-joining");
		
		game_alias.clear(); 
		outgoing_alias.clear();
		for (GameType gt : GameType.values()) {
			String alias = this.getConfigString(GameUtils.getConfigName(gt) + ".game-alias", "");
			if (alias.length() > 0) {
				game_alias.put(alias, gt);
				outgoing_alias.put(gt, alias);
			}
		}
	}
	
	@Override
	public void onEnable() {
		saveDefaultConfig();
		for (GameType gt : GameType.values()) {
			arenas.put(gt, new ArrayList<Arena>());
		}
		
		SERVER_VERSION = GameUtils.getVersionInt();
						
		loadConfigSettings();
		
		new LobbyGamesCommand(this);
		new JoinCommand(this);
		new ConsoleJoinCommand(this);
		new EventListeners(this);
				
		new BukkitRunnable() {
			int count = 0;
			
			@Override
			public void run() {
				try {
					int arena_count = loadArenas(); //load after worlds
					Bukkit.getConsoleSender().sendMessage("[LobbyGames] Loaded " + arena_count + " arenas!");
					this.cancel();
				} catch (Exception ex) {
					ex.printStackTrace();
					count++;
					if (count >= 3) {
						Bukkit.getLogger().severe("Could not load LobbyGames arenas! Please report this as a bug on the discord server.");
						arenas.clear();
						this.cancel();
					}
					else Bukkit.getConsoleSender().sendMessage("[LobbyGames] Failed to load arenas, trying again in 10 seconds...");
				}
			}
		}.runTaskTimer(this, 0, 200l);
		
		papi = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
		if (papi) {
			save_highscores = true;
			papi_hook = new PAPIHook(this);
			papi_hook.register();
		}	
	}
	
	public void onDisable() {

		for (GameType gt : GameType.values()) {
			for (Arena a : arenas.get(gt)) {
				if (a.getLeaderboard() != null) a.getLeaderboard().remove();
			}
		}
		
		if (papi) {
			papi_hook.unregister();
		}

		saveArenas();
	}
	
	public String sp(Player p, String s) { //set placeholders
		return papi ? PlaceholderAPI.setPlaceholders(p, s) : s;
	}
	
	public HashMap<UUID,Game> getActiveGames(){return active;} //use Game#end() to remove a game
	
	public HashMap<UUID,Long> getJoinCooldown(){return join_cooldown;}
	
	public HashMap<String, GameType> getGameAlias(){return game_alias;}
	public HashMap<GameType, String> getOutgoingGameAlias(){return outgoing_alias;}
	
	public HashMap<UUID,Long> getProximityDelay(){return proximity_delay;}
	
	public List<Arena> getArenas(GameType gt){return arenas.get(gt);}
	
	public long getLeaderboardExpiry() {return leaderboard_expiry;}
	
	public boolean isPAPI() {return papi;}
		
	public int getCommandBlockMode() {return this.command_whitelist_mode;}
	
	public List<String> getBlockedCommands(){return this.blocked_commands;}
	
	public boolean poolProximityJoining() {return pool_proximity;}
	public boolean soccerProximityJoining() {return soccer_proximity;}
	
		
	public boolean isPlayerInActiveGame(UUID u) {
		if (!active.containsKey(u)) return false;
		return active.get(u).isActive();
	}
	
	public void reload() { //reload config with cache clear
		highscore_cache.clear();
		join_cooldown.clear();
		reloadConfig();
		
		loadConfigSettings();
	}
	
	public boolean isArenaAvailable(GameType gt, int id) {
		Arena a = arenas.get(gt).get(id);
		return isArenaAvailable(a);
	}
	public boolean isArenaAvailable(Arena a) {
		return a.getHostingGame() == null; 
	}
	public Arena getArena(GameType gt, int id) {
		List<Arena> gtar = arenas.get(gt);
		if (arenas == null) return null;
		if (id <= 0 || id > gtar.size()) return null; //1-indexed
		
		if (gtar.get(id-1).getID() == id) return gtar.get(id-1);
		else return null; //error
	}
	
	public HashMap<GameType,List<Leaderboard>> getGlobalLeaderboards(){return this.global_leaderboard;}
	
	public void quitPlayer(Player p) {
		quitPlayer(p.getUniqueId());
	}
	
	public void quitPlayer(UUID u) { //take a player out of their game
		Player p = Bukkit.getPlayer(u);
		if (active.containsKey(u)) active.get(u).removePlayer(p);
		join_cooldown.remove(u);
		highscore_cache.remove(u);
		active.remove(u);
		proximity_delay.remove(u);
	}
	
	public Game getGame(Player p) {
		return getGame(p.getUniqueId());
	}
	
	public Game getGame(UUID u) {
		return active.get(u);
	}
	
	public Game joinPlayer(Player p, GameType gt) { //join a player into a game
		return joinPlayer(p, gt, -1);
	}
	
	public Game joinPlayer(Player p, GameType gt, int id) {
		if (getActiveGames().get(p.getUniqueId()) != null) {
			p.sendMessage(getConfigString(p, "error-already-in-game", "§4Error: §cYou are already in a game, use /lg quit"));
			return null;
		}
		
		if (id > -1) {
			
			Arena a = getArena(gt, id);
			if (a == null) {
				p.sendMessage("§4Error: §cArena #" + id + " does not exist.");
				return null;
			}
			return joinPlayer(p, a);	
			
		} else { //first available arena
			if (gt.isMultiplayer()) { //waiting arenas
				for (Arena a : getArenas(gt)) {
					if (a.getHostingGame() != null && !a.getHostingGame().isActive()) {
						return joinPlayer(p, a);
					}
				}
			}
			for (Arena a : getArenas(gt)) { //ordered by id
				if (isArenaAvailable(a)) {
					return joinPlayer(p, a);
				}
			}
			p.sendMessage(getConfigString(p, "error-no-arenas-available", "§4Error: §cThere are no available arenas for this game!"));
			return null;
		}
	}
	
	public Game joinPlayer(Player p, Arena a) {
		if (a == null) {
			p.sendMessage("§4Error: §cThis arena does not exist!");
			return null;
		}
				
		if (join_cooldown.containsKey(p.getUniqueId())) {
			long cooldown = join_cooldown.get(p.getUniqueId());
			if (System.currentTimeMillis() < cooldown) {
				int seconds = (int) ((cooldown - System.currentTimeMillis()) / 1000) + 1;
				p.sendMessage(getConfigString(p, "cooldown-msg", "§cYou must wait " + seconds + " second(s) to do this!").replaceAll("\\Q%seconds%\\E", "" + seconds).replaceAll("\\Q(s)\\E", seconds == 1 ? "" :"s"));
				return null;
			} else join_cooldown.remove(p.getUniqueId());
		}
		
		if (a.getGameType().isMultiplayer()) { //team games
			if (a.getHostingGame() != null && a.getHostingGame().isActive()) {
				p.sendMessage(getConfigString(p, "error-arena-in-use", "§4Error: §cThis arena is already in use!"));
				return null;
			}
		} else if (!isArenaAvailable(a)) {
			p.sendMessage(getConfigString(p, "error-arena-in-use", "§4Error: §cThis arena is already in use!"));
			return null;
		}
		
		if (!p.hasPermission("lobbygames.admin") && !interworld && !a.getLocation1().getWorld().getName().equals(p.getLocation().getWorld().getName())) {
			p.sendMessage("§4Error: §cYou cannot access this arena from this world!");
			return null;
		}
		if (a.getGameType() == GameType.SPLEEF) {
			p.teleport(a.getSpawn1());
			return null;
		}
		
		if (a.getHostingGame() != null) a.getHostingGame().appendPlayer(p);
		else {
			switch(a.getGameType().getId()) {
				case 0:
					return new Snake(this, a, p);
				case 1:
					return new Minesweeper(this, a, p);
				case 3:
					return new Clicker(this, a, p);
				case 4:
					return new Soccer(this, a, p);
				case 5:
					return new Sudoku(this, a, p);
				case 6:
					return new T048(this, a, p);
				case 7:
					return new TicTacToe(this, a, p);
				case 8:
					return new Pool(this, a, p);
				case 9:
					return new Connect4(this, a, p);
				default:
					p.sendMessage("§4Error: §cInvalid game type");
			}
		}
		return null;
		
	}
	
	public boolean saveArena(Arena a) { //add a new arena object into the list
		if (a.isValidConfiguration()) {
			if (arenas.get(a.getGameType()).size() >= a.getID()) {
				Bukkit.getConsoleSender().sendMessage("§cCould not create a new arena, invalid ID!");
				return false;
			}
			
			arenas.get(a.getGameType()).add(a);
			GameUtils.initArena(a, this);
			saving_enabled = true;
			saveArenas();
			return true;
		}
		return false;
	}
	public boolean deleteArena(Arena a) { //remove arena object from list
		//Code showcase - please purchase a license if you want to use the plugin on your server.
		return false;
	}
	
	private int loadArenas() { 
		//Code showcase - please purchase a license if you want to use the plugin on your server.
		return 0;
	}
		
	public void saveArenas() {
		//Code showcase - please purchase a license if you want to use the plugin on your server.
	}
	
	public void setHighScore(Player p, GameType gt, PlayerStats stats) {
		setHighScore(p, gt, stats.getScore(), stats.getDisplayScore(), stats.getSecondsPlayed());
	}

	public void setHighScore(Player p, GameType gt, int score, String display_score, int seconds_played) {
		//Code showcase - please purchase a license if you want to use the plugin on your server.
	}
	
	public HashMap<GameType, PlayerStats> getHighScoreMap(UUID u){
		if (!save_highscores) return null;
		HashMap<GameType, PlayerStats> hs = highscore_cache.get(u);
		if (hs == null) {
			File f = new File(this.getDataFolder().getAbsolutePath() + "/player_stats.yml");
			if (!f.exists()) {
				try {
					f.createNewFile();
				} catch (IOException ex) {
					ex.printStackTrace();
					Bukkit.getLogger().severe("Could not save player_stats.yml!");
					return null;
				}
			}
			FileConfiguration afile = YamlConfiguration.loadConfiguration(f);
			hs = PlayerStats.deserialize(afile, u);
			highscore_cache.put(u, hs);
		}
		return hs;
	}

	public String getHighScore(UUID u, GameType gt) {
		if (!save_highscores || !gt.usesLeaderboard()) return "0";
		HashMap<GameType, PlayerStats> hs = getHighScoreMap(u);
		if (hs != null && hs.get(gt) != null) return hs.get(gt).getDisplayScore();
		
		return "0";
	}
	
	public int getHighScoreRaw(UUID u, GameType gt) {
		if (!save_highscores) return Integer.MIN_VALUE;
		HashMap<GameType, PlayerStats> hs = getHighScoreMap(u);
		if (hs != null && hs.get(gt) != null) return hs.get(gt).getScore();
		
		return Integer.MIN_VALUE;
	}
	
	public int getSecondsPlayed(UUID u, GameType gt) {
		if (!save_highscores) return 0;
		HashMap<GameType, PlayerStats> hs = getHighScoreMap(u);
		if (hs != null && hs.get(gt) != null) return hs.get(gt).getSecondsPlayed();
		return 0;
	}
	
	public int getTimesWon(UUID u, GameType gt) {
		if (!save_highscores) return 0;
		HashMap<GameType, PlayerStats> hs = getHighScoreMap(u);
		if (hs != null && hs.get(gt) != null) return hs.get(gt).getGamesPlayed();
		return 0;
	}
	
	public void sendActionbar(Player p, String msg, boolean recurring) {
		if (p == null || msg.length() == 0) return;
		msg = sp(p, msg);
		if (LobbyGames.SERVER_VERSION < 12) {
			if (!recurring) p.sendMessage(msg); 
		} 
		else p.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(msg));
		
	}
	
	public String getConfigString(Player p, String dir, String def) {
		String r = getConfigString(dir);
		return sp(p, r == null ? def.replaceAll("&", "§").replaceAll("\\Q[newline]\\E", "\n") : r);
	}
	
	public String getConfigString(String dir, String def) {
		String r = getConfigString(dir);
		return r == null ? def.replaceAll("&", "§").replaceAll("\\Q[newline]\\E", "\n") : r;
	}
			
	public String getConfigString(String dir) {
		
		String s = getConfig().getString(dir);
		if (s == null) {
			Bukkit.getLogger().warning("Could not get value from config: '" + dir + "'");
			return null;
		}
		return s
				.replace('&', ChatColor.COLOR_CHAR)
				.replaceAll("\\Q[newline]\\E", "\n")
				.replaceAll("\\n", "\n");
	}

}
