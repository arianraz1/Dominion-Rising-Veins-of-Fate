package game;

import java.util.ArrayList;
import java.util.HashMap;

public class EventManager {
    final private String eventsFolder = "/events";
    private HashMap<Integer, Event> loadedEvents;
    private ArrayList<Event> currentDominionEvents;
}
