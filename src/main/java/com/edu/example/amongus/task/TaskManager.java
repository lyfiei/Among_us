package com.edu.example.amongus.task;

import java.util.ArrayList;
import java.util.List;

public class TaskManager {
    private final List<Task> tasks = new ArrayList<>(); // 统一管理 Task 接口

    public void addTask(Task task) {
        tasks.add(task);
    }

    // 玩家位置触发任务（暂时空着）
    public void checkTasks(double playerX, double playerY) {
        for (Task task : tasks) {
            if (task.isCompleted() || task.isActive()) continue; // 已完成或进行中不触发
            // 暂时不触发
        }
    }

    // 判断所有任务是否完成
    public boolean allCompleted() {
        for (Task task : tasks) {
            if (!task.isCompleted()) return false;
        }
        return true;
    }
}
