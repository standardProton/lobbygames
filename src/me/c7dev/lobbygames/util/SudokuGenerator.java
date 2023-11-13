package me.c7dev.lobbygames.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public class SudokuGenerator {
	
	int[][] spaces;
	Random r;
	boolean finished = false;
	List<Integer> numbers = new ArrayList<>();
	Integer[] numbers_a = new Integer[9];
	
	public SudokuGenerator() {
		r = new Random();
		spaces = new int[9][9];
		for (int i = 0; i < 9; i++) {
			numbers.add(i+1);
			for (int j = 0; j < 9; j++) {
				spaces[i][j] = 0;
			}
		}
	}
	
	public void loadSudoku(int[][] s) {
		spaces = s;
		finished = true;
	}
	
	public int[][] generateSudoku() throws Exception {
		if (finished) return spaces;
		int k = 0;
		while (!generateSudokuIteration()) {
			if (k >= 10000) {
				throw new Exception("Could not generate a new Sudoku puzzle!");
			}
			k++;
			for (int i = 0; i < 9; i++) {
				for (int j = 0; j < 9; j++) {
					spaces[i][j] = 0;
				}
			}
		}
		return spaces;
	}
	
	public boolean generateSudokuIteration(){
				
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				if (!generateNumber(i, j)) return false;
			}
		}
		
		finished = true;
		return true;
		
	}
	
	public boolean generateNumber(int row, int column) { //combination of cross and box generators
		int n = 0;
		Collections.shuffle(numbers);
		for (int i = 0; i < 10; i++) {
			if (i == 9) {
				n = 0;
				return false;
			}
			n = numbers.get(i);
			if (unfilledInBox(row, column, n) && unfilledInCross(row, column, n)) break;
		}
		spaces[column][row] = n;
		return true;
	}
	
	public boolean unfilledInCross(int row, int column, int num) { //find a number previously unused in column and row
		for (int i = 0; i < 9; i++) {
			if (spaces[column][i] == num) {
				return false;
			}
		}
		for (int i = 0; i < 9; i++) {
			if (spaces[i][row] == num) {
				return false;
			}
		}
		return true;
	}
	
	public boolean unfilledInBox(int row, int column, int num) { //find a number previously unused in 3x3 box
		int rowstart = row - (row % 3);
		int colstart = column - (column % 3);
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				if (spaces[i + colstart][j + rowstart] == num) {
					return false;
				}
			}
		}
		return true;
	}
	
	public boolean gradeSudoku() { //is valid sudoku solution
		if (!finished) return false;
		for (int i = 0; i < 9; i++) {
			if (!gradeCross(i, i)) return false;
		}
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				if (!gradeBox(i*3, j*3)) return false;
			}
		}
		return true;
	}
	
	public boolean gradeCross(int row, int column) {
		List<Integer> a = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			int num = spaces[column][i];
			if (num == 0 || a.contains(num)) return false;
			a.add(num);
		}
		List<Integer> b = new ArrayList<>();
		for (int i = 0; i < 9; i++) {
			int num = spaces[i][row];
			if (num == 0 || b.contains(num)) return false;
			b.add(num);
		}
		return a.size() == 9 && b.size() == 9;
	}
	
	public boolean gradeBox(int rowStart, int colStart) {
		List<Integer> a = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			for (int j = 0; j < 3; j++) {
				int num = spaces[i + colStart][j + rowStart];
				if (num == 0 || a.contains(num)) return false;
				a.add(num);
			}
		}
		return a.size() == 9;
	}
	
	public boolean isFinished() {return this.finished;}
	
	public int unfilledCount() {
		int count = 0;
		for (int i = 0; i < 9; i++) {
			for (int j = 0; j < 9; j++) {
				if (spaces[i][j] == 0) count++;
			}
		}
		return count;
	}

}
