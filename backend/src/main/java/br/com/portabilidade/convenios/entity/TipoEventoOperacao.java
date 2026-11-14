package br.com.portabilidade.convenios.entity;

public enum TipoEventoOperacao {
    OPERACAO_CRIADA_LOCALMENTE,
    TENTATIVA_ENVIO_CENTRAL,
    PROTOCOLO_RECEBIDO,
    FALHA_INTEGRACAO,
    RESULTADO_INDETERMINADO
}
