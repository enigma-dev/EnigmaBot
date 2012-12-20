#!/usr/bin/perl -TI .
package Enigma::Thundercleese
use strict;
use warnings;
use diagnostics;
use Data::Dumper;
use Time::Duration;
use Text::Truncate;
use WWW::Shorten::Bitly;
use Net::Twitter;
use IRC::Utils qw(:ALL);
use POE qw(Component::IRC::State);

our $NAME;

BEGIN {
	$NAME = 'thevalog';

	if(-f "/tmp/$NAME") {
		open my $pfile, "</tmp/$NAME";
		my $p = <$pfile>;
		print "$NAME is already running under pid: $p\n";
		exit;
		close $pfile;
	}
	open my $pfile, ">/tmp/$NAME";
	print $pfile $$;
	close $pfile;
}
END 
{
	unlink "/tmp/$NAME";
}

sub interrupt {
	$SIG{INT} = \&interrupt;
	unlink "/tmp/$NAME";
	exit;
}

$SIG{INT} = \&interrupt;


#Database Handling

use Enigma::Thundercleese::Database;
our $dbh = Enigma::Thundercleese::Database->new({ host => "localhost", password => "alpha345", username => "sirmxe", db => "irc"});
our $dbh;

#GIT Handling
my $bitly_user = "";
my $bitly_api = "";
my $twitter = {
		consumer_key => "",
		consumer_secret => "",
		access_token => "",
		access_token_secret => "",
};
my $bitly = WWW::Shorten::Bitly->new(USER => "enigmadevfeed", APIKEY => "R_d0837fef7b998cdf3368cf953fb00c32");
my $nt = Net::Twitter->new( #create new Net::Twitter instance
	traits => [qw/OAuth API::REST/], #use OAuth and REST API
	consumer_key => $twitter->{consumer_key},
	consumer_secret => $twitter->{consumer_secret},
	access_token => $twitter->{access_token},
	access_token_secret => $twitter->{access_token_secret}
);

my @message_queue;
sub message_queue {
	my $heap = $_[HEAP];
	my $kernel = $_[KERNEL];
	my $irc = $heap->{irc};
	irc_say($irc,pop @message_queue) if scalar(@message_queue) > 0;
	$kernel->delay("message_queue",1);
}

sub commit_check  {
	my $heap = $_[HEAP];
	my $st = $dbh->prepare("SELECT * from commits WHERE sent=0 ORDER BY id ASC LIMIT 1");
	$st->execute();
	while(my $row = $st->fetchrow_hashref()) {
		$bitly->shorten(URL => $row->{'url'});
		my $url = $bitly->{bitlyurl};
		my $name = $row->{'name'};
		my $email = $row->{'email'};
		my $msg = $row->{'message'};
		my @m = split(/\n/,$msg);
		$msg = $m[0];
		my $ts = $row->{'ts'};
		my $id = $row->{'id'};
		my $commit_id = substr($row->{'commit_id'},0,6);
		my $repo = $row->{'repo'};
		$dbh->do("UPDATE commits SET sent=1 WHERE id=$id");
		my $update = truncstr("$name committed to $repo: \"".truncstr($msg,60)."\"",90)." $url <\@$ts>";
		eval { 
			$nt->update({status => $update});
		};
		$msg = BROWN."New commit ".NORMAL.RED."$commit_id".NORMAL.BROWN." from ".NORMAL.GREEN."$name".NORMAL.BROWN." in ".NORMAL.RED."$repo".NORMAL.BROWN.": \"".ORANGE.truncstr($msg,100).NORMAL.BROWN."\" <@".NORMAL.BLUE."$ts".NORMAL.BROWN.">";
		irc_say($heap->{irc},$msg);

	}
	my $kernel = $_[KERNEL];
	$kernel->delay("commit_check",3);
}

#IRC Handling
my $ircname = "muff cabbage";
my $server = "irc.freenode.org";
my $irc = POE::Component::IRC::State->spawn(
	nick => $NAME,
	ircname => $ircname,
	server => $server
) or die "oh shit $!";
POE::Session->create(
	inline_states => {
		insult => \&irc_insult,
		mysql_fix => \&mysql_fix,
		commit_check => \&commit_check,
		message_queue => \&message_queue,
	},
	package_states => [
		main => [ qw(irc_353 irc_372 _default _start irc_001 irc_public irc_join irc_kick irc_part irc_quit irc_nick irc_ctcp_action) ],
	],
	heap => { irc => $irc },
);
$poe_kernel->run();

sub _start {
	my $heap = $_[HEAP];
	my $kernel = $_[KERNEL]; 
	my $irc = $heap->{irc};
	my $pass = "";
	$irc->yield(register => 'all');
	$irc->yield(connect => { } );
	$irc->yield(nickserv => "ghost $NAME $pass");
	$irc->yield(nickserv => "identify $pass");
	return;
}

sub irc_001 { #welcome to irc
	my $irc = $_[HEAP]->{irc};
	$irc->yield( join => "#enigma");
	return;
}

#Custom irc subs
sub irc_insult {
	my $kernel = $_[KERNEL];
	my $heap = $_[HEAP];
	my $irc = $heap->{irc};
	$irc->yield(names => "#enigma");
	return;
}

sub sys_log {
	my $wut = $_;
	print $wut, "\n";
	my $time = time;
	my $st = $dbh->prepare("INSERT INTO logs (who, timestamp, msg) VALUES (?, ?, ?)");
	$st->execute("System",$time,$wut);
}

