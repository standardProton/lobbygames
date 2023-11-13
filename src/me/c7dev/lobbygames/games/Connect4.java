package me.c7dev.lobbygames.games;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
import me.c7dev.lobbygames.commands.GameCreateInstance;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Connect4 extends Game implements Listener {
	
	enum State{EMPTY, RED, YELLOW};
	
	private Player p1, p2;
	private boolean p1_turn, p1_red, placing = false;
	private String your_turn, opp_turn, red, yellow;
	private LobbyGames plugin;
	private Arena arena;
	private State[][] spaces;
	private int place_count = 0;
	
	Random random = new Random();
	
	public Connect4(LobbyGames plugin, Arena arena, Player p) {
		super(plugin, GameType.CONNECT4, arena, p);
		if (!this.canStart() || arena.getGameType() != GameType.CONNECT4) return;
		if (!arena.isVerticalLayout()) {
			if (p1.hasPermission("lobbygames.admin")) p1.sendMessage("§cThere was an error in starting this game, check the console.");
			Bukkit.getLogger().severe("This Connect 4 game could not start because flat (horizontal) arenas are not supported!");
			return;
		}
		
		if (arena.getSpawn1() != null) p.teleport(arena.getSpawn1());
		
		preparePlayer(p);
		
		this.plugin = plugin;
		this.arena = arena;
		p1 = p;
		p1_turn = random.nextBoolean();
		p1_red = random.nextBoolean();
		your_turn = plugin.getConfigString("your-turn-msg", "§aYour Turn");
		opp_turn = plugin.getConfigString("opponent-turn-msg", "§7Opponent's Turn");
		String[] words = plugin.getConfigString("connect4.translatable-words", "Red, Yellow").split(",");
		red = "§c§l" + words[0].trim();
		yellow = "§e§l" + (words.length > 1 ? words[1].trim() : "Yellow");
		
		spaces = new State[arena.getWidth()][arena.getHeight()];
		
		for (int i = 0; i < arena.getWidth(); i++) {
			for (int j = 0; j < arena.getHeight(); j++) {
				spaces[i][j] = State.EMPTY;
			}
		}
				
		p.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
				
		Bukkit.getPluginManager().registerEvents(this, plugin);
				
		//p1_turn = true;
		//setActive(true);
		
		reset();
	}
	
	@EventHandler
	public void onEntityInteract(PlayerInteractAtEntityEvent e) {
		if (getPlayers().contains(e.getPlayer().getUniqueId())) {
			place(e.getPlayer(), null);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onArmorStand(PlayerArmorStandManipulateEvent e) {
		if (!canStart()) return;
		if (getPlayers().contains(e.getPlayer().getUniqueId())) {
			e.setCancelled(true);
			place(e.getPlayer(), null);
		}
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (!canStart()) return;
		if (getPlayers().contains(e.getPlayer().getUniqueId())) {
			ItemStack hand = GameUtils.getHandItem(e.getPlayer());
			if (hand != null && hand.getType() == Material.ARROW) {
				quitConfirmation(e.getPlayer());
				return;
			}
			
			if (isActive()) {
				if (e.getPlayer().getUniqueId().equals(p1_turn ? p1.getUniqueId() : p2.getUniqueId())) {
					e.setCancelled(true);
					place(e.getPlayer(), e.getClickedBlock() == null ? null : e.getClickedBlock().getLocation());
				} else {
					String msg = plugin.getConfigString("not-your-turn-msg", "");
					if (msg.length() > 0) getPlugin().sendActionbar(e.getPlayer(), msg, false);
				}
			} 
		}
	}
	
	public boolean checkWin(int x, int y, State state) {
		int count = 0;
		
		//vertical
		for (int i = Math.max(y-3, 0); i < y+3; i++) {
			if (i < arena.getHeight()) {
				if (spaces[x][i] == state) {
					count++;
					if (count >= 4) return true;
				}
				else count = 0;
			}
		}
		count = 0;
		
		//horizontal
		for (int i = Math.max(x - 3, 0); i < x + 3; i++) {
			if (i < arena.getWidth()) {
				if (spaces[i][y] == state) {
					count++;
					if (count >= 4) return true;
				}
				else count = 0;
			}
		}
		count = 0;
		
		//diag1
		for (int i = -3; i < 4; i++) {
			int x2 = x+i, y2 = y+i;
			if (x2 >= 0 && y2 >= 0 && x2 < arena.getWidth() && y2 < arena.getHeight()) {
				if (spaces[x2][y2] == state) {
					count++;
					if (count >= 4) return true;
				}
				else count = 0;
			}
		}
		count = 0;
				
		//diag2
		for (int i = -3; i < 4; i++) {
			int x2 = x-i, y2 = y+i;
			if (x2 >= 0 && y2 >= 0 && x2 < arena.getWidth() && y2 < arena.getHeight()) {
				if (spaces[x2][y2] == state) {
					count++;
					if (count >= 4) return true;
				}
				else count = 0;
			}
		}
		
		return false;
	}
	
	public Location getLookingBlock(Player p) { //vector addition until intersects with arena screen
		Location loc = p.getLocation().add(0, p.isSneaking() ? 1.25:1.5, 0);
		Vector dir = p.getLocation().getDirection().normalize().multiply(0.4);
		int i = 0;
		while (!arena.isInBounds(loc)) {
			if (i >= 12) return null;
			i++;
			loc.add(dir);
		}
		return loc;
	}
	
	public void place(Player p, Location loc) { //player selects a column
		if (placing || !isActive() || !p.getUniqueId().equals(p1_turn ? p1.getUniqueId() : p2.getUniqueId())) return;
		
		if (loc == null) loc = getLookingBlock(p);
		else if (!arena.isInBounds(loc)) return;
		
		if (loc == null) return;
		
		CoordinatePair clicked = getCoords(loc);
		int x = clicked.getX() + (arena.getWidth()/2);
		if (arena.getWidth() % 2 == 0 && (arena.getCoordinateRotation() == 0 || arena.getCoordinateRotation() == 1)) x -= 1;
				
		if (x < 0 || x >= spaces.length) return;
		
		int y = 0;
		for (int i = 0; i < arena.getHeight(); i++) {
			if (spaces[x][i] == State.EMPTY) break;
			else y++;
		}
		
		if (y >= arena.getHeight()) return;
		
		clicked.setY(y);
		State newstate = (p1_red ? p1_turn : !p1_turn) ? State.RED : State.YELLOW;
		spaces[x][y] = newstate;
		
		placing = true;
		place_count++;
		
		GameCreateInstance.blockLocation(loc); //integer coords
		loc.add(0.5, 0, 0.5);
		loc.add(arena.getGenericOffset().multiply(0.25)); //normal vector to arena
		Location loc2 = loc.clone();
		if (arena.isVerticalLayout()) {
			loc.setY(arena.getLocation2().getY());
			loc2.setY(loc.getY() - arena.getHeight() + y);
			loc2.add(0, -0.2, 0);
			loc.add(0, -0.25, 0);
		}
				
		ArmorStand drop = getWorld().spawn(loc, ArmorStand.class);
		drop.setVisible(false);
		drop.setGravity(false);
		drop.setHelmet(GameUtils.createWool(1, (p1_red ? p1_turn : !p1_turn) ? 14 : 4, "Click"));
		
		int ticks = 6;
		final Location locf = loc;
				
		new BukkitRunnable() {
			int ticks_done = 0;
			Vector incr = loc2.toVector().subtract(locf.toVector()).multiply(1.0/ticks);
			
			public void run() {
				if (ticks_done >= ticks) {
					placing = false;
					this.cancel();
					return;
				}
				
				drop.teleport(drop.getLocation().add(incr));
				ticks_done++;
			}
		}.runTaskTimer(plugin, 5l, 1l);
		
		if (checkWin(x, y, newstate)) win(p1_turn, true);
		else if (place_count >= arena.getWidth()*arena.getHeight()) draw();
		
		p1_turn = !p1_turn;
		
	}
	
	@EventHandler
	public void onJoin(PlayerJoinLobbyGameEvent e) {
		if (!canStart() || e.getGame().getGameType() != GameType.CONNECT4 || e.getGame().getArena().getID() != this.getArena().getID()) return;
		if (getArena().getSpawn1() != null) {
			e.getPlayer().teleport(this.getArena().getSpawn1());
			preparePlayer(e.getPlayer());
		}
		if (getPlayers().size() == 2) { //start
			setActive(true);
			p2 = e.getPlayer();
			p2.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
						
			String defaultmsg = "§3§m----------------------------------------\n" +
					"§b§lConnect 4: §bAdd tiles to the top of the board and try to connect 4 of your color in any row, column, or diagonal!\n" +
					"§3§m----------------------------------------";
			String startmsg = getPlugin().getConfigString("connect4.start-msg", defaultmsg);
			if (startmsg.length() > 0) {
				p1.sendMessage(plugin.sp(p1, startmsg));
				p2.sendMessage(plugin.sp(p2, startmsg));
			}
			
			if ((!p1.getWorld().getName().equals(getArena().getLocation1().getWorld().getName()) || p1.getLocation().distance(getArena().getCenterPixel()) > 10) && getArena().getSpawn1() != null) p1.teleport(getArena().getSpawn1());
			
			reset();
			
			String side_msg = plugin.getConfigString("connect4.side-msg", "\n§bYou are playing as %side%§b!");
			String red_msg = side_msg.replaceAll("\\Q%side%\\E", red);
			String yellow_msg = side_msg.replaceAll("\\Q%side%\\E", yellow);
			if (p1_red) {
				p1.sendMessage(plugin.sp(p1, red_msg + " §f" + (p1_turn ? your_turn : opp_turn)));
				p1.getInventory().setItem(4, GameUtils.createWool(1, 14, red_msg));
				p2.sendMessage(plugin.sp(p2, yellow_msg + " §f" + (p1_turn ? opp_turn : your_turn)));
				p2.getInventory().setItem(4, GameUtils.createWool(1, 4, yellow_msg));	
			} else {
				p1.sendMessage(plugin.sp(p1, yellow_msg + " §f" + (p1_turn ? your_turn : opp_turn)));
				p1.getInventory().setItem(4, GameUtils.createWool(1, 4, yellow_msg));
				p2.sendMessage(plugin.sp(p2, red_msg + " §f" + (p1_turn ? opp_turn : your_turn)));
				p2.getInventory().setItem(4, GameUtils.createWool(1, 14, red_msg));	
			}
			
			if (opp_turn.length() > 0 || your_turn.length() > 0) {
				new BukkitRunnable() {
					@Override
					public void run() {
						if (!canStart() || !isActive()) {
							this.cancel();
							return;
						}
						getPlugin().sendActionbar(p1, p1_turn ? your_turn : opp_turn, true);
						getPlugin().sendActionbar(p2, p1_turn ? opp_turn : your_turn, true);
					}
				}.runTaskTimer(plugin, 0, 20l);
			}
			
		}
	}
	
	public void draw() {
		if (p2 != null) {
			String wmsg = plugin.getConfigString("connect4.draw-msg", "&2&m----------------------------------------[newline]&a&lThis game is a draw![newline]&2&m----------------------------------------");
			p1.sendMessage(plugin.sp(p1, wmsg));
			p2.sendMessage(plugin.sp(p2, wmsg));
			
			Bukkit.getPluginManager().callEvent(new GameWinEvent(p1, this, 0.5));
			Bukkit.getPluginManager().callEvent(new GameWinEvent(p1, this, 0.5));
			p1.playSound(p1.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
			p2.playSound(p2.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		}
		this.end();
	}
	
	public void win(boolean p1_win, boolean end_of_game) {
		if (p2 != null) {
			Player winner = p1_win ? p1 : p2;
			String wname = winner.getName();
			String wmsg = plugin.getConfigString("connect4.win-msg", "&6&m----------------------------------------[newline]&e&l" + wname + "&e won the Connect 4 game![newline]&6&m----------------------------------------")
					.replaceAll("\\Q%winner%\\E", wname).replaceAll("\\Q%player%\\E", wname);
			p1.sendMessage(plugin.sp(p1, wmsg));
			p2.sendMessage(plugin.sp(p2, wmsg));
			
			if (end_of_game) {
				this.setConsoleCommand(this.getConsoleCommand().replaceAll("\\Q%winner%\\E", wname).replaceAll("\\Q%player%\\E", wname).replaceAll("\\Q%loser%\\E", p1_win ? p2.getName() : p1.getName()));
				int games_won = plugin.getHighScoreRaw(winner.getUniqueId(), getGameType());
				addScore(winner, games_won <= 0 ? 1 : games_won+1);
			}
						
			Bukkit.getPluginManager().callEvent(new GameWinEvent(p1_win ? p1 : p2, this, 1));
			p1.playSound(p1.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
			p2.playSound(p2.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		}
		this.end();
	}
	
	@EventHandler
	public void onQuit(PlayerQuitLobbyGameEvent e) {
		if (!canStart()) return;
		
		if (e.getPlayer().getUniqueId().equals(p1.getUniqueId())) win(false, false);
		else if (p2 != null && e.getPlayer().getUniqueId().equals(p2.getUniqueId())) win(true, false);
	}
	
	@EventHandler
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == arena.getID()) {
			HandlerList.unregisterAll(this);
			if (plugin.getConfig().getBoolean("connect4.reset-on-end")) {
				new BukkitRunnable() {
					@Override
					public void run() {
						if (getArena().getHostingGame() == null) {
							reset();
						}
					}
				}.runTaskLater(plugin, 120l);
			}
		}
	}
	
	public void reset() {
		
		this.clearArmorStands();
	}
}
