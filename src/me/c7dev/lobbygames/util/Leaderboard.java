package me.c7dev.lobbygames.util;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.LobbyGames;

public class Leaderboard {
	
	private List<LeaderboardEntry> board = new ArrayList<>();
	private int size;
	private List<ArmorStand> as = new ArrayList<>();
	private Location loc;
	private LobbyGames plugin;
	private String title, format, empty;
	private long expiry;
	private GameType gt;
	private boolean merge = false;
	
	public Leaderboard(LobbyGames plugin, GameType gt, Location loc) {
		int s = plugin.getConfig().getInt("leaderboard-size");
		if (s < 1) s = 1;
		else if (s > 10) s = 10;
		this.size = s;
		this.plugin = plugin;
		this.loc = loc;
		this.gt = gt;
		expiry = plugin.getLeaderboardExpiry();
		
		loadStrings();
		if (gt == GameType.POOL) merge = true;
	}
	
	public void loadStrings() {
		String gt_str = GameUtils.outgoingAliases(gt, plugin);
		title = plugin.getConfigString("leaderboard-title", "§2§l%game% Leaderboard:")
				.replaceAll("\\Q%game%\\E", gt_str.toUpperCase().toString().charAt(0) + gt_str.toString().substring(1));
		format = plugin.getConfigString("leaderboard-format", "§b%ranking%: §a%name% §6%score%");
		empty = format.replaceAll("\\Q%name%\\E", "§7-").replaceAll("\\Q%player%\\E", format.contains("-") ? "" : "§7-").replaceAll("\\Q%score%\\E", "").trim();
		merge = plugin.getConfig().getBoolean("merge-leaderboard-scores");
	}
	
	public List<LeaderboardEntry> getEntires(){return this.board;}
	
	public int getSize() {return this.size;}
	
	public void setEntries(List<LeaderboardEntry> n) {
		this.board = n;
		remove();
		updateDisplay();
	}
	
	public void addScore(Player p, int score) {
		boolean display_name = plugin.getConfig().getBoolean("use-display-names");
		addScore(display_name ? p.getDisplayName() : p.getName(), score, null);
	}
	
	public void addScore(Player p, int score, String display_score) {
		boolean display_name = plugin.getConfig().getBoolean("use-display-name");
		addScore(display_name ? p.getDisplayName() : p.getName(), score, display_score);
	}
	
	public void resortScores() {
		List<LeaderboardEntry> nb = new ArrayList<LeaderboardEntry>();
		while(board.size() > 0) {
			LeaderboardEntry highest = null;
			for (int j = 0; j < board.size(); j++) {
				LeaderboardEntry entry = board.get(j);
				if (highest == null || board.get(j).getScore() > highest.getScore()) {
					highest = entry;
				}
			}
			board.remove(highest);
			nb.add(highest);
		}
		board = nb;
	}

	public void addScore(String display_name, int score, String display_score) {
		LeaderboardEntry c = new LeaderboardEntry(display_name, score, display_score, expiry);
		
		boolean added = false;
		if (merge) {
			for (LeaderboardEntry entry : board) {
				if (entry.getDisplayName().equals(display_name)) {
					if (score > entry.getScore()) {
						entry.setScore(score);
						entry.setDisplayScore(display_score == null ? "" + score : display_score);
						entry.setExpiration(expiry);
						added = true;
						resortScores();
						break;
					} else return;
				}
			}
		}

		if (!added) {
			List<LeaderboardEntry> n = new ArrayList<LeaderboardEntry>();
			for (LeaderboardEntry entry : board) {
				if (!added && score > entry.getScore()) {
					n.add(c);
					added = true;
					if (!entry.isExpired()) n.add(entry);
				} 
				else if (!entry.isExpired()) n.add(entry);
			}
			if (!added) n.add(c);

			board = n;
		}
		
		updateDisplay();
		
		/*if (!global) {
			List<Leaderboard> boards = plugin.getGlobalLeaderboards().get(gt);
			if (boards != null) {
				for (Leaderboard b : boards) b.addScore(display_name, score, display_score);
			}
		}*/
	}

	public void reloadFromConfig() {
		int s = plugin.getConfig().getInt("leaderboard-size");
		if (s < 2) s = 2;
		else if (s > 10) s = 10;
		this.size = s;
		
		loadStrings();
		
		updateDisplay();
	}
	
	public String entriesString() {
		String r = title;
		for (int i = 1; i < Math.min(size, board.size()) + 1; i++) {
			LeaderboardEntry entry = board.get(i-1);
			String entrystr = format.replaceAll("\\Q%ranking%\\E", "#" + i)
					.replaceAll("\\Q%player%\\E", entry.getDisplayName())
					.replaceAll("\\Q%score%\\E", entry.getDisplayScore())
					.replaceAll("\\Q%name%\\E", entry.getDisplayName());
			r += "\n" + entrystr;
		}
		return r;
	}
	
	public String lineString(int line) {
		if (board.size() - 1 < line || line < 0) return empty.replaceAll("\\Q%ranking%\\E", "").replaceAll(":", "").trim();
		LeaderboardEntry entry = board.get(line);
		return format.replaceAll("\\Q%ranking%\\E", "#" + (line+1))
				.replaceAll("\\Q%player%\\E", entry.getDisplayName())
				.replaceAll("\\Q%score%\\E", entry.getDisplayScore())
				.replaceAll("\\Q%name%\\E", entry.getDisplayName()).trim();
	}

	public void updateDisplay() {
		remove();
		new BukkitRunnable() {
			public void run() {
				if (as.size() < size + 1) {
					for (ArmorStand a : as) a.remove();
					as.clear();

					double linespacing = 0.26;
					Location spawnloc = loc.clone().add(0, ((size+1)*linespacing) - 1.5, 0);
					for (int j = 0; j < size + 1; j++) {
						ArmorStand a = spawnloc.getWorld().spawn(spawnloc, ArmorStand.class);
						a.setVisible(false);
						a.setGravity(false);
						a.setCustomNameVisible(true);
						//a.setCustomName("§b#" + j + ":§7 -");
						a.setCustomName(empty.replaceAll("\\Q%ranking%\\E", "#" + j));
						as.add(a);
						spawnloc.add(0, -linespacing, 0);
					}
				}


				as.get(0).setCustomName(title);
				for (int i = 1; i <= Math.min(size, board.size()); i++) {
					//as.get(i).setCustomName("§b#" + i +": §7" + board.get(i-1).getDisplayName());
					LeaderboardEntry entry = board.get(i-1);
					as.get(i).setCustomName(format.replaceAll("\\Q%ranking%\\E", "#" + i)
							.replaceAll("\\Q%player%\\E", entry.getDisplayName())
							.replaceAll("\\Q%score%\\E", entry.getDisplayScore())
							.replaceAll("\\Q%name%\\E", entry.getDisplayName()));
				}
			}
		}.runTaskLater(plugin, 1l);


	}

	public void remove() {
		for (ArmorStand a : as) a.remove();
		as.clear();
		if (loc != null) {
			for (Entity e : loc.getWorld().getNearbyEntities(loc, 0, 5, 0)) {
				if (e instanceof ArmorStand) e.remove();
			}
		}
	}
	
	public Location getLocation() {return this.loc;}
	

}
