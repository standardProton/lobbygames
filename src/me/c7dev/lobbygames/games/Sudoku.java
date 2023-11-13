package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.List;
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
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;
import me.c7dev.lobbygames.util.SudokuGenerator;

public class Sudoku extends Game implements Listener {
			
	private LobbyGames plugin;
	private Player p;
	private int[][] spaces = new int[9][9], spaces_init;
	private ArmorStand[][] labels = new ArmorStand[9][9];
	private List<CoordinatePair> shown_spaces = new ArrayList<>();
	private double starttime;
	private Material mat;
	private final int[] colors = {11, 9, 3, 13, 5, 4, 1, 14, 2};
	private final String[] color_str = {"§1", "§3", "§b", "§2", "§a", "§e", "§6", "§c", "§d"};
	private String action_bar;
	private SudokuGenerator grader;
	private boolean can_fly = false, restart_conf = false;

	public CoordinatePair randomSpace(Random r) {
		CoordinatePair c = new CoordinatePair(r.nextInt(9), r.nextInt(9));
		if (spaces[c.getX()][c.getY()] == 0) return c;
		else return randomSpace(r);
	}

	public Sudoku(LobbyGames plugin, Arena arena, Player p) {
		super(plugin, GameType.SUDOKU, arena, p);
		if (!this.canStart() || arena.getGameType() != GameType.SUDOKU) return;
		this.plugin = plugin;
		this.p = p;

		setActive(true);
		this.starttime = System.currentTimeMillis() / 1000;
		
		mat = LobbyGames.SERVER_VERSION >= 13 ? Material.WHITE_CONCRETE : Material.valueOf("WOOL");

		Bukkit.getPluginManager().registerEvents(this, plugin);

		String defaultmsg = "§3§m----------------------------------------\n" +
				"§b§lSudoku: §bSet the numbers in the puzzle so every row, column, and 3x3 box has one of each digit from 1 to 9!\n" +
				"§3§m----------------------------------------";
		String startmsg = getPlugin().getConfigString(p, "sudoku.start-msg", defaultmsg);
		if (startmsg.length() > 0) p.sendMessage(startmsg);
		
		action_bar = plugin.getConfigString("sudoku.action-bar", "§aTime: §f%time%");

		if (arena.getSpawn1() != null) p.teleport(arena.getSpawn1());
		preparePlayer(p);
		
		setActive(true);
		
		giveItems();
		

		if (plugin.getConfig().getBoolean("sudoku.particles-enabled") && LobbyGames.SERVER_VERSION >= 12) {
			boolean rot03v = arena.isVerticalLayout() && (arena.getCoordinateRotation() == 0 || arena.getCoordinateRotation() == 3);
			Vector vadd = new Vector(rot03v ? 1 : 0, arena.isVerticalLayout() ? 0 : 1, rot03v ? 1 : 0);

			final Location a1 = this.getPixel(-1, 5).add(vadd);
			final Location a2 = this.getPixel(-1, -4).add(vadd);
			final Location b1 = this.getPixel(2, 5).add(vadd);
			final Location b2 = this.getPixel(2, -4).add(vadd);
			final Location c1 = this.getPixel(5, 2).add(vadd);
			final Location c2 = this.getPixel(-4, 2).add(vadd);
			final Location d1 = this.getPixel(5, -1).add(vadd);
			final Location d2 = this.getPixel(-4, -1).add(vadd);
									
			new BukkitRunnable() {
				@Override
				public void run() {
					if (!canStart() || !isActive()) this.cancel();
					GameUtils.particleLine(a1, a2, 9d);
					GameUtils.particleLine(b1, b2, 9d);
					GameUtils.particleLine(c1, c2, 9d);
					GameUtils.particleLine(d1, d2, 9d);
				}
			}.runTaskTimer(plugin, 0, 5l);
		}
		
		if (action_bar.length() > 0) {
			new BukkitRunnable() {
				@Override
				public void run() {
					if (!isActive() || !canStart()) {
						this.cancel();
						return;
					}
					int seconds = (int) ((System.currentTimeMillis() / 1000) - starttime);
					String time = (seconds >= 60 ? (seconds / 60) + "m " : "") + (seconds % 60) + "s";
					if (!(grader != null && grader.isFinished() && grader.unfilledCount() == 0)) {
						getPlugin().sendActionbar(p, action_bar.replaceAll("\\Q%time%\\E", time), true);
					}
				}
			}.runTaskTimer(plugin, 0, 20l);
		}

		start();
	}
	
