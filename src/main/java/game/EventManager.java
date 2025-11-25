package game;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    // Load all static events (note: this ONLY loads a players respective dominionLevel events)
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

        int i = 0;
        while (true) {
            String path = dirPath + "/" + i + ".json";
            try (InputStream in = getClass().getResourceAsStream(path)) {
                if (in == null) break;

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                    Event event = gson.fromJson(reader, Event.class);
                    if (event == null) {
                        System.err.println("[EventManager] Warning: parsed null event " + path);
                    } else {
                        if (newEvents.containsKey(event.getId())) {
                            System.err.println("[EventManager] Duplicate event " + event.getId());
                        }
                        newEvents.put(event.getId(), event);
                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            i++;
        }

        // Replace static events
        this.loadedEvents = newEvents;

        // Validate references inside events (requiredEvents, preventEvents, forcesEvent, eventInfluences)
        validateEventReferences();
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
     * Otherwise, selects uniformly from currently available events (after recalculation).
     */
    public Event getRandomEvent(GameState gs) {
        while (!forcedQueue.isEmpty()) {
            int forcedId = forcedQueue.pollFirst();
            Event forced = loadedEvents.get(forcedId);
            // Re-run if the forced event is null
            if (forced != null) return forced;
        }

        // Fall to available events if no forced event
        updateAvailableEvents(gs);
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
        Event event = loadedEvents.get(id);
        if (event == null) return null;
        eventTriggers.put(id, eventTriggers.getOrDefault(id, 0) + 1);
        triggeredHistory.add(event.getId());
        return event;
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

        // Apply stat changes
        gs.applyStats(choice.getStatChange());

        // Queue's if the choice has a forced event
        Event.EventRef forcesEvent = choice.getForcesEvent();
        if (forcesEvent != null && forcesEvent.getId() != -1) {
            if (!loadedEvents.containsKey(forcesEvent.getId())) {
                System.err.println("[EventManager] Warning: choice forced unknown event id " + forcesEvent.getId());
            } else {
                forcedQueue.addLast(forcesEvent.getId());
            }
        }

        // Handle prevented events, if any
        for (Event.EventRef preventEvent : choice.getPreventEvents()) {
            if (preventEvent != null && preventEvent.getId() != -1) {
                if (!loadedEvents.containsKey(preventEvent.getId())) {
                    System.err.println("[EventManager] Warning: choice prevents unknown event id " + preventEvent.getId());
                } else {
                    preventedEvents.add(preventEvent.getId());
                }
            }
        }

        // Apply influences: choose highest-priority influenced event which has been triggered before
        List<Event.EventInfluence> eventInfluences = new ArrayList<>(choice.getEventInfluences());
        eventInfluences.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (Event.EventInfluence eventInfluence : eventInfluences) {
            if (eventTriggers.getOrDefault(eventInfluence.getId(), 0) > 0) {
                gs.applyStats(eventInfluence.getStatChange());
                break;
            }
        }

        // Update available events for next round
        updateAvailableEvents(gs);
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
        this.eventTriggers.clear();
        this.eventTriggers.putAll(triggers);
    }

    void setTriggeredHistory(List<Integer> history) {
        this.triggeredHistory.clear();
        this.triggeredHistory.addAll(history);
    }

    void setPreventedEvents(Set<Integer> prevented) {
        this.preventedEvents.clear();
        this.preventedEvents.addAll(prevented);
    }

    void setForcedQueue(Deque<Integer> forced) {
        this.forcedQueue.clear();
        this.forcedQueue.addAll(forced);
    }

    // ---------- EVENT VIEW INFORMATION ----------

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

