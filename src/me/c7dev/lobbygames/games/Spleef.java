package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.GameWinEvent;
import me.c7dev.lobbygames.api.events.PlayerJoinLobbyGameEvent;
import me.c7dev.lobbygames.api.events.PlayerQuitLobbyGameEvent;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Spleef extends Game implements Listener {
	
	List<UUID> eliminated = new ArrayList<UUID>();
	HashMap<UUID,Long> lastmove = new HashMap<UUID,Long>();
	int starting_players = 0;
	boolean countdown = false;
	
	public void broadcast(String msg, boolean action_bar) {
		if (msg.length() == 0) return;
		for (UUID u : this.getPlayers()) {
			Player p = Bukkit.getPlayer(u);
			if (p != null) {
				if (action_bar) getPlugin().sendActionbar(p, msg, false);
				else p.sendMessage(getPlugin().sp(p, msg));
			}
		}
		for (UUID u : eliminated) {
			Player p  = Bukkit.getPlayer(u);
			if (p != null) {
				if (action_bar) getPlugin().sendActionbar(p, msg, false);
				else p.sendMessage(getPlugin().sp(p, msg));
			}
		}
	}
	
	private LobbyGames plugin;
	private boolean melting = false;
	
	public Spleef(LobbyGames plugin, Arena arena, Player player) {
		super(plugin, GameType.SPLEEF, arena, player);
		if (!this.canStart() || arena.getGameType() != GameType.SPLEEF) return;
		if (this.getArena().isInBoundsXZ(this.getArena().getSpawn1())) {
			Bukkit.getLogger().warning("Could not start Spleef because the spawn point is inside the arena!");
			return;
		}
		
		preparePlayer(player, GameMode.SURVIVAL);
		
		player.getInventory().addItem(new ItemStack(Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "DIAMOND_SPADE" : "DIAMOND_SHOVEL"), 1));
		player.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
		broadcast(plugin.getConfigString("waiting-players", "§cWaiting for more players to join..."), true);
		this.plugin = plugin;
		reset();
		Bukkit.getPluginManager().registerEvents(this, plugin);
	}
	
	public void setMelting(boolean b) {this.melting = b;}

	public void start() {
		if (!this.canStart() || this.isActive() || this.getPlayers().size() <= 1) return;
		this.setActive(true);
		
		this.starting_players = this.getPlayers().size();
		
		String startmsg = plugin.getConfigString("spleef.start-msg", "§3§m----------------------------------------\n§b§lSpleef: §bUse the shovel to break the snow blocks and don't fall below the surface! The last one standing wins!\n§3§m----------------------------------------\n");
		for (UUID u : this.getPlayers()) {
			Player p = Bukkit.getPlayer(u);
			if (p != null) {
				p.sendMessage(getPlugin().sp(p, startmsg));
				p.getInventory().setItem(8, new ItemStack(Material.AIR));
			}
		}
		
		int melt_delay = plugin.getConfig().getInt("spleef.melt-delay");
		if (melt_delay == 0) melt_delay = 50;
		if (melt_delay != -1) {
			final int md = melt_delay;
			final Spleef game = this;
			new BukkitRunnable() {
				int rem = md;
				@Override 
				public void run() { //main game runnable
					if (!game.canStart()) {
						this.cancel();
						return;
					}
					if (rem <= 0) {
						game.setMelting(true);
						game.broadcast(plugin.getConfigString("spleef.blocks-melting-msg", "§cBlocks are now melting!"), false);
						for (UUID u : game.getPlayers()) {
							Player p = Bukkit.getPlayer(u);
							if (p != null) {
								lastmove.put(u, System.currentTimeMillis());
								Block b = p.getLocation().add(0, -1, 0).getBlock();
								if (b.getType() == Material.SNOW_BLOCK && game.getArena().isInBounds(b.getLocation())) b.setType(Material.AIR);
							}
						}
						new BukkitRunnable() {
							@Override
							public void run() { //runnable to check if player has stopped, then melt a 2x2 square around them
								if (!canStart() || !isActive()) {
									this.cancel();
									return;
								}
								long time = System.currentTimeMillis();
								for (UUID u : game.getPlayers()) {
									if (lastmove.containsKey(u) && time - lastmove.get(u) >= 0.8) {
										Player p = Bukkit.getPlayer(u);
										if (p != null) {
											Location l1 = p.getLocation().add(0, -1, 0);
											Block b1 = l1.getBlock();
											if ((b1.getType() == Material.SNOW_BLOCK || b1.getType() == Material.AIR) && b1.getLocation().getBlockY() == getArena().getLocation1().getBlockY()) {
												Block l2, l3, l4;
												
												int xmult = l1.getBlockX() >= 0 ? 1 : -1;
												l2 = new Location(l1.getWorld(), l1.getBlockX() + (Math.abs(l1.getX() % 1) >= 0.5 ? xmult : -xmult), l1.getBlockY(), l1.getBlockZ()).getBlock();
												
												int zmult = l1.getBlockZ() >= 0 ? 1 : -1;
												l3 = new Location(l1.getWorld(), l1.getBlockX(), l1.getBlockY(), l1.getBlockZ() + (Math.abs(l1.getZ() % 1) >= 0.5 ? zmult : -zmult)).getBlock();
												
												l4 = new Location(l1.getWorld(), l2.getLocation().getBlockX(), l1.getBlockY(), l3.getLocation().getBlockZ()).getBlock();
												
												b1.setType(Material.AIR);
												if (l2.getType() == Material.SNOW_BLOCK) l2.setType(Material.AIR);
												if (l3.getType() == Material.SNOW_BLOCK) l3.setType(Material.AIR);
												if (l4.getType() == Material.SNOW_BLOCK) l4.setType(Material.AIR);
											}
										}
									}
								}
							}
						}.runTaskTimer(plugin, 20l, 5l);
						this.cancel();
					}
					
					if (rem == 30 || rem == 10 || rem == 5)
						game.broadcast(plugin.getConfigString("spleef.blocks-melting-seconds-msg", "§eBlocks melting in §c"+ rem + "§e seconds!").replaceAll("\\Q%seconds%\\E", "" + rem), true);
					
					rem--;
					
				}
			}.runTaskTimer(plugin, 0, 20l);
		}
	}
	
	public void reset() {
		GameUtils.fill(getArena(), Material.SNOW_BLOCK, (byte) 0, null, (byte) 0, false);
	}
	
	@Override
	public String getScore(Player p) {
		return getPlayTime();
	}
	
	public void die(Player p) {
		if (eliminated.contains(p.getUniqueId())) return;
		if (p.isOnline()) {
			eliminated.add(p.getUniqueId());
			p.teleport(this.getArena().getSpawn1());
		}
		
		super.removePlayer(p); //quit game event
		
		if (this.getPlayers().size() == 1) win(this.getPlayer1());
		else {
			String emsg = plugin.getConfigString("spleef.player-eliminated-msg");
			if (emsg == null) emsg = "§e" + p.getName() + " §cwas eliminated! §6" + this.getPlayers().size() + " players remaining!";
			else emsg = emsg.replaceAll("\\Q%remaining%\\E", this.getPlayers().size() + "").replaceAll("%eliminated_player%", p.getName());		
			broadcast(emsg, false);
		}
		
	}
	
	public void win(Player p) {
		String wmsg = plugin.getConfigString("spleef.win-msg", "&6&m----------------------------------------[newline]&e&l" + p.getName() + "&e won the Spleef game![newline]&6&m----------------------------------------");
		wmsg = wmsg.replaceAll("\\Q%winner%\\E", p.getName()).replaceAll("\\Q%player%\\E", p.getName());
		broadcast(wmsg, false);
		p.playSound(p.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		for (UUID u : eliminated) {
			Player p2 = Bukkit.getPlayer(u);
			if (p2 != null) p2.playSound(p2.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		}
		
		this.setConsoleCommand(this.getConsoleCommand().replaceAll("\\Q%winner%\\E", p.getName()).replaceAll("\\Q%player%\\E", p.getName()));
				
		reset();
		super.end();
		
		Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, starting_players));
		addScore(p, starting_players);
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (!canStart()) return;
		if (this.getArena().isInBoundsXZ(e.getPlayer().getLocation())) {
			double ydiff = e.getPlayer().getLocation().getY() - this.getArena().getLocation1().getBlockY();
			if (ydiff >= 1 && ydiff <= 5) {
				if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
					if (this.isActive() && melting) {
						Vector diff = e.getTo().toVector().subtract(e.getFrom().toVector());
						if (diff.getX() >= 0.01 || diff.getZ() >= 0.01) lastmove.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
						
						final Block b = e.getPlayer().getLocation().add(0, -1, 0).getBlock();
						if (b.getLocation().getBlockY() == this.getArena().getLocation1().getBlockY() && b.getType() == Material.SNOW_BLOCK) {
							new BukkitRunnable() { //add delay to melted blocks
								@Override
								public void run() {
									if (isActive() && canStart()) b.setType(Material.AIR);
								}
							}.runTaskLater(plugin, 3l);
						}
					}
				} else {
					if (this.isActive()) {
						e.getPlayer().teleport(this.getArena().getSpawn1());
						e.getPlayer().sendMessage(plugin.getConfigString(e.getPlayer(), "error-arena-in-use", "§4Error: §cThis arena is already in use!"));
					} else {
						super.appendPlayer(e.getPlayer()); //creates joinlobbygame event						
					}
				}
			} else if (ydiff < 0.25 && this.isActive() && this.getPlayers().contains(e.getPlayer().getUniqueId())) {
				die(e.getPlayer());
			}
		} else if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
			this.removePlayer(e.getPlayer());
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onBreak(BlockBreakEvent e) {
		if(!canStart()) return;
		if (this.getPlayers().contains(e.getPlayer().getUniqueId())){
			if (e.getBlock().getType() == Material.SNOW_BLOCK && this.getArena().isInBounds(e.getBlock().getLocation())) {
				if (LobbyGames.SERVER_VERSION >= 12) e.setDropItems(false);
				e.setCancelled(!this.isActive());
			} else e.setCancelled(true);
		}
	}
	
	@EventHandler
	public void onItemDamage(PlayerItemDamageEvent e) {
		if (!canStart()) return;
		if (this.isActive() && this.getPlayers().contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
	}
	
	@EventHandler
	public void onMelt(BlockFadeEvent e) {
		if (!canStart()) return;
		if (e.getBlock().getType() == Material.SNOW_BLOCK && getArena().isInBounds(e.getBlock().getLocation())) e.setCancelled(true);
	}
	
	@EventHandler
	public void onQuit(PlayerQuitEvent e) {
		if (!canStart()) return;
		if (eliminated.contains(e.getPlayer().getUniqueId())) eliminated.remove(e.getPlayer().getUniqueId());
	}
	
	@EventHandler
	public void onJoin(PlayerJoinLobbyGameEvent e) { //player joins before game has started
		if (!canStart()) return;
		if (e.getGame().getGameType() == GameType.SPLEEF && e.getGame().getArena().getID() == this.getArena().getID()) {
			if (this.isActive()) return; //error
			if (!this.getArena().isInBoundsXZ(e.getPlayer().getLocation())) {
				e.getPlayer().sendMessage("§4Error: §cYou need to be inside the spleef arena to play!");
				return; //player tried to use command
			}
			
			preparePlayer(e.getPlayer(), GameMode.SURVIVAL);
			e.getPlayer().getInventory().setItem(0, new ItemStack(Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "DIAMOND_SPADE" : "DIAMOND_SHOVEL"), 1));
			e.getPlayer().getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
			
			if (!countdown && this.getPlayers().size() >= 2) {
				countdown = true;
				new BukkitRunnable() {
					int rem = getPlugin().getConfig().getInt("spleef.countdown-seconds");
					
					@Override
					public void run() {
						if (!canStart() || getPlayers().size() < 2) {
							this.cancel();
							countdown = false;
						}
						else if (rem <= 0) {
							this.cancel();
							countdown = false;
							start();
						}
						else if (rem == 30 || rem == 20 || rem == 10 || rem <= 5) {
							broadcast(plugin.getConfigString("countdown-format", "§eThe game will start in §c" + rem + "§e seconds!").replaceAll("\\Q%seconds%\\E", "" + rem), true);
						}
						rem--;
					}
				}.runTaskTimer(this.getPlugin(), 0l, 20l);
			}
		}
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (!canStart()) return;
		if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
			ItemStack hand = GameUtils.getHandItem(e.getPlayer());
			if (hand != null && hand.getType() == Material.ARROW) {
				quitConfirmation(e.getPlayer());
			}
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitLobbyGameEvent e) {
		if (!canStart()) return;
		if (e.getGame().getGameType() == GameType.SPLEEF && e.getGame().getArena().getID() == this.getArena().getID()) {
			if (this.isActive()) die(e.getPlayer());
			else {
				this.removePlayer(e.getPlayer());
				if (this.getPlayers().size() == 1) broadcast(plugin.getConfigString("waiting-players", "§cWaiting for more players to join..."), true);
			}
		}
	}
	
	@EventHandler
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == this.getArena().getID()) {
			HandlerList.unregisterAll(this);
		}
	}
}
