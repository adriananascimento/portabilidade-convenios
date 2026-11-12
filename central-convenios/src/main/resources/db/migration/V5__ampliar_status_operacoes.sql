ALTER TABLE operacoes_portabilidade
DROP CONSTRAINT operacoes_portabilidade_status_check;

ALTER TABLE operacoes_portabilidade
    ADD CONSTRAINT ck_operacoes_status
        CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'REJEITADA'));
