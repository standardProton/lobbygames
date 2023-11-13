package me.c7dev.lobbygames.games;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

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
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.api.events.GameEndEvent;
import me.c7dev.lobbygames.api.events.GameWinEvent;
import me.c7dev.lobbygames.api.events.PlayerQuitLobbyGameEvent;
import me.c7dev.lobbygames.util.ClickBlock;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class Clicker extends Game implements Listener {
	
	private HashMap<UUID,ClickBlock> blocks = new HashMap<UUID,ClickBlock>();
	private List<CoordinatePair> usedcoords = new ArrayList<>();
	private int score = 0, spawned = 0, bpg = 40;
	private Player p;
	private String score_format;
	private Location loc;
	
	public CoordinatePair getRandomOffset(Random r) {
		CoordinatePair c = new CoordinatePair(r.nextInt(3) - 1, r.nextInt(3) - 1);
		if (c.getX() == 0 && c.getY() == 0) return getRandomOffset(r);
		if (usedcoords.size() < 8) { //overflow
			for (CoordinatePair c2 : usedcoords) {
				if (c.equals(c2)) return getRandomOffset(r);
			}
		}
		return c;
	}
	
	public Clicker(LobbyGames plugin, Arena a, Player p) {
		super(plugin, GameType.CLICKER, a, p);
		if (!canStart() || a.getGameType() != GameType.CLICKER) return;
		
		this.p = p;
		loc = a.getLocation1().add(0.5, 0, 0.5);
		p.teleport(this.loc);
		preparePlayer(p);
		setActive(true);
		
		for (Entity e : loc.getWorld().getNearbyEntities(loc, 3, 3, 3)) {
			if (e instanceof ArmorStand) e.remove();
		}
				
		Bukkit.getPluginManager().registerEvents(this, plugin);
				
		String startmsg = plugin.getConfigString(p, "clicker.start-msg", "");
		if (startmsg.length() > 0) p.sendMessage(startmsg);
		
		score_format = plugin.getConfigString("clicker.score-format", "§2Score: §f%score%");
		
		p.getInventory().setItem(8, GameUtils.createItem(Material.ARROW, 1, (byte) 0, getPlugin().getConfigString("quit-item-title", "§c§lQuit")));
		
		new BukkitRunnable() {
			Random r = new Random();
			
			public void run() {
				if (canStart()) {
					final CoordinatePair coords = getRandomOffset(r);
					ClickBlock c = new ClickBlock(loc.clone().add(coords.getX(), -1.2, coords.getY()), plugin); //spawn block
					blocks.put(c.getArmorStand().getUniqueId(), c);
					usedcoords.add(coords);
					spawned++;
					this.cancel();;
					
					c.setRemoveRunnable(new BukkitRunnable() {
						@Override
						public void run() {
							blocks.remove(c.getArmorStand().getUniqueId());
							usedcoords.remove(coords);
							if (!isActive() && blocks.size() == 0) die();
						}
					});
					
					if (score_format.length() > 0) getPlugin().sendActionbar(p, plugin.sp(p, score_format.replaceAll("\\Q%score%\\E", "" + score)), true);
				} else {
					this.cancel();
					setActive(false);
				}
			}
		}.runTaskTimer(plugin, 0, 17l);
	}
	
	public void click(ArmorStand as) { //player punches block
		ClickBlock c = blocks.get(as.getUniqueId());
		if (c == null || c.isClicked()) return;
		
		c.click();
		if (c.isGreen()) {
			score++;
			p.playSound(p.getLocation(), GameUtils.getOrbPickupSound(), 1f, 8f);
		}
		else {
			score -= 3;
			p.playSound(p.getLocation(), GameUtils.getSound(11, "GLASS", "BLOCK_GLASS_BREAK"), 1f, 1f);
		}
		if (score_format.length() > 0) getPlugin().sendActionbar(p, getPlugin().sp(p, score_format.replaceAll("\\Q%score%\\E", "" + score)), true);
	}
	
	@Override
	public String getScore(Player p) {
		return "" + score;
	}
	
	@EventHandler
	public void onClick(PlayerArmorStandManipulateEvent e) {
		if (!canStart()) return;
		boolean a = blocks.containsKey(e.getRightClicked().getUniqueId());
		boolean b = e.getPlayer().getUniqueId().equals(p.getUniqueId());
		if (a || b) e.setCancelled(true);
		if (a && b) click(e.getRightClicked());	
	}
	
	@EventHandler
	public void onLeftClick(EntityDamageByEntityEvent e) {
		if (!canStart()) return;
		if (e.getDamager() instanceof Player && e.getDamager().getUniqueId().equals(p.getUniqueId()) && e.getEntity() instanceof ArmorStand) {
			if (blocks.containsKey(e.getEntity().getUniqueId())) {
				e.setCancelled(true);
				click((ArmorStand) e.getEntity());
			}
		}
	}
	
	@EventHandler
	public void onInteractBlock(PlayerInteractEvent e) {
		if (!canStart()) return;
		if (e.getPlayer().getUniqueId().equals(p.getUniqueId())) {
			ItemStack hand = GameUtils.getHandItem(e.getPlayer());
			if (hand != null && hand.getType() == Material.ARROW) {
				removePlayer(e.getPlayer());
			}
			else if ((e.getAction() == Action.RIGHT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_BLOCK) && e.getBlockFace() == BlockFace.UP) {
				Block b = e.getClickedBlock();
				if (b == null) return;
				int x = this.loc.getBlockX() - b.getX();
				int z = this.loc.getBlockZ() - b.getZ();
				if (Math.abs(x) <= 1 && Math.abs(z) <= 1) {
					for (Entry<UUID,ClickBlock> ase : blocks.entrySet()) {
						ArmorStand as = ase.getValue().getArmorStand();
						if (as.getLocation().getBlockX() == b.getX() && as.getLocation().getBlockZ() == b.getZ()) {
							click(as);
						}
					}
				}
			}
		}
	}
	
	@EventHandler
	public void onBreakBlock(BlockBreakEvent e) {
		if (!canStart()) return;
		if (e.getPlayer().getUniqueId().equals(p.getUniqueId())) e.setCancelled(true);
	}
	
	@EventHandler
	public void onBuild(BlockPlaceEvent e) {
		if (!canStart()) return;
		if (e.getPlayer().getUniqueId().equals(p.getUniqueId())) e.setCancelled(true);
	}
	
	@EventHandler
	public void onKick(PlayerQuitLobbyGameEvent e) {
		if (e.getPlayer().getUniqueId().equals(p.getUniqueId()) && p.isOnline()) {
			p.teleport(this.getArena().getSpawn1());
			if (canStart()) this.end();
		}
	}
	
	public void die() {
		if(!canStart()) return;
		this.setConsoleCommandPlayer(p.getName()).setConsoleCommandScore(score + "");
		String defmsg = "\n§2§m----------------------------------------\n§a§lScore: §f" + score + "\n§2§m----------------------------------------";
		String msg = getPlugin().getConfigString(p, "clicker.win-msg", defmsg).replaceAll("\\Q%score%\\E", "" + score);
		if (msg.length() > 0) p.sendMessage(msg);
		p.teleport(this.getArena().getSpawn1());
		
		addScore(p, score);
		
		Bukkit.getPluginManager().callEvent(new GameWinEvent(p, this, score));
		
		HandlerList.unregisterAll(this);
		this.end();
	}
	
	@EventHandler
	public void onEnd(GameEndEvent e) {
		if (e.getGame().getGameType() == this.getGameType() && e.getGame().getArena().getID() == super.getArena().getID()) {
			HandlerList.unregisterAll(this);
		}
	}

}
