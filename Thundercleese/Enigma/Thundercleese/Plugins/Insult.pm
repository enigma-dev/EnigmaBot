package Insult;
require IrcCmds;
@ISA = qw(IrcCmds);
require Acl;

#startup
my @insults;
rebuild_insults();

#commands
sub commands { return (InsultDo, InsultAdd, InsultDel, InsultWho); }

{
package InsultDo;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(insult); }
	sub help { return '<name>: insult user <name>'; }
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		if($line =~ /^\s*$/i or $line eq "random" or lc($line) =~ /< *random *>/i) {
			$irc->yield(names => "#enigma");
			return;
		}
		my $insult = $insults[int rand scalar(@insults)+1];
		$insult =~ s/\%s/$line/ig;
		return $insult;
	}
}

{
package InsultAdd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(insadd addins); }
	sub help { return '<insult>: add insult to database; use %s to represent username, i.e. \'you suck %s\'; requires insadd perms'; }
	sub execute {
		my ($irc, $insult, $creator, $auth) = @_;

		return 'Please be sure to include %s to represent the username.' if $insult !~ /\%s/;
		return "You don't have permission to add insults, $creator." if !can_add_insult($creator);
		my $st = $dbh->prepare("INSERT INTO insults (insult, timestamp, who) VALUES (?,?,?)");
		my $ex = $st->execute($insult,time,$creator);
		my $msg;
		if($ex == 1) {
			push @insults, $insult;
			return "Insult added successfully, $creator";
		}
		return "Man, you suck at this, $creator.";
	}
}

{
package InsultDel;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(insdel delins); }
	sub help { return '<insult>: delete insult from database. requires insdel perms.'; }
	sub execute {
		my ($irc, $regex, $sender, $auth) = @_;

		return 'Please be identified to use this command.' if !$auth;
		return 'You don\'t have permission to delete insults.' if !can_del_insult($sender);
		rebuild_insults();
		my $r = qr/$regex/;
		my @to_del = grep { /$r/ } @insults;
		return 'Couldn\'t find any matching insults.' if scalar(@to_del) == 0;
		my $st = $dbh->prepare("DELETE FROM insults WHERE insult=?");
		my $msg = "";
		my $tot = 0;
		foreach(@to_del) {
			print $tot;
			my $ex = $st->execute($_);
			if($ex == 1) {
				$msg .= "Insult: $_ (deleted) ";
				$tot++;
			} else {
				$msg .= "Insult: $_ (not deleted) ";
			}
		}
		$msg .= " [$tot total deleted]";
		rebuild_insults();
		return $msg;
	}
}

{
package InsultWho;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(whodunnit inswho insblame whoins blameins); }
	sub help { return '<regex>: list who created an insult; spits out all insults that match regex'; }
	sub execute {
		my ($irc, $regex, $nick, $auth) = @_;

		rebuild_insults();
		irc_say($irc,"Searching... Please hold...");
		my $r = qr/$regex/;
		my @to_check = grep { /$r/ } @insults;
		return denied($irc,"No insults found") if scalar(@to_check) == 0;
		my $st = $dbh->prepare("SELECT who FROM insults WHERE insult=?");
		my $fname = "${nick}_".substr("".time,0,8).".html";
		my @temp_queue;
		open my $file, ">/var/www/html/enigma-dev.org/whodunnit/$fname";
		print $file "<html>\n\t<head>\n\t\t<title>Query: $regex</title>\n\t</head>\n\t<body>\n\t\t<div class='query'>Your query: $regex</div>\n\t\t<br />\n\t\t<hr />";
			foreach(@to_check) {
				my $ex = $st->execute($_);
				my $href = $st->fetchrow_hashref();
				my $who = $href->{"who"};
				if($#to_check > 5) {
					if($ex) {
						print $file "\n\t\t<div class='insult'>";
						print $file "\n\t\t\t<div style='display: inline;' class='insultText'>$_</div>";
						print $file "\n\t\t\t<div class='whodunnit'>$who</div>";
						print $file "\n\t\t</div>";
					}
				} else {
					push @temp_queue, "$_: $who";
				}
			}
		if($#to_check > 5) {
			print $file "\n\t</body>\n</html>";
			irc_say($irc,"Search results can be found at: http://enigma-dev.org/whodunnit/$fname");
		} else {
			push @message_queue, reverse @temp_queue;
		}
		close $file;
		unlink "/var/www/html/enigma-dev.org/whodunnit/$fname" if $#to_check <= 5;
	}
}

#Convenience methods

sub can_add_insult {
	my $user = shift;
	return (defined $acl->{$user}->{"insadd"} && $acl->{$user}->{"insadd"} == 1) ? 1 : 0;
}

sub can_del_insult {
	my $user = shift;
	return (defined $acl->{$user}->{"insdel"} && $acl->{$user}->{"insdel"} == 1) ? 1 : 0;
}

sub rebuild_insults {
	undef(@insults);
	my $st = $dbh->prepare("SELECT insult FROM insults");
	$st->execute();
	while(my $row = $st->fetchrow_hashref()) {
		push @insults, $row->{"insult"};
	}
}

