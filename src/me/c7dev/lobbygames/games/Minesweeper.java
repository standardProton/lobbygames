package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;

import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.GameWinEvent;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Minesweeper extends Game implements Listener {
	
	List<CoordinatePair> mines = new ArrayList<CoordinatePair>();
	List<CoordinatePair> opened = new ArrayList<CoordinatePair>();
	List<CoordinatePair> flagged = new ArrayList<CoordinatePair>();
	HashMap<CoordinatePair,ArmorStand[]> flagged_armorstand = new HashMap<CoordinatePair,ArmorStand[]>();
	boolean can_fly = false;
	
	int time = 0;
	long clickdelay = System.currentTimeMillis();
	
	boolean flagdecor = true;
	
	int starting_mines;
	
	LobbyGames plugin;
	public Minesweeper(LobbyGames plugin, Arena arena, Player player) {
		super(plugin, GameType.MINESWEEPER, arena, player);
		if (!super.canStart() || arena.getGameType() != GameType.MINESWEEPER) return;
		Bukkit.getPluginManager().registerEvents(this, plugin);
		this.plugin = plugin;
		//player.setGameMode(GameMode.SURVIVAL);
		
		double diff = plugin.getConfig().getDouble("minesweeper.flag-landmine-distribution");
		if (diff != 0) {
			if (diff < 0.1) diff = 0.1;
			if (diff > 0.3) diff = 0.3;
		} else diff = (1.0/6);
		
		
		starting_mines = (int) (super.getArena().getWidth() * super.getArena().getHeight() * diff);
		
		String defmsg = "§3§m----------------------------------------\n§b§lMinesweeper: §bThere are " + starting_mines + " landmines randomly spread through the grid! Right-click to open a cell, and use the flag tool to mark a landmine. The numbers represent how many mines a cell is touching!\n§3§m----------------------------------------";
		String startmsg = plugin.getConfigString(player, "minesweeper.start-msg", defmsg);
		if (startmsg.length() > 0) getPlayer1().sendMessage(startmsg.replaceAll("\\Q%starting_mines%\\E", "" + starting_mines));
				
		flagdecor = plugin.getConfig().getBoolean("minesweeper.flag-armor-stands-enabled");
		
		start();
		
	}
	
	public void start() {
		setActive(true);
				
		reset();
		//for (int i = 0; i < starting_mines; i++) mines.add(createMine());
		//for (CoordinatePair m : mines) getPixel(m.getX(), m.getY()).getBlock().setType(Material.TNT);
		
		can_fly = getPlayer1().getAllowFlight();
		if (plugin.getConfig().getBoolean("minesweeper.player-can-fly")) getPlayer1().setAllowFlight(true);
		if (super.getArena().getSpawn1() != null) getPlayer1().teleport(super.getArena().getSpawn1());
		preparePlayer(getPlayer1());
		getPlayer1().getInventory().setItem(0, GameUtils.createItem(Material.BLAZE_ROD, 1, (byte) 0, "§c§lSet Flag"));
		getPlayer1().getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, getPlugin().getConfigString("quit-item-title", "§c§lQuit")));
		
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!isActive()) this.cancel();
				Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
					public void run() {
						updateActionBar();
					}
				});
				time++;
			}
		}.runTaskTimerAsynchronously(plugin, 0, 20l);
	}
	
	public CoordinatePair createMine(CoordinatePair avoid) {
		CoordinatePair a = super.randomPixel();
		
		for (CoordinatePair m : mines) {
			if (m.getX() == a.getX() && m.getY() == a.getY()) return createMine(avoid);
		}
		
		if (Math.abs(avoid.getX() - a.getX()) <= 1 && Math.abs(avoid.getY() - a.getY()) <= 1) return createMine(avoid);
		
		return a;
	}
	
	public byte getByte(int n) {
		if (n == 0) return (byte) 0;
		else if (n == 1) return (byte) 11;
		else if (n == 2) return (byte) 13;
		else if (n == 3) return (byte) 14;
		else if (n == 4) return (byte) 10;
		else if (n == 5) return (byte) 12;
		else if (n >= 6) return (byte) 15;
		
		return (byte) 0;
	}
	
	public void addFlag(CoordinatePair c) { //mark a cell has likely having a landmine 
		
		if (flagged.size() >= starting_mines + 10 || mines.size() == 0) return;
		
		Location loc = getPixel(c);
				
		if (!this.getArena().isInBounds(loc)) return;
		
		
						
		flagged.add(c);
		//Location loc = getPixel(c.getX(), c.getY());
		loc.getBlock().setType(Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "STEP" : "SMOOTH_STONE_SLAB"));
		loc.add(0, -1, 0);
		
		checkWin();
		updateActionBar();
		
		if (LobbyGames.SERVER_VERSION > 8) getPlayer1().playSound(loc, Sound.BLOCK_GRASS_PLACE, 1f, 1f);
		
		if (!flagdecor) return;
		
		ArmorStand stick = getWorld().spawn(loc.clone().add(0.85, 0.6, 1), ArmorStand.class);
		stick.setVisible(false);
		stick.setArms(true);
		stick.setGravity(false);
		stick.setRightArmPose(new EulerAngle(Math.toRadians(81), 0f, 0f));
		stick.setItemInHand(new ItemStack(Material.STICK));


		Location locf = loc.clone(); locf.setYaw(90);

		locf.add(0.85, 1.51, 0.717); //1.8: 1.02, 1.28, 0.717 (z+=0.02 if z > 0)
		
		ItemStack banner = new ItemStack(Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "BANNER" : "RED_BANNER"), (byte) 1);
		BannerMeta bm = ((BannerMeta) banner.getItemMeta());
		bm.setBaseColor(DyeColor.RED);
		banner.setItemMeta(bm);

		ArmorStand flag = getWorld().spawn(locf, ArmorStand.class);
		flag.setVisible(false);
		flag.setArms(true);
		flag.setGravity(false);
		flag.setRightArmPose(new EulerAngle(0, 0, 0)); //1.8: 340deg
		flag.setSmall(true);
		flag.setItemInHand(banner);
		
		flagged_armorstand.put(c, new ArmorStand[]{stick, flag});
						
	}
	
	public void removeFlag(CoordinatePair c) {
		getPixel(c.getX(), c.getY()).getBlock().setType(Material.QUARTZ_BLOCK);
		for (CoordinatePair f : flagged.toArray(new CoordinatePair[flagged.size()])) if (f.getX() == c.getX() && f.getY() == c.getY()) flagged.remove(f);
		updateActionBar();

		if (flagdecor) {
			for (Entry<CoordinatePair,ArmorStand[]> as : flagged_armorstand.entrySet()) {
				if (as.getKey().getX() == c.getX() && as.getKey().getY() == c.getY()) {
					for (ArmorStand e : as.getValue()) e.remove();
					flagged_armorstand.remove(as.getKey());				
					break;
				}
			}
		}
	}
		
	@EventHandler
	public void onInteractBlock(PlayerInteractEvent e) {
		if (!canStart()) return;
		//if (e.getHand() != EquipmentSlot.HAND) return; //|| LobbyGames.clickdelay.contains(e.getPlayer().getUniqueId())) return;
		if (!e.getPlayer().getUniqueId().equals(getPlayer1().getUniqueId()) || !isActive()) return;
		if (e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) {
			interactBlock(e.getPlayer(), e.getClickedBlock());
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onArmorStand(PlayerArmorStandManipulateEvent e) {	
		if (!canStart()) return;
		if (e.getPlayer().getUniqueId().equals(getPlayer1().getUniqueId())) {
			e.setCancelled(true);
			ItemStack hand = GameUtils.getHandItem(e.getPlayer());
			if (hand != null && hand.getType() == Material.ARROW) {
				quitConfirmation(e.getPlayer());
				return;
			}
			//if (LobbyGames.SERVER_VERSION < 12) return;
			for (Block b : e.getPlayer().getLineOfSight(null, 4)) {
				
				if (b.getType() == Material.QUARTZ_BLOCK) {
					interactBlock(e.getPlayer(), b);
					return;
				}
				else if (b.getType() == Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "STEP" : "SMOOTH_STONE_SLAB")) {
					if (hand != null && hand.getType() == Material.BLAZE_ROD) {
						interactBlock(e.getPlayer(), b);
						return;
					}
				}
			}
		}
	}
	
	public void updateActionBar() {
		
		Player p = getPlayer1();
		if (p == null || !getPlayer1().isOnline()) return;
		
		String t = "";
		if ((int) (time / 60) > 0) t = (int) (time / 60) + "m ";
		t = t + (time % 60) + "s";
		int m = starting_mines - flagged.size();
		if (m < 0) m = 0;
		
		String actionbar = plugin.getConfigString("minesweeper.action-bar", "§4§l" + m + "§c mines remaining! §c§l" + t)
				.replaceAll("\\Q%remaining_mines%\\E", "" + m)
				.replaceAll("\\Q%time%\\E", t);
		if (actionbar.length() == 0) return;
				
		getPlugin().sendActionbar(getPlayer1(), actionbar, true);
		
	}

	public void interactBlock(Player p, Block clickedblock) {
		if (clickdelay > System.currentTimeMillis()) return;
		clickdelay = System.currentTimeMillis() + 100;
		
		Location loc = clickedblock.getLocation();
		Location center = getPixel(0, 0);

		if (super.getArena().isInBounds(loc)) {
			
			//don't need to worry about if already clicked, unclicked blocks are y+0
			CoordinatePair clicked = new CoordinatePair((int) (loc.getBlockX() - center.getBlockX()), (int) (loc.getBlockZ() - center.getBlockZ()));

			ItemStack hand = GameUtils.getHandItem(p);
			if (hand != null && hand.getType() == Material.BLAZE_ROD) {
				if (clickedblock.getType() == Material.QUARTZ_BLOCK) addFlag(clicked);
				else removeFlag(clicked);
			} else if (clickedblock.getType() == Material.QUARTZ_BLOCK) {
				if (open(clicked, true)) {
					die();
					if (LobbyGames.SERVER_VERSION >= 12) getPlayer1().spawnParticle(Particle.EXPLOSION_LARGE, getPixel(clicked.getX(), clicked.getY()).add(0, 3, 0), 10);
				}
			}
		}
	}
	
	public int getN(CoordinatePair c) { //neighboring landmines count
		int n = 0;
		for (CoordinatePair m : mines) {
			int xd = Math.abs(m.getX() - c.getX()); int yd = Math.abs(m.getY() - c.getY());
			if ((xd <= 1 && yd <= 1) && !(m.getX() == c.getX() && m.getY() == c.getY())) n++;
		}
		return n;
	}
	
	public List<CoordinatePair> getNeighbors(CoordinatePair c){ //list of neighboring cells
		List<CoordinatePair> z = new ArrayList<CoordinatePair>();
		for (int dx = -1; dx < 2; dx++) { //delta x
			for (int dy = -1; dy < 2; dy++) {
				if (!(dx == 0 && dy == 0)) {
					CoordinatePair c2 = new CoordinatePair(c.getX() + dx, c.getY() + dy);
					if (super.getArena().isInBounds(getPixel(c2))) z.add(c2);
					//if (Math.abs(x) <= x_size && Math.abs(y) <= y_size) z.add(new CoordinatePair(x, y));
				}
			}
		}
		return z;
	}
	
	public String getArmorName(int n) {
		if (n >= 6) return "§f§l" + n;
		switch (n){
		case 1: return "§1§l1";
		case 2: return "§2§l2";
		case 3: return "§c§l3";
		case 4: return "§d§l4";
		case 5: return "§6§l5";
		default: return "";
		}
	}
	
	public boolean open(CoordinatePair c, boolean cw) { //selecting a block, also called recursively when neighbors=0
		if (!isActive()) return false;
		
		if (mines.size() == 0) {
			for (int i = 0; i < starting_mines; i++) mines.add(createMine(c)); 
		}
		
		for (CoordinatePair o : opened) if (o.getX() == c.getX() && o.getY() == c.getY()) return false;
		for (CoordinatePair m : mines) if (m.getX() == c.getX() && m.getY() == c.getY()) return true; 
		
		int n = getN(c);
		for (CoordinatePair fl : flagged) {
			if (c.getX() == fl.getX() && c.getY() == fl.getY()) {
				if (n == 0) {for (CoordinatePair c2 : getNeighbors(c)) open(c2, false);}
				return false;
			}
		}
		
		Location l = getPixel(c.getX(), c.getY());
		Block wool_block = l.clone().add(0, -1, 0).getBlock();
				
		GameUtils.setWool(wool_block, getByte(n));
		
		l.getBlock().setType(Material.AIR);
		
		opened.add(c);
		
		if (n == 0) {for (CoordinatePair c2 : getNeighbors(c)) open(c2, false);}
		else {
			final ArmorStand a = super.getWorld().spawn(l.add(0.5, -1.75, 0.5), ArmorStand.class);
			a.setGravity(false);
			a.setCustomName(getArmorName(n));
			a.setCustomNameVisible(true);
			a.setVisible(false);
		}
		
		if (cw) checkWin();
		
		return false;
	}
	
	@Override
	public String getScore(Player p) {
		return getPlayTime();
	}
	
	public void die() {
		if (!isActive()) return;
		setActive(false);
		
		Player p = getPlayer1();
		p.playSound(p.getLocation(), GameUtils.getSound(8, "EXPLODE","ENTITY_GENERIC_EXPLODE"), 1f, 1f);
		if (getArena().getSpawn1() != null) p.teleport(getArena().getSpawn1());

		int min = time/60; int s = time % 60; int mr = starting_mines - flagged.size();
		String time = s + " Second" + (s==1? "" : "s");
		if (min > 0) time = min + " Minute" + (min==1?"":"s") + ", " + time;
		
		String msg = plugin.getConfigString("minesweeper.end-msg");
		if (msg == null) msg = "\n§3§m----------------------------------------\n§c§lBOOM! You clicked a landmine!\n§bScore: §f" + time + " (" + mr + " Mine" + (mr == 1 ? "" : "s") + " Remaining)\n§3§m----------------------------------------";
		else msg = msg.replaceAll("\\Q%minutes%\\E", "" + min).replaceAll("\\Q%seconds%\\E", "" + s).replaceAll("\\Q%remaining_mines%\\E", "" + mr);
		if (msg.length() > 0) p.sendMessage(plugin.sp(p, msg));
				
		for (CoordinatePair m : mines) {
			Location loc = getPixel(m.getX(), m.getY());
			loc.getBlock().setType(Material.REDSTONE_BLOCK);
			loc.add(0, 1, 0);
			if (!loc.getBlock().getType().equals(Material.valueOf(LobbyGames.SERVER_VERSION <= 12 ? "STEP" : "SMOOTH_STONE_SLAB"))) loc.getBlock().setType(Material.AIR);
		}
		for (CoordinatePair f : flagged) {
			Location loc = getPixel(f.getX(), f.getY());
			if (loc.getBlock().getType() != Material.REDSTONE_BLOCK) loc.add(0, 1, 0).getBlock().setType(Material.AIR);
		}
				
		this.end();
	}
	
	public void checkWin() { //check if opened all the cells except landmines
		int xs = (this.getArena().getWidth() / 2) + 1;
		int ys = (this.getArena().getHeight() / 2) + 1;
		if (opened.size() >= (this.getArena().getWidth() * this.getArena().getHeight()) - starting_mines) {
			for (int x = -xs; x < xs; x++) {
				for (int y = -ys; y < ys; y++) {
					if (getPixel(x, y).getBlock().getType() == Material.QUARTZ_BLOCK) {
						addFlag(new CoordinatePair(x, y));
					}
				}
			}
			win();
		}
		
	}
	
	public void win() {
		if (!isActive()) return;
		setActive(false);
		
		Player p = getPlayer1();
		int min = time/60; int s = time % 60;
		if (getArena().getSpawn1() != null) p.teleport(getArena().getSpawn1());
		String defmsg = "\n§2§m----------------------------------------\n§a§lYou win!\n§aScore: §f" + min + " Minute" + (min == 1 ? "" : "s") + ", " + s + " Second" + (s == 1 ? "" : "s") + "\n§2§m----------------------------------------";
		String msg = plugin.getConfigString("minesweeper.win-msg", defmsg).replaceAll("\\Q%minutes%\\E", "" + min).replaceAll("\\Q%seconds%\\E", "" + s).replaceAll("\\Q%remaining_mines%\\E", "0");
		if (msg.length() > 0) p.sendMessage(plugin.sp(p, msg));
				
		Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, time));
		this.setConsoleCommandPlayer(p.getName()).setConsoleCommandScore("" + this.getPlayTime());
		
		this.end();
		p.playSound(p.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		addScore(p, -time, min +"m " + s + "s");
	}
	
	@EventHandler
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == super.getArena().getID()) {
			if (getPlayer1().getAllowFlight()) getPlayer1().setAllowFlight(can_fly);
			if (plugin.getConfig().getBoolean("minesweeper.reset-on-end")) reset();
			HandlerList.unregisterAll(this);
		}
	}
	
	public void reset() {
		
		if (LobbyGames.SERVER_VERSION <= 12) GameUtils.fill(this.getArena(), Material.QUARTZ_BLOCK, (byte) 0, Material.valueOf("WOOL"), (byte) 8);
		else GameUtils.fill(this.getArena(), Material.QUARTZ_BLOCK, (byte) 0, Material.LIGHT_GRAY_WOOL, (byte) 0);
		
		this.clearArmorStands();
	}
	

}
