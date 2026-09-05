package com.gomouse.pro.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A named, saved control layout: its list of {@link InputMapping}s plus its
 * own sensitivity, opacity, and grid settings. Profiles are persisted to
 * local JSON storage (see {@code storage.ProfileRepository}) and can be
 * associated with a specific installed app.
 */
public class Profile implements Serializable {

    private String id;
    private String name;

    /** Package name of the app/game this profile is for, or null for "any app". */
    private String targetPackageName;

    private List<InputMapping> mappings;

    private float sensitivityX;
    private float sensitivityY;

    /** Overlay opacity while playing, in [0.2, 1.0]. */
    private float opacity;

    private boolean gridSnapEnabled;

    /** Grid cell size, normalized to screen width, in (0, 0.2]. */
    private float gridSize;

    private long createdAt;
    private long updatedAt;

    /** No-arg constructor required by Gson. */
    public Profile() {
        this.id = UUID.randomUUID().toString();
        this.name = "New Profile";
        this.targetPackageName = null;
        this.mappings = new ArrayList<>();
        this.sensitivityX = 1.0f;
        this.sensitivityY = 1.0f;
        this.opacity = 0.85f;
        this.gridSnapEnabled = true;
        this.gridSize = 0.02f;
        long now = System.currentTimeMillis();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Profile(String name) {
        this();
        this.name = name;
    }

    public Profile deepCopy(String newName) {
        Profile copy = new Profile(newName);
        copy.targetPackageName = this.targetPackageName;
        copy.sensitivityX = this.sensitivityX;
        copy.sensitivityY = this.sensitivityY;
        copy.opacity = this.opacity;
        copy.gridSnapEnabled = this.gridSnapEnabled;
        copy.gridSize = this.gridSize;
        for (InputMapping mapping : this.mappings) {
            InputMapping m = mapping.copy();
            m.setX(mapping.getX());
            m.setY(mapping.getY());
            copy.mappings.add(m);
        }
        return copy;
    }

    public void touch() {
        this.updatedAt = System.currentTimeMillis();
    }

    // --- Getters / setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTargetPackageName() {
        return targetPackageName;
    }

    public void setTargetPackageName(String targetPackageName) {
        this.targetPackageName = targetPackageName;
    }

    public List<InputMapping> getMappings() {
        if (mappings == null) {
            mappings = new ArrayList<>();
        }
        return mappings;
    }

    public void setMappings(List<InputMapping> mappings) {
        this.mappings = mappings;
    }

    public float getSensitivityX() {
        return sensitivityX;
    }

    public void setSensitivityX(float sensitivityX) {
        this.sensitivityX = sensitivityX;
    }

    public float getSensitivityY() {
        return sensitivityY;
    }

    public void setSensitivityY(float sensitivityY) {
        this.sensitivityY = sensitivityY;
    }

    public float getOpacity() {
        return opacity;
    }

    public void setOpacity(float opacity) {
        this.opacity = opacity;
    }

    public boolean isGridSnapEnabled() {
        return gridSnapEnabled;
    }

    public void setGridSnapEnabled(boolean gridSnapEnabled) {
        this.gridSnapEnabled = gridSnapEnabled;
    }

    public float getGridSize() {
        return gridSize;
    }

    public void setGridSize(float gridSize) {
        this.gridSize = gridSize;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
