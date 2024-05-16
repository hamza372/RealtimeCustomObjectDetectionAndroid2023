package com.example.odl.ml;

import android.graphics.RectF;

public class Recognition {
    String title;
    float confidence;
    String id;
    RectF location;

    public Recognition(String title, float confidence, String id, RectF location) {
        this.title = title;
        this.confidence = confidence;
        this.id = id;
        this.location = location;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public float getConfidence() {
        return confidence;
    }

    public void setConfidence(float confidence) {
        this.confidence = confidence;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public RectF getLocation() {
        return location;
    }

    public void setLocation(RectF location) {
        this.location = location;
    }
}
