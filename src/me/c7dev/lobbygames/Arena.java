package me.c7dev.lobbygames;

import java.util.Collection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.util.Vector;

import me.c7dev.lobbygames.commands.GameCreateInstance;
import me.c7dev.lobbygames.util.CoordinatePair;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.Leaderboard;

public class Arena {
	
	private int id, width, height, rotation=0;
	private Location center, s1, l1, l2, spec1 = null, spec2 = null;
	boolean vertical, z_plane, valid_configuration = false;
	private Game hosting = null;
	private GameType gt;
	private Leaderboard board = null;
	private Vector as_offset, generic_offset;
	
	public static boolean isValidCoords(Location l1, Location l2) {
		if (l1 == null || l2 == null) {
			Bukkit.getLogger().warning("A coordinate was null when creating a LobbyGames arena!");
			return false;
		}
		
		int diff_axes = 0; //must have exactly 1 shared plane in x, y, z
		if (l1.getBlockX() != l2.getBlockX()) diff_axes++;
		if (l1.getBlockY() != l2.getBlockY()) diff_axes++;
		if (l1.getBlockZ() != l2.getBlockZ()) diff_axes++;
		
		return (diff_axes == 2) && (l1.getWorld().getName() == l2.getWorld().getName())
				&& (l2.getBlockY() >= l1.getBlockY());
	}
	
	public Arena(int id, GameType gt, Location l1, Location l2, Location spawn1, Leaderboard leaderboard, Location spec1_loc, Location spec2_loc) {
		this(id, gt, l1, l2, spawn1, leaderboard);
		spec1 = spec1_loc;
		spec2 = spec2_loc;
	}
	
	public Arena(int id, GameType gt, Location l1, Location l2, Location spawn1, Leaderboard leaderboard) {
		this.id = id;
		this.s1 = spawn1;
		GameCreateInstance.blockLocation(l1); 
		GameCreateInstance.blockLocation(l2);
		this.l1 = l1;
		this.l2 = l2;
		this.gt = gt;
		this.board = leaderboard;
		
		//l1 and l2 create corners of a square that is on the xy, zy, or xz plane, l1 is the bottom left
		vertical = l1.getY() != l2.getY();
		z_plane = l1.getX() == l2.getX();
				
		width = (int) (z_plane ? Math.abs(l2.getZ() - l1.getZ()) : Math.abs(l2.getX() - l1.getX())) + 1;
		height = (int) (vertical ? Math.abs(l2.getY() - l1.getY()) : Math.abs(l2.getZ() - l1.getZ())) + 1;
		
		center = new Location(l1.getWorld(),
				(int) (l1.getBlockX() + l2.getBlockX())/2,
				vertical ? (int) ((l1.getBlockY() + l2.getBlockY())/2) : l1.getBlockY(),
				(int) (l1.getBlockZ() + l2.getBlockZ())/2);
		
		
		if (!isValidCoords(l1, l2) && gt != GameType.CLICKER) return;
		
		generic_offset = new Vector(0, 1, 0); //normal vector to plane of arena
		as_offset = new Vector(-0.5, 0.75, -0.5); //armor stand offset to display hologram above a block
		
		if (!vertical) { //set quadrant that l2 is in 0-3			
			if (l2.getX() > l1.getX()) rotation = l2.getZ() > l1.getZ() ? 0 : 1;
			else rotation = l2.getZ() > l1.getZ() ? 3 : 2;
		}else {
			if (z_plane) rotation = l2.getZ() > l1.getZ() ? 1 : 3;
			else rotation = l2.getX() > l1.getX() ? 0 : 2;
			
			switch(rotation) {
			case 1: 
				generic_offset = new Vector(-1, 0, 0);
				as_offset = new Vector(0.3, 1.75, -0.5);
				break;
			case 2:
				generic_offset = new Vector(0, 0, -1);
				as_offset = new Vector(-0.5, 1.75, 0.3);
				break;
			case 3:
				generic_offset = new Vector(1, 0, 0);
				as_offset = new Vector(-1.2, 1.75, -0.5);
				break;
			default: 
				generic_offset = new Vector(0, 0, 1);
				as_offset = new Vector(-0.5, 1.75, -1.2);
			}
		}
		valid_configuration = true;
	
	}
	
