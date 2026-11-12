package br.com.central.convenios.repository;

import br.com.central.convenios.entity.OperacaoPortabilidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacaoRepository extends JpaRepository<OperacaoPortabilidade, UUID> {
    @EntityGraph(attributePaths = "parcelasIds")
    Optional<OperacaoPortabilidade> findByChaveIdempotencia(UUID chave);
}
