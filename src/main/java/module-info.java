module com.edu.example.amongus {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.edu.example.amongus to javafx.fxml;
    exports com.edu.example.amongus;
}