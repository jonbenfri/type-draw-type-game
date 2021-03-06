# Type Draw Type Game

![Build & Publish Docker images](https://github.com/Bronkoknorb/type-draw-type-game/workflows/Build%20&%20Publish%20Docker%20images/badge.svg)

![Type Draw Type Logo](/tdt-webapp/src/img/logo.svg?raw=true&sanitize=true)

_Type Draw Type_ is a simple and fun game for you and your friends.
It is like the telephone game (Chinese whispers), only with pictures.
Every player starts by typing a sentence.
Then the texts are passed on and you have to draw the sentence you received.
In the next round you get one of the drawings and have to describe it with another sentence (without knowing the initial sentence!).
This goes on, by alternately typing and drawing, until all players have participated in every chain (story).
In the end everybody sees all the stories and can wonder about how the initial sentences have transformed in unexpected ways!

Play online at: https://draw.gerty.roga.czedik.at

This is an open-source Web/mobile version of the pen-and-paper game Telephone Pictionary (also known as Paper Telephone and "Eat Poop You Cat"). It can be installed on mobile phones (Android and iPhone) by using the "Add to Home Screen" functionality.

Alternative versions of the game are: [Drawception](https://drawception.com/), [Interference](https://www.playinterference.com/), [Doodle Or Die](http://doodleordie.com/), [Broken Picturephone](https://www.brokenpicturephone.com/), [Drawphone](https://github.com/tannerkrewson/drawphone), Telestrations, Cranium Scribblish, Stille Post extrem, Scrawl (offline Board games).

## Installation on own server

Prerequisites:

- Install [Docker](https://www.docker.com/)

Pull the newest image from [Docker Hub](https://hub.docker.com/r/bronkoknorb/type-draw-type-game) and tag it with a simpler name:

    docker pull bronkoknorb/type-draw-type-game
    docker tag bronkoknorb/type-draw-type-game tdt-game

To run the Docker image (forwarding the internal port 8080 to the external port 8081):

    docker run -p 8081:8080 --volume /home/pi/tdt-data:/tdt-data tdt-game

Note that /home/pi/tdt-data is a directory on the host where game data will be stored.

You can then open the game on http://localhost:8081/

To run the Docker image (in the background) and do automatic restarts (e.g. when the machine gets rebooted or the application crashed):

    docker run -d --restart always --name tdt -p 8081:8080 --volume /home/pi/tdt-data:/tdt-data tdt-game

To restart the Docker container after pulling (or building) a new version of the image:

    docker stop tdt && docker rm tdt && docker run -d --restart always --name tdt -p 8081:8080 --volume /home/pi/tdt-data:/tdt-data tdt-game

To check the logs of the running Docker container:

    docker logs tdt

## Development

### Backend Server

The Backend is developed using Spring Boot in Java and built with Gradle.

Prerequisites:

- Install Java (tested with version 11)

Run the server in development mode:

    cd tdt-server
    ./gradlew bootRun

To get live-reloads on changes, run in a second terminal:

    ./gradlew build --continuous

See also [Spring-Boot-HELP.md](tdt-server/Spring-Boot-HELP.md).

### Frontend Web App

The Frontend is a React App written in Typescript and built with yarn.

Prerequisites:

- Install [yarn](https://yarnpkg.com/) (tested with version 1.22.4)

Run the frontend in development mode:

    cd tdt-webapp
    yarn start

This will also automatically start a proxy server which forwards API requests to the backend (running on port 8080).

To interactively upgrade dependencies of the frontend:

    yarn upgrade-interactive --latest

See also [React-App-HELP.md](tdt-webapp/React-App-HELP.md).

## Build

To build the app using Docker:

    docker build --tag tdt-game .

### Multi-Architecture Build

I want to build ARM Docker images to run on my Raspberry Pi:

This repository contains a Github Action Workflow which builds the multi-arch images and pushes them to a Docker registry, see [docker-image.yml](.github/workflows/docker-image.yml).

Here are some notes how to achieve the same locally:

As of writing this, experimental features need to be enabled for Docker:

    export DOCKER_CLI_EXPERIMENTAL=enabled

Install qemu instructions to be able to build ARM executables:

    docker run --rm --privileged multiarch/qemu-user-static --reset -p yes

Create a multi-architecture build instance:

    docker buildx create --name mybuilder
    docker buildx use mybuilder
    docker buildx inspect --bootstrap

Build multi-architecture images:

    docker buildx build --platform linux/amd64,linux/arm/v7 .

Note: For now platform linux/arm64 is not included as it is very slow to build.
Might be useful to add it again for future Raspbian version which will use arm64.

Reference: https://www.docker.com/blog/getting-started-with-docker-for-arm-on-linux/

## Author

Copyright by Hermann Czedik-Eysenberg  
git-dev@hermann.czedik.net

License: [GNU AFFERO GENERAL PUBLIC LICENSE](LICENSE)
