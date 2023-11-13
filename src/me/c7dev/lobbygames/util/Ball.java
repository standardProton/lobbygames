package me.c7dev.lobbygames.util;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Slime;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.LobbyGames;

public class Ball {
	
	private ArmorStand as;
	private Slime slime;
	private Vector v = new Vector(0, -0.1, 0);
	
	public final static int SIZE = 1;
	public final static double VMAX = 0.2, BOUNCE = -0.1;
	
	public Ball(Location loc) {
		slime = loc.getWorld().spawn(loc, Slime.class);
		slime.setSize(SIZE);
		as = loc.getWorld().spawn(loc, ArmorStand.class);
		if (LobbyGames.SERVER_VERSION < 12) as.setPassenger(slime);
		else {
			as.addPassenger(slime);
			slime.setAI(false);
			slime.setCollidable(false);
		}
		as.setVisible(false);
		as.setGravity(false);
	}
	
	public void remove() {
		slime.remove();
		as.remove();
	}
	
	public void reload(Location loc) { //respawn
		if (slime.isDead()) {
			slime = loc.getWorld().spawn(loc, Slime.class);
			slime.setSize(SIZE);
			if (LobbyGames.SERVER_VERSION >= 12) as.addPassenger(slime);
			else as.setPassenger(slime);
		}
	}
	
	public UUID getUniqueId() {return slime.getUniqueId();}
	
	public ArmorStand getArmorStand() {return as;}
	
	public Location getLocation() {return slime.getLocation().clone();}
	
	public Vector getVelocity() {return this.v;}

	public void tick(double y) { //update velocity and position
		double len = v.length();
		if (len == 0) return;
		
		if (len > VMAX) v = v.normalize().multiply(VMAX);
		
		Location loc = as.getLocation().add(v);
		//if (LobbyGames.SERVER_VERSION >= 12) as.removePassenger(slime);
		as.eject();
		as.teleport(loc);
		slime.teleport(loc.add(0, 2, 0));
		if (LobbyGames.SERVER_VERSION >= 12) as.addPassenger(slime);
		else as.setPassenger(slime);
		v.add(new Vector(0, -0.04, 0));
		if (v.getY() < 0 && slime.getLocation().getY() <= y + 0.5) { //prev +0.5
			v.setY(-v.getY()).multiply(Math.abs(Ball.BOUNCE));
			if (v.length() <= 0.25) {
				v = new Vector(0, 0, 0);
				return;
			}
		}
	}
	
	public void setVelocity(Vector v, boolean from_kick) {
		//v = v.setY(0).normalize();
		//this.v = v.setY(0.7).multiply(VMAX/2.1);
		if (from_kick) {
			Vector nv = v.normalize().setY(0.7).multiply(VMAX/2.1);
			this.v.add(nv);
		}
		else this.v = v;
	}
	
	public void addGravity() {
		v.add(new Vector(0, -0.1, 0));
	}

}
