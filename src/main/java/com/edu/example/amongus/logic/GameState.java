package com.edu.example.amongus.logic;

import com.edu.example.amongus.task.TaskStatus;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GameState {
    private final Map<String, PlayerInfo> players = new ConcurrentHashMap<>();
    private final Map<String, TaskStatus> taskStatuses = new ConcurrentHashMap<>();

    public void addOrUpdatePlayer(PlayerInfo p) {
        players.put(p.getId(), p);
    }

    public PlayerInfo getPlayer(String id) {
        return players.get(id);
    }

    public void removePlayer(String id) {
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
}
