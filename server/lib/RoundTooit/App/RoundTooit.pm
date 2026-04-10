package RoundTooit::App::RoundTooit;

use strict;
use warnings;
use base 'Yote::YapiServer::App::Base';

use RoundTooit::App::RoundTooit::Task;
use RoundTooit::App::RoundTooit::Note;
use RoundTooit::App::RoundTooit::UserPrefs;

our %cols = (
    %Yote::YapiServer::App::Base::cols,
    task_queues  => '*HASH<64>_*ARRAY_*RoundTooit::App::RoundTooit::Task',
    note_lists   => '*HASH<64>_*ARRAY_*RoundTooit::App::RoundTooit::Note',
    user_prefs   => '*HASH<64>_*RoundTooit::App::RoundTooit::UserPrefs',
    done_emails  => '*HASH<64>_*HASH<256>_VARCHAR(1)',
    data_version => 'INTEGER DEFAULT 0',
);

our %METHODS = (
    addTask      => { auth => 1 },
    getTopTasks  => { auth => 1 },
    completeTask => { auth => 1 },
    delayTask    => { auth => 1 },
    getAllTasks   => { auth => 1 },
    addNote      => { auth => 1 },
    searchNotes  => { auth => 1 },
    listNotes    => { auth => 1 },
    editNote     => { auth => 1 },
    deleteNote   => { auth => 1 },
    getPrefs      => { auth => 1 },
    updatePrefs   => { auth => 1 },
    markEmailDone => { auth => 1 },
    getDoneEmails => { auth => 1 },
    sync          => { auth => 1 },

    # Admin methods
    adminGetStats       => { admin_only => 1 },
    adminListUsers      => { admin_only => 1 },
    adminGetUserData    => { admin_only => 1 },
    adminClearDoneEmails => { admin_only => 1 },
    adminDeleteUser     => { admin_only => 1 },
    adminSetUserLocked  => { admin_only => 1 },
);

our %FIELD_ACCESS = (
    %Yote::YapiServer::App::Base::FIELD_ACCESS,
);

our %PUBLIC_VARS = (
    appName    => 'Round Tooit',
    appVersion => '1.0.0',
);

# ---------------------------------------------------------------
# Helper: get or create the user's task queue
# ---------------------------------------------------------------
sub _user_tasks {
    my ($self, $user) = @_;
    my $uid = $user->id;
    my $queues = $self->get_task_queues;
    my $queue = $queues->{$uid};
    unless ($queue) {
        $queue = $self->store->new_array('*ARRAY_*RoundTooit::App::RoundTooit::Task');
        $queues->{$uid} = $queue;
    }
    return $queue;
}

# ---------------------------------------------------------------
# Helper: get or create the user's note list
# ---------------------------------------------------------------
sub _user_notes {
    my ($self, $user) = @_;
    my $uid = $user->id;
    my $lists = $self->get_note_lists;
    my $list = $lists->{$uid};
    unless ($list) {
        $list = $self->store->new_array('*ARRAY_*RoundTooit::App::RoundTooit::Note');
        $lists->{$uid} = $list;
    }
    return $list;
}

# ---------------------------------------------------------------
# Helper: get or create the user's preferences
# ---------------------------------------------------------------
sub _user_prefs {
    my ($self, $user) = @_;
    my $uid = $user->id;
    my $prefs_hash = $self->get_user_prefs;
    my $prefs = $prefs_hash->{$uid};
    unless ($prefs) {
        $prefs = $self->store->new_obj(
            'RoundTooit::App::RoundTooit::UserPrefs',
            owner => $user,
        );
        $prefs_hash->{$uid} = $prefs;
    }
    return $prefs;
}

# ---------------------------------------------------------------
# Helper: get or create the user's done emails hash
# ---------------------------------------------------------------
sub _user_done_emails {
    my ($self, $user) = @_;
    my $uid = $user->id;
    my $all_done = $self->get_done_emails;
    my $done = $all_done->{$uid};
    unless ($done) {
        $done = $self->store->new_hash('*HASH<256>_VARCHAR(1)');
        $all_done->{$uid} = $done;
    }
    return $done;
}

