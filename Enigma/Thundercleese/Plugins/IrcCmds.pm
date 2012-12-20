package IrcCmds;

use strict;
use warnings;
use Exporter 'import';
our $VERSION = '1.00';
our @EXPORT  = qw(commands);

sub commands {}

{
package IrcCommand;
 our @EXPORT = qw(trigger help execute);
 sub triggers {}
 sub help {}
 sub execute {
  my ($irc, $line, $nick) = @_;
 }
}

