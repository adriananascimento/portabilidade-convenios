package br.com.portabilidade.convenios.repository;

import br.com.portabilidade.convenios.entity.OperacaoPortabilidade;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacaoPortabilidadeRepository extends JpaRepository<OperacaoPortabilidade, UUID> {
    Optional<OperacaoPortabilidade> findByChaveIdempotencia(UUID chaveIdempotencia);
}
