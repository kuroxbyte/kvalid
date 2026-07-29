package dev.kvalid.samples.spring.orders;

import dev.kvalid.annotations.Min;
import dev.kvalid.annotations.NotBlank;
import dev.kvalid.annotations.Validated;

/**
 * Variante <b>Java</b>: el mismo contrato que el DTO Kotlin, procesado por APT en vez de KSP.
 *
 * <p>javac genera {@code CreateOrderRequestValidator.validate(obj)} y —por
 * {@code -Akvalid.componentModel=spring}— un {@code CreateOrderRequestKValidator} anotado
 * {@code @Component}. Spring lo recoge igual que el de Kotlin: <b>la auto-configuración no
 * distingue de qué frontend viene</b>.
 */
@Validated
public record CreateOrderRequest(@NotBlank String reference, @Min(1) int quantity) {}
