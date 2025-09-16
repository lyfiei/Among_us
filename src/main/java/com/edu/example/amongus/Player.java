package com.edu.example.amongus;

import com.edu.example.amongus.logic.GameConfig;
import javafx.geometry.Bounds;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.util.List;

public class Player {
    private double x; // 玩家X坐标
    private double y; // 玩家Y坐标
    private final ImageView view; // 玩家视图
    private final PixelReader collisionReader; // 碰撞像素读取器
    private PlayerStatus status = PlayerStatus.ALIVE;
    private static final double KILL_RANGE = 200; // 可调整杀人有效范围
    private long lastKillTime = 0;       // 上一次杀人的时间戳
    private static final long KILL_COOLDOWN = 30000; // 冷却 30 秒

    private String name; // 玩家名字，用于调试


    public String getId() {
        return GameConfig.getPlayerId();
    }

    public String getNick() {
        return GameConfig.getPlayerName();

    }

    public String getColor() {
        return GameConfig.getPlayerColor();
    }


    public enum PlayerType {
        GOOD, EVIL
    }
    private  PlayerType type; // 新增类型字段


    public Player(double startX, double startY, Image playerImage, PixelReader collisionReader, PlayerStatus status) {
        this.x = startX;
        this.y = startY;
        this.view = new ImageView(playerImage);
        this.view.setFitWidth(GameConstants.PLAYER_SIZE);
        this.view.setFitHeight(GameConstants.PLAYER_SIZE);
        this.collisionReader = collisionReader;
        this.status = status;
        this.type = null;
        updateView();
    }

    public Player(double startX, double startY, Image playerImage, PixelReader collisionReader) {
        this.x = startX;
        this.y = startY;
        this.view = new ImageView(playerImage);
        this.view.setFitWidth(GameConstants.PLAYER_SIZE);
        this.view.setFitHeight(GameConstants.PLAYER_SIZE);
        this.collisionReader = collisionReader;
        this.type = null;
        updateView();
    }

    // ----------------- 名字方法 -----------------
    public void setName(String name) { this.name = name; }
    public String getName() { return name; }

    @Override
    public String toString() {
        return name != null ? name : super.toString();
    }

    public PlayerType getType() {
        return type;
    }

    public void setType(PlayerType type) {
        this.type = type; // GOOD / EVIL
        // 可以根据 type 改变玩家外观或标识
    }

    public ImageView getView() { return view; }
    public double getX() { return x; }
    public double getY() { return y; }
    public PlayerStatus getStatus() { return status; }

    public void setStatus(PlayerStatus status) {
        this.status = status;
        if (status == PlayerStatus.DEAD) {
            view.setOpacity(0.4);
        } else {
            view.setOpacity(1.0);
        }
    }

    public void setPosition(double newX, double newY) {
        this.x = newX;
        this.y = newY;
        updateView();
    }


    public boolean isAlive() {
        return status == PlayerStatus.ALIVE;
    }

    public void move(double dx, double dy) {
        // 如果已经被淘汰，move 不应该被调用（caller 应负责），但这里再双保险不做移动
        if (!isAlive()) return;

        double newX = x + dx;
        double newY = y + dy;

        if (isValidPosition(newX, newY)) {
            x = newX;
            y = newY;
            updateView();

            if (dx > 0) view.setScaleX(-1);
            else if (dx < 0) view.setScaleX(1);
        }
    }

    private void updateView() {
        view.setX(x);
        view.setY(y);
    }

    private boolean isValidPosition(double x, double y) {
        int steps = 3;
        int threshold = 200;
        int offsetY = 2;

        for (int i = 0; i <= steps; i++) {
            int checkX = (int)(x + i * GameConstants.PLAYER_SIZE / steps);
            int checkY = (int)(y + GameConstants.PLAYER_SIZE - offsetY);

            if (checkX < 0 || checkX >= GameConstants.MAP_WIDTH ||
                    checkY < 0 || checkY >= GameConstants.MAP_HEIGHT) return false;

            int argb = collisionReader.getArgb(checkX, checkY);
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;

            if (r < threshold || g < threshold || b < threshold) return false;
        }
        return true;
    }

    public double getWidth() {
        return view.getFitWidth();
    }

    public double getHeight() {
        return view.getFitHeight();
    }

    // ----------------- 坏人杀人 -----------------
    public Player killNearbyPlayer(List<Player> allPlayers) {
        long now = System.currentTimeMillis();
        if (this.type != PlayerType.EVIL) return null;
        if (!this.isAlive()) return null;

        long remaining = getKillCooldownRemaining();
        if (remaining > 0) {
            System.out.println("杀人冷却中，还剩 " + remaining/1000 + " 秒");
            return null;
        }

        for (Player target : allPlayers) {
            if (target == this) continue;
            if (!target.isAlive()) continue;
            if (target.getType() == PlayerType.EVIL) continue;

            double cx1 = this.x + GameConstants.PLAYER_SIZE / 2.0;
            double cy1 = this.y + GameConstants.PLAYER_SIZE / 2.0;
            double cx2 = target.getX() + GameConstants.PLAYER_SIZE / 2.0;
            double cy2 = target.getY() + GameConstants.PLAYER_SIZE / 2.0;
            double distance = Math.hypot(cx1 - cx2, cy1 - cy2);

            if (distance <= KILL_RANGE) {
                target.setStatus(PlayerStatus.DEAD);
                lastKillTime = now;
                System.out.println("Player " + target + " has been killed by " + this);

                // 播放杀人音效
                try {
                    System.out.println("播放杀人音效");
                    String audioPath = getClass().getResource("/com/edu/example/amongus/audio/Impostor_kill.mp3").toExternalForm();
                    Media media = new Media(audioPath);
                    MediaPlayer mediaPlayer = new MediaPlayer(media);
                    mediaPlayer.play();
                } catch (Exception e) {
                    e.printStackTrace();
                }


                return target;
            }
        }

        System.out.println("附近没有可杀的玩家");
        return null;
    }

    // ----------------- 杀人冷却 -----------------
    /** 获取杀人剩余冷却时间（毫秒） */
    public long getKillCooldownRemaining() {
        long now = System.currentTimeMillis();
        long remaining = KILL_COOLDOWN - (now - lastKillTime);
        return Math.max(0, remaining);
    }

    /** 公开读取杀人判定范围（像素） */
    public static double getKillRange() {
        return KILL_RANGE;
    }

    /** 客户端调用：记录一次杀人（把 lastKillTime 设为当前时间） */
    public void markKillUsed() {
        this.lastKillTime = System.currentTimeMillis();
    }
    public Bounds getBounds() { return view.getBoundsInParent(); }
}
