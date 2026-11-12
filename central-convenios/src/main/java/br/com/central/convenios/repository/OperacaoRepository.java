package br.com.central.convenios.repository;

import br.com.central.convenios.entity.OperacaoPortabilidade;
import java.util.Optional;
import java.util.List;
import java.time.OffsetDateTime;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperacaoRepository extends JpaRepository<OperacaoPortabilidade, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OperacaoPortabilidade o where o.protocolo = :protocolo")
    Optional<OperacaoPortabilidade> buscarParaProcessamento(@Param("protocolo") UUID protocolo);

    @Query(value = """
            SELECT protocolo FROM operacoes_portabilidade
            WHERE status = 'PENDENTE' AND data_criacao <= :limite
            ORDER BY data_criacao, protocolo LIMIT 100
            """, nativeQuery = true)
    List<UUID> buscarPendentes(@Param("limite") OffsetDateTime limite);

    @EntityGraph(attributePaths = "parcelasIds")
    Optional<OperacaoPortabilidade> findByChaveIdempotencia(UUID chave);
}
