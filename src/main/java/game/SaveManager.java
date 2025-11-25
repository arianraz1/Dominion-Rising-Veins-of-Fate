package game;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class SaveManager {
    private static final Gson gson = new Gson();
    private static final String SAVE_FILE = System.getProperty("user.home") + File.separator + ".vpg_save.json";

    public static void saveGame(GameState gs, EventManager em) throws IOException {
        FullGameSave fgs = new FullGameSave(
                gs,
                em.getEventTriggers(),
                em.getTriggeredHistory(),
                em.getPreventedEvents(),
                em.getForcedQueue()
        );

        File saveFile = new File(SAVE_FILE);

        // Backup existing save (single backup)
        if (saveFile.exists()) {
            File backup = new File(SAVE_FILE + ".bak");
            if (!saveFile.renameTo(backup)) {
                System.err.println("[SaveManager] Warning: failed to create backup save.");
            }
        }

        // Write the new save
        try (FileWriter fw = new FileWriter(saveFile)) {
            gson.toJson(fgs, fw);
        }
    }

    public static FullGameSave loadGame() {
        File file = new File(SAVE_FILE);

        FullGameSave defaultSave = new FullGameSave(
                new GameState(),
                new HashMap<>(),
                new ArrayList<>(),
                new HashSet<>(),
                new ArrayDeque<>()
        );

        if (!file.exists()) return defaultSave;

        try (FileReader fr = new FileReader(file)) {
            FullGameSave loaded = gson.fromJson(fr, FullGameSave.class);
            if (loaded == null) return defaultSave;
            return loaded;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            System.err.println("[SaveManager] Warning: failed to load save, creating new save. " + e.getMessage());
            return defaultSave;
        }
    }

    public static void restoreEventManager(EventManager em, FullGameSave fgs) {
        em.setEventTriggers(fgs.getEventTriggers());
        em.setTriggeredHistory(fgs.getTriggeredHistory());
        em.setPreventedEvents(fgs.getPreventEvents());
        em.setForcedQueue(fgs.getForcedQueue());
    }
}

