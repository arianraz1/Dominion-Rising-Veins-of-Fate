package game;

import com.google.gson.Gson;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * EventManager Overview:
 *
 * Manages all data related to game events, including:
 * - Parsing events from JSON files except from loads/saves (see SaveManager for saving/loading parsing)
 * - Holds data required for saving event-related state
 * - Tracking and managing the game state.
 *
 * This class does not handle player interactions directly; it only maintains the
 * internal state and logic for event triggering and queuing.
 *
 * Event Flow:
 * 1. Events are loaded for a dominion level via loadStaticEvents().
 * 2. updateAvailableEvents(...) evaluates all loaded events:
 *    - Adds all triggerable events to the availableEvents list.
 *    - Queues triggerable forced events according to queueForcedEventIfApplicable(...) rules.
 * 3. getRandomWeightedEvent(gs) selects the next event:
 *    - Forced Events take priority and are returned and dequeued first
 *    - Non-forced events are weighted by the amount of their requirement events (guaranteed to be fulfilled)
 * 4. Each event/choice impacts stats and instance variables.
 * 5. Outcomes are based on past events (see Influence System section below).
 *
 * Influence System:
 *    - Only the highest-priority triggered influence is applied, determined first-by-descending-priority.
 *    - The influence stat effects compound with the choice's effects
 *    - However, only the influence outcome is displayed
 *
 * EventView object reflects the relevant information to display to the user.
 *
 * See src/resources/events/template.json for interplay and interconnectivity of events and their data.
 */

public class EventManager {
    private Map<Integer, Event> loadedEvents; // All events within the dominion level
    private List<Event> availableEvents; // All available events within current constraints

    private Map<Integer, Integer> eventTriggers; // Number of triggers per event
    private List<Integer> triggeredHistory; // Triggered event history
    private Set<Integer> preventedEvents; // Set of completely prevented events
    private Deque<Integer> forcedQueue; // Queue of forced events

    Integer currentEventID;

    private final Gson gson = new Gson();
    private Random random;

    public EventManager(int dominionLevel) {
        this.loadedEvents = new HashMap<>();
        this.availableEvents = new ArrayList<>();

        this.eventTriggers = new HashMap<>();
        this.triggeredHistory = new ArrayList<>();
        this.preventedEvents = new HashSet<>();
        this.forcedQueue = new ArrayDeque<>();

        this.currentEventID = null;
        this.random = new Random();

        loadStaticEvents(dominionLevel);
    }

