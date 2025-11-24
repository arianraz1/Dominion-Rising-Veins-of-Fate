package game;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

public class EventManager {
    private Map<Integer, Event> loadedEvents; // All events within the dominion level
    private List<Event> availableEvents;
    private Map<Integer, Integer> eventTriggers; // Number of triggers
    private List<Event> triggeredHistory; // Triggered event history
    private Set<Integer> preventedEvents = new HashSet<>();

    // If an event is being forced, it is the next event
    private boolean eventBeingForced;
    private Event nextEvent;

    // Randomize events
    private Random random;

    public EventManager(int dominionLevel) {
        loadedEvents = new HashMap<>();
        availableEvents = new ArrayList<>();
        eventTriggers = new HashMap<>();
        triggeredHistory = new ArrayList<>();
        preventedEvents = new HashSet<>();
        eventBeingForced = false;
        nextEvent = null;
        random = new Random();
        loadAllEvents(dominionLevel);
    }

    // Load all events into their proper sets
    private void loadAllEvents(int dominionLevel) {
        loadedEvents.clear();
        availableEvents.clear();
        // eventTriggers.clear();
        // triggeredHistory.clear();
        // preventedEvents.clear();
        eventBeingForced = false;
        nextEvent = null;

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

        // Check if the event has reached its trigger threshold
        if (event.getMaxTriggered() != -1 && triggers >= event.getMaxTriggered()) return false;

        // Check if all required events for this event have already been triggered
        for (Event.EventRef requiredEvent : event.getRequirements().getRequiredEvents()) {
            if (eventTriggers.getOrDefault(requiredEvent.getId(), 0) == 0) return false;
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
        if (preventedEvents.contains(id)) return false;

        return true;
    }

    // Trigger an event by id
    public Event triggerEvent(int id, GameState gs) {
        if (!canTriggerEvent(id, gs)) return null;

        Event event = loadedEvents.get(id);
        if (event == null) return null;

        // Place event into history, and increase it's trigger count
        triggeredHistory.add(event);
        eventTriggers.put(id, eventTriggers.getOrDefault(id, 0) + 1);

        return event;
    }

    public void updateAvailableEvents(GameState gs) {
        availableEvents.clear();
        for (Event event : loadedEvents.values()) {
            if (canTriggerEvent(event.getId(), gs)) {
                availableEvents.add(event);
            }
        }
    }

    // Get the forced event
    public Event getForcedEvent() {
        if (nextEvent == null) return null;
        eventBeingForced = false;
        return nextEvent;
    }

    // Get a random event (forced takes priority)
    public Event getRandomEvent() {
        if (eventBeingForced) return getForcedEvent();

        int size = availableEvents.size();
        if (size == 0) return null;

        return availableEvents.get(random.nextInt(size));
    }

    // Execute a choice from an event
    public void choose(Event event, Event.Choice choice, GameState gs) {
        if (event == null || choice == null) return;

        // Apply stat changes
        gs.applyStats(choice.getStatChange());

        // Handle forced event, if any
        if (choice.getForcesEvent() != null) {
            nextEvent = loadedEvents.get(choice.getForcesEvent().getId());
            if (nextEvent != null) eventBeingForced = true;
        }

        // Handle prevented events, if any
        for (Event.EventRef prevented : choice.getPreventEvents()) {
            preventedEvents.add(prevented.getId());
        }

        // Apply event influences, if any (first triggered only, already ordered by priority)
        for (Event.EventInfluence influence : choice.getEventInfluences()) {
            if (eventTriggers.getOrDefault(influence.getId(), 0) > 0) {
                gs.applyStats(influence.getStatChange());
                break; // only the first applicable influence triggers
            }
        }

        // Update available events for next round
        updateAvailableEvents(gs);
    }

    public static class EventView {
        private final String title;
        private final String description;
        private final List<String> choiceTexts;

        public EventView(String title, String description, List<String> choiceTexts) {
            this.title = title;
            this.description = description;
            this.choiceTexts = choiceTexts;
        }

        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public List<String> getChoiceTexts() { return choiceTexts; }
    }

    // Return EventView object to display event details
    public EventView getEventView(Event event)  {
        if (event == null) return null;

        List<String> choiceTexts = new ArrayList<>();
        for (Event.Choice choice : event.getChoices()) {
            choiceTexts.add(choice.getText());
        }
        return new EventView(event.getTitle(), event.getDescription(), choiceTexts);
    }

    // After a choice was made, get the resulting text
    public String getChoiceOutcomeText(Event.Choice choice) {
        if (choice == null) return null;

        // Check event influences first (assume JSON is ordered by descending priority)
        for (Event.EventInfluence influence : choice.getEventInfluences()) {
            int influenceId = influence.getId();
            if (eventTriggers.getOrDefault(influenceId, 0) > 0
                    && !influence.getOverrideOutcomeText().isEmpty()) {
                return influence.getOverrideOutcomeText();
            }
        }

        // Fallback to the choice's own outcome text, or the choice text if empty
        return choice.getOutcomeText().isEmpty() ? choice.getText() : choice.getOutcomeText();
    }
}

