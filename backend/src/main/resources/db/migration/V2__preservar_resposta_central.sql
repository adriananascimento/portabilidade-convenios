ALTER TABLE operacoes_portabilidade ADD COLUMN data_criacao_central TIMESTAMP WITH TIME ZONE;
ALTER TABLE operacoes_portabilidade DROP CONSTRAINT operacoes_portabilidade_status_operacao_check;
ALTER TABLE operacoes_portabilidade ADD CONSTRAINT operacoes_portabilidade_status_operacao_check
    CHECK (status_operacao IN ('PENDENTE', 'CONFIRMADA', 'REJEITADA', 'FALHA_TECNICA'));
