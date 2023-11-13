package me.c7dev.lobbygames.commands;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;
import me.c7dev.lobbygames.util.Leaderboard;

public class GameCreateInstance {

	private GameType gt;
	private Player player;
	//private GameCreateState state = GameCreateState.LOCATION1;
	private int instr = 0;
	private Location l1, l2, s1, scoreloc, special1, special2;
	private LobbyGames plugin;
	private boolean quit = false;
	
	public GameCreateInstance(Player p, GameType gt, LobbyGames plugin) { //keeps track of edge cases and varying requirements in setting up a new arena
		if (!p.hasPermission("lobbygames.admin")) return;
		this.plugin = plugin;
		player = p;
		player.sendMessage("\n§bYou are now in editing mode! Use §n/lg quit§b to exit.");
		player.sendMessage("§2Step 1: §aGo to the §b" + (gt == GameType.CLICKER ? "center-point" : "bottom-left") + "§a of the arena and run §b/lg set");
		this.gt = gt;
	}
	
	public static Location blockLocation(Location l) {
		l.setX(l.getBlockX());
		l.setY(l.getBlockY());
		l.setZ(l.getBlockZ());
		l.setYaw(0);
		l.setPitch(0);
		return l;
	}
	
	public static int getMinLength(Location l1, Location l2) {
		
		if (l1.getBlockY() == l2.getBlockY()) {
			return Math.min(Math.abs(l2.getBlockX() - l1.getBlockX()), Math.abs(l2.getBlockZ() - l1.getBlockZ()));
		}
		else {
			if (l1.getBlockX() == l2.getBlockX()) return Math.min(Math.abs(l2.getBlockY() - l1.getBlockY()), Math.abs(l2.getBlockZ() - l1.getBlockZ()));
			else return Math.min(Math.abs(l2.getBlockX() - l1.getBlockX()), Math.abs(l2.getBlockY() - l1.getBlockY()));
		}
	}
	public static int getMaxLength(Location l1, Location l2) {
		
		if (l1.getBlockY() == l2.getBlockY()) {
			return Math.max(Math.abs(l2.getBlockX() - l1.getBlockX()), Math.abs(l2.getBlockZ() - l1.getBlockZ()));
		}
		else {
			
			if (l1.getX() == l2.getX()) return Math.max(Math.abs(l2.getBlockY() - l1.getBlockY()), Math.abs(l2.getBlockZ() - l1.getBlockZ()));
			else return Math.max(Math.abs(l2.getBlockX() - l1.getBlockX()), Math.abs(l2.getBlockY() - l1.getBlockY()));
		}
	}
	
	public boolean isValid() {
		if (player == null || !player.isOnline() || gt == null || !Arena.isValidCoords(l1, l2)) return false;
		if (l1 == null || (l2 == null && gt != GameType.CLICKER) || ((gt != GameType.MINESWEEPER && gt != GameType.SOCCER) && s1 == null)) return false;
		if (gt == GameType.SPLEEF && Arena.isInBoundsXZ(s1, l1, l2)) return false;
		if (gt == GameType.CLICKER && l1.getBlockX() == s1.getBlockX() && l1.getBlockZ() == s1.getBlockZ()) return false;
		return true;
	}
	public Location getLocation1() {return this.l1;}
	public Location getLocation2() {return this.l2;}
	public Location getSpawn1() {return this.s1;}
	public Location getScoreboardLocation() {return this.scoreloc;}
	public GameType getGameType() {return this.gt;}
	
	public int save() { //create arena object
		if (gt == GameType.CLICKER) {
			l1.setYaw(0f);
			l1.setPitch(0f);
			l2 = l1; //exception
		}
		if (l1 == null || l2 == null || s1 == null) {
			player.sendMessage("§4Error: §cNot all points are set!");
			return -2;
		}
		if (!isValid() && gt != GameType.CLICKER) return -1;
		
		if (gt == GameType.SPLEEF) { //past-compatable setup effects
			l1.setY(l1.getY() - 1);
			l2.setY(l2.getY() - 1);
		}
		
		int id = plugin.getArenas(gt).size() + 1;
		Leaderboard board = scoreloc == null ? null : new Leaderboard(plugin, gt, scoreloc);
		Arena a = new Arena(id, gt, l1, l2, s1, board, special1, special2);
		if (plugin.saveArena(a)) return id;
		else return -1;
	}

