package me.c7dev.lobbygames.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.configuration.file.FileConfiguration;

public class PlayerStats {
	
	private String display_score = "";
	private int score = Integer.MIN_VALUE, games_played, seconds_played;
	
	public PlayerStats(int score, String display_score, int times_joined, int seconds_played) {
		this.display_score = display_score;
		this.score = score; this.games_played = times_joined;
		this.seconds_played = seconds_played;
	}
	
	public int getGamesPlayed() {return this.games_played;}
	public int getScore() {return this.score;}
	public int getSecondsPlayed() {return this.seconds_played;}
	public String secondsPlayed() {return GameUtils.timeStr(seconds_played);}
	public String getDisplayScore() {return this.display_score;}
	
	public void setGamesPlayed(int n) {this.games_played = n;}
	public void setScore(int n) {this.score = n;}
	public void setSecondsPlayed(int n) {this.seconds_played = n;}
	public void setDisplayScore(String s) {this.display_score = s;}
	
	public Map<String,Object> serialize(){
		Map<String,Object> m = new HashMap<>();
		m.put("score", score);
		m.put("display-score", display_score);
		m.put("games-played", games_played);
		m.put("time-played", seconds_played);
		return m;
	}
	
	public static HashMap<GameType,PlayerStats> deserialize(FileConfiguration afile, UUID u) {
		HashMap<GameType, PlayerStats> hs = new HashMap<>();
		for (GameType gt : GameType.values()) {
			String dir = u.toString() + "." + gt.toString().toLowerCase();
			if (afile.get(dir) != null) {
				hs.put(gt, new PlayerStats(afile.getInt(dir + ".score"),
						afile.getString(dir + ".display-score"),
						afile.getInt(dir + ".games-played"),
						afile.getInt(dir + ".time-played")));
			}
		}
		return hs;
	}

}
