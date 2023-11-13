package me.c7dev.lobbygames.util;

import java.util.HashMap;
import java.util.Map;

public class LeaderboardEntry {
	
	private int score;
	private long expire;
	private String displayscore, displayname;
	
	public LeaderboardEntry(String display_name, int score, String display_score, long expiry) {
		this.score = score;
		expire = expiry > 0 ? System.currentTimeMillis() + (expiry*1000) : -1;
		displayscore = display_score == null ? "" + score : display_score;
		displayname = display_name;
	}
	
	
	public int getScore() {return score;}
	public long getExpiration() {return expire;}
	public void setRawExpiration(long e) {expire = e;}
	public void setExpiration(long e) {
		expire = e > 0 ? System.currentTimeMillis() + (e*1000) : -1;
	}
	public boolean isExpired() {return (expire > 0 && System.currentTimeMillis() > expire);}
	public String getDisplayScore() {return displayscore;}
	public String getDisplayName() {return displayname;}
	
	public void setDisplayScore(String s) {this.displayscore = s;}
	public void setDisplayName(String s) {this.displayname = s;}
	public void setScore(int s) {score = s;}
	
	public Map<String,Object> serialize(){
		Map<String,Object> m = new HashMap<>();
		m.put("display_name", displayname);
		m.put("score", score);
		m.put("expires", expire);
		if (displayscore != null && !displayscore.equals("" + score)) m.put("display_score", displayscore);
		return m;
	}

}
