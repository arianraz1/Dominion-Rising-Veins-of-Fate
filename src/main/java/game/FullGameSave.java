package game;

import java.util.*;

public class FullGameSave {
    private GameState gameState;
    private Map<Integer, Integer> eventTriggers;
    private List<Integer> triggeredHistory;
    private List<Integer> preventEvents; // Set converted to List for Gson
    private List<Integer> forcedQueue;   // Deque converted to List for Gson
    private Integer currentEventID;

    // Constructor to create save data
    public FullGameSave(GameState gameState,
                        Map<Integer, Integer> eventTriggers,
                        List<Integer> triggeredHistory,
                        Set<Integer> preventEvents,
                        Deque<Integer> forcedQueue, Integer id) {
        this.gameState = gameState;
        this.eventTriggers = eventTriggers;
        this.triggeredHistory = triggeredHistory;
        this.preventEvents = new ArrayList<>(preventEvents);
        this.forcedQueue = new ArrayList<>(forcedQueue);
        this.currentEventID = id;
    }

    // Empty constructor for Gson
    public FullGameSave() {}

    // Getters to restore original types
    public GameState getGameState() { return gameState; }
    public Map<Integer, Integer> getEventTriggers() { return eventTriggers; }
    public List<Integer> getTriggeredHistory() { return triggeredHistory; }
    public Set<Integer> getPreventEvents() { return new HashSet<>(preventEvents); }
    public Deque<Integer> getForcedQueue() { return new ArrayDeque<>(forcedQueue); }
    public Integer getCurrentEventID() { return currentEventID; }

    // Optional setters if needed
    public void setGameState(GameState gameState) { this.gameState = gameState; }
    public void setEventTriggers(Map<Integer, Integer> eventTriggers) { this.eventTriggers = eventTriggers; }
    public void setTriggeredHistory(List<Integer> triggeredHistory) { this.triggeredHistory = triggeredHistory; }
    public void setPreventEvents(Set<Integer> preventEvents) { this.preventEvents = new ArrayList<>(preventEvents); }
    public void setForcedQueue(Deque<Integer> forcedQueue) { this.forcedQueue = new ArrayList<>(forcedQueue); }
    public void setCurrentEventID(Integer id) { this.currentEventID = id; }
}
