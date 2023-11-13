package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.GameWinEvent;
import me.c7dev.lobbygames.api.events.PlayerJoinLobbyGameEvent;
import me.c7dev.lobbygames.api.events.PlayerQuitLobbyGameEvent;
import me.c7dev.lobbygames.util.BilliardBall;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Pool extends Game implements Listener {
	
	private LobbyGames plugin;
	private Player p1, p2;
	private Random random = new Random();
	private boolean p1_turn = true, in_play = false, sides_assigned = false, p1_solid = false, cueball_inhand = false, first_draw = true, 
			collision = false, xplane, mw4, same_color = false, practice = false, can_practice = true, started_instance = false, ball_moved = false;
	private String your_turn, opp_turn, prac_mode_str, exit_prac_mode;
	private String[] words = {"Ball", "Cue Ball", "Pocketed", "Wool", "Terracotta"};
	private List<Integer> sunk_balls = new ArrayList<>();
	private int rail_hits = 0, solids_sc = 5, stripes_sc = 1;
	private long last_interact = 0;
	
	private ItemStack cue, book;
	private Location[] holes;
	private BilliardBall[] balls;
	private ArmorStand[] holes_as = new ArmorStand[6];
	
	public static final int BALL = 0,CUE_BALL=1,POCKETED=2,WOOL=3,TERRACOTTA=4;
	
	public Pool(LobbyGames plugin, Arena arena, Player p) {
		super(plugin, GameType.POOL, arena, p);
		if (!this.canStart() || arena.getGameType() != GameType.POOL) return;
		this.plugin = plugin;
		p1 = p;
		p1_turn = random.nextBoolean();
		your_turn = plugin.getConfigString("your-turn-msg", "§aYour Turn");
		opp_turn = plugin.getConfigString("opponent-turn-msg", "§7Opponent's Turn");
		cue = GameUtils.createItem(Material.STICK, 1, (byte) 0, plugin.getConfigString("pool.cue-item-title", "§3Cue §7(Click the white cue ball)"));
		book = GameUtils.createItem(Material.BOOK, 1, (byte) 0, plugin.getConfigString("pool.open-gui-item-title", "§bOpen Pool Menu"));
		same_color = plugin.getConfig().getBoolean("pool.same-color");
		solids_sc = plugin.getConfig().getInt("pool.solids-color");
		stripes_sc = plugin.getConfig().getInt("pool.stripes-color");
		can_practice = plugin.getConfig().getBoolean("pool.practice-mode-enabled");
		prac_mode_str = plugin.getConfigString(p, "pool.practice-mode-msg", "§cYou are in Practice Mode!");
		exit_prac_mode = plugin.getConfigString(p, "pool.exit-practice-mode", "§cExit Practice Mode");

		if (same_color) {
			if (stripes_sc > 14) stripes_sc = 14;
			if (stripes_sc < 1) stripes_sc = 1;
			if (solids_sc > 14) solids_sc = 14;
			if (solids_sc < 1) solids_sc = 1;
			if (solids_sc == stripes_sc) {
				Bukkit.getLogger().warning("Solids and Stripes cannot be the same color!");
				same_color = false;
			}
		}
		
		xplane = arena.getWidth() > arena.getHeight();
		mw4 = Math.max(arena.getHeight(), arena.getWidth()) == 4;
				
		giveItems(p);
		
		Bukkit.getPluginManager().registerEvents(this, plugin);
		
		String[] cfwords = plugin.getConfigString("pool.translatable-words", "Ball, Cue Ball, Pocketed, Wool, Terracotta").split(",");
		for (int i = 0; i < words.length; i++) {
			if (cfwords.length > i && cfwords[i].length() > 0) words[i] = cfwords[i].trim();
		}
				
		reset();

		if (can_practice) {
			new BukkitRunnable() {
				@Override
				public void run() {
					if (p2 != null || !canStart()) {
						this.cancel();
						return;
					}
					if (practice) plugin.sendActionbar(p1, prac_mode_str, true);
				}
			}.runTaskTimer(plugin, 0, 20l);
		}
		
		
	}
	
	public boolean isSameColor() {return same_color;}
	public int getSolidsSC() {return solids_sc;}
	public int getStripesSC() {return stripes_sc;}
	public String[] getWords() {return words;}
	
	public void giveItems(Player p) {
		if (getArena().getSpawn1() != null && (!p.getWorld().getName().equals(getArena().getSpawn1().getWorld().getName()) || p.getLocation().distance(getArena().getSpawn1()) >= 10)) p.teleport(getArena().getSpawn1());
		preparePlayer(p, GameMode.ADVENTURE);
		p.getInventory().setItem(0, cue);
		p.getInventory().setItem(7, book);
		p.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, plugin.getConfigString("quit-item-title", "§c§lQuit")));
	}
	
	public int randomBallNumber(List<Integer> used_numbers) { //used in reset()
		int r = random.nextInt(14) + 2;
		if (r == 8 || r == 15 || used_numbers.contains(r)) return randomBallNumber(used_numbers);
		used_numbers.add(r);
		return r;
	}
	
	public void startPractice() {
		if (!canStart() || !can_practice) return;
		
		practice = true;
		plugin.sendActionbar(p1, prac_mode_str, false);
		
		in_play = false;
		p1.getInventory().setItem(6, GameUtils.createItem(Material.FEATHER, 1, (byte) 0, exit_prac_mode));//GameUtils.createWool(1, 14, exit_prac_mode));
		
		start();
		
	}
	
	public void quitPractice() {
		practice = false;
		p1.getInventory().setItem(1, new ItemStack(Material.AIR));
		p1.getInventory().setItem(6, new ItemStack(Material.AIR));
		reset();
	}
	
	public boolean tickBall(BilliardBall ball, Location l1, Location l2) {
		boolean vp = false;
		if (ball != null && (ball.getVelocity().getX() != 0 || ball.getVelocity().getZ() != 0)) {
			ball_moved = true;
			vp = ball.tick();

			Location loc = ball.getLocation();

			//from Arena#isInBoundsXZ
			if (!ball.isSunk()) { //check if ball is above hole
				if (l1.getX() < l2.getX()) {
					if ((ball.getVelocity().getX() < 0 && loc.getX() - BilliardBall.BALL_RADIUS < l1.getX()) || 
							(ball.getVelocity().getX() > 0 && loc.getX() - 1 + BilliardBall.BALL_RADIUS > l2.getX()))
						if (!checkInHole(loc, ball)) {
							ball.setVelocity(ball.getVelocity().setX(BilliardBall.BOUNCE*ball.getVelocity().getX()));
							if (ball.getNumber() > 0) rail_hits++;
						}
				} else {
					if ((ball.getVelocity().getX() > 0 && loc.getX() + BilliardBall.BALL_RADIUS > l1.getX() + 1) || 
							(ball.getVelocity().getX() < 0 && loc.getX() - BilliardBall.BALL_RADIUS < l2.getX())) 
						if (!checkInHole(loc, ball)) {
							ball.setVelocity(ball.getVelocity().setX(BilliardBall.BOUNCE*ball.getVelocity().getX()));
							if (ball.getNumber() > 0) rail_hits++;
						}
				}

				if (l1.getZ() < l2.getZ()) {
					if ((ball.getVelocity().getZ() < 0 && loc.getZ() - BilliardBall.BALL_RADIUS < l1.getZ())
							|| (ball.getVelocity().getZ() > 0 && loc.getZ() + BilliardBall.BALL_RADIUS > l2.getZ() + 1))
						if (!checkInHole(loc, ball)) {
							ball.setVelocity(ball.getVelocity().setZ(BilliardBall.BOUNCE*ball.getVelocity().getZ()));
							if (ball.getNumber() > 0) rail_hits++;
						}
				} else {
					if ((ball.getVelocity().getZ() > 0 && loc.getZ() + BilliardBall.BALL_RADIUS > l1.getZ() + 1) || 
							(ball.getVelocity().getZ() < 0 && loc.getZ() - BilliardBall.BALL_RADIUS < l2.getZ()))
						if (!checkInHole(loc, ball)) {
							ball.setVelocity(ball.getVelocity().setZ(BilliardBall.BOUNCE*ball.getVelocity().getZ()));
							if (ball.getNumber() > 0) rail_hits++;
						}
				}
			}

			for (int j = 0; j < 16; j++) {
				BilliardBall cball = balls[j];
				if (cball != null && j != ball.getNumber()) {
					if (ball.collide(cball)) { 
						collision = true;
						vp = false;
					}
				}
			}
		}
		return vp;
	}
	
	public void start() {
		if (p2 == null && !practice) return;
		if (practice && !can_practice) {
			practice = false;
			return;
		}
		
		if (practice) {
			if (p2 != null) { //p2 joins
				quitPractice();
				setActive(true);
			}
		} else {
			setActive(true); //regular start
		}

		if (!practice) {
			String defaultmsg = "§3§m----------------------------------------\n" +
					"§b§lPool: §bHit the (white) cue ball to pocket other balls! Don't hit the (black) 8-ball until all of your designated balls have been pocketed.\n" +
					"§3§m----------------------------------------";
			String startmsg = getPlugin().getConfigString("pool.start-msg", defaultmsg);
			if (startmsg.length() > 0) {
				p1.sendMessage(plugin.sp(p1, startmsg));
				p2.sendMessage(plugin.sp(p2, startmsg));
			}
			if (LobbyGames.SERVER_VERSION < 12) {
				(p1_turn || p2 == null ? p1 : p2).sendMessage(your_turn);
				if (p2 != null) (p1_turn ? p2 : p1).sendMessage(opp_turn);
			}

			new BukkitRunnable() {
				@Override
				public void run() {
					if (!canStart()) {
						this.cancel();
						return;
					}
					if (p1 != null && p1.isOnline()) getPlugin().sendActionbar(p1, p1_turn ? your_turn : opp_turn, true);
					if (p2 != null && p2.isOnline()) getPlugin().sendActionbar(p2, p1_turn ? opp_turn : your_turn, true);
				}
			}.runTaskTimer(plugin, 0, 20l);
		}
		
		in_play = false;
		
		if (!started_instance) {
			started_instance = true;
			new BukkitRunnable() {
				@Override
				public void run() {
					if (!canStart()) {
						this.cancel();
						return;
					}
					if (!in_play) return;
					ball_moved = false;
					Location l1 = getArena().getLocation1();
					Location l2 = getArena().getLocation2();
					List<Integer> vp = new ArrayList<>();
					List<Integer> vp2 = new ArrayList<>();
					for (int i = 0; i < 16; i++) {
						BilliardBall ball = balls[i];
						if (tickBall(ball, l1, l2)) vp.add(i);
					}
										
					while (vp.size() > 0) {
						/*
						 * v_progress (velocity progress) checks if a ball in high velocity collides with any other balls in the frame, providing a
						 * more accurate solution to find the location of the ball right before the collision, resulting in correct direction for
						 * the force of impact.
						 */
						
						vp2.clear();
						
						for (int vpball : vp) {
							if (tickBall(balls[vpball], l1, l2)) vp2.add(vpball);
						}
						
						vp.clear();
						for (int i : vp2) vp.add(i);
					}
										
					for (int i = 0; i < 16; i++) {
						BilliardBall ball = balls[i];
						if (ball != null && !ball.isSunk()) {
							ball.shiftCollisionList();
						}
					}
					if (!ball_moved) endTurn();
				}
			}.runTaskTimer(plugin, 0, 1l);
		}
	}
	
	@EventHandler
	public void onJoin(PlayerJoinLobbyGameEvent e) {
		if (!canStart() || e.getGame().getGameType() != GameType.POOL || e.getGame().getArena().getID() != this.getArena().getID() || isActive()) return;
		//if (getArena().getSpawn1() != null) e.getPlayer().teleport(this.getArena().getSpawn1());
		if (getPlayers().size() == 2) {
			p2 = e.getPlayer();
			giveItems(e.getPlayer());
			start();
		}
	}
	
	public void endTurn() {
		in_play = false;
		boolean switch_turn = true;
		if ((!collision || sunk_balls.contains(0)) && (!first_draw || rail_hits >= 3)) {
			cueball_inhand = true;
			if (balls[0] != null) balls[0].sink();
		}
		if (first_draw) {
			switch_turn = false;
			if (sunk_balls.contains(8)) {
				String msg = plugin.getConfigString("pool.pocketed-8ball", "§cThe 8-ball was pocketed!");
				p1.sendMessage(plugin.sp(p1, msg));
				if (p2 != null) p2.sendMessage(plugin.sp(p2, msg));
				reset();
				first_draw = true;
				collision = false;
			}
			else if (rail_hits < 4) {
				reset();
				first_draw = true;
				collision = false;
				cueball_inhand = false;
			} 
			else if (sunk_balls.size() == 0) switch_turn = true;
		}
		if (sunk_balls.size() > 0) {
			String sunk_solid_str = "", sunk_striped_str = "";
			int sunk_solid = 0, sunk_striped = 0, count = 0;
			for (int i = 0; i < sunk_balls.size(); i++) {
				int number = sunk_balls.get(i);
				if (number > 0) {
					count++;
					if (number < 8) {
						sunk_solid++;
						sunk_solid_str += "#" + number + ", ";
					}
					else if (number > 8) {
						sunk_striped++;
						sunk_striped_str += "#" + number + ", ";
					}
				}
			}
			
			boolean sunk_8ball = sunk_balls.contains(8);

			if (!sunk_8ball) {			
				if (sunk_solid > 0 || sunk_striped > 0) {
					if (sunk_solid_str.length() > 0) {
						sunk_solid_str += "abc";
						sunk_solid_str = sunk_solid_str.replaceAll("\\Q, abc\\E", "");
					} else sunk_solid_str = "-";
					if (sunk_striped_str.length() > 0) {
						sunk_striped_str += "abc";
						sunk_striped_str = sunk_striped_str.replaceAll("\\Q, abc\\E", "");
					} else sunk_striped_str = "-";
					String msg1 = plugin.getConfigString("pool.pocketed-balls", "§3%name% pocketed %count% ball(s):\n§b  Wool: §f%wool_pocketed%\n§b  Terracotta: §f%terracotta_pocketed%");
					if (msg1.length() > 0 && !practice) {
						msg1 = msg1.replaceAll("\\Q%wool_pocketed%\\E", sunk_solid_str)
								.replaceAll("\\Q%terracotta_pocketed%\\E", sunk_striped_str)
								.replaceAll("\\Q%combined_list%\\E", sunk_solid_str + (sunk_solid_str.length() > 0 ? ", " : "") + sunk_striped_str)
								.replaceAll("\\Q%name%\\E", p1_turn || p2 == null ? p1.getName() : p2.getName()).replaceAll("\\Q%player%\\E", p1_turn || p2 == null ?p1.getName():p2.getName())
								.replaceAll("\\Q%count%\\E", "" + count).replaceAll("\\Q(s)\\E", count == 1 ? "":"s")
								.replaceAll("\\Q%wool_count%\\E", "" + sunk_solid).replaceAll("\\Q%terracotta_count%\\E", "" + sunk_striped);
						p1.sendMessage(plugin.sp(p1, msg1));
						if (p2 != null) p2.sendMessage(plugin.sp(p2, msg1));
					}
				}
			}

			if (!first_draw) {
				if (sunk_8ball) {
					String msg = plugin.getConfigString((p1_turn || p2 == null ? p1 : p2), "pool.pocketed-8ball", "§cThe 8-ball was pocketed!");
					p1.sendMessage(msg);
					if (p2 != null) p2.sendMessage(msg);
					cueball_inhand = false;		

					int solids_left = 7;
					int stripes_left = 7;
					for (int i = 1; i < 16; i++) {
						if (balls[i] == null || balls[i].isSunk()) {
							if (i < 8) solids_left--;
							else if (i > 8) stripes_left--;
						}
					}
					
					boolean cueball_pocketed = sunk_balls.contains(0);
					if (practice) {
						win(true, false);
						return;
					}
										
					if (sides_assigned) {
						
						if (solids_left > 0 && stripes_left > 0) win(!p1_turn, true);
						else if (p1_turn) {
							if ((p1_solid && solids_left == 0) || (!p1_solid && stripes_left == 0)) win(!cueball_pocketed, true); //p1 wins
							else win(false, true);
						}
						else {
							if ((p1_solid && stripes_left == 0) || (!p1_solid && solids_left == 0)) win(cueball_pocketed, true); //p2 wins
							else win(true, true);
						}
					} else {
						if (solids_left == 0 && stripes_left == 0) win(p1_turn, true);
						else win(!p1_turn, true);
					}
					
					return;
				} else {
					if (!sides_assigned) {
						switch_turn = false;
						if ((sunk_solid == 0 || sunk_striped == 0) && !sunk_balls.contains(0) && !practice) {
							p1_solid = (p1_turn && sunk_striped == 0) || (!p1_turn && sunk_solid == 0);
							sides_assigned = true;
							
							String msg = plugin.getConfigString("pool.side-designation", "\n§bYou need to pocket the §b§l%side%§b balls!");
							p1.sendMessage(plugin.sp(p1, msg.replaceAll("\\Q%side%\\E", p1_solid ? words[WOOL] : words[TERRACOTTA])));
							if (p2 != null) p2.sendMessage(plugin.sp(p2, msg.replaceAll("\\Q%side%\\E", p1_solid ? words[TERRACOTTA] : words[WOOL])));
						}
					} else {
						if (p1_turn) {
							if ((p1_solid && sunk_solid > 0) || (!p1_solid && sunk_striped > 0)) switch_turn = false;
						} else if ((p1_solid && sunk_striped > 0) || (!p1_solid && sunk_solid > 0)) switch_turn = false;
					}
				}
			}
		}

		if (!practice && (switch_turn || cueball_inhand)) {	
			p1_turn = !p1_turn;
			if (LobbyGames.SERVER_VERSION < 12) {
				Player tp = p1_turn || p2 == null ? p1 : p2;
				tp.sendMessage(plugin.sp(tp, your_turn));
			}
		}
		if (cueball_inhand) {
			(p1_turn || p2 == null ? p1 : p2).getInventory().setItem(1, GameUtils.createItem(Material.QUARTZ_BLOCK, 1, (byte) 0, plugin.getConfigString("pool.cueball-title", "§f§lCue Ball §7(Place anywhere on table)")));
			String hand_msg = plugin.getConfigString("pool.cueball-inhand", "§b%player% has the cue ball in hand!").replaceAll("\\Q%player%\\E", p1_turn || p2 == null ? p1.getName() : p2.getName());
			if (hand_msg.length() > 0 && !practice) {
				p1.sendMessage(plugin.sp(p1, hand_msg));
				if (p2 != null) p2.sendMessage(plugin.sp(p2, hand_msg));
			}
		}
		sunk_balls.clear();
		if (collision) first_draw = false;
		collision = false;
		rail_hits = 0;
		
	}
	
	public void openGUI(Player p) {
		if (!canStart()) return;
		Inventory i = Bukkit.createInventory(null, 27, plugin.getConfigString("pool.gui-title", "Pool Ball Status"));
		
		int[] colors = {4, 11, 14, 10, 1, 5, 12, 15};
		
		if (same_color) {
			int sunk_solids = 0; int sunk_stripes = 0;
			for (int j =1; j < balls.length; j++) {
				if (balls[j] == null || balls[j].isSunk()) {
					if (j < 8) sunk_solids++;
					else if (j > 8) sunk_stripes++;
				}
			}
			for (int j = 0; j < 7; j++) {
				i.setItem(j+(sides_assigned? 2:1), j < sunk_solids?
						GameUtils.createWool(1, 8, "§f§l" + (j+1) + " " + words[BALL] + " (" + words[WOOL] + ")", "§7" + words[POCKETED]) :
							GameUtils.createWool(1, solids_sc, "§f§l" + (j+1) + " " + words[BALL] + " (" + words[WOOL] + ")"));
			}
			for  (int j = 0; j < 7; j++) {
				i.setItem(j+(sides_assigned?11:10), j < sunk_stripes ? 
						GameUtils.createWool(1, 8, "§f§l" + (j+9) + " " + words[BALL] + " (" + words[TERRACOTTA] + ")", "§7" + words[POCKETED]) :
							GameUtils.createWool(1, stripes_sc, "§f§l" + (j+9) + " " + words[BALL] + " (" + words[TERRACOTTA] + ")"));
			}
		} else {
			for (int j = 0; j < 7; j++) {
				boolean sunk = balls[j+1] == null || balls[j+1].isSunk();
				i.setItem(j+(sides_assigned? 2:1), sunk?
						GameUtils.createWool(1, 8, "§f§l" + (j+1) + " " + words[BALL] + " (" + words[WOOL] + ")", "§7" + words[POCKETED]) :
							GameUtils.createWool(1, colors[j], "§f§l" + (j+1) + " " + words[BALL] + " (" + words[WOOL] + ")"));
			}
			for  (int j = 0; j < 7; j++) {
				boolean sunk = balls[j+9] == null || balls[j+9].isSunk();
				i.setItem(j+(sides_assigned?11:10), sunk ? 
						GameUtils.createWool(1, 8, "§f§l" + (j+9) + " " + words[BALL] + " (" + words[TERRACOTTA] + ")", "§7" + words[POCKETED]) :
							GameUtils.createClay(1, colors[j], "§f§l" + (j+9) + " " + words[BALL] + " (" + words[TERRACOTTA] + ")"));
			}
		}
		i.setItem(sides_assigned?22:21, GameUtils.createItem(Material.QUARTZ_BLOCK, 1, (byte) 0, "§f§l" + words[CUE_BALL], 
				plugin.getConfigString("pool.cueball-description", "§7Hit this with the cue").split("(\\n,\\Q[newline]\\E)")));
		i.setItem(sides_assigned?24:23, GameUtils.createWool(1, 15, "§f§l8 " + words[BALL], 
				plugin.getConfigString("pool.8ball-description", "§7Pocket this §conly§7 after pocketing\n§7all of your other designated balls!").split("\n")));

		if (sides_assigned) {
			ItemStack pane = LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.GRAY_STAINED_GLASS_PANE, 1) : new ItemStack(Material.valueOf("STAINED_GLASS_PANE"), 1, (byte) 7);
			ItemMeta panemeta = pane.getItemMeta(); panemeta.setDisplayName("§b ");
			pane.setItemMeta(panemeta);
			i.setItem(1, pane); i.setItem(10, pane); i.setItem(19, pane);
			ItemStack skull1 = LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.PLAYER_HEAD, 1) : new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (byte) 3);
			ItemStack skull2 = LobbyGames.SERVER_VERSION > 12 ? new ItemStack(Material.PLAYER_HEAD, 1) : new ItemStack(Material.valueOf("SKULL_ITEM"), 1, (byte) 3);
			SkullMeta meta1 = (SkullMeta) skull1.getItemMeta();
			SkullMeta meta2 = (SkullMeta) skull1.getItemMeta();
			meta1.setOwner(p1_solid || p2 == null ? p1.getName() : p2.getName());
			meta1.setDisplayName("§f§l" + (p1_solid || p2 == null ? p1.getName() : p2.getName()));
			meta2.setOwner(!p1_solid || p2 == null ? p1.getName() : p2.getName());
			meta2.setDisplayName("§f§l" + (!p1_solid || p2 == null ? p1.getName() : p2.getName()));
			skull1.setItemMeta(meta1);
			skull2.setItemMeta(meta2);
			i.setItem(0, skull1);
			i.setItem(9, skull2);
		}
		
		p.openInventory(i);
	}
	
	public boolean checkInHole(Location loc, BilliardBall ball) {
		double radius = 0.35;
		for (Location hole : holes) {
			if (Math.abs(loc.getX() - hole.getX()) <= radius && Math.abs(loc.getZ() - hole.getZ()) <= radius) {
				ball.sink();
				balls[ball.getNumber()] = null;
				sunk_balls.add(ball.getNumber());
			}
		}
		return false;
	}
	
	@Override
	public Location getPixel(int x, int y) {
		if (xplane) return super.getPixel(x, y).add((mw4 && getArena().getCoordinateRotation() <= 1 ? -1 : 0), 0, 0);
		else return super.getPixel(y, x).add(0, 0, (mw4 && getArena().getCoordinateRotation() >= 2 ? 1 : 0));
	}
	
	@Override
	public String getScore(Player p) {
		if (!isActive()) return "-";
		int solid_sunk = 7;
		int striped_sunk = 7;
		for (int i = 1; i < balls.length; i++) {
			if (balls[i] == null || balls[i].isSunk()) {
				if (i < 8) solid_sunk++;
				else if (i > 8) striped_sunk++;
			}
		}
		if (p.getUniqueId().equals(p1_solid || p2 == null ? p1.getUniqueId() : p2.getUniqueId())) return "" + solid_sunk;
		else return "" + striped_sunk;
	}
	
	public void interact(Player p) { //any left or right click
		ItemStack hand = GameUtils.getHandItem(p);
		if (hand == null && !first_draw) return;
		if (System.currentTimeMillis() - last_interact <= 100) return;
		last_interact = System.currentTimeMillis();
		
		if ((first_draw || cueball_inhand) && (hand == null || hand.getType() == Material.AIR || hand.getType() == Material.QUARTZ_BLOCK)) {
			/*if (p2 == null) {
				if (!practice) {
					if (can_practice) {
						startPractice();
					} else return;
				}
			}*/
			if (isActive() && !p.getUniqueId().equals(p1_turn ? p1.getUniqueId() : p2.getUniqueId())) return;
			if (in_play) return;
			
			Location loc = p.getLocation().add(0, p.isSneaking() ? 1.25:1.5, 0);
			Vector dir = p.getLocation().getDirection().normalize().multiply(0.25);
			int i = 0;
			while (loc.getBlock().getType() == Material.AIR || loc.getBlock().getType() == Material.BARRIER) {
				if (i >= 20) return;
				i++;
				loc.add(dir);
			}
			if (getArena().isInBoundsXZ(loc)) {
				if (first_draw) {
					CoordinatePair click = this.getCoords(loc);
					int rot = getArena().getCoordinateRotation();
					int k = (rot >= 2 ? 1 : -1) * (mw4 && ((rot <= 1 && xplane) || (rot >= 2 && !xplane)) ? 2 : 1);
					int l = xplane ? click.getX() : click.getY();
					if (rot <= 1) {
						if (l > k) return;
					} else if (l < k) return;
				}
				loc.setY(getArena().getLocation1().getY());
				if (balls[0] != null) balls[0].teleport(loc);
				else balls[0] = new BilliardBall(0, loc, this);
				//cueball_inhand = false;
				p.getInventory().setItem(1, new ItemStack(Material.AIR));
			}
		}
		else if (hand.getType() == Material.ARROW) {
			//if (practice) end(true);
			//else win(!is_p1, false);
			quitConfirmation(p);
		}
		else if (hand.getType() == Material.FEATHER && practice) {
			quitPractice();
		}
		else if (hand.getType() == Material.BOOK) openGUI(p);
		else if (hand.getType() == Material.STICK && balls[0] != null && !in_play) { //sneak?0.25:0.5
			if (p2 == null) {
				if (!practice) {
					if (can_practice) startPractice();
					else return;
				}
			}
			else if (!p.getUniqueId().equals(p1_turn ? p1.getUniqueId() : p2.getUniqueId())) {
				if (isActive()) {
					String nyt = plugin.getConfigString("not-your-turn-msg");
					if (nyt.length() > 0) getPlugin().sendActionbar(p, nyt, false);
				}
				return;
			}
			Vector to_ball = balls[0].getLocation().toVector().subtract(p.getLocation().add(0, p.isSneaking()?0.25:0.5, 0).toVector());
			double angle = to_ball.angle(p.getLocation().getDirection().normalize());
			//Bukkit.broadcastMessage("angle = " + angle);
			if (angle <= 0.15 && p.getLocation().distance(getArena().getCenterPixel()) <= 5) {
				in_play = true;
				sunk_balls.clear();
				collision = false;
				rail_hits = 0;
				cueball_inhand = false;
				balls[0].setVelocity(p.getLocation().getDirection().setY(0).normalize().multiply(first_draw ? BilliardBall.VMAX : 0.35));
			}
		}
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
			e.setCancelled(true);
			interact(e.getPlayer());
		}
	}
	
	@EventHandler
	public void onInteract2(PlayerInteractEntityEvent e) {
		if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
			e.setCancelled(true);
			interact(e.getPlayer());
		}
	}
	
	@EventHandler
	public void onInteract3(PlayerInteractAtEntityEvent e) {
		if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
			e.setCancelled(true);
			interact(e.getPlayer());
		}
	}

	@EventHandler
	public void onInteract4(EntityDamageByEntityEvent e) {
		if (e.getDamager() instanceof Player && e.getEntity() instanceof ArmorStand) {
			Player p = (Player) e.getDamager();
			if (this.getPlayers().contains(p.getUniqueId())) {
				e.setCancelled(true);
				interact(p);
			}
		}
	}
	
	@EventHandler
	public void onInteract5(PlayerAnimationEvent e) {
		if (e.getAnimationType() == PlayerAnimationType.ARM_SWING || (LobbyGames.SERVER_VERSION >= 13)) {
			if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
				interact(e.getPlayer());
			}
		}
	}
	
	@EventHandler(priority=EventPriority.HIGHEST) //highest priority to reconcile armor stand editing plugin conflictions
	public void onArmorStandManipulate(PlayerArmorStandManipulateEvent e) {
		if (!canStart()) return;
		for (BilliardBall ball : balls) {
			if (ball != null && ball.getUniqueId().equals(e.getRightClicked().getUniqueId())) {
				e.setCancelled(true);
				break;
			}
		}
		for (ArmorStand as : holes_as) {
			if (as != null && as.getUniqueId().equals(e.getRightClicked().getUniqueId())) {
				e.setCancelled(true);
				break;
			}
		}
		if (getPlayers().contains(e.getPlayer().getUniqueId())) {
			e.setCancelled(true);
			interact(e.getPlayer());
		}
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) { //proximity joining
		if (!canStart()) return;
		if (p2 == null && plugin.poolProximityJoining()) {
			double dist = GameUtils.distSquareXZ(getArena().getCenterPixel(), e.getPlayer().getLocation());
			if (e.getPlayer().getUniqueId().equals(p1.getUniqueId())) {
				if (dist >= 7) this.end(false);
			}
			else if (dist <= 5.2) this.appendPlayer(e.getPlayer());
		}
	}
	
	public void win(boolean p1_win, boolean end_of_game) {
		if (practice) {
			quitPractice();
			return;
		}
		if (p2 != null) {
			Player winner = p1_win ? p1 : p2;
			String wmsg = plugin.sp(p1_win ? p1 : p2, plugin.getConfigString("pool.win-msg", "&6&m----------------------------------------[newline]&e&l" + winner.getName() + "&e won the 8-Ball game![newline]&6&m----------------------------------------")
					.replaceAll("\\Q%winner%\\E", winner.getName()).replaceAll("\\Q%player%\\E", winner.getName()));
			p1.sendMessage(wmsg);
			p2.sendMessage(wmsg);
			
			if (end_of_game) {
				this.setConsoleCommand(this.getConsoleCommand().replaceAll("\\Q%winner%\\E", winner.getName()).replaceAll("\\Q%loser%\\E", p1_win ? p2.getName() : p1.getName()));
				int games_won = plugin.getHighScoreRaw(winner.getUniqueId(), getGameType());
				addScore(winner, games_won <= 0 ? 1 : games_won+1);
			}
			
			Bukkit.getPluginManager().callEvent(new GameWinEvent(p1_win ? p1 : p2, this, 1));
			p1.playSound(p1.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
			p2.playSound(p2.getLocation(), GameUtils.fireworkBlastSound(), 1f, 1f);
		}
		this.end(true);
	}
	
	public void reset() { //clear old armor stands, place new balls and holes, spawn armor stands
		
		rail_hits = 0;
		first_draw = true;
		in_play = false;
		
		clearArmorStands();
		
		if (LobbyGames.SERVER_VERSION <= 12) GameUtils.fill(getArena(), Material.valueOf("WOOL"), (byte) 13, null, (byte) 0, false);
		else GameUtils.fill(getArena(), Material.GREEN_WOOL, (byte) 0, null, (byte) 0, false);	
		
		Arena arena = getArena();
		
		balls = new BilliardBall[16];
		
		Vector hole_offset = new Vector(0, 0.32, 0);
		holes = new Location[6];
		Location l1_hole = arena.getLocation1();
		Location l2_hole = arena.getLocation2();
						
		switch(arena.getCoordinateRotation()) {
		case 1:
			l1_hole.add(0, 0, 1);
			l2_hole.add(1, 0, 0);
			break;
		case 2:
			l1_hole.add(1, 0, 1);
			break;
		case 3:
			l1_hole.add(1, 0, 0);
			l2_hole.add(0, 0, 1);
			break;
		default:
			l2_hole.add(1, 0, 1);
			break;
		}
		holes[0] = l1_hole;
		holes[1] = l2_hole;
		holes[2] = new Location(l1_hole.getWorld(), l1_hole.getX(), l1_hole.getY(), l2_hole.getZ());
		holes[3] = new Location(l1_hole.getWorld(), l2_hole.getX(), l1_hole.getY(), l1_hole.getZ());
		if (xplane) {
			holes[4] = new Location(l1_hole.getWorld(), (l1_hole.getX() + l2_hole.getX()) / 2.0, l1_hole.getY(), l1_hole.getZ());
			holes[5] = new Location(l1_hole.getWorld(), (l1_hole.getX() + l2_hole.getX()) / 2.0, l1_hole.getY(), l2_hole.getZ());
		} else {
			holes[4] = new Location(l1_hole.getWorld(), l1_hole.getX(), l1_hole.getY(), (l1_hole.getZ() + l2_hole.getZ()) / 2.0);
			holes[5] = new Location(l1_hole.getWorld(), l2_hole.getX(), l1_hole.getY(), (l1_hole.getZ() + l2_hole.getZ()) / 2.0);
		}
		
		for (int i = 0; i < 6; i++) {
			if (holes[i] != null) {
				ArmorStand a = this.getWorld().spawn(holes[i].add(hole_offset), ArmorStand.class);
				a.setSmall(true);
				a.setGravity(false);
				a.setVisible(false);
				if (LobbyGames.SERVER_VERSION >= 12) a.setSilent(true);
				a.setHelmet(LobbyGames.SERVER_VERSION <= 12 ? new ItemStack(Material.valueOf("CARPET"), 1, (byte) 15) : new ItemStack(Material.BLACK_CARPET, 1));
				holes_as[i] = a;
			}
		}
		////
		
		int rotm = (arena.getCoordinateRotation() == 0 || arena.getCoordinateRotation() == 1) ? 1 : -1;
		int rota = (arena.getCoordinateRotation() == 0 || arena.getCoordinateRotation() == 1) ? 0 : 1;
		boolean mw5 = Math.max(arena.getHeight(), arena.getWidth()) == 5;
		
		//create balls
		Location cueball = getPixel(rota-rotm, 0);
		
		Location rowstart = getPixel(rotm + rota, 0);
		if (xplane) {
			cueball.add(rotm*(mw5 ? 0.15 : 0.9), 0, 0.5);
			rowstart.add(rotm*(mw5 ? 0.3 : 0.45), 0, 0.5);
		} else {
			cueball.add(0.5, 0, rotm*(mw5 ? 0.15 : 0.9));
			rowstart.add(0.5, 0, rotm*(mw5 ? 0.3 : 0.45));
		}
		balls[0] = new BilliardBall(0, cueball, this);
		
		Location setball = rowstart.clone();
		double spacing = BilliardBall.BALL_DIAMETER + 0.027;
		List<Integer> used_numbers = new ArrayList<Integer>();
		
		int k = 1;
		for (int i = 1; i < 6; i++) {
			for (int j = 0; j < i; j++) {
				int number;
				if (k == 1 || k == 15) number = k;
				else if (k == 5) number = 8;
				else number = randomBallNumber(used_numbers);
				
				balls[number] = new BilliardBall(number, setball, this);
				if (xplane) setball.add(0, 0, -spacing);
				else setball.add(spacing, 0, 0);
				k++;
			}
			if (xplane) rowstart.add(rotm*spacing, 0, spacing/2.0);
			else rowstart.add(-spacing/2.0, 0, rotm*spacing);
			setball = rowstart.clone();
		}
		
	}
	
	public void end(boolean put_delay) {
		if (put_delay) {
			plugin.getProximityDelay().put(p1.getUniqueId(), System.currentTimeMillis() + 7000);
			if (p2 != null) plugin.getProximityDelay().put(p2.getUniqueId(), System.currentTimeMillis() + 7000);
		}
		super.end();
	}
	
	public int sinkedCount() {
		int count = 0;
		for (int i = 1; i < balls.length; i++) {
			if (i != 8 && (balls[i] == null || balls[i].isSunk())) count++;
		}
		return count;
	}

	@EventHandler
	public void onQuit(PlayerQuitLobbyGameEvent e) {
		if (!canStart()) return;
		
		if (e.getPlayer().getUniqueId().equals(p1.getUniqueId())) win(false, sinkedCount() > 4);
		else if (p2 != null && e.getPlayer().getUniqueId().equals(p2.getUniqueId())) win(true, sinkedCount() > 4);
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
			}.runTaskLater(plugin, 120l);
		}
	}
	
}
