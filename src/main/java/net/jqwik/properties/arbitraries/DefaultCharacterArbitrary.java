package net.jqwik.properties.arbitraries;

import net.jqwik.api.*;
import net.jqwik.api.arbitraries.*;

public class DefaultCharacterArbitrary extends NullableArbitraryBase<Character> implements CharacterArbitrary {

	public static final int MAX_ASCII_CODEPOINT = 0x007F;

	private char min = 0;
	private char max = 0;

	public DefaultCharacterArbitrary() {
		super(Character.class);
	}

	@Override
	protected RandomGenerator<Character> baseGenerator(int tries) {
		return RandomGenerators.choose(min, max).withSamples(min, max);
	}

	@Override
	public CharacterArbitrary between(char min, char max) {
		DefaultCharacterArbitrary clone = typedClone();
		clone.min = min;
		clone.max = max;
		return clone;
	}

	@Override
	public CharacterArbitrary ascii() {
		return between((char) Character.MIN_CODE_POINT, (char) MAX_ASCII_CODEPOINT);
	}

	@Override
	public CharacterArbitrary all() {
		return between(Character.MIN_VALUE, Character.MAX_VALUE);
	}

	@Override
	public CharacterArbitrary digit() {
		return between('0', '9');
	}

}
