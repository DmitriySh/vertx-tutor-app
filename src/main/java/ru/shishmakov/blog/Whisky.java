package ru.shishmakov.blog;

import io.vertx.core.json.JsonObject;

public class Whisky {
    private final int id;
    private String name;
    private String origin;

    public Whisky() {
        this.id = -1;
    }

    public Whisky(String name, String origin) {
        this();
        this.name = name;
        this.origin = origin;
    }

    public Whisky(int id, String name, String origin) {
        this.id = id;
        this.name = name;
        this.origin = origin;
    }

    public Whisky(JsonObject json) {
        this.id = json.getInteger("ID");
        this.name = json.getString("NAME");
        this.origin = json.getString("ORIGIN");
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }
}
