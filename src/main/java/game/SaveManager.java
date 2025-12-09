package game;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class SaveManager {
    private static final Gson gson = new Gson();
    private static final String SAVE_FILE = System.getProperty("user.home") + File.separator + "Dominion_Rising_Veins_of_Fate.json";

    public static void saveGame(GameState gs, EventManager em) throws IOException {
        FullGameSave fgs = new FullGameSave(
                gs,
                em.getEventTriggers(),
                em.getTriggeredHistory(),
                em.getPreventedEvents(),
                em.getForcedQueue(),
                em.getCurrentEventID()
        );

        File saveFile = new File(SAVE_FILE);
        File backup = new File(SAVE_FILE + ".bak");

        // temp file for atomic write
        File tmpFile = new File(SAVE_FILE + ".tmp");

        // Make sure backup is clean
        if (saveFile.exists()) {
            if (backup.exists() && !backup.delete()) {
                System.err.println("[SaveManager] Warning: failed to delete old backup save.");
            }
            try {
                // Copy saveFile to backup
                Files.copy(saveFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                System.err.println("[SaveManager] Warning: failed to create backup save: " + e.getMessage());
            }
        }

        // **Atomic save logic**
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tmpFile, StandardCharsets.UTF_8))) {
            gson.toJson(fgs, writer);
        }

        // Replace old save with new atomically (crash-safe)
        try {
            Files.move(
                    tmpFile.toPath(),
                    saveFile.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException e) {
            System.err.println("[SaveManager] Warning: atomic replace failed, trying normal replace: " + e.getMessage());
            Files.copy(tmpFile.toPath(), saveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            tmpFile.delete();
        }
    }

    public static FullGameSave loadGame() {
        File file = new File(SAVE_FILE);

        FullGameSave defaultSave = new FullGameSave(
                new GameState(),
                new HashMap<>(),
                new ArrayList<>(),
                new HashSet<>(),
                new ArrayDeque<>(),
                null
        );

        if (!file.exists()) return defaultSave;

        try (BufferedReader reader = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            FullGameSave loaded = gson.fromJson(reader, FullGameSave.class);
            if (loaded == null) return defaultSave;
            return loaded;
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            System.err.println("[SaveManager] Warning: failed to load save, creating new save. " + e.getMessage());
            return defaultSave;
        }
    }

    // Restore the information in event manager from new load
    public static void restoreEventManager(EventManager em, FullGameSave fgs) {
        em.setEventTriggers(fgs.getEventTriggers());
        em.setTriggeredHistory(fgs.getTriggeredHistory());
        em.setPreventedEvents(fgs.getPreventEvents());
        em.setForcedQueue(fgs.getForcedQueue());
        em.setCurrentEventID(fgs.getCurrentEventID());
    }


    public static void resetSave() {
        File saveFile = new File(SAVE_FILE);
        File backup = new File(SAVE_FILE + ".bak");

        if (saveFile.exists() && !saveFile.delete()) {
            System.err.println("[SaveManager] Warning: failed to delete save file.");
        }
        if (backup.exists() && !backup.delete()) {
            System.err.println("[SaveManager] Warning: failed to delete backup save.");
        }
        System.out.println("[SaveManager] Save files reset.");
    }

    public static boolean saveExists() {
        File file = new File(SAVE_FILE);
        return file.exists();
    }
}

