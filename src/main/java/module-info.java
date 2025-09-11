module com.edu.example.among_us {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.edu.example.among_us to javafx.fxml;
    exports com.edu.example.among_us;
}