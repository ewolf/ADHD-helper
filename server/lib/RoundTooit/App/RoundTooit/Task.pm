package RoundTooit::App::RoundTooit::Task;

use strict;
use warnings;
use base 'Yote::YapiServer::BaseObj';

our %cols = (
    %Yote::YapiServer::BaseObj::cols,
    owner       => '*Yote::YapiServer::User',
    title       => 'VARCHAR(500)',
    description => 'TEXT',
    created     => 'TIMESTAMP DEFAULT CURRENT_TIMESTAMP',
    updated     => 'TIMESTAMP',
);

our %FIELD_ACCESS = (
    owner       => { public => 1 },
    title       => { public => 1 },
    description => { public => 1 },
    created     => { public => 1 },
    updated     => { public => 1 },
);

1;
