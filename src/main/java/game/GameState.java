package game;

public class GameState {
    private int blood;
    private int population;
    private int happiness;
    private int corruption;
    private int dominionLevel;

    // Default values as constants to allow for substantial player growth
    private static final int DEFAULT_BLOOD = 100; // Initial blood to prevent early death
    private static final int DEFAULT_POPULATION = 100; // Initial population to prevent early loss
    private static final int DEFAULT_HAPPINESS = 50; // Base vampiric happiness
    private static final int DEFAULT_CORRUPTION = 75; // Base vampiric corruption
    private static final int DEFAULT_DOMINION_LEVEL = 0; // Base dominion level

    // Maximum values as constants to allow for game continuity
    private static final int MAX_BLOOD = 10_000_000; // Set as the highest stat maximum, blood is most common
    private static final int MAX_POPULATION = 1_000_000; // Allow for substantial kingdom expansion
    private static final int MAX_HAPPINESS = 100_000; // Scarcer, reflects the vampiric nature of the kingdom
    private static final int MAX_CORRUPTION = 1_000_000; // Allow for substantial growth in corruption
    private static final int MAX_DOMINION_LEVEL = 5; // Maximum dominion level — game completion

    // Prevent stats from turning negative
    private static final int MIN_STAT = 0;

    // Construct Default Values
    public GameState() {
        this.blood = DEFAULT_BLOOD;
        this.population = DEFAULT_POPULATION;
        this.happiness = DEFAULT_HAPPINESS;
        this.corruption = DEFAULT_CORRUPTION;
        this.dominionLevel = DEFAULT_DOMINION_LEVEL;
    }

    // Alternatively, construct custom values
    public GameState(int blood, int population, int happiness, int corruption, int dominionLevel) {
        setBlood(blood);
        setPopulation(population);
        setHappiness(happiness);
        setCorruption(corruption);
        setDominionLevel(dominionLevel);
    }

    public int getBlood() { return blood; }
    public int getPopulation() { return population; }
    public int getHappiness() { return happiness; }
    public int getCorruption() { return corruption; }
    public int getDominionLevel() { return dominionLevel; }

    // No stat may go negative
    public void setBlood(int blood) {
        this.blood = clampStat(blood, MIN_STAT, MAX_BLOOD);
    }
    public void setPopulation(int population) {
        this.population = clampStat(population, MIN_STAT, MAX_POPULATION);
    }

    public void setHappiness(int happiness) {
        this.happiness = clampStat(happiness, MIN_STAT, MAX_HAPPINESS);
    }

    public void setCorruption(int corruption) {
        this.corruption = clampStat(corruption, MIN_STAT, MAX_CORRUPTION);
    }
    public void setDominionLevel(int dominionLevel) {
        this.dominionLevel = clampStat(dominionLevel, MIN_STAT, MAX_DOMINION_LEVEL);
    }

    private int clampStat(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    public void addBlood(int amount) {
        setBlood(this.blood + amount);
    }

    public void addPopulation(int amount) {
        setPopulation(this.population + amount);
    }

    public void addHappiness(int amount) {
        setHappiness(this.happiness + amount);
    }

    public void addCorruption(int amount) {
        setCorruption(this.corruption + amount);
    }

    public void addDominionLevel(int amount) {
        setDominionLevel(this.dominionLevel + amount);
    }

    @Override
    public String toString() {
        return String.format(
                "Blood: %,d | Population: %,d | Happiness: %,d | Corruption: %,d | Dominion Level: %d",
                blood, population, happiness, corruption, dominionLevel
        );
    }
}