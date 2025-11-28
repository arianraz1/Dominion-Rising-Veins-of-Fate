package game;

import javax.swing.*;
import java.io.IOException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        // DEV TESTING, DO NOT UNCOMMENT
        // SaveManager.resetSave();

        Scanner input = new Scanner(System.in);
        GameUI ui = new GameUI(input);

        GameSession session = setGameLoad(ui);
        GameState gs = session.gs;
        EventManager em = session.em;

        int dominionLevel = gs.getDominionLevel();

        ui.displayGameTitle();

        boolean running = true;

        // Check if there's any events to begin with
        em.updateAvailableEvents(gs);

        // If not, last session had no events or there are no more events due to other circumstances (updates, etc)
        if (em.getAvailableEvents().isEmpty()) {
            ui.displayNoEventsAvailable();
            return;
        }

        // If the last save had an event running prior to exiting
        if (em.getCurrentEventID() != null) {
            ui.displayGameStats(gs);
            em.triggerEventToHistory(em.getCurrentEventID());
            handleGame(ui, gs, em, em.getCurrentEvent());
            // Manually trigger history and update available events
            em.updateAvailableEvents(gs);
            SaveManager.saveGame(gs, em);
        }

        ui.displayGameStats(gs);

        while (!em.getAvailableEvents().isEmpty()) {
            // DEV TESTING, DO NOT UNCOMMENT
            // System.out.println("Forced Events Queue: " + em.getForcedQueue());
            // System.out.println("Available Events List: " + em.getAvailableEvents());

            // Handle level-up
            if (gs.getDominionLevel() != dominionLevel) {
                em = EventManager.handleLevelUp(gs, em);
                dominionLevel = gs.getDominionLevel();
            }

            Event event = em.getRandomWeightedEvent(gs);

            SaveManager.saveGame(gs, em);

            handleGame(ui, gs, em, event);

            ui.displayGameStats(gs);
        }

        ui.displayNoEventsAvailable();
        input.close();
    }

    private static void handleGame(GameUI ui, GameState gs, EventManager em, Event event) throws IOException {
        if (event == null) {
            ui.displayNoEventsAvailable();
            return;
        }

        // Display event
        EventManager.EventView view = em.getEventView(event);
        displayEvent(ui, view);

        // Skip choice selection if there are no choices
        if (view.getChoiceLines().isEmpty()) {
            handleOutcome(ui, em, gs, null);
            return;
        }

        handleChoices(ui, em, gs, event, view);
    }

    private static void displayEvent(GameUI ui, EventManager.EventView view) {
        ui.displayEventHeader();
        ui.displayEventTitle(view);
        ui.displayEventDescription(view);
        ui.displayEventChoices(view);
        ui.displayBottom();
    }

    private static void handleChoices(GameUI ui, EventManager em, GameState gs, Event event, EventManager.EventView view) throws IOException {
        int choiceIndex = ui.getChoiceSelection(view);

        Event.Choice choice = event.getChoices().get(choiceIndex);

        // Apply choice results
        em.choose(event, choice, gs);

        handleOutcome(ui, em, gs, choice);
    }

    private static void handleOutcome(GameUI ui, EventManager em, GameState gs, Event.Choice choice) throws IOException {
        displayOutcome(ui, gs);
        // If there's a choice, display its outcome
        if (choice != null) ui.displayEventOutcome(em, choice);
        // The game state and outcome is displayed and results are successful, do NOT consider this a current event
        em.setCurrentEventID(null);
        // Save this state
        SaveManager.saveGame(gs, em);
    }

    public static void displayOutcome(GameUI ui, GameState gs) {
        ui.initiateEventOutcome();
        ui.displayGameStats(gs);
        ui.displayOutcomeHeader();
        ui.displayBottom();
        ui.finishEventOutcome();
    }

    private static GameSession setGameLoad(GameUI ui) {
        GameStartOption choice = ui.getNewGameChoice();

        if (choice == GameStartOption.NEW_GAME) { // Player wants new game
            boolean overwrite = ui.getOverWriteSave();
            if (overwrite) {
                SaveManager.resetSave();
                GameState gs = new GameState();
                EventManager em = new EventManager(gs.getDominionLevel());
                return new GameSession(gs, em);
            }
            // If not overwriting, fall through to load existing save
        }

        FullGameSave fgs = SaveManager.loadGame();
        GameState gs = new GameState(fgs.getGameState());
        EventManager em = new EventManager(gs.getDominionLevel());
        SaveManager.restoreEventManager(em, fgs);
        return new GameSession(gs, em);
    }

    public static class GameSession {
        public final GameState gs;
        public final EventManager em;

        public GameSession(GameState gs, EventManager em) {
            this.gs = gs;
            this.em = em;
        }
    }
}
