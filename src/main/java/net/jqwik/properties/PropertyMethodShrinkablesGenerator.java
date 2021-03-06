package net.jqwik.properties;

import net.jqwik.api.*;
import net.jqwik.api.providers.*;
import net.jqwik.support.*;

import java.util.*;
import java.util.stream.*;

public class PropertyMethodShrinkablesGenerator implements ShrinkablesGenerator {

	public static PropertyMethodShrinkablesGenerator forParameters(
		List<MethodParameter> parameters, ArbitraryResolver arbitraryResolver, int genSize
	) {
		List<RandomParameterGenerator> parameterGenerators = parameters.stream() //
																	   .map(parameter -> resolveParameter(arbitraryResolver, parameter, genSize))
																	   .collect(Collectors.toList());

		return new PropertyMethodShrinkablesGenerator(parameterGenerators);
	}

	private static RandomParameterGenerator resolveParameter(
		ArbitraryResolver arbitraryResolver, MethodParameter parameter, int genSize
	) {
		Set<RandomGenerator> generators = arbitraryResolver.forParameter(parameter).stream()
														   .map(GenericArbitrary::new)
														   .map(arbitrary -> arbitrary.generator(genSize))
														   .collect(Collectors.toSet());
		if (generators.isEmpty()) {
			throw new CannotFindArbitraryException(parameter);
		}
		return new RandomParameterGenerator(parameter, generators);
	}

	private final List<RandomParameterGenerator> parameterGenerators;

	private PropertyMethodShrinkablesGenerator(List<RandomParameterGenerator> parameterGenerators) {
		this.parameterGenerators = parameterGenerators;
	}

	@Override
	public List<Shrinkable> next(Random random) {
		Map<TypeUsage, RandomGenerator> generatorsCache = new HashMap<>();
		return parameterGenerators
				   .stream()
				   .map(generator -> generator.next(random, generatorsCache))
				   .collect(Collectors.toList());
	}


}
