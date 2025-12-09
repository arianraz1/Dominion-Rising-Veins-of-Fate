package game;

import java.util.List;
import java.util.Scanner;

/**
 * This class displays all game material, while handling player choices
 */
public class GameUI {
    private final Scanner sc;

    public GameUI(Scanner sc) {
        this.sc = sc;
    }

    public void displayGameTitle() {
        System.out.println("Dominion Rising: Veins of Fate");
    }

    public void displayGameStats(GameState gs) {
        System.out.println(gs);
    }

    public void displayNoEventsAvailable() {
        System.out.println("No events available. You may have finished the game or encountered an error.");
    }

    public void displayEventHeader() {
        System.out.println("\n-=-=-=-=-=-=-=--=-=-=-=-=-=-=-=- Event -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=--=-=-=-=-");
    }

    public void displayBottom() {
        System.out.println("\n-=-=-=-=-=-=-=--=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=--=-=-=-=-");
    }

    public void displayOutcomeHeader() {
        System.out.println("\n-=-=-=-=-=-=-=--=-=-=-=-=-=-=-= Outcome =-=-=-=-=-=-=-=-=-=-=-=-=-=-=--=-=-=-=-");
    }

    public void displayEventTitle(EventManager.EventView view) {
        System.out.println(view.getTitle() + "\n");
    }

    public void displayEventDescription(EventManager.EventView view) {
        for (String line : view.getDescriptionLines()) {
            System.out.println(line);
        }
        System.out.println();
    }

    public void displayEventChoices(EventManager.EventView view) {
        for (int i = 0; i < view.getChoiceLines().size(); i++) {
            List<String> choiceTextLines = view.getChoiceLines().get(i);
            System.out.printf("%d: %s%n", i + 1, choiceTextLines.get(0));
            for (int j = 1; j < choiceTextLines.size(); j++) {
                System.out.println("   " + choiceTextLines.get(j));
            }
            System.out.println();
        }
    }

    public void displayEventOutcome(EventManager em, Event.Choice choice) {
        List<String> outcomeLines = em.getChoiceOutcomeLines(choice);
        System.out.println("\nOutcome:");
        for (String line : outcomeLines) {
            System.out.println("  " + line);
        }
    }

    public void finishEventOutcome() {
        handleEventOutcome("\nPress enter to continue...");
    }

    public void initiateEventOutcome() {
        handleEventOutcome("\nPress enter to see outcome");
    }

    public void handleEventOutcome(String prompt) {
        System.out.println(prompt);
        sc.nextLine(); // now reliably waits for one enter press
    }

    public boolean getContinueGame(EventManager em) {
        boolean cont = getYesNoInput("Continue?");
        if (!cont) {
            em.setCurrentEventID(null);
        }
        return cont;
    }

    public int getChoiceSelection(EventManager.EventView view) {
        int choiceIndex = -1;
        while (choiceIndex < 0 || choiceIndex >= view.getChoiceLines().size()) {
            System.out.print("Choose an option: ");
            String input = sc.nextLine().trim();
            try {
                choiceIndex = Integer.parseInt(input) - 1;
                if (choiceIndex < 0 || choiceIndex >= view.getChoiceLines().size()) {
                    System.out.println("Invalid choice. Please enter a number between 1 and " + view.getChoiceLines().size() + ".");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            }
        }
        return choiceIndex;
    }

    public GameStartOption getNewGameChoice() {
        boolean saveExists = SaveManager.saveExists();

        while (true) {
            if (saveExists) {
                System.out.println("1: Continue saved game");
                System.out.println("2: Start new game");
            } else {
                System.out.println("1: Start new game");
            }

            String input = sc.nextLine().trim();
            try {
                int choice = Integer.parseInt(input);
                if (saveExists) {
                    if (choice == 1) return GameStartOption.LOAD_GAME;
                    if (choice == 2) return GameStartOption.NEW_GAME;
                } else {
                    if (choice == 1) return GameStartOption.NEW_GAME;
                }
            } catch (NumberFormatException e) {
                // fall through to print invalid
            }
            System.out.println("Invalid input. Please enter a valid number.");
        }
    }

    public boolean getOverWriteSave() {
        if (SaveManager.saveExists()) {
            return getYesNoInput("A save exists. Overwrite?");
        }
        return true;
    }

    private boolean getYesNoInput(String prompt) {
        while (true) {
            System.out.print(prompt + " (y/n): ");
            String input = sc.nextLine().trim();
            if (input.equalsIgnoreCase("y")) return true;
            if (input.equalsIgnoreCase("n")) return false;
            System.out.println("Invalid input. Please enter 'y' or 'n'.");
        }
    }
}