    /**
     * Loads all static events within the specified dominion level.
     * These events are stored in loadedEvents but do not directly affect availableEvents.
     * Static events need to be updated after the player's dominion level changes.
     *
     * @param dominionLevel The dominion level for which to load static events.
     */
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
                    if (preventEvent.getId() != -1 && !loadedEvents.containsKey(preventEvent.getId())) {
                        System.err.println("[EventManager] Warning: choice in event " + event.getId()
                                + " prevents missing event id " + preventEvent.getId());
                    }
                }
                // influences
                for (Event.EventInfluence eventInfluence : choice.getEventInfluences()) {
                    if (eventInfluence.getId() != -1 && !loadedEvents.containsKey(eventInfluence.getId())) {
                        System.err.println("[EventManager] Warning: influence in event " + event.getId()
                                + " references missing event id " + eventInfluence.getId());
                    }
                }
            }
        }
    }

    /**
     * Returns true if the event is triggerable, meeting constraints (trigger counts, requirements, stats, prevented).
     * NOTE: This does not update any instance variables.
     *
     * @param id The ID of the event to check.
     * @param gs The current GameState used to evaluate stat and requirement constraints.
     * @return true if the event can currently be triggered, false otherwise.
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

        if ((minBlood != -1 && blood < minBlood) || (maxBlood != -1 && blood > maxBlood))
            return false;
        if ((minPopulation != -1 && population < minPopulation) || (maxPopulation != -1 && population > maxPopulation))
            return false;
        if ((minHappiness != -1 && happiness < minHappiness) || (maxHappiness != -1 && happiness > maxHappiness))
            return false;
        if ((minCorruption != -1 && corruption < minCorruption) || (maxCorruption != -1 && corruption > maxCorruption))
            return false;

        // Check if any preventing triggered events exists
        if (preventedEvents.contains(id)) return false;

        return true;
    }

    /**
     * Rewrites the availableEvents list using the provided GameState, see canTriggerEvent(...) for more details.
     * Queues forced events according to their trigger rules.
     *
     * Logic for forced events:
     * - Only queue if the event is triggerable.
     * - Consider maxTriggered limits to determine how many times it can appear in the forced queue.
     * - Avoid adding duplicates in the forcedQueue.
     */
    public void updateAvailableEvents(GameState gs) {
        availableEvents.clear();

        for (Event event : loadedEvents.values()) {
            if (canTriggerEvent(event.getId(), gs)) {
                // Queue forced events if applicable
                if (event.isForced()) {
                    queueForcedEventIfApplicable(event.getId());
                }

                // Always include in available events
                availableEvents.add(event);
            }
        }
    }

    /**
     * Adds the forced event to the queue if it does not exceed the
     * maximum allowed triggers - current triggers - number in queue.
     *
     * @param eventId The ID of the event to potentially queue
     */
    private void queueForcedEventIfApplicable(int eventId) {
        if (!loadedEvents.containsKey(eventId)) {
            System.err.println("[EventManager] Warning: unknown forced event id " + eventId);
            return;
        }

        Event event = loadedEvents.get(eventId);
        int triggers = eventTriggers.getOrDefault(eventId, 0);
        int maxTriggers = event.getMaxTriggered(); // -1 = unlimited

        // Count how many instances are already in the queue
        int inQueue = 0;
        for (Integer id : forcedQueue) {
            if (id == eventId) inQueue++;
        }

        // Only add if total (triggered + in queue) is less than maxTriggers
        if (maxTriggers == -1 || triggers + inQueue < maxTriggers) {
            forcedQueue.addLast(eventId);
        }
    }

    /**
     * Get a random event. forcedQueue takes priority, is polled and returned if applicable.
     * Otherwise, selects from available events, events with their required events fulfilled are weighted higher.
     * Chosen event is added to history and available events are always updated.
     *
     * @param gs The current GameState, used to check event constraints and update available events
     * @return The next Event to be presented to the player, or null if no events are available
     *
     * Notes:
     * - Forced events are always returned first, even if other events are available.
     * - Events are exponentially weighted based on how many required events they have already triggered,
     *   and for an event to be available it needs all of their required events to be triggered.
     * - Triggered events are recorded in event history, and the available events list is recalculated
     *   to reflect updated game state.
     */
    public Event getRandomWeightedEvent(GameState gs) {
        // Handle forced events first
        while (!forcedQueue.isEmpty()) {
            Event forced = loadedEvents.get(forcedQueue.pollFirst());
            if (forced != null) {
                currentEventID = forced.getId();
                triggerEventToHistory(currentEventID);
                updateAvailableEvents(gs);
                return forced;
            }
        }

        if (availableEvents.isEmpty()) return null;

        // Prevent event weight and total weight
        int totalWeight = 0;
        List<Integer> weights = new ArrayList<>();

        for (Event e : availableEvents) {
            int weight = (int) Math.pow(2, e.getRequirements().getRequiredEvents().size());
            weights.add(weight);
            totalWeight += weight;
        }

        // Choose random event based on weights
        int r = random.nextInt(totalWeight);
        for (int i = 0; i < availableEvents.size(); i++) {
            r -= weights.get(i);
            if (r < 0) {
                Event chosen = availableEvents.get(i);
                currentEventID = chosen.getId();
                triggerEventToHistory(currentEventID);
                updateAvailableEvents(gs);
                return chosen;
            }
        }

        return null; // This should never happen
    }

    // Peek forced event without removing (returns null if none)
    public Event peekForcedEvent() {
        if (forcedQueue.isEmpty()) return null;
        return loadedEvents.get(forcedQueue.peekFirst());
    }

    /**
     * Manually pushes a forced event id onto the queue if valid.
     * This is not meant for debugging, not actual gameplay.
     */
    public boolean queueForcedEvent(int eventId) {
        if (!loadedEvents.containsKey(eventId)) return false;
        forcedQueue.addLast(eventId);
        return true;
    }

    /**
     * Trigger an event to history, adding to its trigger count, and update available events
     * @param id The id of the event to add to history
     */
    public void triggerEventToHistory(int id) {
        eventTriggers.put(id, eventTriggers.getOrDefault(id, 0) + 1);
        triggeredHistory.add(id);
    }

    // Handle if the player levels up at all during gameplay
    public static EventManager handleLevelUp(GameState gs, EventManager em) {
        // Create new EventManager for the new level
        EventManager newEM = new EventManager(gs.getDominionLevel());

        // Copy over old dynamic state
        newEM.setEventTriggers(em.getEventTriggers());
        newEM.setTriggeredHistory(em.getTriggeredHistory());
        newEM.setPreventedEvents(em.getPreventedEvents());
        newEM.setForcedQueue(em.getForcedQueue());
        newEM.setCurrentEventID(em.getCurrentEventID());

        return newEM;
    }

    /**
     * Apply the effects of a player's choice based on the triggered event.
     * - Applies the choice's stat changes
     * - Queues forced events per forced event queuing rules (see queueForcedEventIfApplicable(...))
     * - Adds prevented events to preventedEvents set
     * - Applies the highest-priority applicable influence (first-by-descending-priority)
     */
    public void choose(Event event, Event.Choice choice, GameState gs) {
        if (event == null || choice == null) return;

        // Apply stat changes
        gs.applyStats(choice.getStatChange());

        // Queue forced event from this choice
        Event.EventRef forcesEvent = choice.getForcesEvent();
        if (forcesEvent != null && forcesEvent.getId() != -1) {
            queueForcedEventIfApplicable(forcesEvent.getId());
        }

        // Handle prevented events
        for (Event.EventRef ref : choice.getPreventEvents()) {
            int id = ref.getId();
            if (id == -1) continue;

            if (!loadedEvents.containsKey(id)) {
                System.err.println("[EventManager] Warning: choice prevents unknown event id " + id);
            } else {
                preventedEvents.add(id);
            }
        }

        // Apply highest-priority triggered influence (if any)
        Event.EventInfluence influence = getHighestPriorityTriggeredInfluence(choice.getEventInfluences());
        if (influence != null) {
            gs.applyStats(influence.getStatChange());
        }
    }

    /**
     * Determines the highest-priority triggered influence from a list of influences.
     * @param influences The list of the all the choice's influences
     * @return Returns the greatest influence (first-by-descending-priority) or null if no influence
     */
    private Event.EventInfluence getHighestPriorityTriggeredInfluence(List<Event.EventInfluence> influences) {
        if (influences.isEmpty()) return null;

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

    public Integer getCurrentEventID() {
        return this.currentEventID;
    }

    public Event getCurrentEvent() {
        return loadedEvents.get(currentEventID);
    }

    public void setEventTriggers(Map<Integer, Integer> triggers) {
        this.eventTriggers = new HashMap<>(triggers);
    }

    public void setTriggeredHistory(List<Integer> history) {
        this.triggeredHistory = new ArrayList<>(history);
    }

    public void setPreventedEvents(Set<Integer> prevented) {
        this.preventedEvents = new HashSet<>(prevented);
    }

    public void setForcedQueue(Deque<Integer> forced) {
        this.forcedQueue = new ArrayDeque<>(forced);
    }

    public void setCurrentEventID(Integer id) {
        this.currentEventID = id;
    }

    // ---------- EVENT-VIEW/DISPLAY INFORMATION ----------

    /**
     * Represents a simplified view of an event suitable for displaying in the UI.
     * This class is immutable — once created, its data cannot be modified.
     */
    public static class EventView {
        private final String title;
        private final List<String> descriptionLines;
        private final List<List<String>> choiceLines;

        public EventView(String title, List<String> descriptionLines, List<List<String>> choiceLines) {
            this.title = title;
            this.descriptionLines = descriptionLines;
            this.choiceLines = choiceLines;
        }

        public String getTitle() {
            return title;
        }

        public List<String> getDescriptionLines() {
            return descriptionLines;
        }

        public List<List<String>> getChoiceLines() {
            return choiceLines;
        }
    }

    /**
     * Converts an Event object into a simplified EventView for UI display.
     * Extracts the title, description, and choice text.
     */
    public EventView getEventView(Event event) {
        if (event == null) return null;

        // Description and choices are guaranteed non-null
        List<String> descriptionLines = event.getDescription();
        List<List<String>> choiceLines = new ArrayList<>();

        for (Event.Choice choice : event.getChoices()) {
            choiceLines.add(choice.getText());
        }

        return new EventView(event.getTitle(), descriptionLines, choiceLines);
    }

    /**
     * Retrieves the outcome text for a choice, prioritizing triggered influences.
     * The priority for what text to display is:
     * 1. Override outcome text from the highest-priority triggered influence
     * 2. The choice's normal outcome text
     * 3. The choice's display text as a fallback
     *
     * @param choice The choice to evaluate
     * @return A list of strings representing the lines of text to display for this choice's outcome
     */
    public List<String> getChoiceOutcomeLines(Event.Choice choice) {
        if (choice == null) return List.of();

        // Highest-priority triggered influence
        Event.EventInfluence highest =
                getHighestPriorityTriggeredInfluence(choice.getEventInfluences());

        if (highest != null && !highest.getOverrideOutcomeText().isEmpty()) {
            return highest.getOverrideOutcomeText();
        }

        // Normal outcome text
        if (!choice.getOutcomeText().isEmpty()) {
            return choice.getOutcomeText();
        }

        // Fallback: choice text
        if (!choice.getText().isEmpty()) {
            return choice.getText();
        }

        return List.of();
    }
}

