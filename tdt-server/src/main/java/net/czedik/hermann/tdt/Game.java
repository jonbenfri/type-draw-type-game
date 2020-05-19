package net.czedik.hermann.tdt;

import net.czedik.hermann.tdt.actions.AccessAction;
import net.czedik.hermann.tdt.actions.JoinAction;
import net.czedik.hermann.tdt.actions.TypeAction;
import net.czedik.hermann.tdt.playerstate.*;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private static final Logger log = LoggerFactory.getLogger(Game.class);

    // TODO synchronization

    public final String gameId;

    private final Path gameDir;

    private final GameState gameState;

    private final Map<Client, Player> clientToPlayer = new HashMap<>();

    public Game(String gameId, Path gameDir, Player creator) {
        this.gameId = Objects.requireNonNull(gameId);
        this.gameDir = gameDir;
        this.gameState = new GameState();
        gameState.players.add(creator);
    }

    // returns whether the client has been added as a player to the game
    public synchronized boolean access(Client client, AccessAction accessAction) {
        Player player = getPlayerById(accessAction.playerId);
        if (player == null) {
            log.info("Game {}: New player {} accessing via client {}", gameId, accessAction.playerId, client.getId());
            client.send(getStateForAccessByNewPlayer());
            return false;
        } else {
            log.info("Game {}: New client {} connected for known player {}", gameId, client.getId(), player.id);
            addClientForPlayer(client, player);
            updateStateForPlayer(player);
            return true;
        }
    }

    private Player getPlayerById(String playerId) {
        return gameState.players.stream().filter(p -> p.id.equals(playerId)).findAny().orElse(null);
    }

    private PlayerState getStateForAccessByNewPlayer() {
        switch (gameState.state) {
            case WaitingForPlayers:
                return new JoinState();
            case Started:
                return new AlreadyStartedGameState();
            case Finished:
                return getFinishedState();
            default:
                throw new IllegalStateException("Unknown state " + gameState.state);
        }
    }

    private void addClientForPlayer(Client client, Player player) {
        clientToPlayer.put(client, player);
        player.addClient(client);
    }

    // returns whether the client has been added as a player to the game
    public synchronized boolean join(Client client, JoinAction joinAction) {
        if (gameState.state == GameState.State.WaitingForPlayers) {
            log.info("Game {}: Player {} joining with name '{}' via client {}", gameId, joinAction.playerId, joinAction.name, client.getId());
            Player player = getPlayerById(joinAction.playerId);
            if (player != null) {
                log.warn("Game {}: Player {} has already joined", gameId, joinAction.playerId);
            } else {
                player = new Player(joinAction.playerId, joinAction.name, joinAction.avatar, false);
                gameState.players.add(player);
            }
            addClientForPlayer(client, player);
            updateStateForAllPlayers();
            return true;
        } else {
            log.info("Game {}: Join not possible in state {}", gameId, gameState.state);
            client.send(getStateForAccessByNewPlayer());
            return false;
        }
    }

    private void updateStateForAllPlayers() {
        for (Player player : gameState.players) {
            updateStateForPlayer(player);
        }
    }

    private void updateStateForPlayer(Player player) {
        PlayerState playerState = getPlayerState(player);
        for (Client client : player.clients) {
            client.send(playerState);
        }
    }

    private PlayerState getPlayerState(Player player) {
        switch (gameState.state) {
            case WaitingForPlayers:
                return getWaitingForPlayersState(player);
            case Started:
                return getStartedState(player);
            case Finished:
                return getFinishedState();
            default:
                throw new IllegalStateException("Unknown state: " + gameState.state);
        }
    }

    private PlayerState getFinishedState() {
        StoriesState storiesState = new StoriesState();
        storiesState.stories = mapStoriesToFrontendStories();
        return storiesState;
    }

    private FrontendStory[] mapStoriesToFrontendStories() {
        FrontendStory[] frontendStories = new FrontendStory[gameState.stories.length];
        for (int storyIndex = 0; storyIndex < gameState.stories.length; storyIndex++) {
            StoryElement[] elements = gameState.stories[storyIndex].elements;
            FrontendStory frontendStory = mapStoryElementsToFrontendStoryElements(storyIndex, elements);
            frontendStories[storyIndex] = frontendStory;
        }
        return frontendStories;
    }

    private FrontendStory mapStoryElementsToFrontendStoryElements(int storyIndex, StoryElement[] elements) {
        FrontendStory frontendStory = new FrontendStory();
        frontendStory.elements = new FrontendStoryElement[elements.length];
        for (int roundNo = 0; roundNo < elements.length; roundNo++) {
            StoryElement e = elements[roundNo];
            Player player = getPlayerForStoryInRound(storyIndex, roundNo);
            String content = "image".equals(e.type) ? getDrawingSrc(e.content) : e.content;
            frontendStory.elements[roundNo] = new FrontendStoryElement(e.type, content, mapPlayerToPlayerInfo(player));
        }
        return frontendStory;
    }

    private PlayerState getStartedState(Player player) {
        if (gameState.state != GameState.State.Started)
            throw new IllegalStateException("Only valid to call this method in started state");

        if (!hasPlayerFinishedCurrentRound(player)) {
            if (isTypeRound()) {
                return getTypeState(player);
            } else { // draw round
                return getDrawState(player);
            }
        } else {
            return getWaitForRoundFinishedState();
        }
    }

    private PlayerState getWaitForRoundFinishedState() {
        List<Player> playersNotFinished = getNotFinishedPlayers();
        return new WaitForRoundFinishState(mapPlayersToPlayerInfos(playersNotFinished), isTypeRound());
    }

    private PlayerState getDrawState(Player player) {
        int storyIndex = getCurrentStoryIndexForPlayer(player);
        String text = getStoryByIndex(storyIndex).elements[gameState.round - 1].content;
        Player previousPlayer = getPreviousPlayerForStory(storyIndex);
        return new DrawState(gameState.round + 1, gameState.gameMatrix.length, text, mapPlayerToPlayerInfo(previousPlayer));
    }

    private PlayerState getTypeState(Player player) {
        int roundOneBased = gameState.round + 1;
        int rounds = gameState.gameMatrix.length;
        if (gameState.round == 0) {
            return new TypeState(roundOneBased, rounds);
        } else {
            int storyIndex = getCurrentStoryIndexForPlayer(player);
            String imageFilename = getStoryByIndex(storyIndex).elements[gameState.round - 1].content;
            Player previousPlayer = getPreviousPlayerForStory(storyIndex);
            return new TypeState(roundOneBased, rounds, getDrawingSrc(imageFilename), mapPlayerToPlayerInfo(previousPlayer));
        }
    }

    private PlayerState getWaitingForPlayersState(Player player) {
        if (gameState.state != GameState.State.WaitingForPlayers)
            throw new IllegalStateException("Only valid to call this method in started state");

        List<PlayerInfo> playerInfos = mapPlayersToPlayerInfos(gameState.players);
        if (player.isCreator) {
            return new WaitForPlayersState(playerInfos);
        } else {
            return new WaitForGameStartState(playerInfos);
        }
    }

    private String getDrawingSrc(String imageFilename) {
        return "/api/image/" + gameId + "/" + imageFilename;
    }

    private Player getPreviousPlayerForStory(int storyIndex) {
        return getPlayerForStoryInRound(storyIndex, gameState.round - 1);
    }

    private Player getPlayerForStoryInRound(int storyIndex, int roundNo) {
        int previousPlayerIndexForStory = ArrayUtils.indexOf(gameState.gameMatrix[roundNo], storyIndex);
        return gameState.players.get(previousPlayerIndexForStory);
    }

    private Story getStoryByIndex(int storyIndex) {
        return gameState.stories[storyIndex];
    }

    private List<Player> getNotFinishedPlayers() {
        return gameState.players.stream().filter(p -> !hasPlayerFinishedCurrentRound(p)).collect(Collectors.toList());
    }

    private boolean hasPlayerFinishedCurrentRound(Player player) {
        return getCurrentStoryForPlayer(player).elements[gameState.round] != null;
    }

    private static List<PlayerInfo> mapPlayersToPlayerInfos(Collection<Player> players) {
        return players.stream().map(Game::mapPlayerToPlayerInfo).collect(Collectors.toList());
    }

    private static PlayerInfo mapPlayerToPlayerInfo(Player p) {
        return new PlayerInfo(p.name, p.avatar, p.isCreator);
    }

    public synchronized void clientDisconnected(Client client) {
        Player player = clientToPlayer.remove(client);
        if (player == null) {
            return;
        }

        player.removeClient(client);

        if (gameState.state == GameState.State.WaitingForPlayers) {
            if (!player.isCreator && player.clients.isEmpty()) {
                log.info("Game {}: Player {} has left the game", gameId, player.id);
                gameState.players.remove(player);
                updateStateForAllPlayers();
            }

            // TODO if the creator leaves (clients.isEmpty()) we should probably drop the game (and inform all other players)
        } else {
            // TODO
        }
    }

    public synchronized void start(Client client) {
        Player player = clientToPlayer.get(client);
        if (player == null) {
            log.warn("Game {}: Cannot start game. Client {} is not a known player", gameId, client.getId());
            return;
        }
        if (gameState.state != GameState.State.WaitingForPlayers) {
            log.warn("Game {}: Ignoring start in state {}", gameId, gameState.state);
            return;
        }
        if (!player.isCreator) {
            log.warn("Game {}: Non-creator {} cannot start the game (client: {})", gameId, player.id, client.getId());
            return;
        }

        if (gameState.players.size() > 1) {
            startGame();
        } else {
            log.warn("Game {}: Cannot start game with less than 2 players", gameId);
            updateStateForAllPlayers();
        }
    }

    private void startGame() {
        log.info("Game {}: Starting", gameId);
        gameState.state = GameState.State.Started;

        gameState.gameMatrix = GameRoundsGenerator.generate(gameState.players.size());

        gameState.stories = new Story[gameState.players.size()];
        Arrays.setAll(gameState.stories, i -> new Story(gameState.players.size()));

        updateStateForAllPlayers();
    }

    public synchronized void type(Client client, TypeAction typeAction) {
        Player player = clientToPlayer.get(client);
        if (player == null) {
            log.warn("Game {}: Cannot type. Client {} is not a known player", gameId, client.getId());
            return;
        }
        if (gameState.state != GameState.State.Started) {
            log.warn("Game {}: Ignoring type in state {}", gameId, gameState.state);
            return;
        }
        if (!isTypeRound()) {
            log.warn("Game {}: Ignoring type in draw round {}", gameId, gameState.round);
            return;
        }
        if (Strings.isEmpty(typeAction.text)) {
            throw new IllegalArgumentException("Empty text");
        }

        Story story = getCurrentStoryForPlayer(player);
        story.elements[gameState.round] = StoryElement.createTextElement(typeAction.text);

        checkAndHandleRoundFinished();

        updateStateForAllPlayers();
    }

    private Story getCurrentStoryForPlayer(Player player) {
        return getStoryByIndex(getCurrentStoryIndexForPlayer(player));
    }

    private int getCurrentStoryIndexForPlayer(Player player) {
        return gameState.gameMatrix[gameState.round][gameState.players.indexOf(player)];
    }

    private boolean isCurrentRoundFinished() {
        return Arrays.stream(gameState.stories).allMatch(s -> s.elements[gameState.round] != null);
    }

    private boolean isTypeRound() {
        return isTypeRound(gameState.round);
    }

    private boolean isDrawRound() {
        return !isTypeRound();
    }

    private static boolean isTypeRound(int roundNo) {
        return roundNo % 2 == 0;
    }

    public synchronized void draw(Client client, ByteBuffer image) throws IOException {
        Player player = clientToPlayer.get(client);
        if (player == null) {
            log.warn("Game {}: Cannot draw. Client {} is not a known player", gameId, client.getId());
            return;
        }
        if (gameState.state != GameState.State.Started) {
            log.warn("Game {}: Ignoring draw in state {}", gameId, gameState.state);
            return;
        }
        if (!isDrawRound()) {
            log.warn("Game {}: Ignoring draw in type round {}", gameId, gameState.round);
            return;
        }

        Story story = getCurrentStoryForPlayer(player);

        // TODO check that the player did not already send an image? (make consistent with type(.))

        String imageName = UUID.randomUUID().toString() + ".png";
        Path imagePath = gameDir.resolve(imageName);

        try (ByteChannel channel =
                     Files.newByteChannel(imagePath, EnumSet.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))) {
            channel.write(image);
        }

        story.elements[gameState.round] = StoryElement.createImageElement(imageName);

        checkAndHandleRoundFinished();

        updateStateForAllPlayers();
    }

    private void checkAndHandleRoundFinished() {
        if (isCurrentRoundFinished()) {
            gameState.round++;

            if (isGameFinished()) {
                gameState.state = GameState.State.Finished;
            }
        }
    }

    private boolean isGameFinished() {
        return gameState.round >= gameState.gameMatrix.length;
    }
}
