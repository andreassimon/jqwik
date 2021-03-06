package net.jqwik.properties.shrinking;

import net.jqwik.api.*;

import java.util.*;
import java.util.stream.*;

public class ShrinkableSet<E> extends ShrinkableContainer<Set<E>, E> {

	private final int minSize;

	public ShrinkableSet(Set<Shrinkable<E>> elements, int minSize) {
		this(new ArrayList<>(elements), minSize);
	}

	private ShrinkableSet(List<Shrinkable<E>> elements, int minSize) {
		super(elements, minSize);
		this.minSize = minSize;
	}

	@Override
	public ShrinkingSequence<Set<E>> shrink(Falsifier<Set<E>> falsifier) {
		return super.shrink(falsifier.withFilter(set -> set.size() >= minSize));
	}

	@Override
	Collector<E, ?, Set<E>> containerCollector() {
		return Collectors.toSet();
	}

	@Override
	Shrinkable<Set<E>> createShrinkable(List<Shrinkable<E>> shrunkElements) {
		return new ShrinkableSet<>(shrunkElements, minSize);
	}
}
