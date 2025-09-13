package com.edu.example.amongus.logic;

public class PlayerInfo {
    private final String id;
    private String nickname;
    private String color;
    private double x;
    private double y;
    private boolean alive = true;

    public PlayerInfo(String id, String nickname, String color, double x, double y) {
        this.id = id;
        this.nickname = nickname;
        this.color = color;
        this.x = x;
        this.y = y;
    }

    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getColor() { return color; }
    public double getX() { return x; }
    public double getY() { return y; }
    public boolean isAlive() { return alive; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setAlive(boolean a) { this.alive = a; }

    public String getNick() {
        return nickname;
    }
}
