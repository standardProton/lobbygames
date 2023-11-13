package me.c7dev.lobbygames.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import me.c7dev.lobbygames.Arena;
import me.c7dev.lobbygames.LobbyGames;
import me.c7dev.lobbygames.util.GameType;
import me.c7dev.lobbygames.util.GameUtils;

public class JoinCommand implements CommandExecutor {
	
	private LobbyGames plugin;
	public JoinCommand(LobbyGames plugin) {
		this.plugin = plugin;
		plugin.getCommand("lgjoin").setExecutor(this);
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (!(sender instanceof Player)) {
			sender.sendMessage("§cYou must be a player to do this!");
			return true;
		}
		
		Player p = (Player) sender;

		if (label.equalsIgnoreCase("lgjoin")) {
			p.sendMessage("§4Error: §c'" + label + "' is not an existing game type!");
			return true;
		}
		
		GameType gt = GameType.valueOf(GameUtils.incomingAliases(label, plugin).toUpperCase().replaceAll("LOBBYGAMES:", ""));
		if (gt == null) {
			p.sendMessage("§4Error: §c'" + label + "' is not an existing game type!");
			return true;
		}
		
		int id = -1;
		if (args.length >= 1 && !args[0].equalsIgnoreCase("tac")) {
			try {
				id = Integer.parseInt(args[0]);
			} catch (Exception ex) {
				p.sendMessage("§4Error: §c'" + args[0] + "' is not a number!");
				return true;
			}
		}
		
		plugin.joinPlayer(p, gt, id);
		
		return true;
	}

}