	public void giveItems() {
		if (LobbyGames.SERVER_VERSION > 12) {
			for(int i = 0; i < 9; i++) {
				p.getInventory().setItem(i, GameUtils.createItem(Material.valueOf(GameUtils.COLOR_NAMES[colors[i]] + "CONCRETE"), 1, (byte) 0, color_str[i] + "§l" + (i+1)));
			}
		} else {
			for (int i = 0; i < 9; i++) {
				p.getInventory().setItem(i, GameUtils.createItem(mat,1, (byte) colors[i], color_str[i] + "§l" + (i+1)));
			}
		}
	}

	public void start() {
		reset();
		
		setActive(true);
		
		can_fly = p.getAllowFlight();
		if (plugin.getConfig().getBoolean("sudoku.player-can-fly")) p.setAllowFlight(true);
		
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				spaces[i][j] = 0;
			}
		}
		
		grader = new SudokuGenerator();
		grader.loadSudoku(spaces);
		
		int number_prefilled = plugin.getConfig().getInt("sudoku.prefilled-count");
		if (number_prefilled < 3) number_prefilled = 3;
		else if (number_prefilled > 70) number_prefilled = 70;
		
		try {
			spaces_init = (new SudokuGenerator()).generateSudoku();
		} catch (Exception ex) {
			this.end();
			Bukkit.getLogger().severe("Could not create a new Sudoku puzzle!");
			return;
		}
		
		Random r = new Random();
		for (int i = 0; i < number_prefilled; i++) {
			CoordinatePair c = this.randomSpace(r);
			setSpace(c, spaces_init[c.getX()][c.getY()], true);
			shown_spaces.add(c);
		}
	}
		
	public void setSpace(CoordinatePair c, int set, boolean orig) {
		setSpace(c.getX(), c.getY(), set, orig);
	}
	public void setSpace(int x, int y, int set, boolean orig) { //set a number into a cell
		if (set == 0) return;
		if (x > 8 || y > 8 || set > 9) return;
		
		for (CoordinatePair c : shown_spaces) {
			if (c.getX() == x && c.getY() == y) return;
		}
				
		if (spaces[x][y] == set) set = 0;
		spaces[x][y] = set;
				
		Location bpixel = getPixel(x-4, y-4);
		
		//version-compatible set concrete
		if (LobbyGames.SERVER_VERSION >= 13) bpixel.getBlock().setType(Material.valueOf(GameUtils.COLOR_NAMES[set == 0 ? 0 : colors[set-1]] + "CONCRETE"));
		else GameUtils.setWool(bpixel.getBlock(), (byte) (set == 0 ? 0 : colors[set-1]));
		
		//bpixel.getBlock().setType(mat);
		//bpixel.getBlock().setData(set == 0 ? (byte) 0 : (byte) colors[set-1]);
		
		if (labels[x][y] != null) {
			labels[x][y].remove();
			labels[x][y] = null;
		}
		if (set > 0) {
			ArmorStand a = super.getWorld().spawn(bpixel.clone().add(getArena().getArmorStandOffset()), ArmorStand.class);
			a.setVisible(false);
			a.setGravity(false);
			a.setCustomName((orig ? "§f§l" : color_str[set-1]) + (set));
			a.setCustomNameVisible(true);
			labels[x][y] = a;
		}

		if (grader.gradeSudoku()) win();
		else if (grader.unfilledCount() == 0) {
			String invalidsolution = plugin.getConfigString("sudoku.invalid-solution", "§cInvalid Solution!");
			if (invalidsolution.length() > 0) getPlugin().sendActionbar(p, invalidsolution, false);
		}

	}
	
	public void placeBlock(Location loc, int num) {
		if (!this.getArena().isInBounds(loc)) return;
		
		CoordinatePair click = getCoords(loc);
				
		setSpace(click.getX()+4, click.getY()+4, num, false);
	}
	
	@EventHandler
	public void onPlace(PlayerInteractEvent e) {
		if (!canStart() || !isActive() || !e.getPlayer().getUniqueId().equals(p.getUniqueId())) return;
		if (!restart_conf && !(e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
		e.setCancelled(true);
		ItemStack hand = LobbyGames.SERVER_VERSION <= 12 ? e.getPlayer().getItemInHand() : e.getPlayer().getInventory().getItemInMainHand();
		if (hand == null || (LobbyGames.SERVER_VERSION <= 12 ? hand.getType() != mat : !hand.getType().toString().endsWith("CONCRETE") && !restart_conf)) return;
		
		if (restart_conf) {
			if (hand.getData().getData() == (byte) 5) doRestart();
			restart_conf = false;
			giveItems();
			return;
		}
		
		if (e.getClickedBlock().getType().toString().endsWith("CONCRETE") || e.getClickedBlock().getType() == mat) {
			for (int i = 0; i < 9; i++) {
				if (colors[i] == (int) hand.getData().getData()) {
					placeBlock(e.getClickedBlock().getLocation(), i+1);
					break;
				}
			}
		}
		
	}
	
	@EventHandler
	public void onEntityInteract(PlayerInteractAtEntityEvent e) {
		if (restart_conf) return;
		if (!isActive() || !canStart() || !e.getPlayer().getUniqueId().equals(p.getUniqueId())) return;
		ItemStack hand = GameUtils.getHandItem(e.getPlayer());
		Block b = e.getRightClicked().getLocation().add(e.getClickedPosition()).add(getArena().getGenericOffset().multiply(-1)).getBlock();
		if (hand == null || (LobbyGames.SERVER_VERSION <= 12 ? hand.getType() != mat : !hand.getType().toString().endsWith("CONCRETE"))) return;
		if (b.getType().toString().endsWith("CONCRETE") || b.getType() == mat) {
			for (int i = 0; i < 9; i++) {
				if (colors[i] == (int) hand.getData().getData()) {
					placeBlock(b.getLocation(), i+1);
					break;
				}
			}
		}
	}
	
	@Override
	public void restart() {
		if (restart_conf) return;
		restart_conf = true;
		p.getInventory().clear();
		p.getInventory().setItem(3, GameUtils.createWool(1, 5, plugin.getConfigString("yes-text", "§a§lYes")));
		p.getInventory().setItem(5, GameUtils.createWool(1, 14, plugin.getConfigString("no-text", "§c§lNo")));
		
	}
	
	public void doRestart() {
		restart_conf = false;
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				spaces[i][j] = spaces_init[i][j];
				setSpace(i, j, spaces[i][j], true);
			}
		}
	}
	
	public void win() {
		int time = (int) ((System.currentTimeMillis()/1000) - starttime);
		String timestr = (time / 60) +"m " + (time % 60) + "s";
		String winmsg = "§2§m----------------------------------------\n" +
				"§a§lYou finished the sudoku! §aTime: " + timestr + "\n" +
				"§2§m----------------------------------------";
		winmsg = plugin.getConfigString("sudoku.win-msg", winmsg).replaceAll("\\Q%time%\\E", timestr);
		if (winmsg.length() > 0) p.sendMessage(plugin.sp(p, winmsg));
		
		this.setConsoleCommandPlayer(p.getName()).setConsoleCommandScore(time + "");
		
		addScore(p, -time, timestr);
		Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, time));
		this.end();
		p.playSound(p.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
	}
	
	@Override
	public String getScore(Player p) {
		return getPlayTime();
	}

	public void reset() {
		GameUtils.fill(this.getArena(), mat, (byte) 0, null, (byte) 0);

		clearArmorStands();
	}
	
	@EventHandler
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == this.getArena().getID()) {
			p.setAllowFlight(can_fly);
			if (plugin.getConfig().getBoolean("sudoku.reset-on-end")) reset();
			HandlerList.unregisterAll(this);
		}
	}

}
