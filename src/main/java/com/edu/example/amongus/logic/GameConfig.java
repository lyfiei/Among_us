package com.edu.example.amongus.logic;

public class GameConfig {
    private static String playerName;
    private static String playerColor;

    public static String getPlayerName() {
        return playerName;
    }

    public static void setPlayerName(String name) {
        playerName = name;
    }

    public static String getPlayerColor() {
        return playerColor;
    }

    public static void setPlayerColor(String color) {
        playerColor = color;
    }
}
