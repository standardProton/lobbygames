package me.c7dev.lobbygames.commands;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;
import me.c7dev.lobbygames.util.Leaderboard;
import me.c7dev.lobbygames.util.LeaderboardEntry;

public class LobbyGamesCommand implements CommandExecutor, TabCompleter {
	
	private LobbyGames plugin;
	private String noperm;
	private HashMap<UUID,GameCreateInstance> editing = new HashMap<>();
	private HashMap<UUID,Arena> delconfirm = new HashMap<>();
	private List<String> tabgamelist = new ArrayList<>();
	
	public LobbyGamesCommand(LobbyGames plugin_) {
		plugin = plugin_;
		plugin.getCommand("lobbygames").setExecutor(this);
		plugin.getCommand("lobbygames").setTabCompleter(this);
		noperm = plugin.getConfigString("no-permission", "§cYou don't have permission!");
		
		for (GameType gt : GameType.values()) {
			tabgamelist.add(GameUtils.getConfigName(gt).toLowerCase());
		}
	}
	
	public GameType getGameType(String s) {
		try {
			return GameType.valueOf(GameUtils.incomingAliases(s, plugin).toUpperCase());
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
	
	public Arena getArena(String[] args, Player p) {
		GameType gt = getGameType(args[1]);
		if (gt != null) {
			int id = 1;
			if (args.length >= 3) {
				try {
					id = Integer.parseInt(args[2]);
				} catch (Exception ex) {
					p.sendMessage("§4Error: §c'" + args[2] + "' is not a number!");
					return null;
				}
			}
			Arena a = plugin.getArena(gt, id);
			
			if (a == null) {
				p.sendMessage("§4Error: §cThere is no " + GameUtils.outgoingAliases(gt, plugin) + " arena with ID " + id + "!");
				return null;
			}
			
			return a;
		} else p.sendMessage("§4Error: §c'" + args[1] + "' is not an existing lobby game type!");
		return null;
	}
	
	public HashMap<UUID,Integer> state = new HashMap<UUID,Integer>();
	//0 = L1, 1 = L2, 3 = Spawn1, 4 = Spawn2, 5 = Scoreboard
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) return true;
		Player p = (Player) sender;
		String noperm = plugin.sp(p, this.noperm);

		if (args.length == 0) {
			p.sendMessage("§bUsing LobbyGames v" + plugin.getVersion());
			if (p.hasPermission("lobbygames.command")) p.sendMessage("§aUse §b/lg help§a to get started!");
			return true;
		}
		
		//if (args.length >= 2) args[1] = GameUtils.incomingAliases(args[1]);
		
		if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")) {
			if (p.hasPermission("lobbygames.command") || p.hasPermission("lobbygames.admin") || p.hasPermission("lobbygames.kickplayer")) {
				p.sendMessage("\n§3§lLobbyGames commands:");
				p.sendMessage("§b/lg join <game> [arena id]");
				p.sendMessage("§b/lg restart");
				p.sendMessage("§b/lg quit");
				p.sendMessage("§b/lg list [game] §7- lobbygames.command");
				p.sendMessage("§b/lg tp <game> <arena id> §7- lobbygames.command");
				p.sendMessage("§b/lg kick <player> §7- lobbygames.kickplayer");
				p.sendMessage("§b/lg clearboard <game> <arena id> §7- lobbygames.admin");
				p.sendMessage("§b/lg create <game> §7- lobbygames.admin");
				p.sendMessage("§b/lg delete <game> <arena id> §7- lobbygames.admin");
			} else p.sendMessage(noperm);
		}
		
		else if (args[0].equalsIgnoreCase("join")) { //join a lobby game
			if (args.length < 2) {
				p.sendMessage("§4Usage: §c/lg join <game> [arena_id]");
				return true;
			}
			
			if (plugin.getActiveGames().get(p.getUniqueId()) != null) {
				p.sendMessage(plugin.getConfigString(p, "error-already-in-game", "§4Error: §cYou are already in a game, use /lg quit"));
				return true;
			}
			
			if (args.length >= 2) {
				GameType gt = getGameType(args[1]);
				if (gt == null) {
					p.sendMessage("§4Error: §c'" + args[1] + "' is not an existing lobby game type!");
					return true;
				}
				
				int id = -1;
								
				if (args.length >= 3 && !args[2].equalsIgnoreCase("tac")) {
					try {
						id = Integer.parseInt(args[2]);
					} catch (Exception ex) {
						p.sendMessage("§4Error: §c'" + args[2] + "' is not a number!");
						return true;
					}
				}
				
				plugin.joinPlayer(p, gt, id);
				
			} else sender.sendMessage("§4Usage: §c/lg join <game> [arena_id]");
		}
		else if (args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("restart")) {
			Game game = plugin.getActiveGames().get(p.getUniqueId());
			if (game == null) {
				p.sendMessage(plugin.getConfigString(p, "error-must-be-in-game", "§4Error: §cYou must be in a game to do this!"));
				return true;
			} else game.restart();
		}

