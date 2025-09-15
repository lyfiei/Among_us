module com.edu.example.amongus {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;
    requires com.google.gson;
    requires java.desktop;

    opens com.edu.example.amongus.ui to javafx.fxml;
    opens com.edu.example.amongus to javafx.fxml;
    exports com.edu.example.amongus;
    exports com.edu.example.amongus.task;
    opens com.edu.example.amongus.task to javafx.fxml;

}