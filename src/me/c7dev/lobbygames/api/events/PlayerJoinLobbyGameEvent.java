package me.c7dev.lobbygames.api.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import me.c7dev.lobbygames.Game;
import me.c7dev.lobbygames.util.GameType;

public class PlayerJoinLobbyGameEvent extends Event {
	
	Game g;
	Player p;
	public PlayerJoinLobbyGameEvent(Player player, Game game) {
		g = game;
		this.p = player;
	}
	
	public Game getGame() {
		return g;
	}
	
	public GameType getGameType() {
		return g.getGameType();
	}
	
	public Player getPlayer() {
		return p;
	}
	
	public int getDuration() {
		return g.getDuration();
	}
	
	public List<Player> getPlayers() {
		List<Player> r = new ArrayList<Player>();
		for (UUID u : g.getPlayers()) {
			Player p = Bukkit.getPlayer(u);
			if (p != null) r.add(p);
		}
		return r;
	}
		
	private static final HandlerList handlers = new HandlerList();
	
	public HandlerList getHandlers() {
		return handlers;
	}
	
	static public HandlerList getHandlerList() {
		return handlers;
	}

}