		else if (args[0].equalsIgnoreCase("create")) { //create a new arena
			if (p.hasPermission("lobbygames.admin")) {
				
				if (args.length >= 2) {					
					GameType gt = getGameType(args[1]);
					if (gt != null) {
						if (editing.containsKey(p.getUniqueId())) {
							p.sendMessage("§7Deleted an unfinished arena.");
							editing.get(p.getUniqueId()).quit();
						}
						
						editing.put(p.getUniqueId(), new GameCreateInstance(p, gt, plugin));
					} else p.sendMessage("§4Error: §c'" + args[1] + "' is not an existing lobby game type!");
				} else p.sendMessage("§4Usage: §c/lg create <game_type>");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("delete")) { //delete an arena
			if (p.hasPermission("lobbygames.admin")) {
				if (args.length >= 3) {
					Arena a = getArena(args, p);
					if (a == null) return true;
					
					if (a.getHostingGame() != null) p.sendMessage("§6Warning: §eThere is currently an active game in this arena.");
					if (a.getID() < plugin.getArenas(a.getGameType()).size()) 
						p.sendMessage("§6Warning: §eThe IDs of all " + GameUtils.outgoingAliases(a.getGameType(), plugin) + " arenas higher than " + a.getID() + " will be shifted down by 1!");
					
					p.sendMessage("§cAre you sure you want to delete this arena? Type /lg confirm");
					
					delconfirm.put(p.getUniqueId(), a);
					
					new BukkitRunnable() {
						@Override
						public void run() {
							if (delconfirm.containsKey(p.getUniqueId())) delconfirm.remove(p.getUniqueId());
						}
					}.runTaskLater(plugin, 900l);
					
					
				} else p.sendMessage("§4Usage: §c/lg delete <game> <arena id>");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("confirm")) { //confirmation for arena delete
			if (p.hasPermission("lobbygames.admin")) {
				Arena a = delconfirm.get(p.getUniqueId());
				if (a != null) {
					delconfirm.remove(p.getUniqueId());
					if (a.getHostingGame() != null) a.getHostingGame().end();					
					
					if (plugin.deleteArena(a)) p.sendMessage("§bArena deleted. Note that all " + GameUtils.outgoingAliases(a.getGameType(), plugin) + " arenas with a larger ID have been shifted down by 1.");
					else p.sendMessage("§4Error: §cCould not delete this arena!");
				} else p.sendMessage("§4Error: §cThis confirmation has expired!");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("set")) { //set coordinate point
			if (p.hasPermission("lobbygames.admin")) {
				GameCreateInstance i = editing.get(p.getUniqueId());
				if (i != null) {
					if (args.length == 1) i.setLocation(p.getLocation());
					else {
						if (args[1].equalsIgnoreCase("scoreboard") || args[1].equalsIgnoreCase("leaderboard")) {
							if (i.setScoreboardLocation(p.getLocation())) {
								p.sendMessage("§aSuccessfully set the §b" + args[1].toLowerCase() + "§a location!");
							}
						} else p.sendMessage("§4Usage: §c\"/lg set\" or \"/lg set leaderboard\"");
					}
				} else p.sendMessage("§4Error: §cYou must be in editing mode for this! Use §n/lg create");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("finalize") || args[0].equalsIgnoreCase("save") || args[0].equalsIgnoreCase("finish")) { //finish setting coordinate points
			if (p.hasPermission("lobbygames.admin")) {
				GameCreateInstance i = editing.get(p.getUniqueId());
				if (i != null) {
					int id = i.save();
					if (id >= 0) {
						p.sendMessage("§2Success! §aTest this arena with §b/lg join " + GameUtils.getConfigName(i.getGameType()) + " " + id);
						editing.remove(p.getUniqueId());
					} else if (id == -1) p.sendMessage("§4Error: §cCoordinates are invalid (uncaught error in setup phase)");
				} else p.sendMessage("§4Error: §cYou must be in editing mode for this! Use §n/lg create§c or §n/lg edit");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("quit") || args[0].equalsIgnoreCase("exit") || args[0].equalsIgnoreCase("leave")) { //leave lobby game
			GameCreateInstance i = editing.get(p.getUniqueId());
			if (i != null) {
				p.sendMessage("§7Abandoned unfinished arena data, exited editing mode.");
				i.quit();
				editing.remove(p.getUniqueId());
			} else {
				Game g = plugin.getActiveGames().get(p.getUniqueId());
				if (g != null) {
					g.removePlayer(p);
				}
			}
		}
		
		else if (args[0].equalsIgnoreCase("reload")) { //reload config
			if (p.hasPermission("lobbygames.admin")) {
				plugin.reload();
				for (GameType gt : GameType.values()) {
					for (Arena a : plugin.getArenas(gt)) {
						if (a.getLeaderboard() != null) a.getLeaderboard().reloadFromConfig();
					}
					if (plugin.getGlobalLeaderboards().containsKey(gt)) {
						for (Leaderboard b : plugin.getGlobalLeaderboards().get(gt)) {
							b.reloadFromConfig();
						}
					}
				}
				p.sendMessage("§bLobbyGames has been reloaded!");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("kick")) { //kick player from lobby game
			if (p.hasPermission("lobbygames.kickplayer")) {
				if (args.length >= 2) {
					Player kick = Bukkit.getPlayer(args[1]);
					if (kick != null) {
						Game g = plugin.getActiveGames().get(kick.getUniqueId());
						if (g == null) {
							p.sendMessage("§4Error: §c" + kick.getName() + " is not in a game!");
							return true;
						}
						kick.sendMessage("§7You were kicked from a lobby game by " + p.getName());
						g.removePlayer(kick);
						p.sendMessage("§aSuccessfully kicked " + kick.getName() + " from a lobby game!");
					} else p.sendMessage("§4Error: §cThis player is offline!");
				} else p.sendMessage("§4Usage: §c/lg kick <player>");
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("list")) { //list all arenas
			if (p.hasPermission("lobbygames.command")) {
				if (args.length == 1) {
					p.sendMessage("§aLobbyGames arena summary:");
					for (GameType gt : GameType.values()) {
						String name = GameUtils.outgoingAliases(gt, plugin);
						int size = plugin.getArenas(gt).size();
						p.sendMessage("§b" + name.toUpperCase().charAt(0) + name.substring(1) + ": §3" + size + " Arena" + (size==1?"":"s"));
					}
				} else {
					GameType gt = getGameType(args[1]);
					if (gt != null) {
						p.sendMessage("§aLobbyGames " + GameUtils.outgoingAliases(gt, plugin) + " arenas:");
						List<Arena> arenas = plugin.getArenas(gt);
						int i = 1;
						for (Arena a : arenas) {
							Location spawn = a.getSpawn1();
							String scoords = "";
							if (spawn != null) scoords = ", §3(" + spawn.getBlockX() + ", " + spawn.getBlockY() + ", " + spawn.getBlockZ() + ")";
							p.sendMessage("§a#" + i + ": §b'§o" + a.getLocation1().getWorld().getName() + "§b'" + scoords);
							i++;
						}
					} else p.sendMessage("§4Error: §cUnknown lobby game '" + args[1] + "'");
				}
			} else p.sendMessage(noperm);
		}
		else if (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport")) { //teleport to arena
			if (p.hasPermission("lobbygames.command")) {
				if (args.length >= 2) {
					Arena a = getArena(args, p);
					if (a == null) return true;

					if (a.getSpawn1() != null) p.teleport(a.getSpawn1());
					else p.teleport(a.getLocation1());
				} else p.sendMessage("§4Usage: §c/lg tp <game> <arena id>");
			} else p.sendMessage(noperm);
		}
		
		else if (args[0].equalsIgnoreCase("leaderboard") || args[0].equalsIgnoreCase("board") || args[0].equalsIgnoreCase("lb") || args[0].equalsIgnoreCase("scoreboard")) {
			if (!p.hasPermission("lobbygames.admin")) {
				p.sendMessage(noperm);
				return true;
			}
			if (args.length == 1) {
				p.sendMessage("§3§l/lg leaderboard Sub-Commands:");
				p.sendMessage("§b/lg leaderboard create <game>");
				p.sendMessage("§b/lg leaderboard clear <game>");
				p.sendMessage("§b/lg leaderboard delete <game> [id]");
				p.sendMessage("§7This command only applies to global leaderboards, not arena-specific leaderboards.");
				return true;
			}
			if (args[1].equalsIgnoreCase("create")) { //create new global leaderboard
				if (args.length >= 3) {
					GameType gt = getGameType(args[2]);
					if (gt == null) {
						p.sendMessage("§4Error: §c'" + args[2] + "' is not an existing lobby game type");
						return true;
					}
					String gt_str = GameUtils.outgoingAliases(gt, plugin);
					if (!gt.usesLeaderboard()) {
						p.sendMessage("§4Error: §cLeaderboards are not used in " + gt_str.toString().toUpperCase().charAt(0) + gt_str.toString().substring(1) + "!");
						return true;
					}
					Leaderboard board = new Leaderboard(plugin, gt, p.getLocation());
					HashMap<GameType,List<Leaderboard>> globals = plugin.getGlobalLeaderboards();
					if (!globals.containsKey(gt)) {
						List<Leaderboard> blist = new ArrayList<>();
						blist.add(board);
						globals.put(gt, blist);
					} else globals.get(gt).add(board);
					board.updateDisplay();
					
					plugin.saveArenas();
					p.sendMessage("§aSuccessfully added a global leaderboard for " + gt_str.toString().toUpperCase().charAt(0) + gt_str.toString().substring(1) + "!");
				} else p.sendMessage("§4Usage: §c/lg leaderboard create <game>");
			}
			else if (args[1].equalsIgnoreCase("clear") || args[1].equalsIgnoreCase("clearboard")) { //clear leaderboard entries
				if (args.length >= 3) {
					GameType gt = getGameType(args[2]);
					if (gt == null) {
						p.sendMessage("§4Error: §c'" + args[2] + "' is not an existing lobby game type");
						return true;
					}
					
					List<Leaderboard> boards = plugin.getGlobalLeaderboards().get(gt);
					if (boards != null) {
						for (Leaderboard b : boards) {
							b.setEntries(new ArrayList<LeaderboardEntry>());
						}
					}
					String gt_str = GameUtils.outgoingAliases(gt, plugin);
					p.sendMessage("§aSuccessfully cleared the global leaderboard for " + gt_str.toString().toUpperCase().charAt(0) + gt_str.toString().substring(1) + "!");
				} else p.sendMessage("§4Usage: §c/lg leaderboard clear <game>");
			}
			else if (args[1].equalsIgnoreCase("delete")) { //delete global leaderboard
				if (args.length >= 3) {
					GameType gt = getGameType(args[2]);
					if (gt == null) {
						p.sendMessage("§4Error: §c'" + args[2] + "' is not an existing lobby game type");
						return true;
					}
					
					int id = 1;
					if (args.length >= 4) {
						try {
							id = Integer.parseInt(args[3]);
						} catch (Exception ex) {
							p.sendMessage("§4Error: §c'" + args[3] + "' is not a number!");
							return true;
						}
					}
					
					List<Leaderboard> boards = plugin.getGlobalLeaderboards().get(gt);
					if (boards == null) {
						p.sendMessage("§4Error: §cThis game type does not have any global leaderboards!");
						return true;
					}
					if (boards.size() < id || id <= 0) {
						p.sendMessage("§4Error: §cGlobal Leaderboard #" + id + " does not exist for this game type!");
						return true;
					}
					
					Leaderboard td = boards.get(id-1);
					td.remove();
					boards.remove(id-1);
					plugin.saveArenas();
					p.sendMessage("§aSuccessfully deleted a global leaderboard!");
					
				} else p.sendMessage("§4Usage: §c/lg leaderboard delete <game> [id]");
			} else p.sendMessage("§cUnknown sub-command.");
		}
		
		else if (args[0].equalsIgnoreCase("clearboard") || args[0].equalsIgnoreCase("clearleaderboard") || args[0].equalsIgnoreCase("clearscoreboard")) { //clear local leaderboard
			if (p.hasPermission("lobbygames.admin")) {
				if (args.length >= 3) {
					Arena a = getArena(args, p);
					if (a == null) return true;
					
					if (a.getLeaderboard() != null) {
						a.getLeaderboard().setEntries(new ArrayList<LeaderboardEntry>());
						p.sendMessage("§aSuccessfully cleared the leaderboard for this arena!");
					} else p.sendMessage("§4Error: §cThis arena does not have any leaderboard configured!");
				} else p.sendMessage("§4Usage: §c/lg clearboard <game> <arena id> §7(Resets the leaderboard)");
			} else p.sendMessage(noperm);
		}
		else p.sendMessage("§cUnknown sub-command.");
		
		return true;
	}
	
	
	@Override
	public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
		
		ArrayList<String> subc = new ArrayList<>();
		if (sender.hasPermission("lobbygames.command")) {
			if (args.length == 1) {
				String[] com = {"join", "quit", "list", "set", "restart", "tp"};
				for (int i = 0; i < com.length; i++) subc.add(com[i]);
				if (sender.hasPermission("lobbygames.kickplayer")) subc.add("kick");
				if (sender.hasPermission("lobbygames.admin")) {
					subc.add("reload"); subc.add("create"); subc.add("leaderboard"); subc.add("delete");
				}
			} else if (args.length == 2) {
				if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport")
						|| args[0].equalsIgnoreCase("delete")) return tabgamelist;
				else if (args[0].equalsIgnoreCase("leaderboard")) {
					subc.add("create");
					subc.add("clear");
					subc.add("delete");
				}
			} else if (args.length == 2) {
				if (args[0].equalsIgnoreCase("leaderboard")) return tabgamelist;
			}
		} else {
			if (args.length == 1) {
				String[] com_def = {"join", "quit", "restart"};
				for (int i = 0; i < com_def.length; i++) subc.add(com_def[i]);
			}
			else if (args.length == 2) return tabgamelist;
		}
		return subc;
	}

}
