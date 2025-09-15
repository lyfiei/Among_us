package com.edu.example.amongus.ui;

import com.edu.example.amongus.Main;
import com.edu.example.amongus.logic.GameConfig;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;


public class StartMenuController {

    private MediaPlayer mediaPlayer; // 保存引用

    @FXML
    private StackPane rootPane; // FXML 最外层 StackPane
    @FXML
    private TextField nameField;
    @FXML
    private ImageView characterView;
    @FXML
    private Button prevButton;
    @FXML
    private Button nextButton;
    @FXML
    private Button startButton;
    @FXML
    private Button exitButton;

    @FXML
    private Button staffButton;

    private boolean hasJoined = false;
    private final String[] colors = {"green", "red", "blue", "yellow", "purple"};
    private int currentIndex = 0;



    @FXML
    public void initialize() {
        setupVideoBackground();
        updateCharacter();
    }

    private void setupVideoBackground() {
        String videoPath = getClass().getResource("/com/edu/example/amongus/videos/start_menu.mp4").toExternalForm();
        Media media = new Media(videoPath);
        MediaPlayer mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);

        MediaView mediaView = new MediaView(mediaPlayer);
        mediaView.setFitWidth(1000);  // 适配窗口
        mediaView.setFitHeight(600);
        mediaView.setPreserveRatio(false);

        rootPane.getChildren().add(0, mediaView); // 放在底层
        mediaPlayer.play();
    }

    // 切换到其他场景前调用
    public void stopVideo() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
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
        if (hasJoined) return;

        hasJoined = true;
        String playerName = nameField.getText().trim();
        String skinColor = colors[currentIndex];

        if (playerName.isEmpty()) playerName = "Player";

        GameConfig.setPlayerName(playerName);
        GameConfig.setPlayerColor(skinColor);

        System.out.println("玩家昵称: " + playerName);
        System.out.println("选择皮肤: " + skinColor);

        Main.joinGame();

    }

    private void updateCharacter() {
        String color = colors[currentIndex];
        String path = "/com/edu/example/amongus/images/" + color + ".png";
        characterView.setImage(new Image(getClass().getResource(path).toExternalForm()));
    }

    @FXML
    private void onExit() {
        // 退出整个 JavaFX 应用
        System.exit(0);
    }

    // 制作人员按钮点击事件
    @FXML
    private void onStaff() {
        Stage stage = new Stage();

        // 创建根面板
        AnchorPane pane = new AnchorPane();
        pane.setPrefSize(400, 300);

        // 背景图片
        ImageView bg = new ImageView(new Image(getClass().getResourceAsStream(
                "/com/edu/example/amongus/images/staff_bg.png"))); // 替换成你的图片路径
        bg.setFitWidth(400);
        bg.setFitHeight(300);
        pane.getChildren().add(bg);


        // 半透明覆盖层，必须先定义
        AnchorPane overlay = new AnchorPane();
        overlay.setPrefSize(400, 300);
        overlay.setStyle("-fx-background-color: rgba(0,0,0,0.4); -fx-background-radius: 20;");
        pane.getChildren().add(overlay);

        // 制作人员信息
        Text info = new Text( "制作人员：\n\n胡玥\n谭舒月\n李悦菲");
        info.setFill(javafx.scene.paint.Color.WHITE);
        info.setFont(Font.font("Mix Giants", 20)); // 可以替换成 Among Us 风格字体
        info.setTextAlignment(TextAlignment.CENTER);  // 文字居中

        // 使用 StackPane 居中
        StackPane stack = new StackPane();
        stack.setPrefSize(400, 300);
        stack.getChildren().add(info);
        stack.setAlignment(Pos.CENTER);
        overlay.getChildren().add(stack);

        Scene scene = new Scene(pane);
        stage.setTitle("制作人员");
        stage.setScene(scene);
        stage.show();
    }

}