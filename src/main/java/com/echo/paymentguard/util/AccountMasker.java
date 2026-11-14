package com.echo.paymentguard.util;

public final class AccountMasker {
    private AccountMasker() {
    }

    public static String mask(String account) {
        if (account == null) return null;
        if (account.length() <= 4) return "X".repeat(Math.max(4, account.length()));
        char[] masked = account.toCharArray();
        int visibleDigits = 0;
        for (int i = masked.length - 1; i >= 0; i--) {
            if (Character.isDigit(masked[i]) && visibleDigits < 4) {
                visibleDigits++;
            } else {
                masked[i] = 'X';
            }
        }
        return new String(masked);
    }
}
