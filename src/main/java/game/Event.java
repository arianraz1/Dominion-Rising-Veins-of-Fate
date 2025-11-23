package game;

import java.util.ArrayList;
import java.util.HashMap;

public class Event {
    private int id = -1;
    private String title = "";
    private String description = "";
    private ArrayList<Choice> choices = new ArrayList<>();
    private Requirements requirements = new Requirements();
    private HashMap<Integer, EventRef> preventEvents = new HashMap<>();
    private boolean forced = false;
    private int maxTriggered = -1;
    private EventRef forcesEvent = null;
    private HashMap<Integer, EventInfluence> eventInfluences = new HashMap<>();

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public ArrayList<Choice> getChoices() { return choices; }
    public Requirements getRequirements() { return requirements; }
    public HashMap<Integer, EventRef> getPreventEvents() { return preventEvents; }
    public boolean isForced() { return forced; }
    public int getMaxTriggered() { return maxTriggered; }
    public EventRef getForcesEvent() { return forcesEvent; }
    public HashMap<Integer, EventInfluence> getEventInfluences() { return eventInfluences; }

    public static class Choice {
        private String text = "";
        private StatHolder statChange = new StatHolder();
        private String outcomeText = "";

        public String getText() { return text; }
        public StatHolder getStatChange() { return statChange; }
        public String getOutcomeText() { return outcomeText; }
    }

    public static class StatHolder {
        private int blood = -1;
        private int population = -1;
        private int happiness = -1;
        private int corruption = -1;
        private int dominionLevel = -1;

        public int getBlood() { return blood; }
        public int getPopulation() { return population; }
        public int getHappiness() { return happiness; }
        public int getCorruption() { return corruption; }
        public int getDominionLevel() { return dominionLevel; }
    }

    public static class Requirements {
        private StatHolder minStats = new StatHolder();
        private StatHolder maxStats = new StatHolder();
        private HashMap<Integer, EventRef> requiredEvents = new HashMap<>();

        public StatHolder getMinStats() { return minStats; }
        public StatHolder getMaxStats() { return maxStats; }
        public HashMap<Integer, EventRef> getRequiredEvents() { return requiredEvents; }
    }

    public static class EventRef {
        private String title = "";

        public String getTitle() { return title; }
    }

    public static class EventInfluence {
        private String title = "";
        private int priority = -1;
        private StatHolder statChange = new StatHolder();
        private String overrideOutcomeText = "";

        public String getTitle() { return title; }
        public int getPriority() { return priority; }
        public StatHolder getStatChange() { return statChange; }
        public String getOverrideOutcomeText() { return overrideOutcomeText; }
    }
}

