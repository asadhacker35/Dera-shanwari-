package com.example.model;

import java.io.Serializable;

public class PaymentMethodConfig implements Serializable {
    public static final String METHOD_COD = "Cash on Delivery";
    public static final String METHOD_JAZZCASH = "JazzCash";
    public static final String METHOD_EASYPAISA = "EasyPaisa";

    private String methodId;
    private String displayName;
    private String accountTitle;
    private String accountNumber;
    private boolean enabled;
    private String instructions;

    public PaymentMethodConfig() {}

    public PaymentMethodConfig(String methodId, String displayName, String accountTitle,
                               String accountNumber, boolean enabled, String instructions) {
        this.methodId = methodId;
        this.displayName = displayName;
        this.accountTitle = accountTitle;
        this.accountNumber = accountNumber;
        this.enabled = enabled;
        this.instructions = instructions;
    }

    public String getMethodId() { return methodId; }
    public void setMethodId(String methodId) { this.methodId = methodId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getAccountTitle() { return accountTitle; }
    public void setAccountTitle(String accountTitle) { this.accountTitle = accountTitle; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getInstructions() { return instructions; }
    public void setInstructions(String instructions) { this.instructions = instructions; }
}
