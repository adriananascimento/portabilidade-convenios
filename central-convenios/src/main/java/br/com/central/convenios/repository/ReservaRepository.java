package br.com.central.convenios.repository;

import br.com.central.convenios.entity.ReservaParcela;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservaRepository extends JpaRepository<ReservaParcela, String> {
    @Modifying
    @Query(value = """
            INSERT INTO reservas_parcelas (parcela_id, operacao_protocolo)
            SELECT parcela_id, operacao_protocolo FROM operacoes_parcelas
            WHERE operacao_protocolo = :protocolo
            """, nativeQuery = true)
    int reservar(@Param("protocolo") UUID protocolo);

    @Modifying
    @Query("delete from ReservaParcela r where r.operacaoProtocolo = :protocolo")
    int liberar(@Param("protocolo") UUID protocolo);
}
