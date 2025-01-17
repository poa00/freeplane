package org.freeplane.features.filter;

public interface EditDistanceStringMatchingStrategy extends
		StringMatchingStrategy {

	int distance();

	float matchProb();

	void init(final String searchTerm, final String searchText, final Type matchType);

}

