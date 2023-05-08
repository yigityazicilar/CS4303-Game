package org.deckmaster;
import org.deckmaster.mapgen.Building;
import org.deckmaster.mapgen.Map;
import org.deckmaster.mapgen.MapTile;
import org.deckmaster.mapgen.TileLocation;
import org.deckmaster.ui.EscapeMenu;
import org.deckmaster.ui.MainMenu;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PMatrix2D;
import processing.core.PVector;
import processing.opengl.PShader;

import java.io.*;
import java.util.*;


// TODO: display info when hover mouse to slot and property, next event (event train), file loading, save/load, proactive events (hotkey to activate a event that allows player to spend a card, trigger some results e.g. another events)
public class Game extends PApplet {
    public static Game game = null;
    public PVector cameraPosition = new PVector(0, 0);
    public Player player;
    public Map map;

    public final float UPDATE_TIME = 16.6f;
    private float lag = 0f;
    public long lastUpdate = System.currentTimeMillis();
    InventoryScreen screen;
    EventScreen evtscreen;
    CardSlot slot;
    public GameState state;
    public boolean inBuilding = false;
    public Building buildingToDraw = null;

    private EscapeMenu escapeMenu;
    private MainMenu mainMenu;

    ContentLoader contentLoader;

    ArrayList<String> trackedEvents;

    @Override
    public void settings() {
        size(displayWidth, displayHeight, P2D);
        fullScreen();
    }

    @Override
    public void setup() {
        game = this;
        state = GameState.MAIN_MENU;
        player = new Player(new PVector(25, 25), 10);

        map = new Map();
        map.setup();

        escapeMenu = new EscapeMenu();
        mainMenu = new MainMenu();
    }

    public void toMainMenu() {
        player.pos = new PVector(25, 25);
        calcCameraPos();

        map.setup();
        state = GameState.MAIN_MENU;
    }

    public void startGame() {
        player.pos = new PVector(25, 25);
        calcCameraPos();

        map.setup();

        contentLoader = new ContentLoader();
        trackedEvents = new ArrayList<>();
        initEvents();

        screen = new InventoryScreen(player);

        for (int i = 0; i < 10; i++) {
            Card c = new Card("a", "images/cards/Merlin.png", new HashMap<>());
            c.addProperty(Property.LUNAR, 1);
            player.addCard(c);
        }

        slot = new CardSlot(new ArrayList<>(List.of(Property.LUNAR)));
        slot.setCoord(new PVector((float) g.width * 0.5f, (float) g.height * 0.3f));
        screen.show();

        evtscreen = new EventScreen(new Event("Explosion at the Brass Alchemy Shop", "description", "images/cards/background.png", 1, new HashMap<>(), new HashMap<>(), new ArrayList<>(), "", "", 1), player, screen);
        evtscreen.show();
        state = GameState.WORLD;
    }

    @Override
    public void mousePressed() {
        if (state == GameState.WORLD || state == GameState.EVENT) {
            for (Card card: player.getCards()) {
                if (card.getCoord() != null) card.onClick(mouseX, mouseY);
            }
        }
    }

    @Override
    public void mouseReleased() {
        switch (state) {
            case MAIN_MENU -> {
                mainMenu.checkButtons();
            }
            case WORLD -> {
                screen.leftArrow.onClick(mouseX, mouseY);
                screen.rightArrow.onClick(mouseX, mouseY);
                for (Card card: player.getCards()) {
                    if (card.getCoord() != null) card.onRelease(new ArrayList<>());
                }
            }
            case EVENT -> {
                evtscreen.confirmBtn.onClick(mouseX, mouseY);
                evtscreen.inventory.leftArrow.onClick(mouseX, mouseY);
                evtscreen.inventory.rightArrow.onClick(mouseX, mouseY);
                for (Card card: player.getCards()) {
                    if (card.getCoord() != null) card.onRelease(evtscreen.getSlots());
                }
            }
            case ESCAPE_MENU -> {
                escapeMenu.checkButtons();
            }
        }
    }

