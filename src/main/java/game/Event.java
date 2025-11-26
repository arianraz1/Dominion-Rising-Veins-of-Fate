package game;

import java.util.ArrayList;
import java.util.List;

public class Event {
    private int id = -1;
    private String title = "";
    private List<String> description = new ArrayList<>();
    private List<Choice> choices = new ArrayList<>();
    private Requirements requirements = new Requirements();
    private boolean forced = false;
    private int maxTriggered = -1;

    public int getId() { return id; }
    public String getTitle() { return title; }
    public List<String> getDescription() { return description; }
    public List<Choice> getChoices() { return choices; }
    public Requirements getRequirements() { return requirements; }
    public boolean isForced() { return forced; }
    public int getMaxTriggered() { return maxTriggered; }

    public static class Choice {
        private List<String> text = new ArrayList<>(); // changed to list
        private StatHolder statChange = new StatHolder();
        private List<String> outcomeText = new ArrayList<>(); // changed to list
        private EventRef forcesEvent = null;
        private List<EventRef> preventEvents = new ArrayList<>();
        private List<EventInfluence> eventInfluences = new ArrayList<>();

        public List<String> getText() { return text; }
        public StatHolder getStatChange() { return statChange; }
        public List<String> getOutcomeText() { return outcomeText; }
        public EventRef getForcesEvent() { return forcesEvent; }
        public List<EventRef> getPreventEvents() { return preventEvents; }
        public List<EventInfluence> getEventInfluences() { return eventInfluences; }
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
        private List<EventRef> requiredEvents = new ArrayList<>();

        public StatHolder getMinStats() { return minStats; }
        public StatHolder getMaxStats() { return maxStats; }
        public List<EventRef> getRequiredEvents() { return requiredEvents; }
    }

    public static class EventRef {
        private int id = -1;
        private String title = "";

        public int getId() { return id; }
        public String getTitle() { return title; }
    }

    public static class EventInfluence {
        private int id = -1;
        private String title = "";
        private int priority = -1;
        private StatHolder statChange = new StatHolder();
        private List<String> overrideOutcomeText = new ArrayList<>(); // changed to list

        public int getId() { return id; }
        public String getTitle() { return title; }
        public int getPriority() { return priority; }
        public StatHolder getStatChange() { return statChange; }
        public List<String> getOverrideOutcomeText() { return overrideOutcomeText; }
    }
}
