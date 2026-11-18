package com.baymc.auth.backend.type;

import com.baymc.auth.BayMcAuth;
import com.baymc.auth.backend.Endpoint;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import java.util.function.Function;

public class StringEndpoint extends Endpoint {

  private String value;
  private Function<String, String> function;

  public StringEndpoint(BayMcAuth plugin, String type, Function<String, String> function) {
    super(plugin, type, null);
    this.function = function;
  }

  public StringEndpoint(BayMcAuth plugin, String type, String username, String value) {
    super(plugin, type, username);
    this.value = value;
  }

  @Override
  public void writeContents(ByteArrayDataOutput output) {
    output.writeUTF(this.value);
  }

  @Override
  public void readContents(ByteArrayDataInput input) {
    this.value = this.function.apply(this.username);
  }

  @Override
  public String toString() {
    return "StringEndpoint{"
        + "username='" + this.username + '\''
        + ", value=" + this.value
        + '}';
  }
}
