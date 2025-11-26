package game;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        // DEV ONLY: Uncomment to reset
        SaveManager.resetSave();

        // Load saved game
        FullGameSave load = SaveManager.loadGame();

        // Copy game state from save
        GameState gs = new GameState(load.getGameState());

        // Initialize EventManager
        EventManager em = new EventManager(gs.getDominionLevel(), gs);

        // Restore dynamic event state
        SaveManager.restoreEventManager(em, load);

        int dominionLevel = gs.getDominionLevel();

        System.out.println("Dominion Rising: Veins of Fate");
        System.out.println(gs);

        Scanner input = new Scanner(System.in);
        boolean running = true;

        while (running) {
            em.updateAvailableEvents(gs);

            System.out.print("Forced Queue: " + em.getForcedQueue());
            System.out.println("Available Events: " + em.getAvailableEvents());
            System.out.println("Event Triggers: " + em.getEventTriggers());
            System.out.println("Triggered History: " + em.getTriggeredHistory());
            // Handle level-up
            if (gs.getDominionLevel() != dominionLevel) {
                em = EventManager.handleLevelUp(gs, em);
                dominionLevel = gs.getDominionLevel();
            }

            // Get a random event
            Event event = em.getRandomEvent(gs);
            if (event == null) {
                System.out.println("No events available. You may have finished the game or encountered an error.");
                break;
            }

            // Display event
            EventManager.EventView view = em.getEventView(event);
            System.out.println("\n-=-=-=-=-=-=-=--=-=-=-=-=-=-=- Event -=-=-=-=-=-=-=-=-=-=-=-=-=-=--=-=-=-=-");
            System.out.println(view.getTitle() + "\n");

            // Print multi-line description
            for (String line : view.getDescriptionLines()) {
                System.out.println(line);
            }
            System.out.println();

            // Skip choice selection if there are no choices
            if (view.getChoiceLines().isEmpty()) {
                // Mark event as triggered AFTER viewing it
                em.triggerEvent(event.getId(), gs);

                SaveManager.saveGame(gs, em);
                running = continueGame(input);
                if (!running) { break; }
                else { continue; }
            }

            // Display choices (multi-line)
            for (int i = 0; i < view.getChoiceLines().size(); i++) {
                List<String> choiceTextLines = view.getChoiceLines().get(i);
                System.out.printf("%d: %s%n", i + 1, choiceTextLines.get(0));
                for (int j = 1; j < choiceTextLines.size(); j++) {
                    System.out.println("   " + choiceTextLines.get(j));
                }
                System.out.println();
            }

            // Get player choice
            int choiceIndex = -1;
            while (choiceIndex < 0 || choiceIndex >= view.getChoiceLines().size()) {
                System.out.print("Choose an option: ");
                if (input.hasNextInt()) {
                    choiceIndex = input.nextInt() - 1;
                    input.nextLine(); // Consume leftover newline
                } else {
                    input.next(); // Skip invalid input
                }
            }

            Event.Choice choice = event.getChoices().get(choiceIndex);

            // Apply choice results
            em.choose(event, choice, gs);

            // Save after choice
            SaveManager.saveGame(gs, em);

            // Show outcome (multi-line)
            List<String> outcomeLines = em.getChoiceOutcomeLines(choice);
            System.out.println("\nOutcome:");
            for (String line : outcomeLines) {
                System.out.println("  " + line);
            }

            // Show current stats
            System.out.println("Current stats: " + gs);

            running = continueGame(input);
        }

        System.out.println("Game session ended.");
        input.close();
    }

    public static boolean continueGame(Scanner sc) {
        while (true) {
            System.out.print("Continue? (y/n): ");
            String cont = sc.nextLine().trim();
            if (cont.equalsIgnoreCase("y")) {
                return true;
            } else if (cont.equalsIgnoreCase("n")) {
                return false;
            } else {
                System.out.println("Invalid input. Please enter 'y' or 'n'.");
            }
        }
    }
}


