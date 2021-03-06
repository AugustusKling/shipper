shipper
=======

Monitors a file and forwards additions to log4j via a SocketAppender.

The program is intended as a simplistic JVM based log shipper. The scenario served is that a log file shall be monitored and all changes shall be sent to a central log server.

It is expected that the central log server in use is able process the lines it receives. shipper simply sends each line that is appended to the monitored file as a log4j message (INFO level). Aggregating multiple lines to a single log event and adjusting the severity is left to the central log server.

Usage
-----

Invocations look as follows where you are expected to replace all `…` with proper values. Parameters with default values can be omitted.
```
java -jar shipper.jar --file … --host … --port 4560 --skip true --file-encoding UTF-8 --logging-configuration ""
```

Parameters are as follows:

`--file` Path to local file that shall be monitored. In case the file does not exist, the given location will be monitored and processing will start as soon as the file is created. In case the file gets deleted while processing, processing continues after another file is created at the given path. This parameter can be given multiple times to list several files for monitoring.

`--host` Name of central log server.

`--port` Port where central log server makes a log4j input available.

`--skip` When `true` the existing file contents are never sent. Newly added lines are sent, though. When `false` all lines will be sent which includes lines that have already been sent with an ealier program start.

`--file-encoding` Encoding of input file. Defaults to UTF-8 which should be fine for most Linux systems. Needs to be set explicitly for Windows systems because their default encoding depends on the region they were sold.

`--logging-configuration` Path to detailed logging configuration. The empty default leads to using the bundled `logging.properties`.
