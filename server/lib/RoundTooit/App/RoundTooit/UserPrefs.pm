package RoundTooit::App::RoundTooit::UserPrefs;

use strict;
use warnings;
use base 'Yote::YapiServer::BaseObj';

our %cols = (
    %Yote::YapiServer::BaseObj::cols,
    owner                 => '*Yote::YapiServer::User',
    default_reminder_mode => "VARCHAR(20) DEFAULT 'voice'",
    reminder_1h_enabled   => 'TINYINT DEFAULT 1',
    reminder_5m_enabled   => 'TINYINT DEFAULT 1',
);

our %FIELD_ACCESS = (
    owner                 => { owner_only => 1 },
    default_reminder_mode => { owner_only => 1 },
    reminder_1h_enabled   => { owner_only => 1 },
    reminder_5m_enabled   => { owner_only => 1 },
);

1;
