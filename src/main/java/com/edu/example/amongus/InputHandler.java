package com.edu.example.amongus;

import javafx.scene.input.KeyCode;

import java.util.HashSet;
import java.util.Set;

public class InputHandler {
    private final Set<KeyCode> pressedKeys = new HashSet<>();

    public void press(KeyCode code) {
        pressedKeys.add(code);
    }

    public void release(KeyCode code) {
        pressedKeys.remove(code);
    }

    public boolean isPressed(KeyCode code) {
        return pressedKeys.contains(code);
    }
}

