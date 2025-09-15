package com.edu.example.amongus.logic;

import com.edu.example.amongus.net.GameClient;
import com.edu.example.amongus.net.Message;
import com.edu.example.amongus.ui.MatchUI;
import com.edu.example.amongus.ui.MatchUpdateListener;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


public class GameConfig {
    private static String playerId;  // 唯一 ID
    private static String playerName;
    private static String playerColor;
    private static String playerRole; // GOOD / EVIL
    private static boolean joined = false;

    private String myRole;  // GOOD 或 EVIL

    private static GameClient client;
    private static MatchUpdateListener matchUpdateListener;

    public static void setMatchUpdateListener(MatchUpdateListener listener) {
        matchUpdateListener = listener;
    }




    // ====== 本地配置 ======

    public static String getPlayerId() {
        return playerId;
    }

    public static void setPlayerId(String id) {
        playerId = id;
    }

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

    public static String getPlayerRole() {
        return playerRole;
    }

    public static void setPlayerRole(String role) {
        playerRole = role;
    }

    public static boolean isJoined() {
        return joined;
    }

    public static void setJoined(boolean value) {
        joined = value;
    }


    // ====== 网络相关 ======
    public static void initNetwork(String host, int port) throws IOException {
        client = new GameClient(host, port, GameConfig::handleServerMessage);
    }


    public static void handleServerMessage(Message.Parsed parsed) {
        if (parsed == null) return;
        switch (parsed.type) {
            case "ROLE":
                String role = parsed.payload.get("type");
                System.out.println("你的角色是: " + role);
                setPlayerRole(role);     // ⚡ 把角色写进 Player 对象
                break;
            case "GAME_START":
                System.out.println("游戏开始！");

                PauseTransition delay = new PauseTransition(Duration.seconds(0.5));
                delay.setOnFinished(event -> {
                    System.out.println("收到服务器 GAME_START 后启动游戏runLater 执行了");
                    com.edu.example.amongus.Main.startGameScene();
                    System.out.println("你的角色是 " + com.edu.example.amongus.logic.GameConfig.getPlayerRole());
                });
                delay.play(); // ✅ 这一步启动计时
                delay.play();
                break;
            case "MATCH_UPDATE":
                int current = Integer.parseInt(parsed.payload.get("current"));
                int total = Integer.parseInt(parsed.payload.get("total"));
                if (matchUpdateListener != null) {
                    Platform.runLater(() -> matchUpdateListener.onMatchUpdate(current, total));
                }
                break;
        }
    }
    public static GameClient getClient() {
        return client;
    }
}

