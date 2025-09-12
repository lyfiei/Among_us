package com.edu.example.amongus.ui;

import com.edu.example.amongus.logic.GameConfig;
import javafx.fxml.FXML;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class StartMenuController {

    @FXML private TextField nameField;
    @FXML private ImageView characterView;

    private final String[] colors = {"green", "red", "blue", "yellow", "purple"};
    private int currentIndex = 0;

    @FXML
    public void initialize() {
        updateCharacter();
    }

    @FXML
    private void onPrev() {
        currentIndex = (currentIndex - 1 + colors.length) % colors.length;
        updateCharacter();
    }

    @FXML
    private void onNext() {
        currentIndex = (currentIndex + 1) % colors.length;
        updateCharacter();
    }

    @FXML
    private void onStart() {
        String playerName = nameField.getText().trim();
        String skinColor = colors[currentIndex];

        if (playerName.isEmpty()) {
            playerName = "Player";
        }

        // 保存到全局配置
        GameConfig.setPlayerName(playerName);
        GameConfig.setPlayerColor(skinColor);

        System.out.println("玩家昵称: " + playerName);
        System.out.println("选择皮肤: " + skinColor);

        // TODO: 把玩家名称 & 皮肤传递给 GameApp / GameClient
    }

    private void updateCharacter() {
        String color = colors[currentIndex];
        String path = "/com/edu/example/amongus/images/" + color + ".png";
        characterView.setImage(new Image(getClass().getResource(path).toExternalForm()));
    }
}
