package me.c7dev.lobbygames.util;

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import me.c7dev.lobbygames.LobbyGames;

public class ClickBlock {
	
	private boolean green = true, clicked = false;
	private ArmorStand as = null;
	private BukkitRunnable onRemove = null;
	
	public ClickBlock(Location loc, LobbyGames plugin) {
		green = (new Random()).nextDouble() < 0.6;
		as = loc.getWorld().spawn(loc, ArmorStand.class);
		as.setVisible(false);
		as.setGravity(false);
		String nametag = green ? plugin.getConfigString("clicker.green-blocks", "§aClick!") : plugin.getConfigString("clicker.red-blocks", "§cDon't Click!");
		as.setCustomName(nametag);
		as.setCustomNameVisible(nametag.length() > 0);
		if (LobbyGames.SERVER_VERSION >= 12) as.setSilent(true);
		
		as.setHelmet(LobbyGames.SERVER_VERSION <= 12 ? new ItemStack(Material.valueOf("WOOL"), 1, green ? (byte) 5 : 14) : new ItemStack(green ? Material.LIME_WOOL : Material.RED_WOOL, 1));
		
		int ticks0 = (int) (plugin.getConfig().getDouble("clicker.seconds-in-air") * 10);
		if (ticks0 < 8) ticks0 = 8;
		if (ticks0 > 45) ticks0 = 45;
		
		final int ticks = ticks0;
		final double delta = 1.0/ticks;
		
		new BukkitRunnable() {
			int i = 0;
			
			@Override
			public void run() {
				if (i < ticks) {
					as.teleport(as.getLocation().add(0, delta, 0));
					i++;
				} else if (i < ticks << 1) {
					as.teleport(as.getLocation().add(0, -delta, 0));
					i++;
				} else {
					as.remove();
					this.cancel();
					if (onRemove != null) onRemove.runTask(plugin);
				}
			}
		}.runTaskTimer(plugin, 0, 1l);
	}
	
	public boolean isGreen() {return green;}
	public boolean isClicked() {return clicked;}
	public ArmorStand getArmorStand() {return as;}
	public void setRemoveRunnable(BukkitRunnable r) {this.onRemove = r;}
	
	public void click() {
		if (clicked) return;
		this.clicked = true;
		as.setCustomName(green?"§a§l+1":"§c§l-3");
		as.setCustomNameVisible(true);
		as.setHelmet(LobbyGames.SERVER_VERSION <= 12 ? new ItemStack(Material.valueOf("WOOL"), 1, (byte) 8) : new ItemStack(Material.LIGHT_GRAY_WOOL, 1));
	}

}
