package dev.kvalid.samples.javademo;

import dev.kvalid.annotations.Email;
import dev.kvalid.annotations.Min;
import dev.kvalid.annotations.NotBlank;
import dev.kvalid.annotations.Size;
import dev.kvalid.annotations.Validated;

/** Genera {@code JavaUserValidator.validate(obj)} vía kvalid-apt (APT). */
@Validated
public record JavaUser(
    @NotBlank @Size(max = 20) String name,
    @Min(18) int age,
    @Email String email
) {}
