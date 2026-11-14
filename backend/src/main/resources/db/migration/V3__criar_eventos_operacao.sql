CREATE TABLE eventos_operacao (
    id UUID PRIMARY KEY,
    operacao_id UUID NOT NULL REFERENCES operacoes_portabilidade (id),
    tipo VARCHAR(40) NOT NULL CHECK (tipo IN ('OPERACAO_CRIADA_LOCALMENTE', 'TENTATIVA_ENVIO_CENTRAL',
        'PROTOCOLO_RECEBIDO', 'FALHA_INTEGRACAO', 'RESULTADO_INDETERMINADO')),
    descricao VARCHAR(500) NOT NULL,
    data_criacao TIMESTAMP WITH TIME ZONE NOT NULL,
    status_integracao_anterior VARCHAR(30) CHECK (status_integracao_anterior IN
        ('PENDENTE_ENVIO', 'ENVIADA', 'FALHA_ENVIO', 'ENVIO_INDETERMINADO')),
    status_integracao_novo VARCHAR(30) CHECK (status_integracao_novo IN
        ('PENDENTE_ENVIO', 'ENVIADA', 'FALHA_ENVIO', 'ENVIO_INDETERMINADO')),
    status_operacao_anterior VARCHAR(20) CHECK (status_operacao_anterior IN
        ('PENDENTE', 'CONFIRMADA', 'REJEITADA', 'FALHA_TECNICA')),
    status_operacao_novo VARCHAR(20) CHECK (status_operacao_novo IN
        ('PENDENTE', 'CONFIRMADA', 'REJEITADA', 'FALHA_TECNICA')),
    protocolo_central UUID
);
CREATE INDEX idx_eventos_operacao_historico ON eventos_operacao (operacao_id, data_criacao, id);
