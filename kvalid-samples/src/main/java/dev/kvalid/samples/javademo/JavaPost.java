package dev.kvalid.samples.javademo;

import dev.kvalid.annotations.NotBlank;
import dev.kvalid.annotations.Validated;
import java.util.List;

/** Element-level en Java: {@code List<@NotBlank String>}. Genera {@code JavaPostValidator}. */
@Validated
public record JavaPost(List<@NotBlank String> tags) {}
