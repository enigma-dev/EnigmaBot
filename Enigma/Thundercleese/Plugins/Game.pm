package Game;
require IrcCmds;
@ISA = qw(IrcCmds);

sub commands { return (RollCmd, ChooseCmd, EightBallCmd, YoutubeCmd); }

{
package RollCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(roll); }
	sub help { return '<x>d<y>: rolls x y-sided die (0 < x < 100, 1 < x < 100)'; }
	#hacked together from original code. Could probably use refactoring.
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		my @words = split(/ /,$1);
		if($words[0] !~ /\d{1,3}d\d{1,3}/) {
			return 'Your format string was incorrect, try XdY next time.';
		}
		my($x, $y) = split(/d/,$words[0]);
		return doRoll($x,$y,$nick);
	}
	sub doRoll {
		my ($x, $y, $nick) = @_;
		if($x < 1) { $x = 2; }
		if($x > 100) { $x = 100; }
		if($y < 2) { $y = 2; }
		if($y > 100) { $y = 100; }
		my @results;
		push @results, (int rand($y)+1) foreach 1..$x;
		return "$nick: ".join(", ",@results);
	}
}

{
package ChooseCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(choose pick which); }
	sub help { return '<opt1 opt2... optN>: pick a random item from a list.'; }
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		my @words = split(/ /,$line);
		return "$nick: ".$words[int rand($#words+1)];
	}
}

{
package EightBallCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(8ball); }
	sub help { return '<question>: return a magic 8ball response to a question.'; }
	my @ebanswers = ("It is certain", "It is decidedly so", "Without a doubt", "Yes - definitely", "You may rely on it", "As I see it, yes", "Mosty likely", "Outlook good", "Signs point to yes", "Yes", "Reply hazy, try again", "Ask again later", "Better not tell you now", "Cannot predict now", "Concentrate and ask again", "Don't count on it", "My reply is no", "My sources say no", "Outlook not so good", "Very doubtful");
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		return "$nick: ".($ebanswers[int rand($#ebanswers)+1]) .".";
	}
}

{
package YoutubeCmd;
	@ISA = qw(IrcCommand);
	sub triggers { return qw(yt); }
	sub help { return '<query>: search youtube for query, return first matching video as well as details.'; }
	sub execute {
		my ($irc, $line, $nick, $auth) = @_;
		use WebService::GData::YouTube;
		use IRC::Utils qw(:ALL);
		my $yt = new WebService::GData::YouTube();
		my $ytsearch = $yt->query;
		$ytsearch->q($line)->limit(2,0);
		my $videos = $yt->search_video();
		my $video = shift @{$videos};
		if(defined $video) {
			return "$nick: ".BOLD.$video->title.NORMAL." by ".$video->uploader." (".duration($video->duration).") - http://youtu.be/".$video->video_id." (+".$video->rating->{numLikes}.", -".$video->rating->{numDislikes}.") ".$video->view_count." views.";
		}
		return "No videos found.";
	}
}


