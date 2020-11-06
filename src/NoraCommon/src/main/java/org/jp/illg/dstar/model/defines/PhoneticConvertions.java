package org.jp.illg.dstar.model.defines;

public enum PhoneticConvertions {

	Unknown		((char)0x0),
	Alpha			('A'),
	Bravo			('B'),
	Charlie			('C'),
	Delta			('D'),
	Echo				('E'),
	Foxtrot			('F'),
	Golf				('G'),
	Hotel			('H'),
	India			('I'),
	Juliet			('J'),
	Kilo				('K'),
	Lima				('L'),
	Mike				('M'),
	November	('N'),
	Oscar			('O'),
	Papa			('P'),
	Quebec			('Q'),
	Romeo			('R'),
	Sierra			('S'),
	Tango			('T'),
	Uniform		('U'),
	Victor			('V'),
	Whiskey		('W'),
	Xray				('X'),
	Yankee			('Y'),
	Zulu				('Z'),
	;


	private final char alphabet;

	private PhoneticConvertions(final char alphabet) {
		this.alphabet = alphabet;
	}

	public char getAlphabet() {
		return this.alphabet;
	}

	public String getPhoneticCode() {
		return this.name();
	}

	public static PhoneticConvertions getTypeByPhoneticCode(String phoneticCode) {
		if(phoneticCode != null) {
			for(PhoneticConvertions v : values()) {
				if(v.getPhoneticCode().equals(phoneticCode)) {return v;}
			}
		}
		return PhoneticConvertions.Unknown;
	}

	public static PhoneticConvertions getTypeByAlphabet(char alphabet) {
		for(PhoneticConvertions v : values()) {
			if(v.getAlphabet() == alphabet) {return v;}
		}
		return PhoneticConvertions.Unknown;
	}

}
