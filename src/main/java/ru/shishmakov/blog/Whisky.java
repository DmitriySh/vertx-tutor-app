package ru.shishmakov.blog;

import io.vertx.core.json.JsonObject;

import java.util.Optional;

import static java.util.Optional.ofNullable;

public class Whisky {
    private final int id;
    private String name;
    private String origin;

    public Whisky(int id, String name, String origin) {
        this.id = id;
        this.name = name;
        this.origin = origin;
    }

    public Whisky(String name, String origin) {
        this(-1, name, origin);
    }

    public Whisky() {
        this(-1, null, null);
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

    public static Whisky fromJson(JsonObject json) {
        return new Whisky(
                Optional.of(json).map(j -> j.getInteger("_id", j.getInteger("ID"))).orElse(-1),
                Optional.of(json).map(j -> j.getString("NAME")).orElse(null),
                Optional.of(json).map(j -> j.getString("ORIGIN")).orElse(null));
    }

    public JsonObject toJson() {
        return toJson(false);
    }

    public JsonObject toJson(boolean useMongo) {
        JsonObject json = new JsonObject().put(useMongo ? "_id" : "ID", id);
        ofNullable(name).ifPresent(t -> json.put("NAME", t));
        ofNullable(origin).ifPresent(t -> json.put("ORIGIN", t));
        return json;
    }

    @Override
    public String toString() {
        return "id=" + id +
                ", name='" + name + '\'' +
                ", origin='" + origin + '\'';
    }
}
