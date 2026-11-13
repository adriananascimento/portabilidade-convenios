ALTER TABLE operacoes_portabilidade
    ADD COLUMN tentativas_processamento INTEGER NOT NULL DEFAULT 0
        CHECK (tentativas_processamento BETWEEN 0 AND 5),
    ADD COLUMN proxima_tentativa_em TIMESTAMP WITH TIME ZONE,
    ADD COLUMN ultimo_erro VARCHAR(255);

ALTER TABLE operacoes_portabilidade DROP CONSTRAINT ck_operacoes_status;
ALTER TABLE operacoes_portabilidade ADD CONSTRAINT ck_operacoes_status
    CHECK (status IN ('PENDENTE', 'CONFIRMADA', 'REJEITADA', 'FALHA_TECNICA'));

CREATE INDEX idx_operacoes_pendentes_processamento
    ON operacoes_portabilidade (data_criacao, protocolo, proxima_tentativa_em)
    WHERE status = 'PENDENTE';
