package com.edu.example.amongus.task;

public interface Task {
    void start();                     // 开始任务
    void complete();                  // 手动完成任务
    boolean isActive();               // 是否正在进行
    boolean isCompleted();            // 是否完成

    // —— 新增 —— 多步任务支持
    void completeOneStep();           // 完成一个步骤
    int getTotalSteps();              // 总步数
    int getCompletedSteps();          // 已完成步数
    void setCompletedSteps(int steps);
    void setTaskCompleteListener(TaskCompleteListener listener);

    interface TaskCompleteListener {
        void onTaskComplete(boolean success);
    }
}
