package me.c7dev.lobbygames.games;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class TicTacToe extends Game implements Listener {
	
	enum State{EMPTY, X, O};
	
	private LobbyGames plugin;
	private Player p1, p2;
	private Random random = new Random();
	private boolean p1_x, p1_turn;
	private State[][] spaces = new State[3][3];
	private int move_count = 0;
	private String your_turn, opp_turn;
	
	
	public TicTacToe(LobbyGames plugin, Arena arena, Player p) {
		super(plugin, GameType.TICTACTOE, arena, p);
		if (!this.canStart() || arena.getGameType() != GameType.TICTACTOE) return;
		this.plugin = plugin;
		p1 = p;
		p1_x = random.nextBoolean();
		p1_turn = random.nextBoolean();
		your_turn = plugin.getConfigString("your-turn-msg", "§aYour Turn");
		opp_turn = plugin.getConfigString("opponent-turn-msg", "§7Opponent's Turn");
		
		for (int i = 0; i < 3; i++) {
			spaces[i][0] = State.EMPTY;
			spaces[i][1] = State.EMPTY;
			spaces[i][2] = State.EMPTY;
		}
		
		if (arena.getSpawn1() != null) p.teleport(arena.getSpawn1());
		preparePlayer(p);
				
		p.getInventory().setItem(3, GameUtils.createWool(1, 14, plugin.getConfigString("tictactoe.join-side-title", "§bSwitch to §c§lX§b's").replaceAll("\\Q%side%\\E", "§c§lX")));
		p.getInventory().setItem(5, GameUtils.createWool(1, 0, plugin.getConfigString("tictactoe.join-side-title", "§bSwitch to §f§lO§b's").replaceAll("\\Q%side%\\E", "§f§lO")));
		p.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
				
		reset();
	}
	
	public void checkWin(int x, int y, State state) {
		if (state == State.EMPTY) return;
		
		Vector pv = new Vector(0.5, 0.5, 0.5); //y=0.2
		int vert = 1; //getArena().isVerticalLayout() ? 1 : -1;
		
		//horizontal
		for (int i = 0; i < 3; i++) {
			if (spaces[x][i] != state) break;
			if (i == 2) {
				win((spaces[x][i] == State.X) == p1_x, true);
				return;
			}
		}
		
		//vertical
		for(int i = 0; i < 3; i++) {
			if (spaces[i][y] != state) break;
			if (i == 2) {
				win((spaces[i][y] == State.X) == p1_x, true);
				return;
			}
		}
		
		//diagonal
		if (x == y) {
			for (int i = 0; i < 3; i++) {
				if (spaces[i][i] != state) break;
				if (i == 2) {
					win((spaces[i][i] == State.X) == p1_x, true);
					return;
				}
			}
		}
		
		//diagonal 2
		if (x + y == 2) {
			for (int i = 0; i < 3; i++) {
				if (spaces[2 - i][i] != state) break;
				if (i == 2) {
					win((spaces[2 - i][i] == State.X) == p1_x, true);
					return;
				}
			}
		}
		
		if (move_count >= 9) {
			draw();
			return;
		}
	}
	
	@EventHandler
	public void onEntityInteract(PlayerInteractAtEntityEvent e) {
		if (getPlayers().contains(e.getPlayer().getUniqueId()) && e.getPlayer().getUniqueId().equals((p1_turn || p2 == null) ? p1.getUniqueId() : p2.getUniqueId())) {
			Block b = e.getRightClicked().getLocation().add(e.getClickedPosition()).add(getArena().getGenericOffset().multiply(-1)).getBlock();
			place(b);
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
				if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
					if (e.getPlayer().getUniqueId().equals((p1_turn || p2 == null) ? p1.getUniqueId() : p2.getUniqueId())) {
						e.setCancelled(true);
						place(e.getClickedBlock());
					} else {
						String msg = plugin.getConfigString("not-your-turn-msg", "");
						if (msg.length() > 0) getPlugin().sendActionbar(e.getPlayer(), msg, false);
					}
				}
			} else {
				if (hand != null) {
					if (hand.getType().toString().contains("WOOL")) {
						p1_x = hand.getData().getData() != (byte) 0;
						e.getPlayer().sendMessage(plugin.getConfigString(e.getPlayer(), "tictactoe.now-playing-as", "§bYou will play as %side%§b's!").replaceAll("\\Q%side%\\E", p1_x ? "§c§lX" : "§f§lO"));
					}
				}
			}
		}
	}
	
	public void place(Block block) { //when player places an X or O
		Location clicked = block.getLocation().add(getArena().getGenericOffset());
		if (getArena().isInBounds(clicked)) {
			CoordinatePair click_coords = this.getCoords(clicked);
			int clickx = click_coords.getX() + 1; int clicky = click_coords.getY() + 1;
			if (clickx < 0 || clicky < 0 || clickx > 2 || clicky > 2) return;

			if (spaces[clickx][clicky] == State.EMPTY) {

				p1_turn = !p1_turn;

				boolean set_x = (!p1_turn && p1_x) || (p1_turn && !p1_x);
				spaces[clickx][clicky] = set_x ? State.X : State.O;

				if (LobbyGames.SERVER_VERSION >= 13) block.setType(Material.valueOf((set_x ? "RED" : "WHITE") + "_CONCRETE"));
				else GameUtils.setWool(block, set_x ? 14 : 0);

				final ArmorStand a = super.getWorld().spawn(clicked.clone().add(getArena().getArmorStandOffset().subtract(getArena().getGenericOffset())), ArmorStand.class);
				a.setVisible(false);
				a.setGravity(false);
				a.setCustomName(set_x ? "§c§lX" : "§f§lO");
				a.setCustomNameVisible(true);

				move_count++;

				checkWin(clickx, clicky, set_x ? State.X : State.O);
			}
		}
	}
	
	@EventHandler
	public void onJoin(PlayerJoinLobbyGameEvent e) {
		if (!canStart() || e.getGame().getGameType() != GameType.TICTACTOE || e.getGame().getArena().getID() != this.getArena().getID()) return;
		if (getArena().getSpawn1() != null) e.getPlayer().teleport(this.getArena().getSpawn1());
		preparePlayer(e.getPlayer());
		if (getPlayers().size() == 2) { //start
			setActive(true);
			p2 = e.getPlayer();
			p2.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
						
			String defaultmsg = "§3§m----------------------------------------\n" +
					"§b§lTic Tac Toe: §bGet three X's or three O's in a row to win!\n" +
					"§3§m----------------------------------------";
			String startmsg = getPlugin().getConfigString("tictactoe.start-msg", defaultmsg);
			if (startmsg.length() > 0) {
				p1.sendMessage(plugin.sp(p1, startmsg));
				p2.sendMessage(plugin.sp(p2, startmsg));
			}
			
			reset();
			
			String side_msg = plugin.getConfigString("tictactoe.side-msg", "\n§bYou are playing as %side%§b's!");
			String x_msg = side_msg.replaceAll("\\Q%side%\\E", "§c§lX");
			String o_msg = side_msg.replaceAll("\\Q%side%\\E", "§f§lO");
			p1.sendMessage(plugin.sp(p1, (p1_x ? x_msg : o_msg) + " §f" + (p1_turn ? your_turn : opp_turn)));
			p2.sendMessage(plugin.sp(p2, (p1_x ? o_msg : x_msg) + " §f" + (p1_turn ? opp_turn : your_turn)));
			ItemStack air = new ItemStack(Material.AIR);
			p1.getInventory().setItem(3, air); p1.getInventory().setItem(5, air);
			p2.getInventory().setItem(3, air); p2.getInventory().setItem(5, air);
			//TODO center wool block
			if ((!p1.getWorld().getName().equals(getArena().getLocation1().getWorld().getName()) || p1.getLocation().distance(getArena().getCenterPixel()) > 10) && getArena().getSpawn1() != null) p1.teleport(getArena().getSpawn1());
			
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
			String wmsg = plugin.getConfigString("tictactoe.draw-msg", "&2&m----------------------------------------[newline]&a&lThis game is a draw![newline]&2&m----------------------------------------");
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
			String wmsg = plugin.getConfigString("tictactoe.win-msg", "&6&m----------------------------------------[newline]&e&l" + wname + "&e won the Tic Tac Toe game![newline]&6&m----------------------------------------")
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
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == super.getArena().getID()) {
			HandlerList.unregisterAll(this);
			new BukkitRunnable() {
				@Override
				public void run() {
					if (getArena().getHostingGame() == null) {
						reset();
					}
				}
			}.runTaskLater(plugin, 100l);
		}
	}
	
	public void reset() {
		GameUtils.fill(this.getArena(), Material.AIR, (byte) 0, LobbyGames.SERVER_VERSION <= 12 ? Material.valueOf("WOOD") : Material.DARK_OAK_PLANKS, (byte) 0);
		
		this.clearArmorStands();
	}

}
