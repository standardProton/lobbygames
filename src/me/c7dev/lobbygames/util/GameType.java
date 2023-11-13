package me.c7dev.lobbygames.util;

public enum GameType {

	SNAKE(0, false, false, true, true),
	MINESWEEPER(1, false, true, false, true),
	SPLEEF(2, true, true, false, true),
	CLICKER(3, false, true, false, true),
	SOCCER(4, true, true, false, false),
	SUDOKU(5, false, true, true, true),
	T048(6, false, false, false, true),
	TICTACTOE(7, true, true, true, false),
	POOL(8, true, true, false, false),
	CONNECT4(9, true, true, true, false);
	
	private final int id;
	private final boolean mp, direct_mapping, vertical_supported, leaderboard;
	
	
	GameType(int idn, boolean multiplayer, boolean rotspec, boolean verticalsupp, boolean leaderboard) {
		this.id = idn; this.mp = multiplayer; this.direct_mapping = rotspec; this.vertical_supported = verticalsupp; this.leaderboard = leaderboard;
	}
	public int getId() {return this.id;}
	public boolean isMultiplayer() {return this.mp;}
	public boolean isDirectBlockMapping() {return this.direct_mapping;} //if no rotation needed to get coord location
	public boolean canSupportVerticalArena() {return this.vertical_supported;}
	public boolean usesLeaderboard() {return this.leaderboard;}

}
