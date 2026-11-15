package br.com.portabilidade.convenios.seguranca;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

public class AssinaturaHmac {

    public static String assinar(String mensagem, String chaveSecreta)
            throws Exception {

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec chave = new SecretKeySpec(chaveSecreta.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        mac.init(chave);

        byte[] assinatura = mac.doFinal(mensagem.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(assinatura);
    }

    public static boolean verificar(
            String mensagem,
            String chaveSecreta,
            String assinaturaRecebida) throws Exception {

        if (assinaturaRecebida == null) {
            return false;
        }

        String assinaturaCalculada = assinar(mensagem, chaveSecreta);
        byte[] recebida;

        try {
            recebida = HexFormat.of().parseHex(assinaturaRecebida);
        } catch (IllegalArgumentException e) {
            return false;
        }

        byte[] calculada = HexFormat.of().parseHex(assinaturaCalculada);
        return MessageDigest.isEqual(calculada, recebida);
    }
}
