package me.c7dev.lobbygames.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class ConsoleJoinCommand implements CommandExecutor {
	
	LobbyGames plugin;
	String noperm;
	public ConsoleJoinCommand(LobbyGames plugin) {
		this.plugin = plugin;
		this.noperm = plugin.getConfigString("no-permission", "§cYou don't have permission!");
		plugin.getCommand("lgjoinplayer").setExecutor(this);
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (sender.hasPermission("lobbygames.forcejoin") || sender.hasPermission("lobbygames.admin")) {
			if (args.length >= 2) {
				Player p = Bukkit.getPlayer(args[0]);
				if (p == null) {
					sender.sendMessage("§4Error: §c" + args[0] + " is not online!");
					return true;
				}
				
				GameType gt = GameType.valueOf(GameUtils.incomingAliases(args[1], plugin).toUpperCase());
				if (gt == null) {
					p.sendMessage("§4Error: §c'" + label + "' is not an existing game type!");
					return true;
				}
				
				int id = -1;
				if (args.length >= 3 && !args[2].equalsIgnoreCase("tac")) {
					try {
						id = Integer.parseInt(args[2]);
					} catch (Exception ex) {
						p.sendMessage("§4Error: §c'" + args[2] + "' is not a number!");
						return true;
					}
				}
				
				plugin.joinPlayer(p, gt, id);
								
			} else sender.sendMessage("§4Usage: §c/" + label.toLowerCase() + " <player> <game> [arena id]");
		} else sender.sendMessage(noperm);
		return true;
	}

}
