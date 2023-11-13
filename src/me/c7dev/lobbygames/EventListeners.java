package me.c7dev.lobbygames;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import me.c7dev.lobbygames.api.events.PlayerQuitLobbyGameEvent;
import me.c7dev.lobbygames.games.Pool;
import me.c7dev.lobbygames.games.Soccer;
import me.c7dev.lobbygames.games.Spleef;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class EventListeners implements Listener {
	
	LobbyGames plugin;
	public EventListeners(LobbyGames plugin) {
		this.plugin = plugin;
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		plugin.quitPlayer(e.getPlayer().getUniqueId());
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		Game playing = plugin.getActiveGames().get(e.getPlayer().getUniqueId());
		
		if (playing != null) {
			if (!e.getPlayer().getWorld().getName().equals(playing.getArena().getLocation1().getWorld().getName()) || 
					e.getPlayer().getLocation().distance(playing.getArena().getCenterPixel()) >= Math.max(25, Math.max(playing.getArena().getWidth()*1.2, playing.getArena().getHeight()*1.2))) {
				playing.noEndTeleportation(e.getPlayer().getUniqueId());
				playing.removePlayer(e.getPlayer());
			}
		} else {
			List<Arena> arenas = plugin.getArenas(GameType.SPLEEF);
			if (arenas != null) {
				for (Arena a : arenas) {
					if (!a.getLocation1().getWorld().getName().equals(e.getPlayer().getLocation().getWorld().getName())) continue;
					if (a.getHostingGame() == null && a.isInBoundsXZ(e.getPlayer().getLocation())) {
						double ydiff = e.getPlayer().getLocation().getY() - a.getLocation1().getY();
						if (ydiff >= 0 && ydiff <= 5) {
							new Spleef(plugin, a, e.getPlayer());
							return;
						}
					}
				}
			}
			if (plugin.soccerProximityJoining()) {
				List<Arena> arenas2 = plugin.getArenas(GameType.SOCCER);
				if (arenas2 != null) {
					for (Arena a : arenas2) {
						if (!a.getLocation1().getWorld().getName().equals(e.getPlayer().getLocation().getWorld().getName())) continue;
						if (a.getHostingGame() == null && a.isInBoundsXZ(e.getPlayer().getLocation())) {
							double ydiff = e.getPlayer().getLocation().getY() - a.getLocation1().getY();
							if (ydiff >= 0 && ydiff <= 5) {
								new Soccer(plugin, a, e.getPlayer());
								return;
							}
						}
					}
				}
			}
			if (plugin.poolProximityJoining()) {
				if (!plugin.getProximityDelay().containsKey(e.getPlayer().getUniqueId()) || plugin.getProximityDelay().get(e.getPlayer().getUniqueId()) <= System.currentTimeMillis()) {
					List<Arena> pool_arenas = plugin.getArenas(GameType.POOL);
					if (pool_arenas != null) {
						for (Arena a : pool_arenas) {
							if (a.getHostingGame() == null && GameUtils.distSquareXZ(a.getCenterPixel(), e.getPlayer().getLocation()) <= 5.2) {
								new Pool(plugin, a, e.getPlayer());
								return;
							}
						}
					}
				}
			}
		}
		
	}
	
	@EventHandler
	public void onHunger(FoodLevelChangeEvent e) {
		if (plugin.getActiveGames().containsKey(e.getEntity().getUniqueId())){
			e.setCancelled(true);
			Player p = (Player) e.getEntity();
			if (p.getHealth() < 20) p.setHealth(20d);
			if (p.getFoodLevel() < 20) p.setFoodLevel(20);
		}
	}
	
	@EventHandler
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player) {
			if (plugin.getActiveGames().containsKey(e.getEntity().getUniqueId())){
				e.setCancelled(true);
				Player p = (Player) e.getEntity();
				if (p.getHealth() < 20) p.setHealth(20d);
				if (p.getFoodLevel() < 20) p.setFoodLevel(20);
			}
		}
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		ItemStack hand = GameUtils.getHandItem(e.getPlayer());
		if (hand != null && hand.getType().toString().endsWith("WOOL")) {
			byte data = hand.getData().getData();
			Game game = plugin.getActiveGames().get(e.getPlayer().getUniqueId());
			if (game == null || !game.isInQuitConfirmation(e.getPlayer().getUniqueId())) return;
			
			if (data == (byte) 5) {
				game.removePlayer(e.getPlayer());
				e.setCancelled(true);
				return;
			}
			else if (data == (byte) 14) {
				game.removeQuitConfirmation(e.getPlayer());
				e.setCancelled(true);
				return;
			}
		}
		
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) {
			if (e.getAction() == Action.LEFT_CLICK_BLOCK && e.getPlayer().hasPermission("lobbygames.admin")) return;
			if (e.getClickedBlock().getType().toString().contains("SIGN")) {
				String[] lines = ((Sign) e.getClickedBlock().getState()).getLines();
				if (lines.length < 4) return;
				if (lines[0].length() == 0) {
					for (int i = 0; i < 3; i++) lines[i] = lines[i+1];
				}
				if (lines[0].equalsIgnoreCase("[" + plugin.getConfigString("join-sign-text", "JOIN") + "]")) {
					e.setCancelled(true);
					String[] gtstr_split = lines[1].split(":");
					int id = -1;
					GameType gt;
					
					try {
						gt = GameType.valueOf(GameUtils.incomingAliases(gtstr_split[0].replaceAll(" ", "").replaceAll("-", ""), plugin).toUpperCase());
					} catch(IllegalArgumentException ex) {
						return;
					}			
					if (gtstr_split.length > 1) {
						if (gtstr_split[1].equalsIgnoreCase("A")) id = -1; //first available
						else if (gtstr_split[1].equalsIgnoreCase("B")) { //bordering
							int proximity = Integer.MAX_VALUE;
							Location loc = e.getClickedBlock().getLocation();
							for (Arena a : plugin.getArenas(gt)) {
								if (a.getCenterPixel().getWorld().getName().equalsIgnoreCase(loc.getWorld().getName())) {
									int dist = GameUtils.dist(loc, a.getCenterPixel());
									if (id == -1 || GameUtils.dist(loc, a.getCenterPixel()) < proximity) {
										id = a.getID();
										proximity = dist;
									}
								}
							}
						} else {
							try {
								id = Integer.parseInt(gtstr_split[1]);
							} catch (Exception ex) {
								return;
							}
						}
					}
					plugin.joinPlayer(e.getPlayer(), gt, id);
					
				}
			}
		}
	}
	
	@EventHandler
	public void onRunCommand(PlayerCommandPreprocessEvent e) {
		if (!e.getPlayer().hasPermission("lobbygames.command") && plugin.getActiveGames().containsKey(e.getPlayer().getUniqueId())) {
			int mode = plugin.getCommandBlockMode();
			if (mode == 0) return;
			String command = e.getMessage().replaceFirst("\\Q/\\E", "").split(" ")[0];
			if (command.equalsIgnoreCase("lg") || command.equalsIgnoreCase("lobbygames") || command.equalsIgnoreCase("lgames")) return;
			
			boolean in_list = false;
			for (String bc : plugin.getBlockedCommands()) {
				if (command.equalsIgnoreCase(bc)) {
					in_list = true;
					break;
				}
			}
			
			if (mode == (in_list ? 1 : 2)) {
				String msg = plugin.getConfigString(e.getPlayer(), "command-blocked-msg", "§cYou cannot use commands while playing this game!");
				e.getPlayer().sendMessage(msg);
				e.setCancelled(true);
			}
			
		}
	}
	
	@EventHandler
	public void onGameQuit(PlayerQuitLobbyGameEvent e) {
		if (e.getPlayer().isOnline()) {
			String cmd = plugin.getConfigString(e.getPlayer(), GameUtils.getConfigName(e.getGame().getGameType()) + ".console-command-on-quit", "");
			if (cmd != null && cmd.length() > 0) {
				cmd = cmd.replaceAll("\\Q%player%\\E", e.getPlayer().getName()).replaceAll("\\Q%score%\\E", e.getGame().getScore(e.getPlayer()));
				for (String command : cmd.split("\n")) {
					Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.trim());
				}
			}
		}
	}
	
	@EventHandler
	public void onDrop(PlayerDropItemEvent e) {
		if (plugin.getActiveGames().containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
	}
	
	@EventHandler
	public void onInventory(InventoryClickEvent e) {
		if (plugin.getActiveGames().containsKey(e.getWhoClicked().getUniqueId())) e.setCancelled(true);
	}
	
	@EventHandler
	public void onBreak(BlockBreakEvent e) {
		Game a = plugin.getActiveGames().get(e.getPlayer().getUniqueId());
		if (a != null && (a.getGameType() != GameType.SPLEEF || e.getBlock().getType() != Material.SNOW_BLOCK)) e.setCancelled(true);
	}
	
	@EventHandler
	public void onPlace(BlockPlaceEvent e) {
		Game a = plugin.getActiveGames().get(e.getPlayer().getUniqueId());
		if (a != null) e.setCancelled(true);
	}

}
