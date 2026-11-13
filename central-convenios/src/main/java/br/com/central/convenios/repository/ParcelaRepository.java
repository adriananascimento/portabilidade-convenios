package br.com.central.convenios.repository;

import br.com.central.convenios.entity.Parcela;
import br.com.central.convenios.entity.StatusAutorizacao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Set;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcelaRepository extends JpaRepository<Parcela, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Parcela p where p.id in :ids order by p.id")
    List<Parcela> buscarParaReserva(@Param("ids") Set<String> ids);

    @Query("""
            select p from Parcela p
            where p.beneficiarioId = :beneficiarioId
              and p.dataDesconto >= :dataReferencia
              and not exists (select r.parcelaId from ReservaParcela r where r.parcelaId = p.id)
              and exists (select a.id from Autorizacao a
                          where a.beneficiarioId = p.beneficiarioId
                            and a.convenioId = p.convenioId and a.status = :status)
            order by p.dataDesconto asc, p.id asc
            """)
    List<Parcela> consultarAgenda(@Param("beneficiarioId") String beneficiarioId,
                                 @Param("dataReferencia") LocalDate dataReferencia,
                                 @Param("status") StatusAutorizacao status);

    @Query("select case when not exists (select r.parcelaId from ReservaParcela r where r.parcelaId = p.id) " +
            "then true else false end " +
            "from Parcela p where p.id = :parcelaId")
    Boolean parcelaDisponivel(@Param("parcelaId") String parcelaId);
}
