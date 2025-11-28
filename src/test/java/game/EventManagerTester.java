package game;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class EventManagerTester {

    private GameState gs;
    private EventManager em;

    @BeforeEach
    void setUp() {
        gs = new GameState();
        em = new EventManager(gs.getDominionLevel(), gs);

        // Manually add a test event
        Event event = new Event();
        try {
            var idField = Event.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(event, 999);

            Event.Choice choice = new Event.Choice();
            var textField = Event.Choice.class.getDeclaredField("text");
            textField.setAccessible(true);
            textField.set(choice, "Test Choice");
            event.getChoices().add(choice);

            var loadedEventsField = EventManager.class.getDeclaredField("loadedEvents");
            loadedEventsField.setAccessible(true);
            var loadedMap = (java.util.Map<Integer, Event>) loadedEventsField.get(em);
            loadedMap.put(event.getId(), event);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testTriggerEvent() {
        Event event = em.triggerEvent(999, gs);
        assertNotNull(event, "Event should trigger");
        assertEquals(999, event.getId(), "Event ID should match");
    }

    @Test
    void testForcedEventQueue() {
        assertTrue(em.queueForcedEvent(999), "Should successfully queue forced event");
        Event peek = em.peekForcedEvent();
        assertNotNull(peek);
        assertEquals(999, peek.getId(), "Forced event peek should match");

        Event polled = em.getRandomEvent();
        assertEquals(999, polled.getId(), "Forced event should be returned by getRandomEvent");
    }

    @Test
    void testChooseAppliesStats() {
        Event event = em.getEvent(999);
        Event.Choice choice = event.getChoices().get(0);

        // Set stat change via reflection
        try {
            var statField = Event.StatHolder.class.getDeclaredField("blood");
            statField.setAccessible(true);
            statField.set(choice.getStatChange(), 50);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int bloodBefore = gs.getBlood();
        em.choose(event, choice, gs);
        assertEquals(bloodBefore + 50, gs.getBlood(), "Blood should increase by 50");
    }

    @Test
    void testSaveLoadCompatible() throws IOException {
        // Trigger the event so it shows in eventTriggers
        em.triggerEvent(999, gs);
        em.queueForcedEvent(999);

        // Save
        SaveManager.saveGame(gs, em);

        // Load
        FullGameSave loaded = SaveManager.loadGame();

        // Restore manually using EventManager API
        // Use triggerEvent and queueForcedEvent to "replay" state
        for (int id : loaded.getEventTriggers().keySet()) {
            int count = loaded.getEventTriggers().get(id);
            for (int i = 0; i < count; i++) {
                em.triggerEvent(id, gs);
            }
        }

        for (int id : loaded.getForcedQueue()) {
            em.queueForcedEvent(id);
        }

        for (int id : loaded.getPreventEvents()) {
            // no direct method to add prevented events, but you can test logic via canTriggerEvent
            // skipped for simplicity
        }

        assertTrue(em.getEventTriggers().containsKey(999), "Loaded triggers should contain event 999");
        assertEquals(1, em.getEventTriggers().get(999), "Event 999 should have triggered once");
        assertNotNull(em.peekForcedEvent(), "Forced event should still be in queue");
        assertEquals(999, em.peekForcedEvent().getId(), "Forced event ID should match");
    }
}
