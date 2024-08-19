MediaToad
=========

(formally DLNAtoad but it now also does other stuff, and renaming the repo might
break things)

What is this?
-------------

A minimal media server in a single jar. On start up it will index the working
directory and make any media found available via a web interface (defaults to
port 8192) and via DLNA.  Additional features like user management and tagging
are optional and enabled via CLI flags.

How do I use it?
----------------

It compiles to a runnable jar. Run it in the top directory you want to share.
Your DLNA device should magically detect it on your LAN. DLNA is magical like
that. Media files are identified purely on file extension. Known extensions are
in the `src/main/java/com/vaguehope/dlnatoad/media/MediaFormat.java` file.

How can I compile it?
---------------------

It's a Java Maven project.

```shell
$ mvn clean install
$ java -jar target/dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar
```

Any configuration?
------------------

You can include multiple specific root directories instead of defaulting to the
current directory:

```shell
$ java -jar dlnatoad.jar foo/ bar/
```

Instead of listing paths on the command line a text file of directories can be
given using the `--tree` pointing to a file with one path per line.

Ideally it should not have write access to any of the media directories, it will
never be needed as data is only ever stored in the DB.

### Thumbnails

If you want the web interface to show thumbnails the `--thumbs` flag must point
to a directory where they can be written to.  Thumbnails will be generated as
part of scanning for files on start up and whenever new files are found.
Currently thumbnails are only generated for images files.

### DB and Tagging

If you want metadata to be collected and used, use `--db` to specify where this
data should be stored.  It will be created if it does not exist.  This sqlite DB
will also be used for storing any tags added to files.

All files will be hashed so that metadata can be reassociated when a file is
moved or renamed.  Metadata will be preserved so long as file content and name
do not change at the same time - leave sufficient time for each change to be
fully observed.

### Reading Media Info

If a DB is provided to store it, media info like duration etc will be read using
`ffprobe`.  If ffprobe can not be found on `PATH` this will be skipped.

### Users and Auth

WARNING: this auth system is BEST EFFORT, do not trust it to protect anything
important.  Auth changes are only loaded on startup and are not dynamically
updated.  The process must be restarted after any auth changes.

Directories can be restricted to specific users and users must have explicit
permission to edit item tags.  Any directory protected with an AUTH file will
not be exported via DLNA, since that has no concept of permissions.

Uses are listed in a text file specified by the `--userfile` flag in the format
described below.  Additionally `--sessiondir` must point to an empty writable
directory where session tokens can be stored.  For obvious security reasons no
other processes on the system should be able to read or write to the directory.

The users file has the following format:

```
$username $bcrypt_hash $permissions
```

For example this specifies 2 users.  user1 has permission to edit tags in
directories where they have permission.  user2 has read-only access irrespective
of per-direction permissions.

```
user1 $2a$10$su3ctfwMCULBeBvk0WDffuyGV/rJki4IZrFHrqs8XjtCXykMs5wii +edittags +editdirprefs
user2 $2a$10$wV3v3YKOS4Iy8IFoA.ucnugDRrDMWdx2yv3Jx5rc0JCgtEWHmQMNi
```

Currently there is no sign-up page in the UI, but users can be added on the
command line with the following command.  Alternatively just ask them to
generate and send a bcrypt hash :3.

```
$ java -jar target/dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar --userfile $path_to_file --adduser
```

Directories are protected by placing AUTH files in them listing one username per
line.  Each name may be followed by permissions.  In the following example,
user1 has permission to view and to add and remove tags.  user2 has read-only
access.  No other users have any access.

```
user1 +edittags +editdirprefs
user2
```

If a subdirectory has its own AUTH file a user must appear in all AUTH files in
parent directories to have access.  Permissions are taken from the first AUTH
file found going back up the directory tree.

Running as a Service
--------------------

See the `systemd` directory for an example unit file.

<!-- vim: textwidth=80 noautoindent nocindent
-->
