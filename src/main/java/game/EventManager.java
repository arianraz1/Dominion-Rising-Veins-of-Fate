package game;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

public class EventManager {
    private HashMap<Integer, Event> loadedEvents;
    private ArrayList<Event> currentDominionEvents;

    private void loadAllEvents(int dominionLevel) {
        loadedEvents = new HashMap<>();
        currentDominionEvents = new ArrayList<>();

        String dirPath = switch (dominionLevel) {
            case 0 -> "/events/0_Early";
            case 1 -> "/events/1_Early_Mid";
            case 2 -> "/events/2_Mid";
            case 3 -> "/events/3_Mid_Late";
            case 4 -> "/events/4_Mid_Late";
            case 5 -> null; // TODO
            default -> throw new IllegalArgumentException("Invalid dominion level");
        };

        if (dirPath == null) return;

        int i = 0;
        while (true) {
            String path = dirPath + "/" + i + ".json";
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in == null) break;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    Event event = new Gson().fromJson(reader, Event.class);
                    loadedEvents.put(i, event);
                    currentDominionEvents.add(event);
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            i++;
        }
    }
}

