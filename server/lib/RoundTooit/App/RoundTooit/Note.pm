package RoundTooit::App::RoundTooit::Note;

use strict;
use warnings;
use base 'Yote::YapiServer::BaseObj';

our %cols = (
    %Yote::YapiServer::BaseObj::cols,
    owner   => '*Yote::YapiServer::User',
    text    => 'TEXT',
    created => 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP',
    updated => 'TIMESTAMP',
);

our %FIELD_ACCESS = (
    owner   => { public => 1 },
    text    => { public => 1 },
    created => { public => 1 },
    updated => { public => 1 },
);

1;
