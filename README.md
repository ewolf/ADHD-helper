# Round Tooit

An Android app and YAPI server designed to help ADHD individuals maintain awareness of their daily life.

## Core Components

- **Android app** — Kotlin / Jetpack Compose, using OkHttp + kotlinx.serialization for YAPI communication
- **Web app** — Spiderpup (YAML-to-HTML/JS) frontend, served by the same YAPI server as madyote via symlinks
- **YAPI server** — Perl JSON-RPC backend hosted at `madyote.com`

## Authentication

Login is required to use the app. Currently single-user only.

- Username/password authentication
- Passwords stored as cryptographic hashes in the database
- After login, the YAPI session token is used for all subsequent API messages

## Features

### Google Cloud
- A Google Cloud project must be created with Calendar API and Gmail API enabled
- The app authenticates directly with Google using OAuth 2.0

### Calendar Integration
- Read and write to Google Calendar
- "Upcoming Events" section shows all events for today; if none, displays "no events today"
- Below today's events, a separator and the next 3 upcoming calendar events with relative labels:
  - Tomorrow: "Tomorrow"
  - Under a week: "In X days"
  - Longer: the date

### Email Integration
- Poll Gmail every 10 minutes
- Display all emails from people the user has previously sent an email to
- Emails can be marked "done" from the client; the server tracks done status per user
- Displayed emails are copied and labeled into an "ADHD" Gmail folder (created automatically if it doesn't exist)
- Tapping an email opens it in Gmail

### Task Management
- Tasks are maintained in a queue
- Display the top 3 tasks from the queue
- Each task has options to complete or delay
- Delay moves the task to the end of the queue
- Task title: max 500 characters
- Task description: no size limit
- No due dates — priority is determined by queue position only

### Voice & Reminders
- On-device speech recognition for voice control
- Reminders at 1 hour and 5 minutes before calendar events, scheduled locally via AlarmManager
- Default reminder: voice announcement of event title to all active audio channels + status bar notification
- Tapping an upcoming event opens its description, where reminders can be:
  - Disabled entirely
  - Customized to chime only, buzz only, or both (instead of voice)
- Honors DND and silent mode — no audio override

### Notes
- Free-form plain text notes, no length limit
- Stored as a sequential list
- Searchable

### Offline & Sync
- App works offline with locally cached data
- Syncs with server every 10 minutes

---

## App Installation

### Prerequisites
- Android device running Android 8.0 (API 26) or higher
- Microphone permission (for voice control)
- Internet access

### Install
1. Download the latest APK from the releases page
2. On your device, enable **Settings > Security > Install from unknown sources** (if not already enabled)
3. Open the APK and follow the install prompts
4. Launch **Round Tooit** and log in with your credentials
5. Grant permissions when prompted (microphone, notifications, calendar)

---

## Server Installation & Setup

### Prerequisites
- A server with a public-facing domain (configured for `madyote.com`)
- Perl (with YAPI framework and dependencies installed — see [yapi-server](https://github.com/your-repo/yapi-server))
- MariaDB **or** SQLite (configurable)
- Google OAuth is handled by the Android app directly (not the server)

### Install

```bash
# Clone the repository
git clone <repo-url>
cd ADHD_helper/server

# Install YAPI framework (if not already installed)
# See /home/coyo/yoteproj/yapi-server for the framework source

# Install Perl dependencies
cpanm --installdeps .
```

### Configure

```bash
# Copy the example config and edit it
cp config.example.yaml config.yaml
chmod 600 config.yaml  # read-only to the yapi process user
```

Edit `config.yaml` and set:
- `host` — bind address (default `0.0.0.0`)
- `port` — listen port (default `5001`)
- `domain` — `madyote.com`
- `db.type` — `SQLite` or `MariaDB` (default `SQLite`)
- `db.data_dir` — data directory (when using `SQLite`)
- `db.host` — MariaDB host (when using `MariaDB`)
- `db.port` — MariaDB port (default `3306`)
- `db.name` — MariaDB database name
- `db.user` — MariaDB user
- `db.password` — MariaDB password

**Security:** The config file contains database credentials and must be readable only by the user running the YAPI process.

### Database

Tables are driven by the object model — YAPI's ORM auto-generates database tables from the app/object definitions. No manual schema migration is needed.

### Run

```bash
# Start the YAPI server
perl yapi.pl config.yaml

# Or with a process manager for production
# Example with systemd — see roundtooit.service
```

The server will be available at the `/yapi` endpoint on `madyote.com`.

### Verify

```bash
curl -X POST https://madyote.com/yapi -d '{"action":"connect","app":"RoundTooit"}'
# Expected: JSON response with app method stubs
```
