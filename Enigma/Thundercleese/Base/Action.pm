package Enigma::Thundercleese::Base::Action;

has identifier => {
	isa => "String",
	is => "rw",
};

has coderef => {
	isa => "CodeRef",
	is => "rw"
};

