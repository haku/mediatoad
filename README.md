DLNAtoad
========

What is this?
-------------

A minimal DLNA media server in a single jar.  On start up it will index the
working directory and make any media found available via DLNA.

Why?
----

The few good free DLNA server implementations I found online were clunky with
awkward config.  I wanted something that was ultra simple without any extra
(unsecured) admin APIs I do not want but which will always be a (if minor)
security worry.

How do I use it?
----------------

It compiles to a runnable jar.  Run it in the top directory you want to share.
Your DLNA device should magically detect it on your LAN.  DLNA is magical like
that.  Media files are identified purely on file extension.  Known extensions
are in the `src/main/java/com/vaguehope/dlnatoad/media/MediaFormat.java` file.

Any configuration?
------------------

You can include multiple specific root directories instead of defaulting to the
current directory:

```shell
$ java -jar dlnatoad.jar foo/ bar/
```

### Other Args

```shell
 --db VAL             : Path for metadata DB.
 --thumbs VAL         : Path for caching image thumbnails.
 -a (--accesslog)     : print access log line at end of each request. (default:
                        false)
 -d (--daemon)        : detach form terminal and run in bakground. (default:
                        false)
 -i (--interface) VAL : Hostname or IP address of interface to bind to.
 -s (--simplify)      : simplify directory structure. (default: false)
 -t (--tree) <file>   : file root dirs to scan, one per line.
 -v (--verbose)       : print log lines for various events. (default: false)
```

Instead of specifying every path on the command line, a text file of
directories can be used using the `--tree` option.  This should be a text file
with one path per line.

If you want metadata to be remembered, you must use `--db` to specify where
this data should be stored.  It will be created if it does not exist.

If you want thumbnails to be generated for image files you must specify a
directory to store them in using `--thumbs`.  Thumbnails will not be generated
for videos or other non-image formats.  You must use another tool to generate
these.  Thumbnails will be looked for in the same directory as the media files
using a bunch of filename-matching rules defined in
`src/main/java/com/vaguehope/dlnatoad/media/CoverArtHelper.java`.

How can I compile it?
---------------------

Its a Maven project.

```shell
$ mvn clean install
$ java -jar target/dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar
```

How does it work?
-----------------

It advertises its self on the LAN and provides simple directory listing service
via UPnP (using the Cling library).  When the renderer wants to play a media
file it fetches it via HTTP from the embedded Jetty server.  The RANGE header is
used to fetch parts of files.

What does it use?
-----------------

### Cling

A very useful set of libraries for working with UPnP.

http://4thline.org/projects/cling/

### Jetty 8

Provides a HTTP server implementation that directly supports the RANGE header
for serving the actual content.

http://www.eclipse.org/jetty/

### 3rd party code in this repository

Some classes are based on code from WireMe and are used under the Apache 2
License.  WireMe was also used as a general example of how to expose content via
DLNA using the Cling library.  See https://code.google.com/p/wireme/ for more
details.

DB Migrations
-------------

Add AUTOINCREMENT to files table:

```sql
BEGIN TRANSACTION;
CREATE TABLE new_files (key INTEGER PRIMARY KEY AUTOINCREMENT, file STRING NOT NULL, size INT NOT NULL, modified INT NOT NULL, hash STRING NOT NULL, id STRING NOT NULL, UNIQUE(file));
INSERT INTO new_files (file, size, modified, hash, id) SELECT file, size, modified, hash, id FROM files;
DROP TABLE files;
ALTER TABLE new_files RENAME TO files;
COMMIT TRANSACTION;
```
