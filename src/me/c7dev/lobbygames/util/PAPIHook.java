package me.c7dev.lobbygames.util;

import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PAPIHook extends PlaceholderExpansion {

	private LobbyGames plugin;
	public PAPIHook(LobbyGames plugin) {
		this.plugin = plugin;

	}
	
	public GameType getGameType(String s) {
		try {
			return GameType.valueOf(GameUtils.incomingAliases(s, plugin).toUpperCase());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}

	@Override
	public String getAuthor() {
		return "c7dev";
	}

	@Override
	public String getIdentifier() {
		return "lobbygames";
	}

	@Override
	public String getVersion() {
		return plugin.getVersion();
	}

	@Override
	public String onRequest(OfflinePlayer offline_player, String params) {
		params = params.toLowerCase();
		if (params.equals("version")) return plugin.getVersion();
		else if (params.endsWith("_leaderboard")) {
			String[] args = params.split("_");
			if (args.length == 2) {
				GameType gt = getGameType(args[0]);
				if (gt != null) {
					List<Leaderboard> boards = plugin.getGlobalLeaderboards().get(gt);
					if (boards != null && boards.size() > 0) {
						Leaderboard board = boards.get(0);
						return board.entriesString();
					}
				}
			}
		}
		else if (params.contains("_leaderboard_line")) {
			String[] args = params.split("_");
			String digit_str = args[args.length-1].replaceAll("line", "");
			int line = 0;
			if (args.length == 3) {
				try {
					line = Integer.parseInt(digit_str);
				} catch(Exception ex) {
					return "[Invalid Number]";
				}
				
				if (line < 1) return "[Invalid Number]";
				
				GameType gt = getGameType(args[0]);
				if (gt != null) {
					List<Leaderboard> boards = plugin.getGlobalLeaderboards().get(gt);
					if (boards != null && boards.size() > 0) {
						Leaderboard board = boards.get(0);
						return board.lineString(line-1);
					}
				}
			}
			
		}
		
		UUID u = offline_player.getUniqueId();
		if (params.endsWith("_highscore")) {
			String[] args = params.split("_");
			if (args.length == 2) {
				GameType gt = getGameType(args[0]);
				if (gt != null) return plugin.getHighScore(u, gt);
			}
		}
		else if (params.endsWith("_play_time")) {
			String[] args = params.split("_");
			if (args.length == 3) {
				GameType gt = getGameType(args[0]);
				if (gt != null) return GameUtils.timeStr(plugin.getSecondsPlayed(u, gt));
			}
		}
		else if (params.endsWith("_play_time_seconds")) {
			String[] args = params.split("_");
			if (args.length == 3) {
				GameType gt = getGameType(args[0]);
				if (gt != null) return "" + plugin.getSecondsPlayed(u, gt);
			}
		}
		else if (params.endsWith("_games_played")) {
			String[] args = params.split("_");
			if (args.length == 3) {
				GameType gt = getGameType(args[0]);
				if (gt != null) return "" + plugin.getTimesWon(u, gt);
			}
		}
		else if (params.endsWith("_games_won")) {
			String[] args = params.split("_");
			if (args.length == 3) {
				GameType gt = getGameType(args[0]);
				if (gt != null) {
					if (gt.isMultiplayer()) return "" + Math.max(0, plugin.getHighScoreRaw(u, gt));
					else return "" + Math.max(0, plugin.getTimesWon(u, gt));
				}
			}
		}
		
		if (offline_player != null && offline_player.isOnline()) {
			Player p = (Player) offline_player;
			
			Game g = plugin.getActiveGames().get(p.getUniqueId());
			if (g != null) {
				if (params.equals("game")) {
					String gtstr = GameUtils.outgoingAliases(g.getGameType(), plugin);
					return gtstr.toUpperCase().charAt(0) + gtstr.toLowerCase().substring(1);
				}
				else if (params.equals("score")) return g.getScore(p);
				else if (params.equals("time_played")) return g.getPlayTime();
				else if (params.equals("arena_id")) return "" + g.getArena().getID();
				else if (params.equals("player_count")) return "" + g.getPlayers().size();
				else if (params.equals("player1_name")) return g.getPlayer1().getName();
				else if (params.equals("player2_name") && g.getPlayers().size() >= 2) {
					Player p2 = Bukkit.getPlayer(g.getPlayers().get(1));
					if (p2 != null) return p2.getName();
				}
				else if (params.equals("opponent_name") && g.getPlayers().size() >= 2) {
					if (g.getPlayer1().getUniqueId().equals(p.getUniqueId())) {
						Player p2 = Bukkit.getPlayer(g.getPlayers().get(1));
						if (p2 != null) return p2.getName();
					} else return g.getPlayer1().getName();
				}
				//TODO local leaderboard
			}
		}
		return "-";
	}

	

}
