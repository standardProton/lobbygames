package me.c7dev.lobbygames.util;

public class CoordinatePair {
	
	private int xn, yn;
	public CoordinatePair(int x, int y) {
		this.xn = x; this.yn = y;
	}
	
	public int getX() {return this.xn;}
	public int getY() {return this.yn;}
	
	public CoordinatePair setX(int x) {this.xn = x; return this;}
	public CoordinatePair setY(int y) {this.yn = y; return this;}
	
	public boolean equals(CoordinatePair c) {return (c.getX() == xn && c.getY() == yn);}
	
	public CoordinatePair clone() {return new CoordinatePair(xn, yn);}
	
	public CoordinatePair add(CoordinatePair add) {
		xn += add.getX();
		yn += add.getY();
		return this;
	}
	public CoordinatePair multiply(int m) {
		xn *= m;
		yn *= m;
		return this;
	}
	
	public String toString() {
		return "(" + xn + ", " + yn + ")";
	}

}
