package game;

import java.util.ArrayList;
import java.util.HashMap;

public class Event {
    private int id;
    private String title;
    private String description;
    private ArrayList<Choice> choices;
    private Requirements requirements;
    private HashMap<Integer, EventRef> preventEvents;
    private boolean forced;
    private HashMap<Integer, EventRef> forcesEvents;
    private HashMap<Integer, EventInfluence> eventInfluences;

    public Event() {
        this.id = -1;
        this.title = "";
        this.description = "";
        this.choices = new ArrayList<>();
        this.requirements = new Requirements();
        this.preventEvents = new HashMap<>();
        this.forced = false;
        this.forcesEvents = new HashMap<>();
        this.eventInfluences = new HashMap<>();
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ArrayList<Choice> getChoices() { return choices; }
    public Requirements getRequirements() { return requirements; }
    public HashMap<Integer, EventRef> getPreventEvents() { return preventEvents; }
    public boolean isForced() { return forced; }
    public HashMap<Integer, EventRef> getForcesEvents() { return forcesEvents; }
    public HashMap<Integer, EventInfluence> getEventInfluences() { return eventInfluences; }

    public static class Choice {
        private String text;
        private StatHolder statChange;
        private String outcomeText;

        public String getText() { return text; }
        public StatHolder getStatChange() { return statChange; }
        public String getOutcomeText() { return outcomeText; }
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
        private HashMap<Integer, EventRef> requiredEvents;

        public StatHolder getMinStats() { return minStats; }
        public StatHolder getMaxStats() { return maxStats; }
        public HashMap<Integer, EventRef> getRequiredEvents() { return requiredEvents; }
    }

    public static class EventRef {
        private String title;

        public String getTitle() { return title; }
    }

    public static class EventInfluence {
        private String title;
        private int priority;
        private StatHolder statChange;
        private String overrideOutcomeText;

        public String getTitle() { return title; }
        public int getPriority() { return priority; }
        public StatHolder getStatChange() { return statChange; }
        public String getOverrideOutcomeText() { return overrideOutcomeText; }
    }
}
