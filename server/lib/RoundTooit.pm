package RoundTooit;

use strict;
use warnings;
use base 'Yote::YapiServer::Site';

use RoundTooit::App::RoundTooit;

our %cols = (
    %Yote::YapiServer::Site::cols,
);

our %INSTALLED_APPS = (
    %Yote::YapiServer::Site::INSTALLED_APPS,
    roundtooit => 'RoundTooit::App::RoundTooit',
);

sub installed_apps {
    return \%INSTALLED_APPS;
}

1;
