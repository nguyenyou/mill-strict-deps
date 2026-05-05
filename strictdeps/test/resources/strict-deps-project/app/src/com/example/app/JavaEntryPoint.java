package com.example.app;

import com.example.helper.Helper;

public final class JavaEntryPoint {
  private JavaEntryPoint() {}

  public static String value() {
    return Helper.message();
  }
}
