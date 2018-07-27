# Live-Video-Stream
Self built live video streaming using akka-http

## Configuration File

Configuration file is ```application.conf```

### Main Configurations
You need to set at least these 3 variables in the _application.conf_ file:

```livevideostream.fps``` example : ```livevideostream.fps = 25 ```

_How many frames will be produced by the source and also the output video frame._

```livevideostream.port``` example: ```livevideostream.port = 1234```

_Which port will be used by this application._

```livevideostream.level``` example: ```livevideostream.level = 1```

_Number of different quality levels._ ** _Only 1 and 3 are available_**

### Additional Configurations

```livevideostream.numberOfCores``` example: ```livevideostream.numberOfCores = 2```

_Number of threads will be used by the program. Number of cores is automatically detected and the default is maximum._

```livevideostream.videoDirectory``` example: ```livevideostream.videoDirectory = "folder1/folder2" ```

_A folder named livevideostream will be created under the given directory and all files will be stored there. Default is current working directory._

## Prerequisities

You need to have the latest versions of sbt and ffmpeg downloaded and installed on your computer.

## How To Run

First go to project directory then: ```sbt run```

Alternatively you can pull the docker image from dockerhub
```docker pull hivecdn/testvideoserver:latest```
then run with port forwarding
```docker run -p 1234:1234 hivecdn/testvideoserver:latest```
finally open your favorite web browser and type
```http://localhost:1234```



## Main Flow Diagram
<img src="https://github.com/eminayar/Live-Video-Stream/blob/master/diagram.png">
