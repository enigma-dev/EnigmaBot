package Database;
use Moose;

has username => {
	isa => "String",
	is => "rw"
};
has host => {
	isa => "String",
	is => "rw",
};
has password => {
	isa => "String",
	is => "rw",
};
has db => { 
	isa => "String",
	is => "rw",
};
has dsn => {
	isa => "String",
	is => "ro",
	default => sub { my $self = shift;
		"DBI:mysql:database=".$self->db.";host=".$self->host.";port=3306";
	}
};
has dbi => {
	isa => DBI,
	is => "rw",
	default => sub {
		my $self = shift;
		return DBI->connect($self->dsn,$self->username,$self->password);
	}
};

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