	public boolean isInBoundsXZ(Location pixel) { //same as isInBounds but no Y check
		if (l1.getX() < l2.getX()) {
			if (pixel.getBlockX() < l1.getX() || pixel.getBlockX() > l2.getX()) return false;
		} else if (pixel.getBlockX() > l1.getX() || pixel.getBlockX() < l2.getX()) return false;
		
		if (l1.getZ() < l2.getZ()) {
			if (pixel.getBlockZ() < l1.getZ() || pixel.getBlockZ() > l2.getZ()) return false;
		} else if (pixel.getBlockZ() > l1.getZ() || pixel.getBlockZ() < l2.getZ()) return false;
		
		return true;
	}
	
	public static boolean isInBoundsXZ(Location pixel, Location l1, Location l2) { //same as isInBoundsXZ but static (game creation instance)
		if (l1.getX() < l2.getX()) {
			if (pixel.getBlockX() < l1.getX() || pixel.getBlockX() > l2.getX()) return false;
		} else if (pixel.getBlockX() > l1.getX() || pixel.getBlockX() < l2.getX()) return false;
		
		if (l1.getZ() < l2.getZ()) {
			if (pixel.getBlockZ() < l1.getZ() || pixel.getBlockZ() > l2.getZ()) return false;
		} else if (pixel.getBlockZ() > l1.getZ() || pixel.getBlockZ() < l2.getZ()) return false;
		
		return true;
	}
	
	public boolean isInBounds(Location pixel) {
		if (pixel.getBlockY() < l1.getY() || pixel.getBlockY() > l2.getY()) return false;
		
		if (l1.getX() < l2.getX()) {
			if (pixel.getBlockX() < l1.getX() || pixel.getBlockX() > l2.getX()) return false;
		} else if (pixel.getBlockX() > l1.getX() || pixel.getBlockX() < l2.getX()) return false;

		if (l1.getZ() < l2.getZ()) {
			if (pixel.getBlockZ() < l1.getZ() || pixel.getBlockZ() > l2.getZ()) return false; //changed "< l2.getZ()" to l1
		} else if (pixel.getBlockZ() > l1.getZ() || pixel.getBlockZ() < l2.getZ()) return false;

		return true;
	}
	
	public void clearArmorStands() {
		Collection<Entity> entities = null;
		
		double add = gt == GameType.POOL ? 1.25 : 1;
		
		if (isVerticalLayout()) {
			if (rotation == 0 || rotation == 2) 
				entities = l1.getWorld().getNearbyEntities(center.clone().add(generic_offset), (int) (width/2.0) + add, (int) (height/2.0) + add, 2);
			else entities = l1.getWorld().getNearbyEntities(center.clone().add(generic_offset), 2, (int) (height/2.0) + add, (int) (width/2.0) + add);
		} 
		else entities = l1.getWorld().getNearbyEntities(center, (int) (width/2.0) + add, 2, (int) (height/2.0) + add);
		
		for (Entity e : entities) {
			if (e instanceof ArmorStand) e.remove();
		}
	}
	
	
	public int getID() {return id;}
	
	@Deprecated
	public void setID() {this.id -= 1;} //ONLY use when deleting an arena with lower id
	
	public int getWidth() {return width;}
	public int getHeight() {return height;}
	public Game getHostingGame() {return hosting;}
	public boolean isHostingGame() {return hosting != null;}
	public boolean isVerticalLayout() {return vertical;}
	public boolean isVerticalZAxis() {return this.z_plane;}
	public int getCoordinateRotation() {return this.rotation;}
	public Location getCenterPixel() {return center.clone();}
	public Vector getArmorStandOffset() {return as_offset.clone();}
	public Vector getGenericOffset() {return generic_offset.clone();}
	public Location getLocation1() {return l1.clone();}
	public Location getLocation2() {return l2.clone();}
	public Location getSpawn1() {return s1;}
	public Location getSpecialLoc1() {return spec1;}
	public Location getSpecialLoc2() {return spec2;}
	public Location getLeaderboardLocation() {return board == null ? null : board.getLocation();}
	public Leaderboard getLeaderboard() {return this.board;}
	public boolean isValidConfiguration() {return this.valid_configuration;}
	public GameType getGameType() {return this.gt;}
	
	public void setHostingGame(Game g) {this.hosting = g;}
	

}
