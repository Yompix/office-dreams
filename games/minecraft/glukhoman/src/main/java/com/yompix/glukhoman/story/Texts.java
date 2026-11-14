package com.yompix.glukhoman.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Тексты сюжета из glukhoman_story.json: главы, цели, реплики, записки, титры. Общие для сервера и клиента. */
public final class Texts {
    private static Texts INSTANCE;
    private final JsonObject root;

    private Texts(JsonObject root) {
        this.root = root;
    }

    public static Texts get() {
        if (INSTANCE == null) {
            try (var in = Texts.class.getResourceAsStream("/glukhoman_story.json")) {
                INSTANCE = new Texts(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                throw new RuntimeException("glukhoman_story.json", e);
            }
        }
        return INSTANCE;
    }

    public Set<String> lineIds() {
        return root.getAsJsonObject("lines").keySet();
    }

    public String chapterTitle(int n) {
        var c = root.getAsJsonObject("chapters").getAsJsonObject(Integer.toString(n));
        return c == null ? "" : c.get("title").getAsString();
    }

    public String chapterSub(int n) {
        var c = root.getAsJsonObject("chapters").getAsJsonObject(Integer.toString(n));
        return c == null ? "" : c.get("sub").getAsString();
    }

    public String objective(String id) {
        var o = root.getAsJsonObject("objectives").get(id);
        return o == null ? "" : o.getAsString();
    }

    public String lineText(String id) {
        var l = root.getAsJsonObject("lines").getAsJsonObject(id);
        return l == null ? id : l.get("text").getAsString();
    }

    public String lineWho(String id) {
        var l = root.getAsJsonObject("lines").getAsJsonObject(id);
        if (l == null) return "";
        var w = root.getAsJsonObject("who").get(l.get("who").getAsString());
        return w == null ? "" : w.getAsString();
    }

    /** Ключ говорящего (zakhar, vika…) — для цвета имени и перевода в субтитрах. */
    public String lineWhoKey(String id) {
        var l = root.getAsJsonObject("lines").getAsJsonObject(id);
        return l == null ? "" : l.get("who").getAsString();
    }

    /** Имя для списка «кто в кадре»: житель, игрок (player:Ник), перрон, поезд… */
    public String whoName(String key) {
        if (key.startsWith("player:")) return key.substring(7);
        var n = root.getAsJsonObject("names");
        if (n != null && n.has(key)) return n.get(key).getAsString();
        return key;
    }

    public String lineWhoName(String key) {
        return whoName(key);
    }

    public List<String> sequence(String id) {
        List<String> out = new ArrayList<>();
        JsonArray a = root.getAsJsonObject("sequences").getAsJsonArray(id);
        if (a != null) a.forEach(e -> out.add(e.getAsString()));
        return out;
    }

    public String noteTitle(String id) {
        return root.getAsJsonObject("notes").getAsJsonObject(id).get("title").getAsString();
    }

    public String noteAuthor(String id) {
        return root.getAsJsonObject("notes").getAsJsonObject(id).get("author").getAsString();
    }

    public List<String> notePages(String id) {
        List<String> out = new ArrayList<>();
        root.getAsJsonObject("notes").getAsJsonObject(id).getAsJsonArray("pages").forEach(e -> out.add(e.getAsString()));
        return out;
    }

    public List<String> credits() {
        List<String> out = new ArrayList<>();
        root.getAsJsonArray("credits").forEach(e -> out.add(e.getAsString()));
        return out;
    }
}
