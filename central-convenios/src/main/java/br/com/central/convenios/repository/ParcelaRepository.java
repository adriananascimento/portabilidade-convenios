package br.com.central.convenios.repository;

import br.com.central.convenios.entity.Parcela;
import br.com.central.convenios.entity.StatusAutorizacao;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParcelaRepository extends JpaRepository<Parcela, String> {
    @Query("""
            select p from Parcela p
            where p.beneficiarioId = :beneficiarioId
              and p.dataDesconto >= :dataReferencia
              and exists (select a.id from Autorizacao a
                          where a.beneficiarioId = p.beneficiarioId
                            and a.convenioId = p.convenioId and a.status = :status)
            order by p.dataDesconto asc, p.id asc
            """)
    List<Parcela> consultarAgenda(@Param("beneficiarioId") String beneficiarioId,
                                 @Param("dataReferencia") LocalDate dataReferencia,
                                 @Param("status") StatusAutorizacao status);
}
