package Enigma::Thundercleese::Base::Plugin;

has actions => {
	isa => "ArrayRef[Enigma::Thundercleese::Base::Action]",
	is => "rw",
	default => sub { {}; }
};
