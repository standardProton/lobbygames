package me.c7dev.lobbygames.games;

import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.GameWinEvent;
import me.c7dev.lobbygames.api.events.PlayerQuitLobbyGameEvent;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;
import me.c7dev.lobbygames.util.T048Tile;

public class T048 extends Game implements Listener {
	
	private Player p;
	private T048Tile[][] spaces;
	Location spawn;
	int width, height, w2, h2, future_move = -1, move_block = -1, score = 0;
	boolean made_2048 = false;
	
	private ItemStack[] tileblocks = {
			new ItemStack(GameUtils.whiteWool(), 1),
			new ItemStack(Material.CLAY, 1),
			GameUtils.createWool(1, 1, "orange"),
			new ItemStack(LobbyGames.SERVER_VERSION > 12 ? Material.RED_SAND : Material.valueOf("RED_SANDSTONE"), 1),
			(LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.ACACIA_PLANKS, 1) : new ItemStack(Material.valueOf("WOOD"), 1, (byte) 4)),
			GameUtils.createWool(1, 14, "red"),
			new ItemStack(Material.SAND, 1),
			//new ItemStack(LobbyGames.SERVER_VERSION > 12 ? Material.END_STONE : Material.valueOf("ENDER_STONE")),
			(LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.BIRCH_PLANKS, 1) : new ItemStack(Material.valueOf("WOOD"), 1, (byte) 2)),
			new ItemStack(LobbyGames.SERVER_VERSION > 12 ? Material.OAK_PLANKS : Material.valueOf("WOOD"), 1),
			GameUtils.createWool(1, 4, "yellow"),
			new ItemStack(Material.GOLD_BLOCK, 1),
			new ItemStack(Material.NETHERRACK, 1)
	};
	
	public T048(LobbyGames plugin, Arena arena, Player player) {
		super(plugin, GameType.T048, arena, player);
		if (!this.canStart() || arena.getGameType() != GameType.T048) return;
		p = this.getPlayer1();
				
		Block standing = arena.getSpawn1().getBlock().getRelative(BlockFace.DOWN);
		if (standing.getType() == Material.AIR) standing.setType(Material.BARRIER);
		
		setActive(true);
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
		
		String defaultmsg = "§3§m----------------------------------------\n" +
							"§b§l2048: §bUse the W, A, S, and D keys to merge tiles and get to the 2048 tile without filling the board!\n" +
							"§3§m----------------------------------------";
		String msg = getPlugin().getConfigString(p, "2048.start-msg", defaultmsg);
		if (msg.length() > 0) p.sendMessage(msg);
				
		float[] flatyaw = {-90, -180, 90, 0}; float yaw;
		float[] wallyaw = {-180, -90, 0, 90};
		if (getArena().isVerticalLayout()) yaw = wallyaw[getArena().getCoordinateRotation()];
		else yaw = flatyaw[getArena().getCoordinateRotation()];
		spawn = getArena().getSpawn1().clone();
		spawn.setYaw(yaw);
		p.teleport(spawn);
		preparePlayer(p);
		width = arena.getWidth();
		height = arena.getHeight();
		w2 = (int) Math.ceil(width/2.0);
		if (!arena.isInBounds(getPixel(-w2, 0))) w2 -= 1;
		h2 = (int) Math.ceil(height/2.0);
		if (!arena.isInBounds(getPixel(0, -h2))) h2 -= 1;
		
		start(p, plugin);
	}
	
	public boolean isFilled() {
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (spaces[i][j] == null) return false;
			}
		}
		return true;
	}
	
	public CoordinatePair randomCoords(Random r) {
		CoordinatePair c = new CoordinatePair(r.nextInt(width), r.nextInt(height));
		if (!getArena().isInBounds(getPixel(c.getX() - w2, c.getY() - h2))) return randomCoords(r);
		
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				if (c.getX() == i && c.getY() == j && spaces[i][j] != null) return randomCoords(r);
			}
		}
		return c;
	}
	
	public void start(Player p, LobbyGames plugin) {

		reset();
		spaces = new T048Tile[width][height];
				
		p.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, getPlugin().getConfigString("quit-item-title", "§c§lQuit")));
		
		spawnTile();
		spawnTile();
		
		String action_bar = plugin.getConfigString("2048.action-bar", "§aScore: §f%score");
		if (action_bar.length() > 0) {
			new BukkitRunnable() {
				@Override
				public void run() {
					if (!canStart() || !isActive()) {
						this.cancel();
						return;
					}
					getPlugin().sendActionbar(p, action_bar.replaceAll("\\Q%score%\\E", "" + score), false);
				}
			}.runTaskTimer(plugin, 0, 20l);
		}
	}
	
	public boolean checkStuck(){ //return true if no cells have a neighbor that is empty or can be merged
		for (int i = 0; i < width; i++) {
			for (int j = 0; j < height; j++) {
				T048Tile curr = spaces[i][j];
				if (curr == null) return false;
				if (isInBounds(i+1, j)) {
					T048Tile neighbor = spaces[i+1][j];
					if (neighbor == null || neighbor.getNum() == curr.getNum()) return false;
				}
				if (isInBounds(i-1, j)) {
					T048Tile neighbor = spaces[i-1][j];
					if(neighbor == null || neighbor.getNum() == curr.getNum()) return false;
				}
				if (isInBounds(i, j+1)) {
					T048Tile neighbor = spaces[i][j+1];
					if (neighbor == null || neighbor.getNum() == curr.getNum()) return false;
				}
				if (isInBounds(i, j-1)) {
					T048Tile neighbor = spaces[i][j-1];
					if (neighbor == null || neighbor.getNum() == curr.getNum()) return false;
				}
			}
		}
		return true;
	}
	
	public void spawnTile() {
		if (isFilled()) {
			die();
			return;
		}
		
		Random r = new Random();
		int two = r.nextInt(7);
		CoordinatePair c = randomCoords(r);
		CoordinatePair c2 = new CoordinatePair(c.getX() - w2, c.getY() - h2);
				
		spaces[c.getX()][c.getY()] = new T048Tile(two == 1 ? 1 : 0, c2, this, tileblocks);
		
		if (checkStuck()) die();
		
	}
	
	public void moveTile(T048Tile tile, int dx, int dy, T048Tile incr_tile) { //play animation on tile and update on spaces array
		CoordinatePair c = tile.getCoords();
		spaces[c.getX() + w2][c.getY() + h2] = null;
		if (incr_tile == null) {
			c.setX(c.getX() + dx);
			c.setY(c.getY() + dy);
			spaces[c.getX() + w2][c.getY() + h2] = tile;
		}
		tile.teleport(getPixel(c), incr_tile);
	}
	
	public void move(int t) { //move in direction t following rules of 2048
		if (t < 0) return;
		if (move_block != -1) {
			if (move_block != t) future_move = t;
			return;
		}
		move_block = t;
		future_move = -1;
		//if (!this.getArena().isVerticalLayout()) t--;
		//else if (this.getArena().getCoordinateRotation() % 2 == 1) t -= 2; //not supported yet
		t--;
		
		t = (t + this.getArena().getCoordinateRotation()) % 4;
		
		CoordinatePair traverse;
		CoordinatePair nextRow = t % 2 == 0 ? new CoordinatePair(0, 1) : new CoordinatePair(1, 0);
		CoordinatePair start = new CoordinatePair(0, 0);
		switch(t % 4) {
		case 0:  //right
			traverse = new CoordinatePair(1, 0);
			break;
		case 1: //down
			traverse = new CoordinatePair(0, -1);
			break;
		case 2: //left
			traverse = new CoordinatePair(-1, 0);
			break;
		default: //up
			traverse = new CoordinatePair(0, 1);
		}
						
		if (executeMove(traverse, start.clone(), nextRow, true)) { //returns true if has moved anything
			new BukkitRunnable() {
				@Override
				public void run() { //slight delay to spawn next tile
					spawnTile();
					new BukkitRunnable() {
						public void run() {
							move_block = -1;
							move(future_move);
						}
					}.runTaskLater(getPlugin(), 3l);
				}
			}.runTaskLater(getPlugin(), 4l);
		} 
		
	}
	
	public boolean executeMove(CoordinatePair traverse, CoordinatePair rowStart, CoordinatePair nextRow, boolean merge) { //move or merge the tiles according to direction
		boolean moved = false;
		while(isInBounds(rowStart)) { 
			CoordinatePair p = rowStart.clone();
			
			while (isInBounds(p)) { //vector addition to traverse through each row and cell
				CoordinatePair next = p.clone().add(traverse);
				T048Tile curr_tile = spaces[p.getX()][p.getY()];
				if (curr_tile != null && isInBounds(next)) {
					T048Tile next_tile = spaces[next.getX()][next.getY()];
					if (next_tile == null) { //empty space
						moved = true;
						moveTile(curr_tile, traverse.getX(), traverse.getY(), null);
					}
					else if (next_tile.getNum() == curr_tile.getNum()) { //can merge, same powers of 2
						CoordinatePair next2 = next.clone().add(traverse);
						boolean triple_merge = false;
						if (isInBounds(next2)) {
							T048Tile next2_tile = spaces[next2.getX()][next2.getY()];
							if (next2_tile != null && next2_tile.getNum() == curr_tile.getNum()) {
								triple_merge = true;
								CoordinatePair next3 = next2.clone().add(traverse);
								if (isInBounds(next3)) {
									T048Tile next3_tile = spaces[next3.getX()][next3.getY()];
									if (next3_tile != null && next3_tile.getNum() == curr_tile.getNum()) triple_merge = false;
								}
							}
						}
						if (!triple_merge) { //indicate which tiles need to be merged in the case that there are 3 of the same number in row
							moved = true;
							score += Math.pow(2, curr_tile.getNum() + 2);
							moveTile(curr_tile, traverse.getX(), traverse.getY(), next_tile);
							spaces[p.getX()][p.getY()] = null;
							if (curr_tile.getNum() == 9 && !made_2048) { //when player makes first 2048 tile
								getPlayer1().playSound(getPlayer1().getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
								made_2048 = true;
							}
						}
					}
				}
				////
				p = next;
			}
			
			if (moved) {
				//traverse backwards and pull all tiles to nearest spot
				CoordinatePair back_traverse = traverse.clone().multiply(-1);
				p.add(back_traverse);
				int move = 0;
				while (isInBounds(p)) {
					T048Tile curr_tile = spaces[p.getX()][p.getY()];
					if (curr_tile == null) move++;
					else if (move > 0) {
						moveTile(curr_tile, move*traverse.getX(), move*traverse.getY(), null);
					}
					p.add(back_traverse);
				}
			}
			
			rowStart.add(nextRow);
		}
		return moved;
	}
	
	public void die() {
		String defaultmsg = "§3§m----------------------------------------\n" +
				"§b§lRan out of moves! §bScore:§f %score% Points\n" +
				"§3§m----------------------------------------";
		String msg = getPlugin().getConfigString(p, "2048.end-msg", defaultmsg).replaceAll("\\Q%score%\\E", "" + score);
		if (msg.length() > 0) p.sendMessage(msg);
		Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, score));
		this.setConsoleCommandPlayer(p.getName()).setConsoleCommandScore("" + score);
		this.end();
	}
	
	@Override
	public String getScore(Player p) {
		return "" + score;
	}
	
	
	public void reset() {
		/*if (LobbyGames.SERVER_VERSION <= 12) GameUtils.fill(this.getArena(), Material.AIR, (byte) 0, Material.valueOf("WOOL"), (byte) 7);
		else GameUtils.fill(this.getArena(), Material.AIR, (byte) 0, Material.GRAY_WOOL, (byte) 0);*/
		
		for (Entity e : getWorld().getNearbyEntities(super.getArena().getCenterPixel(), this.getArena().getWidth(), 2, this.getArena().getHeight())) {
			if (e instanceof ArmorStand) e.remove();
		}
	}
	
	public boolean isInBounds(CoordinatePair c) {return isInBounds(c.getX(), c.getY());}
	public boolean isInBounds(int x, int y) {
		if (x < 0 || y < 0) return false;
		if (x >= width || y >= height) return false;
		return true;
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (canStart() && e.getPlayer().getUniqueId().equals(p.getUniqueId())) {
			ItemStack hand = GameUtils.getHandItem(p);
			if (hand != null && hand.getType() == Material.ARROW) {
				quitConfirmation(e.getPlayer());
			}
		}
	}
	
	@EventHandler
	public void onQuit(PlayerQuitLobbyGameEvent e) {
		if (e.getPlayer().getUniqueId().equals(this.getPlayer1().getUniqueId()) && canStart()) {
			this.setConsoleCommandPlayer(p.getName()).setConsoleCommandScore("" + score);
			this.end();
			Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, score));
		}
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (!canStart() || !e.getPlayer().getUniqueId().equals(p.getUniqueId()) || !isActive()) return;
		
		p.teleport(spawn);
		
		double k = 0.002;
		if (e.getTo().getX() - e.getFrom().getX() > k) move(4);
		else if (e.getTo().getX() - e.getFrom().getX() < -k) move(2);
		else if (e.getTo().getZ() - e.getFrom().getZ() > k) move(1);
		else if (e.getTo().getZ() - e.getFrom().getZ() < -k) move(3);
		
	}
	
	@EventHandler 
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == this.getArena().getID()) {
			
			addScore(p, score);
			
			if (getPlugin().getConfig().getBoolean("2048.reset-on-end")) {
				new BukkitRunnable() {
					@Override
					public void run() {
						if (getArena().getHostingGame() == null) {
							reset();
						}
					}
				}.runTaskLater(getPlugin(), 120l);
			}
			
			HandlerList.unregisterAll(this);
		}
	}

}
