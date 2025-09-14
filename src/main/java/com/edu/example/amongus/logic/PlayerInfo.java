package com.edu.example.amongus.logic;

import com.edu.example.amongus.Player;
import com.edu.example.amongus.PlayerStatus;

public class PlayerInfo {
    private final String id;
    private String nickname;
    private String color;
    private double x;
    private double y;
    private PlayerStatus status = PlayerStatus.ALIVE; // 默认存活

    private boolean alive = true;
    private Player.PlayerType type = Player.PlayerType.GOOD;

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
    public PlayerStatus getStatus() { return status; }
    public Player.PlayerType getType() { return type; }

    public void setX(double x) { this.x = x; }
    public void setY(double y) { this.y = y; }
    public void setStatus(PlayerStatus status) { this.status = status; }

    // 辅助方法：是否存活
    public boolean isAlive() {
        return status == PlayerStatus.ALIVE;
    }

    public String getNick() {
        return nickname;
    }

    public void setAlive(boolean b) {
        this.status = b ? PlayerStatus.ALIVE : PlayerStatus.DEAD;
    }
    public void setType(Player.PlayerType type) { this.type = type; }
}
