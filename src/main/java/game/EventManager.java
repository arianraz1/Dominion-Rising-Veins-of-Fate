package game;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * This class manages all data relating directly to Events, whether that's parsing, saving, loading, or tracking them.
 */
public class EventManager {
    private Map<Integer, Event> loadedEvents; // All events within the dominion level
    private List<Event> availableEvents; // All available events within current constraints

    private Map<Integer, Integer> eventTriggers; // Number of triggers per event
    private List<Integer> triggeredHistory; // Triggered event history
    private Set<Integer> preventedEvents; // Set of completely prevented events
    private Deque<Integer> forcedQueue; // Queue of forced events

    private final Gson gson = new Gson();
    private Random random;

    public EventManager(int dominionLevel, GameState gs) {
        this.loadedEvents = new HashMap<>();
        this.availableEvents = new ArrayList<>();

        this.eventTriggers = new HashMap<>();
        this.triggeredHistory = new ArrayList<>();
        this.preventedEvents = new HashSet<>();
        this.forcedQueue = new ArrayDeque<>();

        this.random = new Random();

        loadStaticEvents(dominionLevel);
    }

    private void loadStaticEvents(int dominionLevel) {
        Map<Integer, Event> newEvents = new HashMap<>();

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

        // Load manifest
        try (InputStream manifestStream = getClass().getResourceAsStream(dirPath + "/manifest.json")) {
            if (manifestStream == null) {
                System.err.println("[EventManager] Manifest not found: " + dirPath + "/manifest.json");
                return;
            }

            try (BufferedReader manifestReader = new BufferedReader(new InputStreamReader(manifestStream, StandardCharsets.UTF_8))) {
                Manifest manifest = gson.fromJson(manifestReader, Manifest.class);

                if (manifest == null || manifest.events == null) {
                    System.err.println("[EventManager] Manifest is empty or invalid: " + dirPath + "/manifest.json");
                    return;
                }

                // Load each event
                for (String fileName : manifest.events) {
                    String path = dirPath + "/Event_List/" + fileName;
                    try (InputStream fileStream = getClass().getResourceAsStream(path)) {
                        if (fileStream == null) {
                            System.err.println("[EventManager] Event file not found: " + path);
                            continue;
                        }

                        try (BufferedReader eventReader = new BufferedReader(new InputStreamReader(fileStream, StandardCharsets.UTF_8))) {
                            Event event = gson.fromJson(eventReader, Event.class);
                            if (event == null) {
                                System.err.println("[EventManager] Warning: parsed null event " + path);
                                continue;
                            }

                            // Always add event to loadedEvents
                            if (newEvents.containsKey(event.getId())) {
                                System.err.println("[EventManager] Duplicate event " + event.getId() + " detected.");
                            }
                            newEvents.put(event.getId(), event);

                            // Queue INITIALLY forced events only once
                            if (event.isForced() && eventTriggers.getOrDefault(event.getId(), 0) == 0) {
                                forcedQueue.addLast(event.getId());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        // Replace static events
        this.loadedEvents = newEvents;

        // Validate references inside events
        validateEventReferences();
    }

    public void initializeForcedEventsAfterRestore() {
        forcedQueue.clear();
        for (Event event : loadedEvents.values()) {
            if (event.isForced() && eventTriggers.getOrDefault(event.getId(), 0) == 0) {
                forcedQueue.addLast(event.getId());
            }
        }
    }

    // Validate the internal content/references — warn any reference to a missing event.
    private void validateEventReferences() {
        for (Event event : loadedEvents.values()) {
            for (Event.Choice choice : event.getChoices()) {
                // forcesEvent
                Event.EventRef forcesEvent = choice.getForcesEvent();
                if (forcesEvent != null && forcesEvent.getId() != -1 && !loadedEvents.containsKey(forcesEvent.getId())) {
                    System.err.println("[EventManager] Warning: choice in event " + event.getId()
                            + " forces missing event id " + forcesEvent.getId());
                }
                // preventEvents
                for (Event.EventRef preventEvent : choice.getPreventEvents()) {
                    if (preventEvent != null && preventEvent.getId() != -1 && !loadedEvents.containsKey(preventEvent.getId())) {
                        System.err.println("[EventManager] Warning: choice in event " + event.getId()
                                + " prevents missing event id " + preventEvent.getId());
                    }
                }
                // influences
                for (Event.EventInfluence eventInfluence : choice.getEventInfluences()) {
                    if (eventInfluence != null && eventInfluence.getId() != -1 && !loadedEvents.containsKey(eventInfluence.getId())) {
                        System.err.println("[EventManager] Warning: influence in event " + event.getId()
                                + " references missing event id " + eventInfluence.getId());
                    }
                }
            }
        }
    }

    /**
     * Returns true if the event is triggerable, meeting constraints (trigger counts, requirements, stats, prevented).
     * NOTE: This does not check forced-event selection priority, that is handled by getRandomEvent()
     */
    public boolean canTriggerEvent(int id, GameState gs) {
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

    /**
     * Rewrites availableEvents list using the provided GameState.
     * Does not alter dynamic state other than updating availableEvents.
     */
    public void updateAvailableEvents(GameState gs) {
        availableEvents.clear();
        for (Event event : loadedEvents.values()) {
            if (canTriggerEvent(event.getId(), gs)) {
                availableEvents.add(event);
            }
        }
    }

    /**
     * Get a random event. forcedQueue takes priority, is polled and returned if applicable.
     * Otherwise, selects uniformly from currently available events.
     */
    public Event getRandomEvent(GameState gs) {
        while (!forcedQueue.isEmpty()) {
            Event forced = loadedEvents.get(forcedQueue.pollFirst());
            if (forced != null && canTriggerEvent(forced.getId(), gs)) return forced;
        }

        // Fall to available events if no forced event
        if (availableEvents.isEmpty()) return null;
        return availableEvents.get(random.nextInt(availableEvents.size()));
    }

    // Peek forced event without removing (returns null if none)
    public Event peekForcedEvent() {
        if (forcedQueue.isEmpty()) return null;
        return loadedEvents.get(forcedQueue.peekFirst());
    }

    /**
     * Manually pushes a forced event id onto the queue if valid (used by choose()).
     * Returns true if queued.
     */
    public boolean queueForcedEvent(int eventId) {
        if (!loadedEvents.containsKey(eventId)) return false;
        forcedQueue.addLast(eventId);
        return true;
    }

    /**
     * Trigger an event by id. This records trigger count and history, and returns the Event object or null if it cannot trigger.
     * Note: This does not execute the consequences of choices — that's handled by choose(event, choice, gs).
     */
    public Event triggerEvent(int id, GameState gs) {
        if (!canTriggerEvent(id, gs)) return null;
        eventTriggers.put(id, eventTriggers.getOrDefault(id, 0) + 1);
        triggeredHistory.add(id);
        return loadedEvents.get(id);
    }

    // Handle if the player levels up at all during gameplay
    static EventManager handleLevelUp(GameState gs, EventManager em) {
        // Create new EventManager for the new level
        EventManager newEM = new EventManager(gs.getDominionLevel(), gs);

        // Copy over old dynamic state
        newEM.setEventTriggers(em.getEventTriggers());
        newEM.setTriggeredHistory(em.getTriggeredHistory());
        newEM.setPreventedEvents(em.getPreventedEvents());
        newEM.setForcedQueue(em.getForcedQueue());

        return newEM;
    }


    /**
     * Apply the effects of a player's choice based on the triggered event.
     * - Applies the choice's stat changes
     * - Queues forced events (does NOT overwrite existing forced queue)
     * - Adds prevented events to preventedEvents set
     * - Applies the highest-priority applicable influence (first-by-descending-priority)
     * - Recalculates availableEvents
     */
    public void choose(Event event, Event.Choice choice, GameState gs) {
        if (event == null || choice == null) return;

        // Record the event trigger BEFORE applying choice effects so requirements/influences/maxTriggered are correct.
        Event triggered = triggerEvent(event.getId(), gs);
        if (triggered == null) {
            System.err.println("[EventManager] Warning: attempt to choose on untriggerable event id " + event.getId());
            return;
        }

        // Apply stat changes
        gs.applyStats(choice.getStatChange());

        // Queue forced events from this choice
        Event.EventRef forcesEvent = choice.getForcesEvent();
        if (forcesEvent != null && forcesEvent.getId() != -1) {
            if (!loadedEvents.containsKey(forcesEvent.getId())) {
                System.err.println("[EventManager] Warning: choice forced unknown event id " + forcesEvent.getId());
            } else {
                forcedQueue.addLast(forcesEvent.getId());
            }
        }

        // Handle prevented events
        for (Event.EventRef preventEvent : choice.getPreventEvents()) {
            if (preventEvent != null && preventEvent.getId() != -1) {
                if (!loadedEvents.containsKey(preventEvent.getId())) {
                    System.err.println("[EventManager] Warning: choice prevents unknown event id " + preventEvent.getId());
                } else {
                    preventedEvents.add(preventEvent.getId());
                }
            }
        }

        // Apply highest-priority triggered influence (if any)
        Event.EventInfluence influence = getHighestPriorityTriggeredInfluence(choice.getEventInfluences());
        if (influence != null) {
            gs.applyStats(influence.getStatChange());
        }

        // Recompute available events
        updateAvailableEvents(gs);
    }


    private Event.EventInfluence getHighestPriorityTriggeredInfluence(List<Event.EventInfluence> influences) {
        if (influences == null || influences.isEmpty()) return null;

        // Make a copy and sort descending by priority
        List<Event.EventInfluence> sorted = new ArrayList<>(influences);
        sorted.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));

        for (Event.EventInfluence influence : sorted) {
            if (eventTriggers.getOrDefault(influence.getId(), 0) > 0) {
                return influence;
            }
        }
        return null;
    }

    // ----------------- HELPERS -----------------

    public Event getEvent(int id) {
        return loadedEvents.get(id);
    }

    public Collection<Event> getAllEvents() {
        return Collections.unmodifiableCollection(loadedEvents.values());
    }

    public Map<Integer, Integer> getEventTriggers() {
        return Collections.unmodifiableMap(eventTriggers);
    }

    public List<Integer> getTriggeredHistory() {
        return Collections.unmodifiableList(triggeredHistory);
    }

    public List<Event> getAvailableEvents() {
        return Collections.unmodifiableList(availableEvents);
    }

    public Set<Integer> getPreventedEvents() {
        return Collections.unmodifiableSet(preventedEvents);
    }

    public Deque<Integer> getForcedQueue() {
        return new ArrayDeque<>(forcedQueue);
    }

    void setEventTriggers(Map<Integer, Integer> triggers) {
        this.eventTriggers = new HashMap<>(triggers);
    }

    void setTriggeredHistory(List<Integer> history) {
        this.triggeredHistory = new ArrayList<>(history);
    }

    void setPreventedEvents(Set<Integer> prevented) {
        this.preventedEvents = new HashSet<>(prevented);
    }

    void setForcedQueue(Deque<Integer> forced) {
        this.forcedQueue = new ArrayDeque<>(forced);
    }

    public List<String> getChoiceOutcomeLines(Event.Choice choice) {
        if (choice == null) return List.of();

        // Check highest-priority triggered influence first
        Event.EventInfluence highest = getHighestPriorityTriggeredInfluence(choice.getEventInfluences());
        if (highest != null && highest.getOverrideOutcomeText() != null && !highest.getOverrideOutcomeText().isEmpty()) {
            return highest.getOverrideOutcomeText();
        }

        // Use the choice's outcome text if present
        if (choice.getOutcomeText() != null && !choice.getOutcomeText().isEmpty()) {
            return choice.getOutcomeText();
        }

        // Fallback: return the choice text
        if (choice.getText() != null && !choice.getText().isEmpty()) {
            return choice.getText();
        }

        // If everything else is empty, return an empty list
        return List.of();
    }

    // ---------- EVENT VIEW INFORMATION ----------

    public static class EventView {
        private final String title;
        private final List<String> descriptionLines;
        private final List<List<String>> choiceLines;

        public EventView(String title, List<String> descriptionLines, List<List<String>> choiceLines) {
            this.title = title;
            this.descriptionLines = descriptionLines;
            this.choiceLines = choiceLines;
        }

        public String getTitle() { return title; }
        public List<String> getDescriptionLines() { return descriptionLines; }
        public List<List<String>> getChoiceLines() { return choiceLines; }
    }

    // Return EventView object to display event details
    public EventView getEventView(Event event) {
        if (event == null) return null;

        // Use the event's description directly (already a List<String>)
        List<String> descriptionLines = event.getDescription() != null
                ? event.getDescription()
                : List.of();

        // Prepare choice lines
        List<List<String>> choiceLines = new ArrayList<>();
        for (Event.Choice choice : event.getChoices()) {
            choiceLines.add(choice.getText());
        }

        return new EventView(event.getTitle(), descriptionLines, choiceLines);
    }
}

