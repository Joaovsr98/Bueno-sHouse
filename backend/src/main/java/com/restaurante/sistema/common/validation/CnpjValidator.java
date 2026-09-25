package com.restaurante.sistema.common.validation;

/**
 * Validacao de CNPJ pelo algoritmo padrao de digitos verificadores (RN07).
 * Aceita com ou sem mascara; internamente so considera os digitos.
 */
public final class CnpjValidator {

    private static final int[] FIRST_WEIGHTS = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
    private static final int[] SECOND_WEIGHTS = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

    private CnpjValidator() {}

    public static boolean isValid(String rawCnpj) {
        if (rawCnpj == null) {
            return false;
        }
        String cnpj = rawCnpj.replaceAll("\\D", "");
        if (cnpj.length() != 14 || cnpj.chars().distinct().count() == 1) {
            return false; // 14 digitos e nao todos iguais (ex.: 00000000000000)
        }

        int[] digits = cnpj.chars().map(c -> c - '0').toArray();
        int firstCheck = checkDigit(digits, FIRST_WEIGHTS, 12);
        if (firstCheck != digits[12]) {
            return false;
        }
        int secondCheck = checkDigit(digits, SECOND_WEIGHTS, 13);
        return secondCheck == digits[13];
    }

    private static int checkDigit(int[] digits, int[] weights, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += digits[i] * weights[i];
        }
        int remainder = sum % 11;
        return remainder < 2 ? 0 : 11 - remainder;
    }
}
