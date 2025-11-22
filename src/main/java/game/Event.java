package game;

import java.util.ArrayList;
import java.util.HashMap;

// Match Json template to class for parsing
public class Event {
    private int id;
    private String title;
    private String description;
    private ArrayList<Choice> choices;
    private Requirements requirements;
    private HashMap<Integer, String> preventEvents; //

    public Event() {
        this.id = 0;
        this.title = "";
        this.description = "";
        this.choices = new ArrayList<>();
        this.preventEvents = new HashMap<>();
        this.requirements = new Requirements();
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ArrayList<Choice> getChoices() { return choices; }
    public Requirements getRequirements() { return requirements; }
    public HashMap<Integer, String> getPreventEvents() { return preventEvents; }

    public static class Choice {
        private String text;
        private StatHolder statChange;

        public String getText() { return text; }
        public StatHolder getStatChange() { return statChange; }
    }

    public static class StatHolder {
        private int blood;
        private int population;
        private int happiness;
        private int corruption;
        private int dominionLevel;

        public int getBlood() { return blood; }
        public int getPopulation() { return population; }
        public int getHappiness() { return happiness; }
        public int getCorruption() { return corruption; }
        public int getDominionLevel() { return dominionLevel; }
    }

    public static class Requirements {
        private StatHolder minStats;
        private StatHolder maxStats;

        public StatHolder getMinStats() { return minStats; }
        public StatHolder getMaxStats() { return maxStats; }
    }
}
