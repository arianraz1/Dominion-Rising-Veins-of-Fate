package game;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class EventManager {
    private HashMap<Integer, Event> loadedEvents; // All events within the dominion level
    private HashMap<Integer, Event> availableEvents;
    private HashMap<Integer, Integer> eventTriggers; // Number of triggers, also serves as history

    // If an event is being forced, it is the next event
    private boolean eventBeingForced;
    Event nextEvent;

    public EventManager(int dominionLevel) {
        loadedEvents = new HashMap<>();
        eventTriggers = new HashMap<>();
        eventBeingForced = false;
        nextEvent = null;
        loadAllEvents(dominionLevel);
    }

    // Load all events into their proper sets
    private void loadAllEvents(int dominionLevel) {
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
                    loadedEvents.put(event.getId(), event);
                    eventTriggers.put(event.getId(), 0);
                    if (event.isForced() && !eventBeingForced) {
                        nextEvent = event;
                        eventBeingForced = true;
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            i++;
        }
    }

    // Get event by id
    public Event getEvent(int id) {
        return loadedEvents.get(id);
    }

    // Get all events
    public Collection<Event> getAllEvents() {
        return loadedEvents.values();
    }

    // Check if an event can currently trigger
    public boolean canTriggerEvent(int id, GameState gs) {
        if (eventBeingForced) return false;

        Event event = loadedEvents.get(id);
        if (event == null) return false;

        // Get the number of triggers of the event
        int triggers = eventTriggers.getOrDefault(id, 0);

        // Check if the event does not have unlimited triggers and reached its trigger threshold
        if (event.getMaxTriggered() != -1 && triggers >= event.getMaxTriggered()) return false;

        // Check if all required events for this event have already been triggered
        for (Integer reqId : event.getRequirements().getRequiredEvents().keySet()) {
            if (!eventTriggers.containsKey(reqId)) return false;
        }

        // Check if all the stats are between min and max
        int blood = gs.getBlood();
        int population = gs.getPopulation();
        int happiness = gs.getHappiness();
        int corruption = gs.getCorruption();

        int minBlood = event.getRequirements().getMinStats().getBlood();
        int maxBlood = event.getRequirements().getMaxStats().getBlood();
        int minPopulation = event.getRequirements().getMinStats().getPopulation();
        int maxPopulation = event.getRequirements().getMaxStats().getPopulation();
        int minHappiness = event.getRequirements().getMinStats().getHappiness();
        int maxHappiness = event.getRequirements().getMaxStats().getHappiness();
        int minCorruption = event.getRequirements().getMinStats().getCorruption();
        int maxCorruption = event.getRequirements().getMaxStats().getCorruption();

        if ((minBlood != -1 && blood < minBlood) || (maxBlood != -1 && blood > maxBlood)) return false;
        if ((minPopulation != -1 && population < minPopulation) || (maxPopulation != -1 && population > maxPopulation)) return false;
        if ((minHappiness != -1 && happiness < minHappiness) || (maxHappiness != -1 && happiness > maxHappiness)) return false;
        if ((minCorruption != -1 && corruption < minCorruption) || (maxCorruption != -1 && corruption > maxCorruption)) return false;

        // Check if any preventing triggered events exists
        for (Integer preventId : event.getPreventEvents().keySet()) {
            if (eventTriggers.containsKey(preventId)) return false;
        }

        return true;
    }

    // Trigger an event by id
    public Event triggerEvent(int id, GameState gs) {
        if (!canTriggerEvent(id, gs)) return null;

        Event event = availableEvents.get(id);
        if (event == null) return null;

        // Place event into history, and increase it's trigger count
        eventTriggers.put(id, eventTriggers.getOrDefault(id, 0) + 1);

        // If the event forces an event, make sure to include it
        if (event.getForcesEvent() != null) {
            eventBeingForced = true;
            nextEvent = event;
        }

        return event;
    }

    // Check if there are any forced events waiting
    public Event getForcedEvent() {
        if (nextEvent == null) return null;
        return nextEvent;
    }

    // Get a random event (forced takes priority)
    // TODO
    public Event getRandomEvent() {
        if (eventBeingForced) return nextEvent;

        return null;

    }
}

