package net.jqwik.properties;

import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import java.util.stream.*;

import org.assertj.core.api.*;
import org.junit.platform.engine.reporting.*;
import org.mockito.*;

import net.jqwik.api.*;
import net.jqwik.descriptor.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Group
class GenericPropertyTests {

	private static final Consumer<ReportEntry> NULL_PUBLISHER = entry -> {
	};

	@SuppressWarnings("unchecked")
	@Example
	void collectStatistics() {
		ForAllSpy forAllFunction = new ForAllSpy(value -> {
			Statistics.collect(value);
			return true;
		}, value -> true);

		Arbitrary<Integer> arbitrary = Arbitraries.samples(1);
		ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

		GenericProperty property = new GenericProperty("simple", shrinkablesGenerator, forAllFunction);
		PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
		Consumer<ReportEntry> mockPublisher = mock(Consumer.class);

		PropertyCheckResult result = property.check(configuration, mockPublisher);

		ArgumentCaptor<ReportEntry> reportEntryCaptor = ArgumentCaptor.forClass(ReportEntry.class);
		verify(mockPublisher, atLeast(2)).accept(reportEntryCaptor.capture());

		Set<String> keys = reportEntryCaptor.getAllValues().stream() //
											.flatMap(entry -> entry.getKeyValuePairs().keySet().stream()) //
											.collect(Collectors.toSet());

		Assertions.assertThat(keys).contains("collected statistics");

		// Remove statistics from this test from ThreadLocal<Collector>:
		StatisticsCollector.clearAll();
	}

	@SuppressWarnings("ConstantConditions")
	@Group
	class OneParameter {

		private final Function<List<Object>, Boolean> exactlyOneInteger = args -> args.size() == 1 && args.get(0) instanceof Integer;

