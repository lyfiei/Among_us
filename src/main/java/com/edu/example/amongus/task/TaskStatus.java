package com.edu.example.amongus.task;

public class TaskStatus {
    private final String taskName;   // 任务名
    private final int total;         // 总步数
    private int completed = 0;       // 已完成步数

    // 构造函数支持初始化已完成步数
    public TaskStatus(String taskName, int total, int completed) {
        this.taskName = taskName;
        this.total = total;
        this.completed = completed;
    }

    // 完成一步任务
    public void completeOne() {
        if (completed < total) completed++;
    }

    // 直接设置完成进度（同步 Task 时更方便）
    public void setCompleted(int completed) {
        this.completed = Math.min(completed, total);
    }

    public int getCompleted() {
        return completed;
    }

    public int getTotal() {
        return total;
    }

    public String getTaskName() { return taskName; }

    // 获取状态文本，例如 "下载数据 (1/2)"
    public String getStatusText() {
        return taskName + " (" + completed + "/" + total + ")";
    }


}
