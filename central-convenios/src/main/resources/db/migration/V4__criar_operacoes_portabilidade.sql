CREATE TABLE operacoes_portabilidade (
    protocolo UUID PRIMARY KEY,
    chave_idempotencia UUID NOT NULL UNIQUE,
    beneficiario_id VARCHAR(100) NOT NULL,
    banco VARCHAR(100) NOT NULL,
    agencia VARCHAR(100) NOT NULL,
    conta VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDENTE')),
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE operacoes_parcelas (
    operacao_protocolo UUID NOT NULL REFERENCES operacoes_portabilidade (protocolo),
    parcela_id VARCHAR(100) NOT NULL REFERENCES parcelas (id),
    PRIMARY KEY (operacao_protocolo, parcela_id),
    CONSTRAINT uk_operacoes_parcelas_parcela UNIQUE (parcela_id)
);

CREATE INDEX idx_operacoes_beneficiario ON operacoes_portabilidade (beneficiario_id);
