package com.lotline.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Quantities {
  private Quantities() {}

  public static BigDecimal parse(String value) {
    return parse(value, false);
  }

  public static BigDecimal parse(String value, boolean zeroAllowed) {
    if (value == null || !value.matches("[0-9]{1,17}(\\.[0-9]{1,3})?"))
      throw DomainError.invalid(
          "QUANTITY_PRECISION",
          "Use a decimal string with at most 17 whole digits and 3 decimal places. No rounding is"
              + " performed.");
    BigDecimal n = new BigDecimal(value).setScale(3, RoundingMode.UNNECESSARY);
    if (n.signum() < 0 || (!zeroAllowed && n.signum() == 0))
      throw DomainError.invalid(
          "QUANTITY_RANGE",
          "Quantity must be " + (zeroAllowed ? "zero or positive." : "positive."));
    return n;
  }

  public static String text(BigDecimal n) {
    return n.stripTrailingZeros().toPlainString();
  }
}
