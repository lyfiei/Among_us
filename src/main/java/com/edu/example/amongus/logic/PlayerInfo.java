package com.edu.example.amongus.logic;

public class PlayerInfo {
    public enum PlayerType { GOOD, EVIL }

    private final String id;
    private String nick;
    private String color;
    private double x;
    private double y;
    private boolean alive = true;
    private PlayerType type = PlayerType.GOOD; // 默认好人

    public PlayerInfo(String id, String nickname, String color, double x, double y) {
        this.id = id;
        this.nick = nickname;
        this.color = color;
        this.x = x;
        this.y = y;
    }

    // --- getter ---
    public String getId() { return id; }
    public String getNick() { return nick; }
    public String getColor() { return color; }
    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isAlive() { return alive; }
    public PlayerType getType() { return type; }

    // --- setter ---
    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAlive(boolean a) { this.alive = a; }
    public void setType(PlayerType type) { this.type = type; }
}
