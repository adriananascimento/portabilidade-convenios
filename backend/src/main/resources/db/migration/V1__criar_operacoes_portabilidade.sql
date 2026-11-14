CREATE TABLE operacoes_portabilidade (
    id UUID PRIMARY KEY,
    chave_idempotencia UUID NOT NULL UNIQUE,
    protocolo_central UUID,
    beneficiario_id VARCHAR(100) NOT NULL,
    banco VARCHAR(100) NOT NULL,
    agencia VARCHAR(100) NOT NULL,
    conta VARCHAR(100) NOT NULL,
    status_integracao VARCHAR(30) NOT NULL
        CHECK (status_integracao IN ('PENDENTE_ENVIO', 'ENVIADA', 'FALHA_ENVIO', 'ENVIO_INDETERMINADO')),
    status_operacao VARCHAR(20)
        CHECK (status_operacao IN ('PENDENTE', 'CONFIRMADA', 'REJEITADA')),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    data_atualizacao TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE operacoes_portabilidade_parcelas (
    operacao_id UUID NOT NULL REFERENCES operacoes_portabilidade (id),
    parcela_id VARCHAR(100) NOT NULL,
    PRIMARY KEY (operacao_id, parcela_id)
);
