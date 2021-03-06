package examples.docs;

import net.jqwik.api.*;

import java.io.*;
import java.util.*;

class VariableTypedPropertyExamples {

	@Property(reporting = Reporting.GENERATED)
	<T> boolean unboundedGenericTypesAreResolved(@ForAll List<T> items, @ForAll T newItem) {
		items.add(newItem);
		return items.contains(newItem);
	}

	@Property(reporting = Reporting.GENERATED)
	<T extends Serializable & Comparable> void someBoundedGenericTypesCanBeResolved(@ForAll List<T> items, @ForAll T newItem) {
	}

	@Property(reporting = Reporting.GENERATED)
	<T extends Date> void otherBoundedGenericTypesCannotBeResolved(@ForAll List<T> items, @ForAll T newItem) {
	}

	@Property(reporting = Reporting.GENERATED)
	void wildcardTypesAreResolved(@ForAll List<?> items) {
	}

	@Property(reporting = Reporting.GENERATED)
	void someWildcardTypesWithUpperBoundsCanBeResolved(@ForAll List<? extends Serializable> items) {
	}

	@Property(reporting = Reporting.GENERATED)
	void otherWildcardTypesWithUpperBoundsCannotBeResolved(@ForAll List<? extends Date> items) {
	}

	@Property(reporting = Reporting.GENERATED)
	void wildcardTypesWithLowerBoundsCannotBeResolved(@ForAll List<? super String> items) {
	}

}