    @Override
    public void draw() {
        // Calculate deltaTime for Physics
        long deltaTime = System.currentTimeMillis() - lastUpdate;
        lastUpdate = System.currentTimeMillis();
        lag += deltaTime;

        background(20);

        // Made the camera follow the Player.
        calcCameraPos();
        game.translate(width / 2f - cameraPosition.x, height / 2f - cameraPosition.y);

        switch (state) {
            case MAIN_MENU -> {
                map.draw();
                mainMenu.draw();
            }
            case WORLD -> {
                if (Input.escapePressed) {
                    state = GameState.ESCAPE_MENU;
                    escapeMenu.previousState = GameState.WORLD;
                    Input.escapePressed = false;
                }
                if (!inBuilding) {
                    map.draw();
                } else {
                    buildingToDraw.draw();
                }
//                slot.draw();
                player.draw();
                screen.draw();

                fill(255, 255, 255);
                textAlign(LEFT, CENTER);
                textFont(Configurations.MARCO_FONT);
                textSize(12);
                text(String.format("X: %f Y: %f Z: %f",
                        player.pos.x,
                        player.pos.y,
                        map.tileMap.get(TileLocation.worldToTileCoords(player.pos)).height),
                        cameraPosition.x - width / 2f + 20, cameraPosition.y - height / 2f + 20);
            }
            case EVENT -> {
                if (Input.escapePressed) {
                    state = GameState.ESCAPE_MENU;
                    escapeMenu.previousState = GameState.EVENT;
                    Input.escapePressed = false;
                }
                map.draw();
                evtscreen.draw();
            }
            case ESCAPE_MENU -> {
                switch (escapeMenu.previousState) {
                    case WORLD -> {
                        if (!inBuilding) {
                            map.draw();
                        } else {
                            buildingToDraw.draw();
                        }
//                slot.draw();
                        player.draw();
                        screen.draw();

                    }
                    case EVENT -> {
                        map.draw();
                        evtscreen.draw();
                    }
                }

                if (Input.escapePressed) {
                    state = escapeMenu.previousState;
                    escapeMenu.previousState = null;
                    Input.escapePressed = false;
                }
                escapeMenu.draw();
            }
        }

        update();
    }

    public void update() {
        while (lag > UPDATE_TIME) {
            switch(state) {
                case MAIN_MENU -> {
                    float moveAmount = 150f * (UPDATE_TIME / 1000f);
                    player.pos.add(0, -moveAmount);
                }
                case WORLD -> {
                    player.update();
                    slot.update();
                    screen.update();
                }
                case EVENT -> {
                    evtscreen.update();
                }
            }

            lag -= UPDATE_TIME;
        }
    }

    private void calcCameraPos() {
        if (cameraPosition.x > player.pos.x) {
            cameraPosition.x -= Math.abs(cameraPosition.x - player.pos.x);
        } else {
            cameraPosition.x += Math.abs(cameraPosition.x - player.pos.x);
        }

        if (cameraPosition.y > player.pos.y) {
            cameraPosition.y -= Math.abs(cameraPosition.y - player.pos.y);
        } else {
            cameraPosition.y += Math.abs(cameraPosition.y - player.pos.y);
        }
    }

    @Override
    public void keyPressed() {
        Input.checkKeyPressed();
    }

    @Override
    public void keyReleased() {
        Input.checkKeyReleased();
        if (key == 't') {
            Event event = contentLoader.loadEvent("xxx"); // TODO: PROACTIVE EVENT
            evtscreen = new EventScreen(event, player, screen);
            evtscreen.show();
            state = GameState.EVENT;
        }
    }

    public boolean overRect(int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public static void main(String[] args) {
        PApplet.main(Game.class);
    }

    public void save() {
        try {
            // saving map
            FileOutputStream mapFoe = new FileOutputStream(Configurations.MAP_SAVING_LOCATION);
            ObjectOutputStream mapOos = new ObjectOutputStream(mapFoe);
            mapOos.writeObject(map.tileMap);

            // saving player stats
            FileOutputStream playerFoe = new FileOutputStream(Configurations.PLAYER_SAVING_LOCATION);
            ObjectOutputStream playerOos = new ObjectOutputStream(playerFoe);
            playerOos.writeObject(player.getCards());

            // saving player pos
            FileOutputStream posFoe = new FileOutputStream(Configurations.POS_SAVING_LOCATION);
            ObjectOutputStream posOos = new ObjectOutputStream(posFoe);
            posOos.writeObject(player.pos);

            // saving active events list
            FileOutputStream eventFoe = new FileOutputStream(Configurations.EVENT_SAVING_LOCATION);
            ObjectOutputStream eventOos = new ObjectOutputStream(eventFoe);
            eventOos.writeObject(trackedEvents);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void load() {
        try {
            // loading map
            FileInputStream mapFie = new FileInputStream(Configurations.MAP_SAVING_LOCATION);
            ObjectInputStream mapOis = new ObjectInputStream(mapFie);
            map.tileMap = (HashMap<TileLocation, MapTile>) mapOis.readObject();

            // loading player
            FileInputStream playerFie = new FileInputStream(Configurations.PLAYER_SAVING_LOCATION);
            ObjectInputStream playerOis = new ObjectInputStream(playerFie);
            player.setCards((ArrayList<Card>) playerOis.readObject());

            // loading player pos
            FileInputStream posFie = new FileInputStream(Configurations.POS_SAVING_LOCATION);
            ObjectInputStream posOis = new ObjectInputStream(posFie);
            player.pos = (PVector) posOis.readObject();

            // loading events
            FileInputStream eventsFie = new FileInputStream(Configurations.EVENT_SAVING_LOCATION);
            ObjectInputStream eventOis = new ObjectInputStream(eventsFie);
            trackedEvents = (ArrayList<String>) eventOis.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void initEvents() {
        for (String name : contentLoader.nameFileIndexTable.keySet()) {
            Event e = contentLoader.loadEvent(name);
            if (e != null && e.getPreviousEventName().equals("")) {
                trackedEvents.add(e.getTitle());
            }
        }
    }
}