	public void quit() {
		quit = true;
		instr = -1;
	}
	
	public void playParticles() { //square of particles from l1 to l2
		if (LobbyGames.SERVER_VERSION < 12 || l1 == null) return;
		Location l1a = l1.clone();
		l1a.setX(l1a.getBlockX()); l1a.setY(l1a.getBlockY()); l1a.setZ(l1a.getBlockZ());
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!player.isOnline() || instr == -1 || l1 == null) {
					this.cancel();
					return;
				}

				Location loc = l2 == null ? (gt == GameType.CLICKER ? l1.clone() : player.getLocation()) : l2.clone();
				loc.setX(loc.getBlockX()); loc.setY(loc.getBlockY()); loc.setZ(loc.getBlockZ());
				Location corner = l1a.clone();

				if (Math.abs(corner.getBlockX() - loc.getBlockX()) <= 34 && Math.abs(corner.getBlockZ() - loc.getBlockZ()) <= 34) {
					if (l1.getBlockY() == loc.getBlockY()) {
						if (loc.getZ() <= corner.getZ()) corner.setZ(corner.getZ() + 1);
						else loc.setZ(loc.getZ() + 1);

						if (loc.getX() < corner.getX()) corner.setX(corner.getX() + 1);
						else loc.setX(loc.getX() + 1);

						GameUtils.particleLine(corner, new Location(corner.getWorld(), loc.getX(), corner.getY(), corner.getZ()));
						GameUtils.particleLine(corner, new Location(corner.getWorld(), corner.getX(), corner.getY(), loc.getZ()));
						GameUtils.particleLine(loc, new Location(corner.getWorld(), corner.getX(), loc.getY(), loc.getZ()));
						GameUtils.particleLine(loc, new Location(corner.getWorld(), loc.getX(), loc.getY(), corner.getZ()));
					}
					else if (l1.getBlockX() == loc.getBlockX()) {
						loc.setY(loc.getY() + 1);

						if (loc.getZ() <= corner.getZ()) corner.setZ(corner.getZ() + 1);
						else loc.setZ(loc.getZ() + 1);

						GameUtils.particleLine(corner, new Location(corner.getWorld(), corner.getX(), loc.getY(), corner.getZ()));
						GameUtils.particleLine(corner, new Location(corner.getWorld(), corner.getX(), corner.getY(), loc.getZ()));
						GameUtils.particleLine(loc, new Location(corner.getWorld(), loc.getX(), corner.getY(), loc.getZ()));
						GameUtils.particleLine(loc, new Location(corner.getWorld(), loc.getX(), loc.getY(), corner.getZ()));
					}
					else if (l1.getBlockZ() == loc.getBlockZ()) {
						loc.setY(loc.getY() + 1);
						if (loc.getX() < corner.getX()) corner.setX(corner.getX() + 1);
						else loc.setX(loc.getX() + 1);

						GameUtils.particleLine(corner, new Location(corner.getWorld(), loc.getX(), corner.getY(), corner.getZ()));
						GameUtils.particleLine(corner, new Location(corner.getWorld(), corner.getX(), loc.getY(), corner.getZ()));
						GameUtils.particleLine(loc, new Location(corner.getWorld(), corner.getX(), loc.getY(), loc.getZ()));
						GameUtils.particleLine(loc, new Location(corner.getWorld(), loc.getX(), corner.getY(), loc.getZ()));
					}
				}
			}
		}.runTaskTimer(plugin, 0, 5l);
	}

	public void setLocation(Location l) {
		if (!player.hasPermission("lobbygames.admin")) return;
		switch(instr) {
		case 0:
			if (setLocation1(l)) {
				player.sendMessage("\n§7Set bottom-left to " + l1.getBlockX() + ", " + l1.getBlockY() + ", " + l1.getBlockZ());
				if (gt == GameType.CLICKER) {
					instr += 2;
					player.sendMessage("§2Step 2: §aGo to the §bspawnpoint§a of the arena and run §b/lg set");
				} else {
					instr++;
					player.sendMessage("§2Step 2: §aGo to the §btop-right§a of the arena and run §b/lg set");
				}
				playParticles();
			}
			break;
		case 1:
			if (setLocation2(l)) {
				player.sendMessage("\n§7Set top-right to " + l2.getBlockX() + ", " + l2.getBlockY() + ", " + l2.getBlockZ());
				player.sendMessage("§2Step 3: §aGo to the §bspawnpoint§a of the arena and run §b/lg set");
				instr++;
			}
			break;
		case 2:
			if (setSpawn1(l)) {
				if (gt == GameType.SOCCER) {
					player.sendMessage("§2Step 4: §aGo to the §bupper-right corner§a of the §bred soccer net§a and run §b/lg set");
					instr++;
				} else {
					player.sendMessage("\n§7Set spawn to " + s1.getBlockX() + ", " + s1.getBlockY() + ", " + s1.getBlockZ() + " (yaw: " + Math.round(s1.getYaw()) + ", pitch: " + Math.round(s1.getPitch()) + ")");
					if (gt.usesLeaderboard()) player.sendMessage("§2Locations are set! §aTo add a leaderboard to this arena, use §b/lg set leaderboard");
					player.sendMessage("§c§lImportant: §aDon't forget to save this arena with §b/lg save§a!");
					instr = -1;
				}
			}
			break;
		case 3:
			if (setSpecialLoc1(l)) {
				player.sendMessage("§2Step 5: §aGo to the §bbottom-left corner§a of the same net and run §b/lg set");
				instr++;
			}
			break;
		case 4:
			if (setSpecialLoc2(l)) {
				player.sendMessage("§2Locations are set! " + (gt != GameType.SOCCER ? "§aTo add a leaderboard to this arena, use §b/lg set leaderboard" : ""));
				player.sendMessage("§c§lImportant: §aDon't forget to save this arena with §b/lg save§a!");
				instr = -1;
			} 
			break;
		default:
			player.sendMessage("§aAll locations are set, make sure to save the new arena with §b/lg save");
		}
	}

	public boolean checkCoords(Location l, Location l1) {
		if (l1 == null) return true;
		if (!Arena.isValidCoords(l1, l)) {
			player.sendMessage("§4Error: §cThis coordinate is invalid. The coordinates must make 2 corners of a flat square in the same world.");
			return false;
		}
		
		if (!gt.canSupportVerticalArena() && l.getBlockY() != l1.getBlockY()) {
			player.sendMessage("§4Error: §cThis game type requires that the arena is flat on the ground.");
			return false;
		}
		
		if (gt == GameType.SUDOKU) {
			if (getMinLength(l, l1) != getMaxLength(l, l1) || getMinLength(l, l1) != 8) {
				player.sendMessage("§4Error: §cSudoku must be a 9x9 arena");
				return false;
			}
		} 
		else if (gt == GameType.TICTACTOE) {
			if (getMinLength(l, l1) != getMaxLength(l, l1) || getMinLength(l, l1) != 2) {
				player.sendMessage("§4Error: §cTic Tac Toe must be a 3x3 arena");
				return false;
			}
		}
		else if (gt == GameType.POOL) {
			int ml = getMaxLength(l, l1);
			if (getMinLength(l, l1) != 2 || ml < 3 || ml > 4) {
				player.sendMessage("§4Error: §cPool must be sized as 3x4 or 3x5!");
				return false;
			}
		}
		else if (gt == GameType.CONNECT4) {
			if (l1.getBlockY() == l.getBlockY()) {
				player.sendMessage("§4Error: §cThis game must be vertical (on the wall)!");
				return false;
			}
			int max = getMaxLength(l, l1);
			int min = getMinLength(l, l1);
			if (max != 6 || min != 5) {
				if (min < 5) {
					player.sendMessage("§4Error: §cMinimum width and height is 6 blocks! (Recommended size is 7x6)");
					return false;
				}
				else if (max > 49) {
					player.sendMessage("§4Error: §cMaximum width and height is 50 blocks! (Recommended size is 7x6)");
					return false;
				}
			}
		}
		else {
			if (getMinLength(l1, l) < (gt == GameType.T048 ? 3 : 6)) {
				player.sendMessage("§4Error: §cThe width and height must be at least " + (gt == GameType.T048 ? 4 : 7) + " blocks wide for this game.");
				return false;
			}
			else if (getMaxLength(l1, l) > (gt == GameType.SPLEEF ? 59 : 49) && gt != GameType.SOCCER) {
				player.sendMessage("§4Error: §cThe arena can not be larger than " + (gt == GameType.SPLEEF ? "60" : "50") + " blocks in width!");
				return false;
			}
		}
		return true;
	}
	
	public boolean setLocation1(Location l) {
		if (!checkCoords(l, l2)) return false;
		blockLocation(l);
		l1 = l;
		return true;
	}
	
	public boolean setLocation2(Location l) {
		if (gt == GameType.CLICKER) {
			player.sendMessage("§6Note: §eThis game does not require a 2nd location!");
			return false;
		}
		if (!checkCoords(l, l1)) return false;
		l2 = blockLocation(l);
		return true;
	}
	
	public boolean setSpecialLoc1(Location l) {
		if (gt == GameType.SOCCER) {
			if (l1 != null && l2 != null) {
				l.add(0, 1, 0);
				if (l.getBlockY() - l1.getBlockY() >= 3) {
					if (!Arena.isInBoundsXZ(l, l1, l2)) {
						special1 = blockLocation(l);
						return true;
					} else player.sendMessage("§4Error: §cThe corner of the soccer net must be outside of the arena selection!");
				} else player.sendMessage("§4Error: §cThe net must be at least 3 blocks tall! (This location should be off the ground)");
			} else player.sendMessage("§4Error: §cThe corners of the arena must be defined first!");
		} else {
			player.sendMessage("§4Error: §cThis game type does not use this location.");
			return true;
		}
		return false;
	}
	public boolean setSpecialLoc2(Location l) {
		if (gt == GameType.SOCCER) {
			if (l1 != null && l2 != null) {
				if (l.getBlockY() == l1.getBlockY()) {
					if (!Arena.isInBoundsXZ(l, l1, l2)) {
						if (!neighborsArena(l) && special1 != null && !neighborsArena(special1)) {
							player.sendMessage("§4Error: §cOne of the net locations must neighbor the arena!");
							return false;
						}
						special2 = blockLocation(l);
						return true;
					} else player.sendMessage("§4Error: §cThe corner of the soccer net must be outside of the arena selection!");
				} else player.sendMessage("§4Error: §cThis location must be at the same Y-value as the arena's ground!");
			} else player.sendMessage("§4Error: §cThe corners of the arena must be defined first!");
		} else {
			player.sendMessage("§4Error: §cThis game type does not use this location.");
			return true;
		}
		return false;
	}
	private boolean neighborsArena(Location l) {
		return (Math.abs(l.getBlockX() - l1.getBlockX()) == 1 || Math.abs(l.getBlockZ() - l1.getBlockZ()) == 1 || 
				Math.abs(l.getBlockX() - l2.getBlockX()) == 1 || Math.abs(l.getBlockZ() - l2.getBlockZ()) == 1);
	}
	
	public boolean setSpawn1(Location l) {
		if (gt == GameType.SPLEEF || gt == GameType.SOCCER) {
			if (l1 == null || l2 == null) {
				player.sendMessage("§4Error: §cThe corners of the arena must be set before setting a spawn!");
				return false;
			}
			if (Arena.isInBoundsXZ(l, l1, l2)) {
				player.sendMessage("§4Error: §cThe spawnpoint cannot be inside of the arena! (This is where eliminated players are teleported.)");
				return false;
			}
		}
		else if (gt == GameType.CLICKER) {
			if (l1 == null) {
				player.sendMessage("§4Error: §cThe arena center-point must be set before setting the Clicker spawn!");
				return false;
			}
			if (l.getBlockX() == l1.getBlockX() && l.getBlockZ() == l1.getBlockZ()) {
				player.sendMessage("§4Error: §cSpawnpoint cannot be the same as the center-point!");
				return false;
			}
		}
		else if (gt == GameType.SNAKE) {
			l.setX(l.getBlockX() + 0.5);
			l.setZ(l.getBlockZ() + 0.5);
		}
		s1 = l;
		return true;
	}
	
	public boolean setScoreboardLocation(Location l) {
		if (!gt.usesLeaderboard()) {
			player.sendMessage("§4Error: §cThis game type does not use leaderboards.");
			return false;
		}
		this.scoreloc = l;
		return true;
	}
	
}
