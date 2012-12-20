package Acl;
require IrcCmds;
@ISA = qw(IrcCmds);
require Database;

#startup
my $acl = {};
rebuild_acl();

#commands
sub commands { return (AclAddCmd, AclDelCmd, AclListCmd); }

{
package AclAddCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(addacl acladd); }
	sub help { return '<username> <acl>: grant permission acl to username; current acl\'s are acladd, acldel, insadd, and insdel; requires acladd perms.'; }
	sub execute {
		my ($irc, $line, $adder, $auth) = @_;
		my ($a, $target) = split(/ /,$line);

		return 'Please be identified to use this command.' if !$auth;
		return "You don't have permission to add ACL's, $adder" if !can_add_acl($adder) || has_acl($target,$a);
		my $st = $dbh->prepare("INSERT INTO acl (bot, user, permission, created) VALUES (?,?,?,?)");
		my $ex = $st->execute("thundercleese",$target,$a,time);
		if($ex) {
			$acl->{$target}->{$a} = 1;
			return "$target given acl permission $a successfully, $adder.";
		}
		return 'lols u sux';
	}
}

{
package AclDelCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(delacl acldel); }
	sub help { return '<username> <acl>: remove permission acl from username: requires acldel perms.'; }
	sub execute {
		my ($irc, $line, $adder, $auth) = @_;
		my ($a, $target) = split(/ /,$line);

		return 'Please be identified to use this command.' if !$auth;
		return "You don't have permission to del ACL's, $adder" if !can_add_acl($adder) || !has_acl($target,$a);
		my $st = $dbh->prepare("DELETE FROM acl WHERE user=? AND permission=?");
		my $ex = $st->execute($target,$a);;
		if($ex) {
			$acl->{$target}->{$a} = 0;
			return "removed $target acl permission $a successfully, $adder.";
		return 'lols u sux. ballin? no';
	}
}

{
package AclListCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(listacl acllist); }
	sub help { return '<username>: list permissions for username.'; }
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		my @words = split(/ /,$line);
		my $user = $words[0];

		rebuild_acl();
		my @aaa;
		my $aclstr;
		$aclstr = "";
		foreach my $aclkey (keys %{$acl->{$user}}) {
			push @aaa, $aclkey if (defined $acl->{$user}->{$aclkey} && $acl->{$user}->{$aclkey} == 1);
		}
		if($#aaa+1 == 0) {
			$aclstr = "None";
		} else {
			$aclstr = join ", ", @aaa;
		}
		return "$user has the following ACL's: $aclstr";
	}
}

#Convenience methods

sub has_acl {
	my ($target, $a) = @_;
	return (defined $acl->{$target}->{$a});
}

sub can_add_acl {
	my $user = shift;
	return (defined $acl->{$user}->{"acladd"} && $acl->{$user}->{"acladd"} == 1) ? 1 : 0;
}

sub can_del_acl {
	my $user = shift;
	return (defined $acl->{$user}->{"acldel"} && $acl->{$user}->{"acldel"} == 1) ? 1 : 0;
}

sub rebuild_acl {
	my $st = $dbh->prepare("SELECT * FROM acl WHERE bot='Thundercleese'");
	$st->execute();
	while(my $row = $st->fetchrow_hashref()) {
		my $user = $row->{"user"};
		my $permission = $row->{"permission"};
		if(!defined $acl->{$user}) {
			$acl->{$user} = {};
		}
		if(!defined $acl->{$user}->{$permission}) {
			$acl->{$user}->{$permission} = 1;
		}
	}
}

