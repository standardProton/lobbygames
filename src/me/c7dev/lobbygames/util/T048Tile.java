package me.c7dev.lobbygames.util;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.games.T048;

public class T048Tile {
	
	int num, teleport_order = 0;
	ArmorStand as;
	ItemStack[] tileblocks;
	LobbyGames plugin;
	Location teleport_loc = null;
	CoordinatePair coords;
	boolean done_teleporting = true;
	
	public static double Y_ADD = -1.75;
	public static double Y_INC_MAX = 0.39;
	
	double y_base;
	
	
	public T048Tile(int num, CoordinatePair coords, T048 game, ItemStack[] tileblocks) {
		Location spawn_loc = game.getPixel(coords);
		this.num = num;
		this.tileblocks = tileblocks;
		this.plugin = game.getPlugin();
		this.coords = coords;
		
		y_base = spawn_loc.getY();
		spawn_loc.add(0.5, Y_ADD + (Y_INC_MAX*(Math.min(num, tileblocks.length-1)/((double) tileblocks.length - 1.0))), 0.5);
		
		as = spawn_loc.getWorld().spawn(spawn_loc, ArmorStand.class);
		as.setVisible(false);
		as.setGravity(false);
		if (LobbyGames.SERVER_VERSION >= 12) as.setSilent(true);
		as.setCustomName("§f§l" + (int) Math.pow(2, num+1));
		as.setCustomNameVisible(true);
		as.setHelmet(tileblocks[Math.min(num, tileblocks.length-1)]);
	}
	
	public Location getLocation() {
		return as.getLocation();
	}
	
	public double getLocY() {
		return y_base + Y_ADD + (Y_INC_MAX*(Math.min(num, tileblocks.length-1)/((double) tileblocks.length - 1.0)));
	}
	
	public void incr() { //increment power of 2, merging
		num++;
		as.setCustomName("§f§l" + (int) Math.pow(2, num+1));
		as.setHelmet(tileblocks[Math.min(num, tileblocks.length-1)]);
		Location loc = as.getLocation();
		loc.setY(getLocY());
		as.teleport(loc);
	}
	
	public int getNum() {
		return this.num;
	}
	
	public void remove() {
		this.as.remove();
	}
	
	public CoordinatePair getCoords() {return coords;}
	public void setCoords(CoordinatePair c) {this.coords = c;}
	
	public void teleport(Location newloc, T048Tile incr_tile) { //merge with incr_tile if applicable and play teleportation animation
		int ticks = 2;
		newloc.add(0.5, 1, 0.5);
		Vector incr = newloc.toVector().subtract(as.getLocation().toVector()).multiply(1.0/ticks);
		
		teleport_order++;
		//if (!done_teleporting) as.teleport(teleport_loc);
		
		new BukkitRunnable() {
			int ticks_done = 0;
			final int telo = teleport_order;
			
			@Override
			public void run() {
				if (ticks_done >= ticks || teleport_order != telo) {
					if (incr_tile != null) {
						as.remove();
						incr_tile.incr();
					}
					this.cancel();
					return;
				}
					
				as.teleport(as.getLocation().add(incr));
				ticks_done++;
			}
		}.runTaskTimer(plugin, 0, 1l);
	}
	

}
