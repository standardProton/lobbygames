package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.GameWinEvent;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Snake extends Game implements Listener {
	
	public Snake(LobbyGames plugin, Arena arena, Player player) {
		super(plugin, GameType.SNAKE, arena, player);
		if (!this.canStart() || arena.getGameType() != GameType.SNAKE) return;
		p = this.getPlayer1();
		
		headstart = new CoordinatePair(-1, 1);
		head = new CoordinatePair(headstart.getX(), headstart.getY());
		
		Block standing = arena.getSpawn1().getBlock().getRelative(BlockFace.DOWN);
		if (standing.getType() == Material.AIR) standing.setType(Material.BARRIER);
		
		setActive(true);
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
		
		String defaultmsg = "§3§m----------------------------------------\n" +
							"§b§lSnake: §bEat apples to grow larger, but don't run into the walls or yourself! Use the W, A, S, and D keys to move!\n" +
							"§3§m----------------------------------------";
		String startmsg = getPlugin().getConfigString(p, "snake.start-msg", defaultmsg);
		if (startmsg.length() > 0) p.sendMessage(startmsg);
				
		float[] flatyaw = {-90, -180, 90, 0}; float yaw;
		float[] wallyaw = {-180, -90, 0, 90};
		if (getArena().isVerticalLayout()) yaw = wallyaw[getArena().getCoordinateRotation()];
		else yaw = flatyaw[getArena().getCoordinateRotation()];
		spawn = getArena().getSpawn1().clone();
		spawn.setYaw(yaw);
		p.teleport(spawn);
		preparePlayer(p);
		
		start(p, plugin);
	}
	
	private Player p;
	private int score = 0, fat = 0, direction = 0;
	private List<CoordinatePair> pixels = new ArrayList<CoordinatePair>();
	//private List<Integer> futuremoves = new ArrayList<Integer>();
	private CoordinatePair head, headstart, apple; //head=(-3, 1)
	private boolean keylistening = true;
	private Location spawn;
	
	
	private void start(Player p, LobbyGames plugin) {
		
		reset(false);
		
		keylistening = true;
		for (int i = 2; i >= 0; i--) {
			CoordinatePair c = new CoordinatePair(headstart.getX() - i, headstart.getY());
			pixels.add(c);
			getPixel(c).getBlock().setType(GameUtils.whiteWool());
		}
		
		createApple();
		
		p.setWalkSpeed(0.15f);
		
		new BukkitRunnable() {
			@Override
			public void run() {
				if (!isActive()) this.cancel();
				shiftFrame();
			}
		}.runTaskTimer(plugin, 0, 3l);
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (!canStart() || e.getPlayer().getUniqueId() != p.getUniqueId() || !isActive()) return;
		
		double k = 0.005;
		if (e.getTo().getX() - e.getFrom().getX() > k) turn(4);
		else if (e.getTo().getX() - e.getFrom().getX() < -k) turn(2);
		else if (e.getTo().getZ() - e.getFrom().getZ() > k) turn(1);
		else if (e.getTo().getZ() - e.getFrom().getZ() < -k) turn(3);
		
		p.teleport(spawn);
	}
	
	@Override
	public String getScore(Player p) {
		return "" + score;
	}
	
	public void createApple() {
		CoordinatePair a = super.randomPixel();
		
		for (CoordinatePair c : pixels) {
			if (c.getX() == a.getX() && c.getY() == a.getY()) {
				createApple();
				return;
			}
		}

		apple = a;
		Block b = getWorld().getBlockAt(getPixel(a));
		GameUtils.setWool(b, (byte) 14);
	}
	
	public void shiftFrame() { //move snake in direction
		/*if (futuremoves.size() > 0) {
			turn(futuremoves.get(0));
			futuremoves.remove(0);
		}*/
		
		if (direction == 0) return;
		int x_add = 0;
		int y_add = 0;

		if (direction == 1) x_add = 1; //right
		else if (direction == 2) y_add = -1; //down
		else if (direction == 3) x_add = -1; //left
		else if (direction == 4) y_add = 1; //up

		head.setX(head.getX() + x_add);
		head.setY(head.getY() + y_add);
				
		if (!this.getArena().isInBounds(getPixel(head.getX(), head.getY()))) die(true);
		
		for (CoordinatePair c : pixels) {
			if (head.equals(c)) {
				die(false);
				return;
			}
		}
		
		boolean a = false;
		if (head.getX() == apple.getX() && head.getY() == apple.getY()) {
			fat += 2;
			score++;
			a = true;
		}

		if (!isActive()) return;
		
		Block b = getWorld().getBlockAt(getPixel(head.getX(), head.getY()));
		Material whitewool = GameUtils.whiteWool();
		if (b.getType() == Material.AIR || b.getType().toString().endsWith("WOOL")) {
			GameUtils.setWool(b, 0);
			pixels.add(new CoordinatePair(head.getX(), head.getY()));
		}

		if (fat == 0) {
			CoordinatePair last = pixels.get(0);
			Block b2 = getWorld().getBlockAt(getPixel(last.getX(), last.getY()));
			if (b2.getType() == whitewool) b2.setType(Material.AIR);
			pixels.remove(0);
		} else fat--;
		
		if (a) createApple();
		
		keylistening = true;
	}
	
	public void turn(int t) { //input range 1-4, dir 1 = right relative to player
				
		//calculate the relative direction player moved in based on arena coordinate system
		if (!this.getArena().isVerticalLayout()) t++;
		else if (this.getArena().getCoordinateRotation() % 2 == 1) t -= 1; 
		t = (t + this.getArena().getCoordinateRotation() + 1) % 4;
		
		if (direction == 0 && t == 2) return;
				
		if (t == 0) moveRight();
		else if (t == 1) moveDown();
		else if (t == 2) moveLeft();
		else if (t == 3) moveUp();
	}
	
	public void moveRight() { //1
		if (!(direction == 3 || direction == 1) && keylistening){
			//direction = ((5 - this.getArena().getCoordinateRotation()) % 4) + 1;
			direction = 1;
			keylistening = false;
		}
	}
	
	public void moveDown() { //2
		if (!(direction == 4 || direction == 2) && keylistening) {
			//direction = ((5 - this.getArena().getCoordinateRotation()) % 4) + 1;
			direction = 2;
			keylistening = false;
		}
	}

	public void moveLeft() { //3
		if (!(direction == 3 || direction == 1) && keylistening) {
			//direction = ((6 - this.getArena().getCoordinateRotation()) % 4) + 1;
			direction = 3;
			keylistening = false;
		}
	}
	
	public void moveUp() { //4
		if (!(direction == 4 || direction == 2) && keylistening) {
			//direction = ((7 - this.getArena().getCoordinateRotation()) % 4) + 1;
			direction = 4;
			keylistening = false;
		}
	}
	
	public void die(boolean wall) {
		if (!isActive()) return;
		setActive(false);
		
		//futuremoves.clear();
		direction = 0;
		Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, score));
		
		addScore(p, score);
		this.setConsoleCommandPlayer(p.getName()).setConsoleCommandScore("" + score);
		
		super.end();
		
		p.playSound(p.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		
		String defaultmsg = "\n§3§m----------------------------------------\n" +
							(wall ? "§c§lYou hit a wall!" : "§c§lYou ran into yourself!") + "\n" +
							"§bScore: §f" + score + " Apple" + ((score == 1) ? "" : "s") + "\n" +
							"§3§m----------------------------------------";
		String configmsg = getPlugin().getConfigString("snake.end-msg", defaultmsg);
		if (configmsg.length() > 0) {
			configmsg = configmsg.replaceAll("\\Q%score%\\E", "" + score).replaceAll("\\Q(s)\\E", ((score == 1) ? "" : "s"));
			if (configmsg.contains("%reason%"))
				configmsg = configmsg.replaceAll("%reason%", wall ? 
						getPlugin().getConfig().getString("snake.hit-wall-msg").replaceAll("&", "§") : 
						getPlugin().getConfig().getString("snake.ran-into-self-msg").replaceAll("&", "§"));
			p.sendMessage(getPlugin().sp(p, configmsg));
		}
		
		p.setWalkSpeed(0.2f);
		
		reset(true);
				
	}
	
	/*@EventHandler
	public void onRestart(GameRestartEvent e) {
		if (!canStart() || (!isActive() && e.getPlayer().getUniqueId().equals(p.getUniqueId()))) {
			score = 0; fat = 0; direction = 0; pixels.clear(); pixels.clear();
			head = new CoordinatePair(headstart.getX(), headstart.getY());
			preparePlayer(p, true);
			
			start(p, getPlugin());
		}
	}*/
	
	@EventHandler 
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == this.getArena().getID()) {
			HandlerList.unregisterAll(this);
			reset(true);
		}
	}
	
	public void reset(boolean new_snake) {
		
		if (LobbyGames.SERVER_VERSION <= 12) GameUtils.fill(this.getArena(), Material.AIR, (byte) 0, true, Material.valueOf("WOOL"), (byte) 15, false);
		else GameUtils.fill(this.getArena(), Material.AIR, (byte) 0, true, Material.BLACK_WOOL, (byte) 0, false);

		if (new_snake) {
			setActive(false);
			new BukkitRunnable() {
				public void run() {
					for (int i = 0; i < 3; i++) getWorld().getBlockAt(getPixel(headstart.getX() - i, headstart.getY())).setType(GameUtils.whiteWool());
				}
			}.runTaskLater(this.getPlugin(), 20l);
		}
	}
	
		

}
