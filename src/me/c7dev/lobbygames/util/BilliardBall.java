package me.c7dev.lobbygames.util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.games.Pool;

public class BilliardBall {
	
	private int number;
	private ArmorStand as;
	private boolean sunk = false;
	private Pool game;
	private double v_progress = 0;
	
	private Vector v = new Vector(0, 0, 0);
	
	private List<Integer> collided = new ArrayList<Integer>(), collided_lastframe = new ArrayList<Integer>();
	
	private Location v_prog_loc = null;
	
	private Vector as_offset;
	
	public static double BALL_RADIUS = 0.0945, BALL_DIAMETER = 2*0.0945, VMAX = 0.45, VMIN = 0.0005, FRICTION = 0.98, BOUNCE = -0.95, COLLIDE = 0.95;
	
	public BilliardBall(int n, Location loc, Pool game) {
		String ballstr = game.getWords()[0];
		number = n;
		loc.setYaw(0f);
		loc.setPitch(0f);
		this.game = game;
		
		as_offset = new Vector(0.333333, 0.66, -0.125);
		//else as_offset = new Vector(0.333333, 0.57, -0.1);
		
		as = loc.getWorld().spawn(loc.clone().add(as_offset), ArmorStand.class);
		as.setVisible(false);
		as.setSmall(true);
		as.setArms(true);
		as.setRightArmPose(new EulerAngle(Math.toRadians(-15), Math.PI/4, Math.toRadians(1))); //-15, pi/4, 1
		if (LobbyGames.SERVER_VERSION >= 12) as.setSilent(true);
		//else as.setRightArmPose(new EulerAngle(Math.toRadians(-41), Math.toRadians(40), Math.toRadians(-18)));
		
		as.setGravity(false);
		
		int[] colors = {4, 11, 14, 10, 1, 5, 12, 15};
		
		ItemStack hand = null;
		if (n == 0) hand = new ItemStack(Material.QUARTZ_BLOCK);
		else {
			if (game.isSameColor()) {
				hand = GameUtils.createWool(1, n == 8 ? 15 : (n < 8 ? game.getSolidsSC() : game.getStripesSC()), "§f§l" + n + " " + ballstr);
			} else {
				if (n <= 8) hand = GameUtils.createWool(1, colors[n-1], "§f§l" + n + " " + ballstr);
				else if (n <= 15) hand = GameUtils.createClay(1, colors[n-9], "§f§l" + n + " " + ballstr);
			}
		}
		if (hand == null) {
			sunk = true;
			return;
		}
		
		if (LobbyGames.SERVER_VERSION < 12) as.getEquipment().setItemInHand(hand);
		else as.getEquipment().setItemInMainHand(hand);
		
	}
	
	public boolean tick() { //update velocity and position
		double len = v.length();
		if (len == 0) return false;
		if (len < VMIN) {
			v = new Vector(0, 0, 0);
			game.checkInHole(getLocation(), this);
			return false;
		}
		else if (len > VMAX) v.normalize().multiply(VMAX);
		
		if (v_progress >= len || len < BALL_DIAMETER) {
			as.teleport(as.getLocation().add(v));
			v.multiply(FRICTION);
		} else {
			v_progress += BALL_RADIUS;
			v_prog_loc = as.getLocation().add(v.clone().normalize().multiply(v_progress));
			return true;
		}
		return false;
	}
	
	public List<Integer> getCollided(){
		return collided;
	}
	
	public void shiftCollisionList() {
		//collided_lastframe.clear();
		//for (int i = 0; i < collided.size(); i++) collided_lastframe.add(collided.get(i));
		v_progress = 0;
		v_prog_loc = null;
		collided_lastframe.clear();
		for (int i = 0; i < collided.size(); i++) collided_lastframe.add(collided.get(i));
		collided.clear();
	}
	
	public boolean collide(BilliardBall collide) {
		if (number != collide.getNumber() && Math.abs(fastLocation().getX() - collide.fastLocation().getX()) <= BALL_DIAMETER && Math.abs(fastLocation().getZ() - collide.fastLocation().getZ()) <= BALL_DIAMETER
				&& fastLocation().distance(collide.fastLocation()) <= BALL_DIAMETER && !this.sunk && !collide.isSunk()
				&& !collided.contains(collide.getNumber()) && !collided_lastframe.contains(collide.getNumber())) {
			if (v_progress > 0) as.teleport(v_prog_loc);
			
			//vector pointing from center1 to center2
			Vector cv = collide.getLocation().toVector().subtract(this.getLocation().toVector()).normalize();
			
			double theta = cv.angle(v);
			double phi = cv.clone().multiply(-1).angle(collide.getVelocity());
			if (Double.isNaN(theta)) return false;
			
			//magnitude of force of impact
			double F_imp = COLLIDE*(this.getVelocity().length() + collide.getVelocity().length()) * Math.cos(Math.min(Math.abs(theta), Double.isNaN(phi) ? 7 : Math.abs(phi)));
			
			//add F_imp in direction of cv vector to velocity
			collide.setVelocity(collide.getVelocity().add(cv.clone().multiply(F_imp)));
			this.setVelocity(getVelocity().subtract(cv.multiply(F_imp)));
			
			//collide.addFImp(F_imp);
			//this.addFImp(F_imp.clone().multiply(-1));
						
			collided.add(collide.getNumber());
			collide.getCollided().add(number);
			return F_imp > 0.001;
		}
		return false;
	}
	
	public void sink() {
		if (sunk) return;
		sunk = true;
		v = new Vector(0, 0, 0);
		as.remove();
	}
	
	public int getNumber() {return number;}
	public boolean isSunk() {return sunk;}
	public UUID getUniqueId() {return as.getUniqueId();}
	public Location getLocation() {return as.getLocation().subtract(as_offset);}
	public Location fastLocation() {
		return v_prog_loc == null ? as.getLocation() : v_prog_loc;
	}
	public void setLocation(Location loc) {as.teleport(loc.add(as_offset));}
	public void teleport(Location loc) {
		loc.setYaw(0f);
		loc.setPitch(0f);
		as.teleport(loc.add(as_offset));
		
	}
	
	public Vector getVelocity() {return v;}
	
	public void setVelocity(Vector v2) {
		v = v2.setY(0);
	}
	
	

}
