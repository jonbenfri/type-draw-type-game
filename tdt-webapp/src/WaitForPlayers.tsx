import React from "react";
import Player from "./Player";
import Logo from "./Logo";
import { PlayerInfo } from "./model";

const WaitForPlayersScreen = ({
  gameId,
  players,
  handleStart,
}: {
  gameId: string;
  players: PlayerInfo[];
  handleStart: () => void;
}) => {
  const buttonDisabled = players.length <= 1;

  const link = window.location.toString();

  return (
    <BeforeGameStartScreen players={players}>
      <Logo />
      <div>Ask your friends to join the game:</div>
      <div>
        <div className="field-label">Game Code:</div>
        <div className="field">{gameId}</div>
      </div>
      <div>
        <div className="field-label">Link:</div>
        <div className="field">{link}</div>
      </div>
      <div className="buttons">
        <button
          className="button"
          disabled={buttonDisabled}
          title={
            buttonDisabled
              ? "Waiting for more players"
              : "Let's get this party started!"
          }
          onClick={handleStart}
        >
          Start Game
        </button>
      </div>
      <div className="small">
        Once the game is started, no more players can join!
      </div>
    </BeforeGameStartScreen>
  );
};

// TODO extract into own file
export const WaitForGameStartScreen = ({
  players,
}: {
  players: PlayerInfo[];
}) => {
  const creator = players.find((p) => p.isCreator)!;

  return (
    <BeforeGameStartScreen players={players}>
      <div>Waiting for {creator.name} to start game.</div>
    </BeforeGameStartScreen>
  );
};

// TODO rename CSS classes
const BeforeGameStartScreen = ({
  players,
  children,
}: {
  players: PlayerInfo[];
  children: React.ReactNode;
}) => {
  return (
    <div className="WaitForPlayers">
      <div className="WaitForPlayers-left">
        <div className="Players-title">Players:</div>
        <div className="Players">
          {players.map((player, index) => (
            <Player key={index} face={player.avatar}>
              {player.name}
            </Player>
          ))}
        </div>
      </div>
      <div className="WaitForPlayers-right">{children}</div>
    </div>
  );
};

export default WaitForPlayersScreen;