package dev.sky.skysdomains.domain;

/** One phase of a domain lifecycle: name (matches the sequence JSON) and length in ticks. */
public record DomainPhase(String name, int ticks) {
	public static final String OPEN = "open";
	public static final String ACTIVE = "active";
	public static final String CLOSE = "close";
}
