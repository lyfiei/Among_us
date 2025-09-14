package com.edu.example.amongus.logic;

import com.edu.example.amongus.task.TaskStatus;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameState {
    private final Map<String, PlayerInfo> players = new ConcurrentHashMap<>();
    private final Map<String, TaskStatus> taskStatuses = new ConcurrentHashMap<>();
    private boolean gameStarted = false;
    private int maxPlayers = 5; // 或从配置里读

    public void addOrUpdatePlayer(PlayerInfo p) {
        players.put(p.getId(), p);
    }

    public PlayerInfo getPlayer(String id) {
        return players.get(id);
    }

    public synchronized void removePlayer(String id) {
        if (id == null) {
            System.out.println("removePlayer called with null, ignored");
            return;
        }
        players.remove(id);
    }

    public Collection<PlayerInfo> getAllPlayers() {
        return players.values();
    }

    public void addOrUpdateTask(TaskStatus task) {
        taskStatuses.put(task.getTaskName(), task);
    }

    public TaskStatus getTask(String taskName) {
        return taskStatuses.get(taskName);
    }

    public Map<String, TaskStatus> getTaskStatuses() {
        return taskStatuses;
    }

    /** 玩家数量是否满足开始游戏 */
    public synchronized boolean isReadyToStart() {
        return players.size() == maxPlayers;
    }

    /** 游戏是否已经开始 */
    public synchronized boolean isGameStarted() {
        return gameStarted;
    }

    /** 设置游戏开始标志 */
    public synchronized void setGameStarted(boolean started) {
        this.gameStarted = started;
    }

    /** 获取当前玩家数 */
    public synchronized int getPlayerCount() {
        return players.size();
    }

    /** 获取最大玩家数 */
    public int getMaxPlayers() {
        return maxPlayers;
    }
}
