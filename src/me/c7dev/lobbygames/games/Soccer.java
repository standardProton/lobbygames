package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Difficulty;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
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
import me.c7dev.lobbygames.util.Ball;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Soccer extends Game implements Listener {
	
	public void broadcast(String msg) {
		if (msg.length() == 0) return;
		for (UUID u : this.getPlayers()) {
			Player p = Bukkit.getPlayer(u);
			if (p != null) p.sendMessage(getPlugin().sp(p, msg));
		}
	}
	
	private int ascore = 0, bscore = 0, goal_threshold = 15;
	private Ball ball;
	private List<UUID> a = new ArrayList<UUID>(), b = new ArrayList<UUID>(); //a = blue
	private HashMap<UUID,Long> boost_delay = new HashMap<UUID,Long>();
	private Random random = new Random();
	private String cooldown_msg;
	
	private Location net1a, net2a, net1b, net2b;
	private boolean zplane, goal_registered = false, start_countdown = false, boost_enabled = false;
	
	public boolean checkNet(Location l) { //1=a, 2=b
		if (l.getBlockY() > Math.max(net1a.getBlockY(), net1b.getBlockY())) return false; //above net
		
		if (Arena.isInBoundsXZ(l, net1a, net1b)) {
			goal(true);
			return true;
		}
		if (Arena.isInBoundsXZ(l, net2a, net2b)) {
			goal(false);
			return true;
		}
		
		return false;
	}
		
	public Soccer(LobbyGames plugin, Arena arena, Player p1) {
		super(plugin, GameType.SOCCER, arena, p1);
		if (!canStart() || arena.getGameType() != GameType.SOCCER) return;
		
		//run checks to make sure game can run
		if (arena.getSpecialLoc1() == null || arena.getSpecialLoc2() == null) {
			if (p1.hasPermission("lobbygames.admin")) p1.sendMessage("§cThere was an error in starting this game, check the console.");
			Bukkit.getLogger().severe("This soccer game can not start because a soccer net location is undefined!");
			return;
		}
		else if (arena.getLocation1().getWorld().getDifficulty() == Difficulty.PEACEFUL) {
			if (p1.hasPermission("lobbygames.admin")) p1.sendMessage("§cThere was an error in starting this game, check the console.");
			Bukkit.getLogger().severe("This soccer game can not start because the world is set to Peaceful difficulty! The ball (a Slime entity) can not spawn.");
			return;
		}
		
		net1a = arena.getSpecialLoc1().clone();	
		net1b = arena.getSpecialLoc2().clone();
		if (arena.isInBoundsXZ(net1a) || arena.isInBoundsXZ(net1b)) {
			if (p1.hasPermission("lobbygames.admin")) p1.sendMessage("§cThere was an error in starting this game, check the console.");
			Bukkit.getLogger().severe("This soccer game can not start because a soccer net location is inside the arena! (Must define a cube outside)");
			return;
		}
		
		//set up net locations
		int xmin = Math.min(arena.getLocation1().getBlockX(), arena.getLocation2().getBlockX());
		int xmax = Math.max(arena.getLocation1().getBlockX(), arena.getLocation2().getBlockX());
		int zmin = Math.min(arena.getLocation1().getBlockZ(), arena.getLocation2().getBlockZ());
		int zmax = Math.max(arena.getLocation1().getBlockZ(), arena.getLocation2().getBlockZ());
		zplane = xmin <= net1a.getBlockX() && net1a.getBlockX() <= xmax; //assumed net1a is not in bounds of arena
		
		if (zplane) {
			int netdepth = Math.min(Math.abs(net1a.getBlockZ() - zmax), Math.abs(net1a.getBlockZ() - zmin));
			int netwidth = Math.abs(net1b.getBlockX() - net1a.getBlockX()) + 1;
			if (netwidth > arena.getHeight()) {
				if (p1.hasPermission("lobbygames.admin")) p1.sendMessage("§cThere was an error in starting this game, check the console.");
				Bukkit.getLogger().severe("This soccer game can not start because the 2 soccer net locations encapsulate the arena! They must create a cuboid outside of the arena's bounds.");
				return;
			}
			int m = net1a.getZ() < zmin ? 1 : -1;
			net2b = net1a.clone().add(0, 0, m*(arena.getHeight() + (2*netdepth) - 1));
			net2b.setY(net1b.getY());
			
			net2a = net1b.clone().add(0, 0, m*(arena.getHeight()+1));
			net2a.setY(net1a.getY());
		} else {
			int netdepth = Math.min(Math.abs(net1a.getBlockX() - xmax), Math.abs(net1a.getBlockX() - xmin));
			int netwidth = Math.abs(net1b.getBlockZ() - net1a.getBlockZ()) + 1;
			if (netwidth > arena.getWidth()) {
				if (p1.hasPermission("lobbygames.admin")) p1.sendMessage("§cThere was an error in starting this game, check the console.");
				Bukkit.getLogger().severe("This soccer game can not start because the 2 soccer net locations encapsulate the arena! They must create a cuboid outside of the arena's bounds.");
				return;
			}
			int m = net1a.getX() < xmin ? 1 : -1;
			net2b = net1a.clone().add(m*(arena.getWidth() + (2*netdepth) - 1), 0, 0);
			net2b.setY(net1b.getY());
			
			net2a = net1b.clone().add(m*(arena.getWidth()+1), 0, 0);
			net2a.setY(net1a.getY());
			
		}
						
		Bukkit.getPluginManager().registerEvents(this, plugin);
		
		if (!arena.isInBoundsXZ(p1.getLocation())) p1.teleport(arena.getCenterPixel());
		preparePlayer(p1);
		assignTeam(p1);
		
		ball = new Ball(arena.getCenterPixel().clone().add(0, 1, 0)); //spawn soccer ball
		
		goal_threshold = plugin.getConfig().getInt("soccer.point-win-threshold");
		boost_enabled = plugin.getConfig().getBoolean("soccer.boost-jump-enabled");
		cooldown_msg = plugin.getConfigString("cooldown-msg", "§cYou must wait %seconds% second(s) to do this!");
		
				
		new BukkitRunnable() {			
			@Override
			public void run() {
				if (!canStart()) this.cancel();
				updateActionBar();
			}
		}.runTaskTimer(plugin, 0, 20l);
		
		new BukkitRunnable() {
			@Override
			public void run() { //tick ball and check if in nets
				if (!canStart()) {
					this.cancel();
					return;
				}
				
				if (ball == null) return;
				ball.tick(arena.getLocation1().getY()); 
				if (ball.getVelocity().length() > Ball.VMAX/2) {
					ball.getLocation().getWorld().playEffect(ball.getArmorStand().getLocation().clone().add(0.7, 1.7, 0.7), Effect.SMOKE, 0);
				}
				
				Location loc = ball.getLocation();
				Location l1 = arena.getLocation1();
				Location l2 = arena.getLocation2();	
								
				//from Arena#isInBoundsXZ
				if (l1.getX() < l2.getX()) {
					if ((loc.getBlockX() < l1.getX() && ball.getVelocity().getX() < 0) || 
							(loc.getBlockX() > l2.getX() && ball.getVelocity().getX() > 0))
						if (zplane || !checkNet(loc)) ball.setVelocity(ball.getVelocity().setX(Ball.BOUNCE*ball.getVelocity().getX()), false);
				} else {
					if ((loc.getBlockX() > l1.getX() && ball.getVelocity().getX() > 0) || 
							(loc.getBlockX() < l2.getX() && ball.getVelocity().getX() < 0)) 
						if (zplane || !checkNet(loc)) ball.setVelocity(ball.getVelocity().setX(Ball.BOUNCE*ball.getVelocity().getX()), false);
				}

				if (l1.getZ() < l2.getZ()) {
					if ((loc.getBlockZ() < l1.getZ() && ball.getVelocity().getZ() < 0)
							|| (loc.getBlockZ() > l2.getZ() && ball.getVelocity().getZ() > 0))
						if (!zplane || !checkNet(ball.getLocation())) ball.setVelocity(ball.getVelocity().setZ(Ball.BOUNCE*ball.getVelocity().getZ()), false);
				} else {
					if ((loc.getBlockZ() > l1.getZ() && ball.getVelocity().getZ() > 0) || 
							(loc.getBlockZ() < l2.getZ() && ball.getVelocity().getZ() < 0))
						if (!zplane || !checkNet(ball.getLocation())) ball.setVelocity(ball.getVelocity().setZ(Ball.BOUNCE*ball.getVelocity().getZ()), false);
				}
				
			}
		}.runTaskTimer(plugin, 0, 1l);
	}
	
	public String getScoreStr() {
		return getPlugin().getConfigString("soccer.score-format", "§9§l" + ascore + "§7§l - §c§l" + bscore)
				.replaceAll("\\Q%team1_score%\\E", "" + ascore).replaceAll("\\Q%team2_score%\\E", "" + bscore);
	}
	
	@Override
	public String getScore(Player p) {
		return isActive() ? getScoreStr() : "-";
	}
	
	public void updateActionBar() {
		if (goal_registered) return; //ball respawn countdown
		String msg = isActive() ? getScoreStr() : getPlugin().getConfigString("waiting-players", "§7Waiting for more players to join...");
		for (UUID u : this.getPlayers()) getPlugin().sendActionbar(Bukkit.getPlayer(u), msg, true);
	}
	
	public void goal(boolean a_team) {
		if (goal_registered) return;
		if (a_team) {
			if (isActive()) ascore++;
			GameUtils.spawnFirework(ball.getLocation(), Color.BLUE);
			if (ascore >= goal_threshold) {
				win(true);
				return;
			}
			String msg = getPlugin().getConfigString("soccer.team1-score-msg", "§9§lBLUE team scoared a goal! %score%").replaceAll("\\Q%score%\\E", getScoreStr());
			for (UUID u : this.getPlayers()) {
				Player p = Bukkit.getPlayer(u);
				if (p != null) {
					p.sendMessage(getPlugin().sp(p, msg));
					p.playSound(p.getLocation(), GameUtils.getOrbPickupSound(), 1f, 8f);
				}
			}
		} else {
			if (isActive()) bscore++;
			GameUtils.spawnFirework(ball.getLocation(), Color.RED);
			if (bscore >= goal_threshold) {
				win(false);
				return;
			}
			String msg = getPlugin().getConfigString("soccer.team2-score-msg", "§c§lRED team scored a goal! %score").replaceAll("\\Q%score%\\E", getScoreStr());
			for (UUID u : this.getPlayers()) {
				Player p = Bukkit.getPlayer(u);
				if (p != null) {
					p.sendMessage(getPlugin().sp(p, msg));
					p.playSound(p.getLocation(), GameUtils.getOrbPickupSound(), 1f, 8f);
				}
			}
		}
		goal_registered = true;
		int respawn_seconds = getPlugin().getConfig().getInt("soccer.ball-respawn-delay");
		new BukkitRunnable() {
			@Override
			public void run() {
				ball.remove();
				ball = null;
				
				if (respawn_seconds == 0) {
					goal_registered = false;
					ball = new Ball(getArena().getCenterPixel().clone().add(0, 10, 0));
					return;
				}
				new BukkitRunnable() {
					int seconds = respawn_seconds;
					String respawn_str = getPlugin().getConfigString("soccer.ball-respawn-msg", "§eThe ball will respawn in §c%seconds% §esecond(s)!");
					
					@Override
					public void run() {
						if (seconds > 0) {
							for (UUID u : getPlayers()) getPlugin().sendActionbar(Bukkit.getPlayer(u), respawn_str.replaceAll("\\Q%seconds%\\E", "" + seconds).replaceAll("\\Q(s)\\E", seconds == 1 ? "" : "s"), false);
							seconds--;
						} else {
							this.cancel();
							goal_registered = false;
							ball = new Ball(getArena().getCenterPixel().clone().add(0, 10, 0));
							updateActionBar();
						}
					}
				}.runTaskTimer(getPlugin(), 0, 20l);
			}
		}.runTaskLater(super.getPlugin(), 7l);
		
	}
	
	public void win(boolean a_team) {
		broadcast(a_team ? getPlugin().getConfigString("soccer.team1-win-msg", "§9§lBLUE team wins the game!") : 
			getPlugin().getConfigString("soccer.team2-win-msg", "§c§lRED team wins the game!"));
		
		String winning_team = "";
		String losing_team = "";
		
		for (UUID u : getPlayers()) {
			Player p = Bukkit.getPlayer(u);
			if (p != null) {
				if ((a_team ? a : b).contains(u)) {
					Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, a_team ? ascore : bscore));
					winning_team += p.getName() + ", ";
					
					int games_won = getPlugin().getHighScoreRaw(p.getUniqueId(), getGameType());
					addScore(p, games_won <= 0 ? 1 : games_won+1);
				} else losing_team += p.getName() + ", ";
				if (getArena().getSpawn1() != null) p.teleport(getArena().getSpawn1());
			}
		}
		
		winning_team += "ABC%";
		winning_team = winning_team.replaceAll("\\Q, ABC%\\E", "").replaceAll("\\QABC%\\E", "");
		losing_team += "ABC%";
		losing_team = losing_team.replaceAll("\\Q, ABC%\\E", "").replaceAll("\\QABC%\\E", "");
		this.setConsoleCommand(this.getConsoleCommand().replaceAll("\\Q%winning_player_list%\\E", winning_team).replaceAll("\\Q%losing_player_list%\\E", losing_team));
		
		this.end();
		
	}
	
	public void assignTeam(Player p) {
		if (!this.getPlayers().contains(p.getUniqueId())) {
			Bukkit.getLogger().warning("Could not add a player to soccer: Not added to the game");
			return;
		}
		boolean join_a = (a.size() == b.size()) ? random.nextBoolean() : a.size() < b.size();
		if (join_a) {
			a.add(p.getUniqueId());
			p.getInventory().setItem(4, GameUtils.createWool(1, 3, getPlugin().getConfigString("soccer.team1-wool-title", "§bYou are on the §b§lBLUE§b team!")));
			broadcast(getPlugin().getConfigString("soccer.team1-join-msg", "§9" + p.getName() + " joined the blue team!").replaceAll("\\Q%player%\\E", p.getName()));
		} else {
			b.add(p.getUniqueId());
			p.getInventory().setItem(4, GameUtils.createWool(1, 14, getPlugin().getConfigString("soccer.team2-wool-title", "§cYou are on the §c§lRED§c team!")));
			broadcast(getPlugin().getConfigString("soccer.team2-join-msg", "§c" + p.getName() + " joined the red team!").replaceAll("\\Q%player%\\E", p.getName()));
		}
		p.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, getPlugin().getConfigString("quit-item-title", "§c§lQuit")));
		Color color = join_a ? Color.fromRGB(75, 75, 255) : Color.fromRGB(255, 75, 75);
		p.getInventory().setHelmet(GameUtils.createArmor(Material.LEATHER_HELMET, color));
		p.getInventory().setChestplate(GameUtils.createArmor(Material.LEATHER_CHESTPLATE, color));
		p.getInventory().setLeggings(GameUtils.createArmor(Material.LEATHER_LEGGINGS, color));
		p.getInventory().setBoots(GameUtils.createArmor(Material.LEATHER_BOOTS, color));
	}
	
	@EventHandler
	public void onJoin(PlayerJoinLobbyGameEvent e) { //new player joins before game has started
		if (!canStart() || isActive() || e.getGame().getGameType() != GameType.SOCCER || e.getGame().getArena().getID() != this.getArena().getID()) return;
		if (!getArena().isInBoundsXZ(e.getPlayer().getLocation())) e.getPlayer().teleport(getArena().getCenterPixel());
		preparePlayer(e.getPlayer());
		assignTeam(e.getPlayer());
		
		int player_threshold1 = getPlugin().getConfig().getInt("soccer.player-join-threshold");
		if (player_threshold1 < 2) player_threshold1 = 2;
		final int player_threshold = player_threshold1;
		
		if (!start_countdown && this.getPlayers().size() >= player_threshold) {
			start_countdown = true;
			final int seconds = getPlugin().getConfig().getInt("soccer.countdown-seconds"); 
			final String format = getPlugin().getConfigString("countdown-format", "&eThe game will start in &c%seconds%&e seconds!");
			new BukkitRunnable() {
				int c = 0;
				@Override
				public void run() {
					int rem = seconds - c;
					if (rem == 0) { //start game
						this.cancel();
						setActive(true);
						ball.reload(ball.getLocation());
						ball.setVelocity(new Vector(0, 0.5, 0), false);
						String start_msg = getPlugin().getConfigString("soccer.start-msg", "§3§m----------------------------------------\n§b§lSoccer: §bPunch the ball into your team's net to win points!\n§3§m----------------------------------------");
						String boost_title = getPlugin().getConfigString("soccer.boost-jump-title", "§a§lBoost Jump");
						for (UUID u : getPlayers()) {
							Player p = Bukkit.getPlayer(u);
							if (p != null) {
								p.sendMessage(getPlugin().sp(p, start_msg));
								p.getInventory().setItem(8, new ItemStack(Material.AIR));
								if (boost_enabled) p.getInventory().setItem(0, GameUtils.createItem(Material.FEATHER, 1, (byte) 0, boost_title));
							}
						}
					}
					else if (getPlayers().size() < player_threshold) {
						this.cancel();
						broadcast(getPlugin().getConfigString("waiting-players", "§cWaiting for more players to join..."));
						start_countdown = false;
					}
					else if (c == 0 || rem % 5 == 0) broadcast(format.replaceAll("\\Q%seconds%\\E", "" + rem));
					c++;
				}
			}.runTaskTimer(this.getPlugin(), 0, 20l);
		}
		
	}
	
	@EventHandler
	public void onQuit(PlayerQuitLobbyGameEvent e) {
		if (!canStart()) return;
		a.remove(e.getPlayer().getUniqueId());
		b.remove(e.getPlayer().getUniqueId());
		if (isActive()) {
			if (a.size() == 0) win(false);
			else if (b.size() == 0) win(true);
		}
	}
	
	@EventHandler(priority=EventPriority.HIGH)
	public void onSpawn(EntitySpawnEvent e) {
		if (e.getEntity() instanceof Slime) {
			if ((ball == null && getArena().isInBoundsXZ(e.getEntity().getLocation())) || (ball != null && e.getEntity().getUniqueId().equals(ball.getUniqueId()))) {
				e.setCancelled(false);
			}
		}
	}
	
	@EventHandler
	public void onDeath(EntityDeathEvent e) {
		if (!canStart() || ball == null) return;
		if (e.getEntity().getUniqueId() == ball.getUniqueId()) {
			e.setDroppedExp(0);
			ball.reload(e.getEntity().getLocation());
		} else if (e.getEntity().getUniqueId() == ball.getArmorStand().getUniqueId()) {
			Location loc = ball.getLocation();
			ball.remove();
			ball = null;
			ball = new Ball(loc);
		}
	}
	
	@EventHandler
	public void onMove(PlayerMoveEvent e) {
		if (!canStart()) return;
		if (this.getPlayers().contains(e.getPlayer().getUniqueId())) {
			if (!this.getArena().isInBoundsXZ(e.getPlayer().getLocation()) &&
					!Arena.isInBoundsXZ(e.getPlayer().getLocation(), net1a, net1b) &&
					!Arena.isInBoundsXZ(e.getPlayer().getLocation(), net2a, net2b)) {
				removePlayer(e.getPlayer());
			}
		} else if (getPlugin().soccerProximityJoining() && this.getArena().isInBoundsXZ(e.getPlayer().getLocation())) {
			this.appendPlayer(e.getPlayer());
		}
	}
	
	@EventHandler
	public void onInteract(PlayerInteractEvent e) {
		if (!canStart()) return;
		if (getPlayers().contains(e.getPlayer().getUniqueId())) {
			ItemStack hand = GameUtils.getHandItem(e.getPlayer());
			if (hand != null) {
				if (hand.getType() == Material.ARROW) quitConfirmation(e.getPlayer());
				else if (hand.getType() == Material.FEATHER && boost_enabled && isActive()) {
					if (!boost_delay.containsKey(e.getPlayer().getUniqueId()) || boost_delay.get(e.getPlayer().getUniqueId()) < System.currentTimeMillis()) {
						long delay = getPlugin().getConfig().getLong("soccer.boost-jump-cooldown");
						boost_delay.put(e.getPlayer().getUniqueId(), System.currentTimeMillis() + (1000*delay));
						e.getPlayer().setVelocity(new Vector(e.getPlayer().getVelocity().normalize().getX()*1.8, 0.8, e.getPlayer().getVelocity().normalize().getZ()*1.8));
					} else {
						int seconds = (int) (boost_delay.get(e.getPlayer().getUniqueId()) - System.currentTimeMillis()) / 1000;
						seconds += 1;
						getPlugin().sendActionbar(e.getPlayer(), cooldown_msg.replaceAll("\\Q%seconds%\\E", "" + seconds).replaceAll("\\Q(s)\\E", seconds==1?"":"s"), false);
					}
				}
				
			}
		}
	}
	
	@EventHandler
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == GameType.SOCCER && e.getGame().getArena().getID() == this.getArena().getID()) {
			setActive(false);
			ball.remove();
			HandlerList.unregisterAll(this);
		}
	}
	
	@EventHandler
	public void onDamage(EntityDamageEvent e) {
		if (!canStart()) return;
		if (e.getEntity() instanceof Slime && (ball != null && e.getEntity().getUniqueId().equals(ball.getUniqueId()))) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onClick(EntityDamageByEntityEvent e) {
		if (!canStart()) return;
		if (this.getPlayers().contains(e.getDamager().getUniqueId())) {
			if (ball != null && e.getEntity().getUniqueId() == ball.getUniqueId()) {
				e.setCancelled(true);
				if (isActive()) ball.setVelocity(e.getDamager().getLocation().getDirection(), true);
				else ball.setVelocity(new Vector(0, 0.5, 0), true);
			}
		}
	}

}
