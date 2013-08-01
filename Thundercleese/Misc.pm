package Logs;
require IrcCmds;
@ISA = qw(IrcCmds);

#startup

#commands
sub commands { return (LogsCmd, SuggestCmd, EvalCmd); }

{
package LogsCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(logs log); }
	sub help { return ': returns the url to the channel logs'; }
	sub execute { return 'http://enigma-dev.org/irclogs'; }
}

{
package SuggestCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(suggest); }
	sub help { return '<suggestion>: add feature suggestion for this bot'; }
	sub execute {
		my ($irc, $suggestion, $nick) = @_;
		use Database;
		my $st = $dbh->prepare("INSERT INTO suggestions (suggestion,who,timestamp) VALUES (?,?,?)");
		my $ex = $st->execute($suggestion,$nick,time);
		if($ex == 1) {
			return "Suggestion submitted successfully.";
		}
		return "Suggestion not submitted. You suck.";
	}
}

{
package EvalCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(perl eval); }
	sub help { return '<code>: evaluates a given piece of perl code'; }
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		my $h = $nick->get_heap();

		return if !$auth or !can_eval($nick) or !$h->is_channel_operator($channel,$nick)

		my $out = eval($line);
		if (!$out || !defined $out) {
			$out = $@;
		}
		if($out =~ /-T/) {
			$out = "Stop trying to be clever.";
		}
		print "error msg if applicable: $@\n";
		$out =~ s/\r?\n/ /g;
		return $out;
	}

	sub can_eval {
		my $user = shift;
		use Acl;
		return (defined $acl->{$user}->{"eval"} && $acl->{$user}->{"eval"} == 1) ? 1 : 0;
	}
}


