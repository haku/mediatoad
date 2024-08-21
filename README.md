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
to a directory where they can be written to.  Thumbnails for images will be
generated as part of scanning for files on start up and whenever new files are
found.

Thumbnails for videos can be enabled per-directory, though this is very
experimental.  Users with +editdirprefs can find this option at the bottom of
each directory page.  This uses ffmpeg which much be available in PATH.

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

### Tag Determiners

Tag determiners (TD) are a mechanism to use external processes (such as ML
models) to automatically add tags to content.  A TD implements the simple RPC
service in `src/main/proto/tagdeterminer.proto`.  The TD will be send the file
content and mimetype, and returns a list of tags that will be added to the item.

Which files have already been send to the TD is tracked by added a hidden tag (a
tag with a class starting with a '.'), which the TD returns from `About` RPC in
the `tag_cls` field.

To connect to a TD use the `--tagdeterminer` flag, which specifies the URL of
the service and which content should be sent to it in the format `URL|query`:

```
--tagdeterminer 'https://my-service.example.com|f~/mnt/photos/ OR f~/mnt/pictures/'
```

The query syntax is the same as used in the web UI.  Multiple TDs can be
configured by repeating the flag.

Query Syntax
------------

There are 2 query engines depending on if a DB is used or not.  If there is no
DB, then it is a simple substring search of filenames.  If DB is enabled, there
is the syntax described below.

NOTE This syntax is somewhat work-in-progress and may change.  This
documentation may be incomplete, see `DbSearchParserTest` for tested examples.

| query                    | what it matches |
| ---                      | --- |
| `foo`                    | file path or tags containing `foo` |
| `f~foo`                  | file path containing `foo`|
| `f~^/foo`                | file path starting with `/foo`|
| `f~foo$`                 | file path ends with `foo`|
| `-f~foo`                 | file path does not contain `foo` |
| `t=foo`                  | exact tag `foo` |
| `-t=foo`                 | not exact tag `foo` |
| `t~foo`                  | tag containing `foo` |
| `t~^foo`                 | tag starts with `foo` |
| `t~foo$`                 | tag ends with `foo` |
| `-t~foo`                 | tag not containing `foo` |
| `t>3`                    | has 4 or more tags |
| `t<3`                    | has 2 or fewer tags |
| `w=1920 h>1080`          | width exactly 1920 pixels and height is more than 1080 pixels |
| `w>=2000 h<=1000`        | width is 2000 pixels or more, height is 1000 pixels or less |
| `type=image`             | mimetype starts with `image/` |
| `type=image/jpeg`        | mimetype is exactly `image/jpeg` |
| `t="foo bar"`            | exact tag `foo bar` |
| `t="foo\"bar"`           | exact tag `foo"bar` |
| `t=foo OR t~bar`         | exact tag `foo` or tag containing `bar` |
| `t=foo OR (f~bar t=baz)` | exact tag `foo` or both file path contains `bar` and exact tag `baz` |

* Terms separated by whitespace are implicitly ANDed together.
* Either `"` or `'` can be used.
* Quotes can be placed anywhere bash-style, eg `t=fo"o b"ar` is the same as
  `t="foo bar"`.
* Use backslash `\` to escape things.
* Open brackets `(` must be preceded by whitespace and close brackets `)` must
  be followed by whitespace,  eg `a(bcd)e` will be searched for as is.
* Queries are currently limited to 10 terms.


Running as a Service
--------------------

See the `systemd` directory for an example unit file.

Development Workflow
--------------------

### Web UI

To make working on HTML/JS/CSS files easier, there are flags to override where
static files and templates are read from so refreshing the page shows changes
immediately.

```
$ java -jar target/dlnatoad-1-SNAPSHOT-jar-with-dependencies.jar --accesslog --webroot src/main/resources/wui --templateroot src/main/resources/templates
```

<!-- vim: textwidth=80 noautoindent nocindent
-->
