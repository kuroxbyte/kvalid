package dev.kvalid.samples.javademo;

import dev.kvalid.runtime.ValidationResult;
import dev.kvalid.runtime.Violation;
import java.util.List;

/**
 * Ejemplos de la variante Java (APT). Usa los {@code *Validator} generados en este mismo módulo.
 * Se compilan junto con el código generado en la misma invocación de javac.
 */
public final class JavaSamples {
    private JavaSamples() {}

    public static void run() {
        print("JavaUser válido", JavaUserValidator.validate(new JavaUser("Ana", 30, "ana@x.com")));
        print("JavaUser inválido", JavaUserValidator.validate(new JavaUser("", 15, "nope")));
        print("JavaPost tags", JavaPostValidator.validate(new JavaPost(List.of("kotlin", " ", "apt"))));
    }

    private static <T> void print(String label, ValidationResult<T> result) {
        List<Violation> violations = result.violationsOrEmpty();
        if (violations.isEmpty()) {
            System.out.println(label + " → OK");
        } else {
            StringBuilder sb = new StringBuilder(label).append(" → ");
            for (int i = 0; i < violations.size(); i++) {
                Violation v = violations.get(i);
                if (i > 0) sb.append(", ");
                sb.append(v.getPath()).append('=').append(v.getCode());
            }
            System.out.println(sb);
        }
    }
}
