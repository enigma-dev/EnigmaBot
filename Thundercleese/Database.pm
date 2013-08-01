package Database;
use DBI;
my $username = "";
my $pass = "";
my $dsn = "DBI:mysql:database=irc;host=localhost;port=3306";
our $dbh = DBI->connect($dsn,$username,$pass);

sub mysql_fix {
	my $kernel = $_[KERNEL];
	my $dbh = DBI->connect($dsn,$username,$pass);
	$kernel->delay("mysql_fix",60*30);
	return;
}
