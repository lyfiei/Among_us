package com.edu.example.amongus;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

public class Map {
    private final ImageView mapView;
    private final PixelReader collisionReader;

    public Map(Image mapImage, Image collisionImage) {
        mapView = new ImageView(mapImage);
        mapView.setFitWidth(GameConstants.MAP_WIDTH);
        mapView.setFitHeight(GameConstants.MAP_HEIGHT);
        collisionReader = collisionImage.getPixelReader();
    }

    public ImageView getMapView() {
        return mapView;
    }

    public PixelReader getCollisionReader() {
        return collisionReader;
    }
}
