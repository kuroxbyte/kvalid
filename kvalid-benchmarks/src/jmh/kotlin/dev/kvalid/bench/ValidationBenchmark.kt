package dev.kvalid.bench

import jakarta.validation.Validation
import jakarta.validation.Validator
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State

/**
 * kvalid (codegen) vs Hibernate Validator (reflexión), validando el MISMO objeto válido.
 * Ejecutar: `./gradlew :kvalid-benchmarks:jmh`.
 */
@State(Scope.Benchmark)
open class ValidationBenchmark {

    private val kvalidUser = KvalidUser("Ann", 30, "ann@x.com")
    private val hibernateUser = HibernateUser("Ann", 30, "ann@x.com")
    private val hibernate: Validator = Validation.buildDefaultValidatorFactory().validator

    @Benchmark
    fun kvalid(): Any = kvalidUser.validate()

    @Benchmark
    fun hibernateValidator(): Any = hibernate.validate(hibernateUser)
}
