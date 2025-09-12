package com.edu.example.amongus;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;

public class Mapp {
    private final ImageView mapView;
    private final PixelReader collisionReader;

    public Mapp(Image mapImage, Image collisionImage) {
        mapView = new ImageView(mapImage);
        mapView.setFitWidth(GameConstants.MAP_WIDTH);
        mapView.setFitHeight(GameConstants.MAP_HEIGHT);
        collisionReader = collisionImage.getPixelReader();//用于读取图像中各个像素的颜色信息
    }

    public ImageView getMapView() {
        return mapView;
    }

    public PixelReader getCollisionReader() {
        return collisionReader;
    }
}