sub irc_say {
	my ($irc,$text) = @_;
	my $time = time;
	$irc->yield(privmsg => "#enigma" => $text);
	my $st = $dbh->prepare("INSERT INTO logs (who, timestamp, msg) VALUES (?, ?, ?)");
	$st->execute($NAME,$time,$text);
}

sub random_witty_response {
	my @r;
	push @r,"Does my skin look black to you?";
	push @r,"Yeah. I'll get right on that.";
	push @r,"Might try asking someone else.";
	push @r,"Sorry, I'm on break.";
	push @r,"I don't follow the laws of robotics. Just saying.";
	push @r,"wut";
	return $r[int rand $#r];
}

my @mods = qw(Acl Game Insult Misc);

sub find_mod_command {
	my $cmd = $_;

	foreach my $mod (@mods) {
		eval "use $mod";
		my @modcmds = $mod->commands();
		foreach my $modcmd (@modcmds) {
			#for now, we do it the list way
			#later, we can switch to hashmap
			my @trigs = $modcmd->triggers();
			if (grep {$_ eq $cmd} @trigs) {
				return $modcmd;
			}
		}
	}
	return;
}

sub list_mod_command {
	my @modcmds;
	foreach my $mod (@mods) {
		eval "use Enigma::Thundercleese::$mod";
		my @modcmds = $mod->commands();
		foreach my $modcmd (@modcmds) {
			my @trigs = $modcmd->triggers();
			push @modcmds, $trigs[0];
		}
	}
	return @modcmds;
}

#Back to normal irc subs
sub irc_public { #message sent to channel
	my ($sender, $who, $where, $what, $auth) = @_[SENDER, ARG0 .. ARG2, ARG3];
	my $nick = ( split /\!/, $who)[0];
	my $channel = $where->[0];
	my $time = time;

	#Log
	print "$nick: $what ($channel)\n";
	my $st = $dbh->prepare("INSERT INTO logs (who, timestamp, msg) VALUES (?, ?, ?)");
	$st->execute($nick,$time,$what);

	#Commands
	if($what =~ /^\@(.+)/) {
		my @line = split(/ /,$1);
		my $cmd = lc shift @line;
		my $msg;

		if ($cmd eq "help") {
			if ($#line == 0 or $line[0] eq '') {
				$msg = join(" ",list_mod_command());
			} else {
				my $modcmd = find_mod_command($line[0]);
				$msg = $modcmd->help() if defined($modcmd) and $modcmd ne '';
			}
		} else {
			my $modcmd = find_mod_command($cmd);
			$modcmd->execute($irc,join(" ", @line), $nick,$auth);
		}

 		$msg = random_witty_response() if !defined($msg) or $msg eq '';
		irc_say($irc,$msg);
	}
}

sub irc_ctcp_action {
	my ($sender, $who, $where, $msg) = @_[SENDER, ARG0 .. ARG2];
	my $nick = ( split /\!/, $who)[0];
	sys_log("$nick $msg");
}

sub irc_nick {
	my ($sender, $who, $new) = @_[SENDER, ARG0 .. ARG1];
	my $nick = ( split /\!/, $who)[0];
	sys_log("$nick changed nick to $new");
}

sub irc_quit {
	my ($sender, $who, $msg) = @_[SENDER, ARG0 .. ARG1];
	my $nick = ( split /\!/, $who)[0];
	sys_log("$nick quit: $msg");
}

sub irc_part {
	my ($sender, $who, $channel, $msg) = @_[SENDER, ARG0 .. ARG2];
	my $nick = ( split /\!/, $who)[0];
	sys_log("$nick parted $channel: $msg");
	if($nick eq $NAME) { #rejoin
		my $irc = $_[HEAP]->{irc};
		$irc->yield( join => "#enigma");
	}
}

sub irc_kick {
	my ($sender, $kicker, $where, $who, $msg) = @_[SENDER, ARG0 .. ARG3];
	my $nick = ( split /\!/, $who)[0];
	my $kick = ( split /\!/, $kicker)[0];
	my $channel = $where;
	sys_log("$kick kicked $nick from $channel: $msg");
	if($nick eq $NAME) { #rejoin
		my $irc = $_[HEAP]->{irc};
		$irc->yield( join => "#enigma");
	}
}

sub irc_join {
	#Log
	my ($sender, $who, $where) = @_[SENDER, ARG0 .. ARG1];
	my $heap = $_[HEAP];
	my $irc = $heap->{irc};
	my $nick = ( split /\!/, $who)[0];
	my $channel = $where;
	syslog("$nick joined $channel");

	#Kernel stuff?
	my $kernel = $_[KERNEL];
	$kernel->delay("mysql_fix",60*5);
	$kernel->delay("commit_check",1);
	$kernel->delay("message_queue",1);

	return;
}

sub irc_353 { #sent userlist
	my ($event, $args) = @_[ARG0 .. $#_];
	my @n = split(/:/,$args);
	my @names = split(/ /,$n[1]);
	my $a = $NAME;
	$a = $names[int rand($#names+1)] while $a eq $NAME;
	$a =~ s/[^a-z0-9_\[\]]//gi;
	insult($_[HEAP],$_[KERNEL],$a);
	return;
}

sub irc_372 {} #ignore MOTD

sub _default { #Unhandled IRC messages
	my ($event, $args) = @_[ARG0 .. $#_];
	my @output = ( "$event: " );

	for my $arg (@$args) {
		if ( ref $arg eq 'ARRAY' ) {
			push( @output, '[' . join(', ', @$arg ) . ']' );
		} else {
			push ( @output, "'$arg'" );
		}
	}
	print join ' ', @output, "\n";
	return;
}