		@Example
		void satisfied() {
			ForAllSpy forAllFunction = new ForAllSpy(trie -> true, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(2, arbitrary);

			GenericProperty property = new GenericProperty("satisfied property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 2, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(2);

			assertThat(result.propertyName()).isEqualTo("satisfied property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.SATISFIED);
			assertThat(result.countTries()).isEqualTo(2);
			assertThat(result.countChecks()).isEqualTo(2);
			assertThat(result.randomSeed()).isEqualTo("42");
			assertThat(result.throwable()).isNotPresent();
			assertThat(result.sample()).isNotPresent();
		}

		@Example
		void falsified() {
			int failingTry = 5;

			ForAllSpy forAllFunction = new ForAllSpy(trie -> trie != failingTry, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5, 6, 7, 8, 9);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

			GenericProperty property = new GenericProperty("falsified property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "41", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(6);

			assertThat(result.propertyName()).isEqualTo("falsified property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.FALSIFIED);
			assertThat(result.countTries()).isEqualTo(failingTry);
			assertThat(result.countChecks()).isEqualTo(failingTry);
			assertThat(result.randomSeed()).isEqualTo("41");
			assertThat(result.throwable()).isNotPresent();

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).containsExactly(failingTry);
		}

		@Example
		void dontShrinkIfShrinkingModeIsOff() {
			int failingTry = 5;

			ForAllSpy forAllFunction = new ForAllSpy(trie -> trie < failingTry, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5, 6, 7, 8);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

			GenericProperty property = new GenericProperty("falsified property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "41", 10, 5, ShrinkingMode.OFF, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(failingTry); // If shrunk number would be higher
			assertThat(result.sample().get()).containsExactly(failingTry);
		}

		@Example
		void falsifiedThroughAssertionError() {
			AssertionError assertionError = new AssertionError("test");
			ForAllSpy forAllFunction = new ForAllSpy(trie -> {
				throw assertionError;
			}, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

			GenericProperty property = new GenericProperty("falsified property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "41", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(1);

			assertThat(result.propertyName()).isEqualTo("falsified property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.FALSIFIED);
			assertThat(result.countTries()).isEqualTo(1);
			assertThat(result.countChecks()).isEqualTo(1);
			assertThat(result.randomSeed()).isEqualTo("41");

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).containsExactly(1);

			assertThat(result.throwable()).isPresent();
			assertThat(result.throwable().get()).isSameAs(assertionError);
		}

		@Example
		void satisfiedWithRejectedAssumptions() {
			IntPredicate isEven = aNumber -> aNumber % 2 == 0;

			ForAllSpy forAllFunction = new ForAllSpy(aTry -> {
				Assumptions.assumeThat(isEven.test(aTry));
				assertThat(isEven.test(aTry)).isTrue();
				return true;
			}, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

			GenericProperty property = new GenericProperty("satisfied property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(10);

			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.SATISFIED);
			assertThat(result.countTries()).isEqualTo(10);
			assertThat(result.countChecks()).isEqualTo(5);
			assertThat(result.randomSeed()).isEqualTo("42");
			assertThat(result.throwable()).isNotPresent();
			assertThat(result.sample()).isNotPresent();
		}

		@Example
		void exhaustedWithAllTriesDiscarded() {
			ForAllSpy forAllFunction = new ForAllSpy(aTry -> {
				Assumptions.assumeThat(false);
				return true;
			}, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

			GenericProperty property = new GenericProperty("exhausted property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(10);

			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.EXHAUSTED);
			assertThat(result.countTries()).isEqualTo(10);
			assertThat(result.countChecks()).isEqualTo(0);
			assertThat(result.randomSeed()).isEqualTo("42");
			assertThat(result.throwable()).isNotPresent();
			assertThat(result.sample()).isNotPresent();
		}

		@Example
		void exhaustedWithMaxDiscardRatioExceeded() {
			int maxDiscardRatio = 2; // Max 2 discards per 1 non-discard
			final AtomicInteger counter = new AtomicInteger(0);
			ForAllSpy forAllFunction = new ForAllSpy(aTry -> {
				if (counter.incrementAndGet() % 4 != 0) // 3 of 4 are discarded
					Assumptions.assumeThat(false);
				return true;
			}, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(20, arbitrary);

			GenericProperty property = new GenericProperty("exhausted property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 20, maxDiscardRatio, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.EXHAUSTED);
			assertThat(result.countTries()).isEqualTo(20);
			assertThat(result.countChecks()).isEqualTo(5);
		}

		@Example
		void exceptionInForAllFunctionMakesPropertyErroneous() {
			int erroneousTry = 5;
			RuntimeException thrownException = new RuntimeException("thrown in test");

			ForAllSpy forAllFunction = new ForAllSpy(aTry -> {
				if (aTry == erroneousTry) throw thrownException;
				return true;
			}, exactlyOneInteger);

			Arbitrary<Integer> arbitrary = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(20, arbitrary);

			GenericProperty property = new GenericProperty("erroneous property", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(forAllFunction.countCalls()).isEqualTo(erroneousTry);

			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.ERRONEOUS);
			assertThat(result.countTries()).isEqualTo(erroneousTry);
			assertThat(result.countChecks()).isEqualTo(erroneousTry);
			assertThat(result.randomSeed()).isEqualTo("42");

			assertThat(result.throwable()).isPresent();
			assertThat(result.throwable().get()).isSameAs(thrownException);

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).containsExactly(erroneousTry);
		}

		@Example
		void falsifiedAndShrunk() {
			Arbitrary<Integer> arbitrary = Arbitraries.integers().between(1, 100);
			CheckedFunction checkedFunction = params -> ((int) params.get(0)) < 5;
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary);

			GenericProperty property = new GenericProperty("falsified property", shrinkablesGenerator, checkedFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "41", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.propertyName()).isEqualTo("falsified property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.FALSIFIED);

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).containsExactly(5);
		}

	}

	@SuppressWarnings("ConstantConditions")
	@Group
	class NoParameter {
		@Example
		void checkPropertyWithoutForAllParametersAreAlsoTriedSeveralTimes() {
			CheckedFunction forAllFunction = args -> {
				assertThat(args).isEmpty();
				return true;
			};

			GenericProperty property = new GenericProperty("satisfied property", createShrinkablesGenerator(2), forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 2, 5, ShrinkingMode.FULL, new Reporting[0]);

			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.propertyName()).isEqualTo("satisfied property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.SATISFIED);
			assertThat(result.countTries()).isEqualTo(2);
			assertThat(result.countChecks()).isEqualTo(2);
			assertThat(result.randomSeed()).isEqualTo("42");
			assertThat(result.throwable()).isNotPresent();
			assertThat(result.sample()).isNotPresent();
		}

		@Example
		void evenIfItFails() {
			CheckedFunction forAllFunction = args -> {
				assertThat(args).isEmpty();
				return false;
			};

			GenericProperty property = new GenericProperty("failing property", createShrinkablesGenerator(2), forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 2, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.propertyName()).isEqualTo("failing property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.FALSIFIED);
			assertThat(result.countTries()).isEqualTo(1);
			assertThat(result.countChecks()).isEqualTo(1);
			assertThat(result.randomSeed()).isEqualTo("42");
			assertThat(result.throwable()).isNotPresent();

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).isEmpty();
		}

		@Example
		void evenIfItThrowsException() {
			CheckedFunction forAllFunction = args -> {
				assertThat(args).isEmpty();
				throw new RuntimeException();
			};

			GenericProperty property = new GenericProperty("failing property", createShrinkablesGenerator(2), forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "42", 2, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.propertyName()).isEqualTo("failing property");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.ERRONEOUS);
			assertThat(result.countTries()).isEqualTo(1);
			assertThat(result.countChecks()).isEqualTo(1);
			assertThat(result.randomSeed()).isEqualTo("42");

			assertThat(result.throwable()).isPresent();
			assertThat(result.throwable().get()).isInstanceOf(RuntimeException.class);

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).isEmpty();
		}

	}

	@Group
	class ManyParameters {

		@Example
		void twoParametersSatisfied() {
			CheckedFunction forAllFunction = args -> {
				assertThat(args).size().isEqualTo(2);
				assertThat(args.get(0)).isInstanceOf(Integer.class);
				assertThat(args.get(1)).isInstanceOf(Integer.class);
				return true;
			};

			Arbitrary<Integer> arbitrary1 = Arbitraries.samples(1, 2, 3, 4, 5);
			Arbitrary<Integer> arbitrary2 = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(5, arbitrary1, arbitrary2);

			GenericProperty property = new GenericProperty("property with 2", shrinkablesGenerator, forAllFunction);
			PropertyConfiguration configuration = new PropertyConfiguration("Property", "4242", 5, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.propertyName()).isEqualTo("property with 2");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.SATISFIED);
			assertThat(result.countTries()).isEqualTo(5);
			assertThat(result.countChecks()).isEqualTo(5);
			assertThat(result.randomSeed()).isEqualTo("4242");
			assertThat(result.throwable()).isNotPresent();
			assertThat(result.sample()).isNotPresent();
		}

		@Example
		void fourParametersFalsified() {
			int failingTry = 5;

			CheckedFunction forAllFunction = args -> {
				assertThat(args).size().isEqualTo(4);
				return ((int) args.get(0)) < failingTry;
			};

			Arbitrary<Integer> arbitrary1 = Arbitraries.samples(1, 2, 3, 4, 5);
			Arbitrary<Integer> arbitrary2 = Arbitraries.samples(1, 2, 3, 4, 5);
			Arbitrary<Integer> arbitrary3 = Arbitraries.samples(1, 2, 3, 4, 5);
			Arbitrary<Integer> arbitrary4 = Arbitraries.samples(1, 2, 3, 4, 5);
			ShrinkablesGenerator shrinkablesGenerator = createShrinkablesGenerator(10, arbitrary1, arbitrary2, arbitrary3, arbitrary4);

			GenericProperty property = new GenericProperty("property with 4", shrinkablesGenerator, forAllFunction);

			PropertyConfiguration configuration = new PropertyConfiguration("Property", "4141", 10, 5, ShrinkingMode.FULL, new Reporting[0]);
			PropertyCheckResult result = property.check(configuration, NULL_PUBLISHER);

			assertThat(result.propertyName()).isEqualTo("property with 4");
			assertThat(result.status()).isEqualTo(PropertyCheckResult.Status.FALSIFIED);
			assertThat(result.countTries()).isEqualTo(failingTry);
			assertThat(result.countChecks()).isEqualTo(failingTry);
			assertThat(result.randomSeed()).isEqualTo("4141");
			assertThat(result.throwable()).isNotPresent();

			assertThat(result.sample()).isPresent();
			assertThat(result.sample().get()).containsExactly(failingTry, 1, 1, 1);
		}

	}

	private ShrinkablesGenerator createShrinkablesGenerator(int genSize, Arbitrary... arbitraries) {
		List<Arbitrary> arbitraryList = Arrays.stream(arbitraries).collect(Collectors.toList());
		List<RandomGenerator> generators = arbitraryList
			.stream()
			.map(arbitrary -> arbitrary.generator(genSize))
			.collect(Collectors.toList());
		return random -> generators
			.stream()
			.map(generator -> generator.next(random))
			.collect(Collectors.toList());
	}

}
