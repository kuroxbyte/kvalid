package dev.kvalid.samples.spring.orders;

import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Controlador Java. Igual que en Kotlin: solo {@code @Valid}, sin llamar a validate(). */
@RestController
public class OrdersController {

    @PostMapping("/orders")
    public Map<String, Object> create(@Valid @RequestBody CreateOrderRequest req) {
        return Map.of("reference", req.reference(), "quantity", req.quantity());
    }
}