# ---------------------------------------------------------------
# Helper: bump data version
# ---------------------------------------------------------------
sub _bump_version {
    my ($self) = @_;
    $self->set_data_version(($self->get_data_version // 0) + 1);
}

# ---------------------------------------------------------------
# Task methods
# ---------------------------------------------------------------

sub addTask {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;

    my $title = $args->{title};
    return 0, "title is required" unless defined $title && length($title);
    return 0, "title exceeds 500 characters" if length($title) > 500;

    my $description = $args->{description} // '';

    my $task = $self->store->new_obj(
        'RoundTooit::App::RoundTooit::Task',
        owner       => $user,
        title       => $title,
        description => $description,
    );

    my $queue = $self->_user_tasks($user);
    push @$queue, $task;

    $self->_bump_version;
    return 1, $task;
}

sub getTopTasks {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $count = $args->{count} // 3;

    my $queue = $self->_user_tasks($user);
    my $total = scalar(@$queue);
    my $end = $count - 1;
    $end = $#$queue if $end > $#$queue;

    my @top = $total > 0 ? @$queue[0..$end] : ();

    return 1, {
        tasks => \@top,
        total => $total,
    };
}

sub completeTask {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $task = $args->{task};

    return 0, "task is required" unless $task;

    my $queue = $self->_user_tasks($user);
    my $found = 0;

    for my $i (0..$#$queue) {
        if ($queue->[$i]->id eq $task->id) {
            splice(@$queue, $i, 1);
            $found = 1;
            last;
        }
    }

    return 0, "task not found in queue" unless $found;

    $self->_bump_version;
    return 1, { completed => 1 };
}

sub delayTask {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $task = $args->{task};

    return 0, "task is required" unless $task;

    my $queue = $self->_user_tasks($user);
    my $found = 0;

    for my $i (0..$#$queue) {
        if ($queue->[$i]->id eq $task->id) {
            my ($removed) = splice(@$queue, $i, 1);
            push @$queue, $removed;
            $found = 1;
            last;
        }
    }

    return 0, "task not found in queue" unless $found;

    $self->_bump_version;
    return 1, { delayed => 1 };
}

sub getAllTasks {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $limit = $args->{limit} // 50;
    my $offset = $args->{offset} // 0;

    my $queue = $self->_user_tasks($user);
    my $total = scalar(@$queue);

    my $end = $offset + $limit - 1;
    $end = $#$queue if $end > $#$queue;

    my @slice = ($offset <= $#$queue) ? @$queue[$offset..$end] : ();

    return 1, {
        tasks => \@slice,
        total => $total,
    };
}

# ---------------------------------------------------------------
# Note methods
# ---------------------------------------------------------------

sub addNote {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;

    my $text = $args->{text};
    return 0, "text is required" unless defined $text && length($text);

    my $note = $self->store->new_obj(
        'RoundTooit::App::RoundTooit::Note',
        owner => $user,
        text  => $text,
    );

    my $list = $self->_user_notes($user);
    push @$list, $note;

    $self->_bump_version;
    return 1, $note;
}

sub searchNotes {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;

    my $query = $args->{query};
    return 0, "query is required" unless defined $query && length($query);

    my $limit = $args->{limit} // 20;
    my $offset = $args->{offset} // 0;

    my $list = $self->_user_notes($user);
    my $lc_query = lc($query);

    my @matches;
    for my $note (reverse @$list) {
        if (index(lc($note->get_text), $lc_query) >= 0) {
            push @matches, $note;
        }
    }

    my $total = scalar(@matches);
    my $end = $offset + $limit - 1;
    $end = $#matches if $end > $#matches;

    my @slice = ($offset <= $#matches) ? @matches[$offset..$end] : ();

    return 1, {
        notes => \@slice,
        total => $total,
    };
}

sub listNotes {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $limit = $args->{limit} // 20;
    my $offset = $args->{offset} // 0;

    my $list = $self->_user_notes($user);
    my $total = scalar(@$list);

    # Newest first
    my @reversed = reverse @$list;
    my $end = $offset + $limit - 1;
    $end = $#reversed if $end > $#reversed;

    my @slice = ($offset <= $#reversed) ? @reversed[$offset..$end] : ();

    return 1, {
        notes => \@slice,
        total => $total,
    };
}

sub editNote {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $note = $args->{note};
    my $text = $args->{text};

    return 0, "note is required" unless $note;
    return 0, "text is required" unless defined $text && length($text);

    # Verify the note belongs to this user
    my $list = $self->_user_notes($user);
    my $found = 0;
    for my $n (@$list) {
        if ($n->id eq $note->id) {
            $found = 1;
            last;
        }
    }
    return 0, "note not found" unless $found;

    $note->set_text($text);

    $self->_bump_version;
    return 1, $note;
}

sub deleteNote {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $note = $args->{note};

    return 0, "note is required" unless $note;

    my $list = $self->_user_notes($user);
    my $found = 0;

    for my $i (0..$#$list) {
        if ($list->[$i]->id eq $note->id) {
            splice(@$list, $i, 1);
            $found = 1;
            last;
        }
    }

    return 0, "note not found" unless $found;

    $self->_bump_version;
    return 1, { deleted => 1 };
}

# ---------------------------------------------------------------
# Preferences methods
# ---------------------------------------------------------------

sub getPrefs {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $prefs = $self->_user_prefs($user);
    return 1, $prefs;
}

sub updatePrefs {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $prefs = $self->_user_prefs($user);

    my %allowed = map { $_ => 1 } qw(
        default_reminder_mode
        reminder_1h_enabled
        reminder_5m_enabled
    );

    for my $key (keys %$args) {
        next unless $allowed{$key};
        my $setter = "set_$key";
        $prefs->$setter($args->{$key});
    }

    $self->_bump_version;
    return 1, $prefs;
}

# ---------------------------------------------------------------
# Email methods
# ---------------------------------------------------------------

sub markEmailDone {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $gmail_id = $args->{gmail_id};

    return 0, "gmail_id is required" unless defined $gmail_id && length($gmail_id);

    my $done = $self->_user_done_emails($user);
    $done->{$gmail_id} = '1';

    $self->_bump_version;
    return 1, { done => 1 };
}

sub getDoneEmails {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;
    my $done = $self->_user_done_emails($user);

    my @ids = keys %$done;
    return 1, { gmail_ids => \@ids };
}

# ---------------------------------------------------------------
# Sync method
# ---------------------------------------------------------------

sub sync {
    my ($self, $args, $session) = @_;
    my $user = $session->get_user;

    my $client_version = $args->{data_version} // 0;
    my $current_version = $self->get_data_version // 0;

    my $queue = $self->_user_tasks($user);
    my $notes = $self->_user_notes($user);
    my $prefs = $self->_user_prefs($user);
    my $done  = $self->_user_done_emails($user);

    return 1, {
        tasks        => [ @$queue ],
        notes        => [ reverse @$notes ],
        done_emails  => [ keys %$done ],
        prefs        => $prefs,
        data_version => $current_version,
    };
}

# ---------------------------------------------------------------
# Admin methods
# ---------------------------------------------------------------

sub adminGetStats {
    my ($self, $args, $session) = @_;

    my $task_queues = $self->get_task_queues // {};
    my $note_lists  = $self->get_note_lists // {};
    my $done_emails = $self->get_done_emails // {};

    my $total_tasks = 0;
    my $total_notes = 0;
    my $total_done_emails = 0;

    my $all_users = $self->store->fetch_root->get_users // {};
    my $user_count = scalar(keys %$all_users);

    for my $uid (keys %$task_queues) {
        my $q = $task_queues->{$uid};
        $total_tasks += scalar(@$q) if $q;
    }
    for my $uid (keys %$note_lists) {
        my $n = $note_lists->{$uid};
        $total_notes += scalar(@$n) if $n;
    }
    for my $uid (keys %$done_emails) {
        my $d = $done_emails->{$uid};
        $total_done_emails += scalar(keys %$d) if $d;
    }

    return 1, {
        total_tasks       => $total_tasks,
        total_notes       => $total_notes,
        total_done_emails => $total_done_emails,
        data_version      => $self->get_data_version // 0,
        user_count        => $user_count,
    };
}

sub adminListUsers {
    my ($self, $args, $session) = @_;

    my $all_users = $self->store->fetch_root->get_users // {};
    my $task_queues = $self->get_task_queues // {};
    my $note_lists  = $self->get_note_lists // {};
    my $done_emails = $self->get_done_emails // {};

    my @users;
    for my $handle (sort keys %$all_users) {
        my $user = $all_users->{$handle};
        next unless $user;
        my $uid = $user->id;
        my $tasks = $task_queues->{$uid};
        my $notes = $note_lists->{$uid};
        my $done  = $done_emails->{$uid};
        push @users, {
            id          => $uid,
            handle      => $user->get_handle,
            is_admin    => $user->get_is_admin ? 1 : 0,
            locked      => $user->get_status  ? 1 : 0,
            task_count  => $tasks ? scalar(@$tasks) : 0,
            note_count  => $notes ? scalar(@$notes) : 0,
            done_emails => $done ? scalar(keys %$done) : 0,
            last_login  => $user->get_last_login // '',
            created     => $user->get_created // '',
        };
    }

    return 1, { users => \@users };
}

sub adminSetUserLocked {
    my ($self, $args, $session) = @_;
    my $uid    = $args->{user_id};
    my $locked = $args->{locked} ? 1 : 0;
    return 0, "user_id is required" unless $uid;

    my $user = $self->store->fetch($uid);
    return 0, "user not found" unless $user;

    # Don't let an admin lock their own account.
    my $viewer = $session && $session->get_user;
    if ($viewer && $viewer->id eq $user->id && $locked) {
        return 0, "cannot lock your own account";
    }

    $user->set_status($locked);
    $self->store->save;

    return 1, { user_id => $uid, locked => $locked };
}

sub adminGetUserData {
    my ($self, $args, $session) = @_;
    my $uid = $args->{user_id};
    return 0, "user_id is required" unless $uid;

    my $store = $self->store;
    my $user = $store->fetch($uid);
    return 0, "user not found" unless $user;

    my $tasks = $self->get_task_queues->{$uid} // [];
    my $notes = $self->get_note_lists->{$uid} // [];
    my $done  = $self->get_done_emails->{$uid} // {};
    my $prefs_hash = $self->get_user_prefs;
    my $prefs = $prefs_hash->{$uid};

    return 1, {
        handle      => $user->get_handle,
        tasks       => [ @$tasks ],
        notes       => [ reverse @$notes ],
        done_emails => [ keys %$done ],
        prefs       => $prefs,
    };
}

sub adminClearDoneEmails {
    my ($self, $args, $session) = @_;
    my $uid = $args->{user_id};

    if ($uid) {
        my $done = $self->get_done_emails;
        if ($done->{$uid}) {
            %{$done->{$uid}} = ();
        }
    } else {
        my $done = $self->get_done_emails;
        for my $uid (keys %$done) {
            %{$done->{$uid}} = ();
        }
    }

    $self->_bump_version;
    return 1, { cleared => 1 };
}

sub adminDeleteUser {
    my ($self, $args, $session) = @_;
    my $uid = $args->{user_id};
    return 0, "user_id is required" unless $uid;

    delete $self->get_task_queues->{$uid};
    delete $self->get_note_lists->{$uid};
    delete $self->get_done_emails->{$uid};
    delete $self->get_user_prefs->{$uid};

    $self->_bump_version;
    return 1, { deleted => 1 };
}

1;
